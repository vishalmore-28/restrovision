import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React, { useState } from 'react';
import { render, act } from '@testing-library/react';
import { WebSocketService, wsService } from '../services/websocket';
import { useWebSocket } from '../hooks/useWebSocket';

let mockClientInstance = null;
let activeSubscriptionsMap = new Map();

vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation(function (config) {
      mockClientInstance = {
        activate: vi.fn(),
        deactivate: vi.fn(),
        subscribe: vi.fn((destination, callback) => {
          const sub = {
            id: 'sub-' + Math.random().toString(36).substring(7),
            destination,
            callback,
            unsubscribe: vi.fn(() => {
              activeSubscriptionsMap.delete(destination);
            }),
          };
          activeSubscriptionsMap.set(destination, sub);
          return sub;
        }),
        publish: vi.fn(),
        connectHeaders: {},
        ...config,
      };
      return mockClientInstance;
    }),
  };
});

describe('BUG-023: WebSocket Subscription Lifecycle & Authorization (Frontend)', () => {
  beforeEach(() => {
    activeSubscriptionsMap.clear();
    mockClientInstance = null;
  });

  afterEach(() => {
    vi.clearAllMocks();
    wsService.disconnect(true);
  });

  // =========================================================================
  // Requirement 3.1: Subscription requested while disconnected
  // =========================================================================
  it('1. retains subscription as pending when requested while STOMP client is disconnected', () => {
    const service = new WebSocketService();
    expect(service.connected).toBe(false);

    const callback = vi.fn();
    const unsub = service.subscribe('/topic/restaurants/1/orders', callback);

    expect(service.subscribers.has('/topic/restaurants/1/orders')).toBe(true);
    expect(service.subscribers.get('/topic/restaurants/1/orders').size).toBe(1);
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/orders')).toBe(false);
    expect(mockClientInstance).toBeNull();

    unsub();
  });

  // =========================================================================
  // Requirement 3.2: Pending subscription flushed on successful connection
  // =========================================================================
  it('2. flushes pending subscriptions exactly once when STOMP connection activates', () => {
    const service = new WebSocketService();

    const cb1 = vi.fn();
    const cb2 = vi.fn();
    service.subscribe('/topic/restaurants/1/orders', cb1);
    service.subscribe('/topic/restaurants/1/tables', cb2);

    expect(service.connected).toBe(false);

    // Establish connection
    service.connect(1);
    expect(mockClientInstance).not.toBeNull();
    expect(mockClientInstance.activate).toHaveBeenCalled();

    // Trigger onConnect callback
    service.client.onConnect();

    expect(service.connected).toBe(true);
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(2);
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/orders')).toBe(true);
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/tables')).toBe(true);

    // Redundant flush should NOT duplicate subscriptions
    service._flushPendingSubscriptions();
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(2);
  });

  // =========================================================================
  // Requirement 3.3: No duplicate subscription after reconnect
  // =========================================================================
  it('3. prevents duplicate subscriptions after reconnect and preserves single broker subscription per topic', () => {
    const service = new WebSocketService();
    service.connect(1);
    service.client.onConnect();

    const cb1 = vi.fn();
    const cb2 = vi.fn();

    // First subscriber creates broker subscription
    service.subscribe('/topic/restaurants/1/alerts', cb1);
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);

    // Second subscriber multiplexes onto existing broker subscription
    service.subscribe('/topic/restaurants/1/alerts', cb2);
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);

    // Simulate disconnect
    service.client.onDisconnect();
    expect(service.connected).toBe(false);
    expect(service.activeStompSubscriptions.size).toBe(0);

    // Simulate reconnect
    service.client.onConnect();
    expect(service.connected).toBe(true);

    // Reconnected: broker subscribe called once more for the new session
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(2);
    expect(service.activeStompSubscriptions.size).toBe(1);

    // Dispatch message: both callbacks receive it
    const brokerHandler = activeSubscriptionsMap.get('/topic/restaurants/1/alerts').callback;
    brokerHandler({ body: JSON.stringify({ alertId: 99, level: 'CRITICAL' }) });

    expect(cb1).toHaveBeenCalledWith({ alertId: 99, level: 'CRITICAL' });
    expect(cb2).toHaveBeenCalledWith({ alertId: 99, level: 'CRITICAL' });
  });

  // =========================================================================
  // Requirement 3.4: Pending subscription removed when component unmounts
  // =========================================================================
  it('4. removes pending subscription when component unmounts before connection opens', () => {
    const service = new WebSocketService();
    expect(service.connected).toBe(false);

    const cb = vi.fn();
    const unsub = service.subscribe('/topic/restaurants/1/tasks', cb);

    expect(service.subscribers.has('/topic/restaurants/1/tasks')).toBe(true);

    // Component unmounts prior to connect
    unsub();

    expect(service.subscribers.has('/topic/restaurants/1/tasks')).toBe(false);

    // Connection now opens
    service.connect(1);
    service.client.onConnect();

    // Broker subscribe was NEVER called for /topic/restaurants/1/tasks
    expect(mockClientInstance.subscribe).not.toHaveBeenCalled();
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/tasks')).toBe(false);
  });

  // =========================================================================
  // Requirement 3.5: Active subscription unsubscribed on unmount
  // =========================================================================
  it('5. unsubscribes active STOMP broker subscription when component unmounts', () => {
    const service = new WebSocketService();
    service.connect(1);
    service.client.onConnect();

    const cb = vi.fn();
    const unsub = service.subscribe('/topic/restaurants/1/orders', cb);

    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/orders')).toBe(true);
    const stompSub = activeSubscriptionsMap.get('/topic/restaurants/1/orders');

    // Component unmounts
    unsub();

    expect(stompSub.unsubscribe).toHaveBeenCalledTimes(1);
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/orders')).toBe(false);
    expect(service.subscribers.has('/topic/restaurants/1/orders')).toBe(false);
  });

  // =========================================================================
  // Requirement 3.6: Repeated cleanup is safe
  // =========================================================================
  it('6. ensures repeated or redundant cleanup calls are completely idempotent and safe', () => {
    const service = new WebSocketService();
    service.connect(1);
    service.client.onConnect();

    const unsub = service.subscribe('/topic/restaurants/1/tables', vi.fn());
    const stompSub = activeSubscriptionsMap.get('/topic/restaurants/1/tables');

    // First call
    expect(() => unsub()).not.toThrow();
    expect(stompSub.unsubscribe).toHaveBeenCalledTimes(1);

    // Second call
    expect(() => unsub()).not.toThrow();
    expect(stompSub.unsubscribe).toHaveBeenCalledTimes(1);

    // Third call
    expect(() => unsub()).not.toThrow();
    expect(stompSub.unsubscribe).toHaveBeenCalledTimes(1);
  });

  // =========================================================================
  // Requirement 3.7: Callback identity changes do not cause unnecessary resubscription
  // =========================================================================
  it('7. ensures callback identity changes in useWebSocket do not trigger resubscription', () => {
    const subscribeSpy = vi.spyOn(wsService, 'subscribe');

    function TestComponent({ callback }) {
      useWebSocket('/topic/restaurants/1/orders', callback);
      return <div>Test</div>;
    }

    const cb1 = vi.fn();
    const { rerender } = render(<TestComponent callback={cb1} />);

    expect(subscribeSpy).toHaveBeenCalledTimes(1);

    // Re-render with brand new arrow function
    const cb2 = vi.fn();
    rerender(<TestComponent callback={cb2} />);

    // Subscription count must NOT increase
    expect(subscribeSpy).toHaveBeenCalledTimes(1);

    // Re-render again with another new callback
    const cb3 = vi.fn();
    rerender(<TestComponent callback={cb3} />);
    expect(subscribeSpy).toHaveBeenCalledTimes(1);

    subscribeSpy.mockRestore();
  });

  // =========================================================================
  // Requirement 3.8: Latest callback is invoked when a message arrives
  // =========================================================================
  it('8. executes the latest callback closure when a message arrives after state changes', () => {
    let capturedHandler = null;
    const subscribeSpy = vi.spyOn(wsService, 'subscribe').mockImplementation((topic, handler) => {
      capturedHandler = handler;
      return () => { capturedHandler = null; };
    });

    function CounterListener() {
      const [count, setCount] = useState(0);
      const [lastMsg, setLastMsg] = useState(null);

      useWebSocket('/topic/restaurants/1/alerts', (msg) => {
        setLastMsg({ msg, capturedCount: count });
      });

      return (
        <div>
          <button data-testid="inc" onClick={() => setCount((c) => c + 1)}>Increment</button>
          <div data-testid="val">{count}</div>
          <div data-testid="msg">{lastMsg ? `${lastMsg.msg.text}-${lastMsg.capturedCount}` : 'none'}</div>
        </div>
      );
    }

    const { getByTestId } = render(<CounterListener />);

    // Increment count from 0 to 2
    act(() => {
      getByTestId('inc').click();
    });
    act(() => {
      getByTestId('inc').click();
    });
    expect(getByTestId('val').textContent).toBe('2');

    // Simulate incoming STOMP message
    act(() => {
      capturedHandler({ text: 'FireAlarm' });
    });

    // Callback should execute with current count (2), not stale closure (0)
    expect(getByTestId('msg').textContent).toBe('FireAlarm-2');

    subscribeSpy.mockRestore();
  });

  // =========================================================================
  // Requirement 3.9: Multiple components independently subscribe and unsubscribe
  // =========================================================================
  it('9. supports multiple components independently subscribing and unsubscribing with ref-counting', () => {
    const service = new WebSocketService();
    service.connect(1);
    service.client.onConnect();

    const cb1 = vi.fn();
    const cb2 = vi.fn();

    // Component 1 mounts
    const unsub1 = service.subscribe('/topic/restaurants/1/alerts', cb1);
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1);
    const stompSub = activeSubscriptionsMap.get('/topic/restaurants/1/alerts');

    // Component 2 mounts
    const unsub2 = service.subscribe('/topic/restaurants/1/alerts', cb2);
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(1); // Multiplexed, no duplicate

    // Dispatch message to topic
    const handler = stompSub.callback;
    handler({ body: JSON.stringify({ message: 'Kitchen pass ready' }) });
    expect(cb1).toHaveBeenCalledWith({ message: 'Kitchen pass ready' });
    expect(cb2).toHaveBeenCalledWith({ message: 'Kitchen pass ready' });

    // Component 1 unmounts: broker subscription remains active
    unsub1();
    expect(stompSub.unsubscribe).not.toHaveBeenCalled();
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/alerts')).toBe(true);

    // Message still delivered to Component 2
    handler({ body: JSON.stringify({ message: 'Table 4 seated' }) });
    expect(cb2).toHaveBeenCalledWith({ message: 'Table 4 seated' });
    expect(cb1).toHaveBeenCalledTimes(1); // cb1 did NOT receive second message

    // Component 2 unmounts: broker subscription is now torn down
    unsub2();
    expect(stompSub.unsubscribe).toHaveBeenCalledTimes(1);
    expect(service.activeStompSubscriptions.has('/topic/restaurants/1/alerts')).toBe(false);
  });

  // =========================================================================
  // Requirement 3.10: Connection failure and recovery preserve correct subscription state
  // =========================================================================
  it('10. preserves subscription state across connection failure and recovery', () => {
    const service = new WebSocketService();
    service.connect(1);
    service.client.onConnect();

    const cbOrders = vi.fn();
    const cbTables = vi.fn();

    service.subscribe('/topic/restaurants/1/orders', cbOrders);
    service.subscribe('/topic/restaurants/1/tables', cbTables);

    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(2);

    // Simulate broker error / transport disconnect
    service.client.onStompError({ headers: { message: 'Heartbeat timeout' } });
    expect(service.connected).toBe(false);
    expect(service.activeStompSubscriptions.size).toBe(0);

    // Reconnect
    service.client.onConnect();
    expect(service.connected).toBe(true);
    expect(mockClientInstance.subscribe).toHaveBeenCalledTimes(4); // 2 original + 2 re-subscribed

    // Both topics functional after recovery
    const ordersHandler = activeSubscriptionsMap.get('/topic/restaurants/1/orders').callback;
    ordersHandler({ body: JSON.stringify({ orderId: 101 }) });
    expect(cbOrders).toHaveBeenCalledWith({ orderId: 101 });

    const tablesHandler = activeSubscriptionsMap.get('/topic/restaurants/1/tables').callback;
    tablesHandler({ body: JSON.stringify({ tableId: 5, status: 'OCCUPIED' }) });
    expect(cbTables).toHaveBeenCalledWith({ tableId: 5, status: 'OCCUPIED' });
  });

  // =========================================================================
  // React StrictMode Resilience
  // =========================================================================
  it('11. handles React StrictMode double-mount and unmount cleanly with zero orphaned subscriptions', () => {
    const subscribeSpy = vi.spyOn(wsService, 'subscribe');

    function StrictModeComponent() {
      useWebSocket('/topic/restaurants/1/tasks', vi.fn());
      return <div>Strict Mode Test</div>;
    }

    const { unmount } = render(
      <React.StrictMode>
        <StrictModeComponent />
      </React.StrictMode>
    );

    // In StrictMode, mount -> unmount -> remount occurs
    // Component is currently mounted with 1 active subscription
    expect(wsService.subscribers.has('/topic/restaurants/1/tasks')).toBe(true);

    unmount();

    // After unmounting, zero subscriptions remain
    expect(wsService.subscribers.has('/topic/restaurants/1/tasks')).toBe(false);

    subscribeSpy.mockRestore();
  });

  // =========================================================================
  // Tenant Switching & Logout
  // =========================================================================
  it('12. cleanly manages tenant switching and disconnect on logout', () => {
    const service = new WebSocketService();
    service.connect(1);

    expect(service.currentRestaurantId).toBe(1);

    service.subscribe('/topic/restaurants/1/tables', vi.fn());
    expect(service.subscribers.has('/topic/restaurants/1/tables')).toBe(true);

    // Logout: disconnect with clearSubscribers = true
    service.disconnect(true);
    expect(mockClientInstance.deactivate).toHaveBeenCalled();
    expect(service.connected).toBe(false);
    expect(service.subscribers.size).toBe(0);
    expect(service.activeStompSubscriptions.size).toBe(0);

    // Switch tenant to Restaurant 2
    service.connect(2);
    expect(service.currentRestaurantId).toBe(2);
  });
});
