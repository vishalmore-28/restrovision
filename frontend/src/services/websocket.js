import { Client } from '@stomp/stompjs';
import { getAuthToken } from './api';

class WebSocketService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.subscribers = new Map(); // topic -> Set of callbacks
    this.connectionListeners = new Set();
  }

  connect(restaurantId = 1) {
    if (this.client && this.connected) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${protocol}//${window.location.host}/ws`;

    this.client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: () => {}, // silent
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
      this.resubscribe(restaurantId);
    };

    this.client.onDisconnect = () => {
      this.connected = false;
      this.notifyConnectionState(false);
    };

    this.client.onStompError = (frame) => {
      console.warn('STOMP broker error:', frame.headers['message']);
      this.connected = false;
      this.notifyConnectionState(false);
    };

    try {
      this.client.activate();
    } catch (err) {
      console.warn('WebSocket connection error:', err);
    }
  }

  onConnectionChange(callback) {
    this.connectionListeners.add(callback);
    callback(this.connected);
    return () => this.connectionListeners.delete(callback);
  }

  notifyConnectionState(state) {
    this.connectionListeners.forEach((cb) => cb(state));
  }

  subscribe(topic, callback) {
    if (!this.subscribers.has(topic)) {
      this.subscribers.set(topic, new Set());
    }
    this.subscribers.get(topic).add(callback);

    if (this.connected && this.client) {
      this.client.subscribe(topic, (msg) => {
        try {
          const data = JSON.parse(msg.body);
          callback(data);
        } catch (_) {
          callback(msg.body);
        }
      });
    }

    return () => {
      const set = this.subscribers.get(topic);
      if (set) {
        set.delete(callback);
      }
    };
  }

  resubscribe(restaurantId) {
    const topics = [
      `/topic/restaurants/${restaurantId}/tables`,
      `/topic/restaurants/${restaurantId}/alerts`,
      `/topic/restaurants/${restaurantId}/orders`,
      `/topic/restaurants/${restaurantId}/tasks`,
    ];

    topics.forEach((topic) => {
      if (this.subscribers.has(topic)) {
        this.client.subscribe(topic, (msg) => {
          try {
            const data = JSON.parse(msg.body);
            this.subscribers.get(topic).forEach((cb) => cb(data));
          } catch (_) {
            this.subscribers.get(topic).forEach((cb) => cb(msg.body));
          }
        });
      }
    });
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.connected = false;
      this.notifyConnectionState(false);
    }
  }
}

export const wsService = new WebSocketService();
