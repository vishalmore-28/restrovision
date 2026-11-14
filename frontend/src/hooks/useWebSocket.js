import { useEffect, useRef } from 'react';
import { wsService } from '../services/websocket';

/**
 * Custom React hook for robust, leak-free WebSocket subscriptions.
 * 
 * Architectural Guarantees (BUG-023):
 * 1. Callback identity stability: Updating callback identity does NOT cause unsubscribe/resubscribe cycles.
 * 2. Latest handler execution: Message arrival always invokes the latest callback closure.
 * 3. React StrictMode safe: Handles dev double-mounting and unmounting cleanly.
 * 4. Automatic cleanup: Always unregisters when unmounted or when topic changes.
 * 5. Pending subscription queue: Seamlessly queues when disconnected and flushes upon connection.
 * 
 * @param {string} topic Destination topic to subscribe to
 * @param {function} callback Message handler function
 * @param {object} [options={}] Additional configuration options
 * @param {boolean} [options.enabled=true] Set to false to disable subscription
 */
export function useWebSocket(topic, callback, options = {}) {
  const { enabled = true } = options;
  const callbackRef = useRef(callback);

  // Maintain latest callback reference without triggering re-subscription
  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  useEffect(() => {
    if (!enabled || !topic) {
      return undefined;
    }

    // Stable proxy handler that routes messages to the current callbackRef
    const stableHandler = (data) => {
      if (typeof callbackRef.current === 'function') {
        try {
          callbackRef.current(data);
        } catch (err) {
          console.error(`[useWebSocket] Handler error for topic ${topic}:`, err);
        }
      }
    };

    const unsub = wsService.subscribe(topic, stableHandler);

    return () => {
      unsub();
    };
  }, [topic, enabled]);
}

export default useWebSocket;
