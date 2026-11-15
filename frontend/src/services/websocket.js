import { Client } from '@stomp/stompjs';
import { getAuthToken } from './api';

export class WebSocketService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.currentRestaurantId = 1;
    // Map of topic (string) -> Map of subId (number) -> callback (function)
    this.subscribers = new Map();
    // Map of topic (string) -> StompSubscription
    this.activeStompSubscriptions = new Map();
    this.connectionListeners = new Set();
    this._subCounter = 0;
  }

  connect(restaurantId = 1) {
    this.currentRestaurantId = restaurantId;

    if (this.client && this.connected) {
      this._flushPendingSubscriptions();
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    this.client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: () => {}, // silent in production
      beforeConnect: () => {
        const token = getAuthToken();
        if (token) {
          this.client.connectHeaders = {
            Authorization: `Bearer ${token}`
          };
        }
      }
    });

    this.client.onConnect = () => {
      this.connected = true;
      this.notifyConnectionState(true);
      this._flushPendingSubscriptions();
    };

    this.client.onDisconnect = () => {
      this.connected = false;
      this.activeStompSubscriptions.clear();
      this.notifyConnectionState(false);
    };

    this.client.onStompError = (frame) => {
      console.warn('STOMP broker error:', frame?.headers ? frame.headers['message'] : frame);
      this.connected = false;
      this.activeStompSubscriptions.clear();
      this.notifyConnectionState(false);
    };

    try {
      this.client.activate();
    } catch (err) {
      console.warn('WebSocket connection error:', err);
    }
  }

  onConnectionChange(callback) {
    if (typeof callback !== 'function') return () => {};
    this.connectionListeners.add(callback);
    try {
      callback(this.connected);
    } catch (err) {
      console.error('[wsService] Error in connection listener:', err);
    }
    return () => this.connectionListeners.delete(callback);
  }

  notifyConnectionState(state) {
    this.connectionListeners.forEach((cb) => {
      try {
        cb(state);
      } catch (err) {
        console.error('[wsService] Error notifying connection listener:', err);
      }
    });
  }

  /**
   * Subscribes a callback to a STOMP topic.
   * 
   * Features:
   * - Multiplexing: Multiple local subscribers to the same topic share a single underlying STOMP broker subscription.
   * - Pending queue: Subscriptions requested while disconnected are queued and flushed upon connection.
   * - Strict cleanup: Unsubscribing removes the callback; if no callbacks remain for the topic, the broker subscription is terminated.
   * - Idempotence: The returned unsubscribe function is safe to call multiple times.
   * 
   * @param {string} topic Destination topic
   * @param {function} callback Message handler
   * @returns {function} Idempotent unsubscribe function
   */
  subscribe(topic, callback) {
    if (!topic || typeof callback !== 'function') {
      return () => {};
    }

    const subId = ++this._subCounter;

    if (!this.subscribers.has(topic)) {
      this.subscribers.set(topic, new Map());
    }
    const topicSubs = this.subscribers.get(topic);
    topicSubs.set(subId, callback);

    // If already connected and no underlying broker subscription exists yet for this topic, create one
    if (this.connected && this.client && !this.activeStompSubscriptions.has(topic)) {
      this._subscribeBrokerTopic(topic);
    }

    let unsubscribed = false;

    // Return idempotent cleanup function
    return () => {
      if (unsubscribed) return;
      unsubscribed = true;

      const currentTopicSubs = this.subscribers.get(topic);
      if (currentTopicSubs) {
        currentTopicSubs.delete(subId);
        if (currentTopicSubs.size === 0) {
          this.subscribers.delete(topic);
          // If active broker subscription exists, tear it down
          const stompSub = this.activeStompSubscriptions.get(topic);
          if (stompSub) {
            try {
              if (typeof stompSub.unsubscribe === 'function') {
                stompSub.unsubscribe();
              }
            } catch (err) {
              console.warn(`[wsService] Error unsubscribing broker topic ${topic}:`, err);
            }
            this.activeStompSubscriptions.delete(topic);
          }
        }
      }
    };
  }

  _resolveTopic(topic) {
    if (!topic || typeof topic !== 'string') return topic;
    if (this.currentRestaurantId && this.currentRestaurantId !== 1 && /^\/topic\/restaurants\/1\//.test(topic)) {
      return topic.replace(/^\/topic\/restaurants\/1\//, `/topic/restaurants/${this.currentRestaurantId}/`);
    }
    return topic;
  }

  _subscribeBrokerTopic(topic) {
    if (!this.connected || !this.client || this.activeStompSubscriptions.has(topic)) {
      return;
    }

    const brokerTopic = this._resolveTopic(topic);
    try {
      const stompSub = this.client.subscribe(brokerTopic, (msg) => {
        this._dispatchMessage(topic, msg);
        if (brokerTopic !== topic) {
          this._dispatchMessage(brokerTopic, msg);
        }
      });
      this.activeStompSubscriptions.set(topic, stompSub);
    } catch (err) {
      console.warn(`[wsService] Failed to subscribe to broker topic ${brokerTopic}:`, err);
    }
  }

  _dispatchMessage(topic, msg) {
    let data = msg?.body;
    try {
      data = JSON.parse(msg.body);
    } catch (_) {
      // payload is plain text or raw string
    }

    const topicSubs = this.subscribers.get(topic);
    if (topicSubs && topicSubs.size > 0) {
      // Create snapshot array to prevent mutation issues during callback execution
      const callbacks = Array.from(topicSubs.values());
      for (const cb of callbacks) {
        try {
          cb(data);
        } catch (cbErr) {
          console.error(`[wsService] Error in subscriber callback for ${topic}:`, cbErr);
        }
      }
    }
  }

  _flushPendingSubscriptions() {
    if (!this.connected || !this.client) return;

    for (const topic of this.subscribers.keys()) {
      const subs = this.subscribers.get(topic);
      if (subs && subs.size > 0 && !this.activeStompSubscriptions.has(topic)) {
        this._subscribeBrokerTopic(topic);
      }
    }
  }

  /**
   * Resubscribes to all active topics or restaurant topics.
   * Backward-compatible with existing call sites.
   */
  resubscribe(restaurantId) {
    if (restaurantId) {
      this.currentRestaurantId = restaurantId;
    }
    this._flushPendingSubscriptions();
  }

  disconnect(clearSubscribers = false) {
    if (this.client) {
      try {
        this.client.deactivate();
      } catch (err) {
        console.warn('[wsService] Error deactivating STOMP client:', err);
      }
    }
    this.connected = false;
    this.activeStompSubscriptions.clear();
    if (clearSubscribers) {
      this.subscribers.clear();
    }
    this.notifyConnectionState(false);
  }
}

export const wsService = new WebSocketService();
export { useWebSocket } from '../hooks/useWebSocket';
