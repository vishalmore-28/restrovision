import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import {
  Flame,
  Clock,
  AlertCircle,
  CheckCircle2,
  ChefHat,
  Filter,
  Play,
  Check,
} from 'lucide-react';

const INITIAL_ORDERS = [
  {
    orderId: 1042,
    orderCode: '#1042',
    tableId: 1,
    status: 'PREPARING',
    createdAt: new Date(Date.now() - 1000 * 60 * 17).toISOString(),
    items: [
      { orderItemId: 1, itemName: 'Paneer Tikka Shashlik', quantity: 2, stationName: 'Tandoor & Grill', expectedPrepMinutes: 10, status: 'READY' },
      { orderItemId: 2, itemName: 'Tandoori Malai Chaap', quantity: 1, stationName: 'Tandoor & Grill', expectedPrepMinutes: 12, status: 'PREPARING' },
      { orderItemId: 3, itemName: 'Butter Garlic Naan', quantity: 3, stationName: 'Tandoor & Grill', expectedPrepMinutes: 6, status: 'CONFIRMED' },
    ],
  },
  {
    orderId: 1044,
    orderCode: '#1044',
    tableId: 5,
    status: 'PREPARING',
    createdAt: new Date(Date.now() - 1000 * 60 * 8).toISOString(),
    items: [
      { orderItemId: 4, itemName: 'Dal Makhani Slow-Cooked', quantity: 1, stationName: 'Curry & Saute', expectedPrepMinutes: 14, status: 'PREPARING' },
      { orderItemId: 5, itemName: 'Paneer Lababdar', quantity: 1, stationName: 'Curry & Saute', expectedPrepMinutes: 12, status: 'PREPARING' },
      { orderItemId: 6, itemName: 'Jeera Rice', quantity: 2, stationName: 'Curry & Saute', expectedPrepMinutes: 8, status: 'READY' },
    ],
  },
  {
    orderId: 1046,
    orderCode: '#1046',
    tableId: 8,
    status: 'READY',
    createdAt: new Date(Date.now() - 1000 * 60 * 3).toISOString(),
    items: [
      { orderItemId: 7, itemName: 'Gulab Jamun with Rabri', quantity: 2, stationName: 'Dessert & Cold', expectedPrepMinutes: 6, status: 'READY' },
    ],
  },
];

const INITIAL_STATIONS = [
  { stationId: 1, name: 'Tandoor & Grill' },
  { stationId: 2, name: 'Curry & Saute' },
  { stationId: 3, name: 'Dessert & Cold' },
];

const INITIAL_DELAYED = [
  { orderId: 1042, orderItemId: 2 }
];

export default function KitchenKds() {
  const [orders, setOrders] = useState(INITIAL_ORDERS);
  const [stations, setStations] = useState(INITIAL_STATIONS);
  const [selectedStation, setSelectedStation] = useState(null);
  const [delayedOrders, setDelayedOrders] = useState(INITIAL_DELAYED);
  const [kdsError, setKdsError] = useState('');

  const flashError = (msg) => {
    setKdsError(msg);
    setTimeout(() => setKdsError(''), 4000);
  };

  const loadData = () => {
    api.getOrders()
      .then((data) => { if (Array.isArray(data) && data.length > 0) setOrders(data); })
      .catch((err) => console.warn('[KitchenKds] Failed to load orders:', err?.message || err));
    api.getKitchenStations()
      .then((data) => { if (Array.isArray(data) && data.length > 0) setStations(data); })
      .catch((err) => console.warn('[KitchenKds] Failed to load kitchen stations:', err?.message || err));
    api.getDelayedOrders()
      .then((data) => { if (Array.isArray(data) && data.length > 0) setDelayedOrders(data); })
      .catch((err) => console.warn('[KitchenKds] Failed to load delayed orders:', err?.message || err));
  };

  useEffect(() => {
    loadData();

    const unsub = wsService.subscribe('/topic/restaurants/1/orders', () => {
      loadData();
    });

    const timer = setInterval(() => {
      api.getDelayedOrders()
        .then(setDelayedOrders)
        .catch((err) => console.warn('[KitchenKds] Delayed orders poll error:', err?.message || err));
    }, 10000);

    return () => {
      unsub();
      clearInterval(timer);
    };
  }, []);

  const handleStartOrder = async (orderId) => {
    try {
      await api.startOrderPrep(orderId);
      setOrders((prev) => prev.map((o) => (o.orderId === orderId ? { ...o, status: 'PREPARING' } : o)));
    } catch (err) {
      console.error('[KitchenKds] Failed to start order prep:', err);
      flashError(`Failed to update order #${orderId} to preparing. Please try again.`);
    }
  };

  const handleMarkReady = async (orderId) => {
    try {
      await api.markOrderReady(orderId);
      setOrders((prev) => prev.map((o) => (o.orderId === orderId ? { ...o, status: 'READY' } : o)));
    } catch (err) {
      console.error('[KitchenKds] Failed to mark order ready:', err);
      flashError(`Failed to mark order #${orderId} as ready. Please try again.`);
    }
  };

  const handleUpdateItemStatus = async (itemId, status) => {
    try {
      await api.updateOrderItemStatus(itemId, status);
      setOrders((prev) =>
        prev.map((o) => ({
          ...o,
          items: o.items ? o.items.map((it) => (it.orderItemId === itemId ? { ...it, status } : it)) : [],
        }))
      );
    } catch (err) {
      console.error('[KitchenKds] Failed to update item status:', err);
      flashError('Failed to update item status. Please try again.');
    }
  };

  const activeOrders = orders.filter(
    (o) => o.status === 'CONFIRMED' || o.status === 'PREPARING' || o.status === 'READY'
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
            Kitchen Display System
            <span
              className="text-xs font-semibold px-2 py-0.5 rounded border"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
              }}
            >
              Delay Detection Active
            </span>
          </h1>
          <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
            Real-time station order queue. Delayed tickets indicate when exceeding 150% expected prep time.
          </p>
        </div>

        {/* Station Filter */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full">
          <button
            onClick={() => setSelectedStation(null)}
            className="px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors duration-150 border whitespace-nowrap"
            style={{
              backgroundColor: selectedStation === null ? 'var(--btn-primary)' : 'var(--bg-card)',
              borderColor: selectedStation === null ? 'var(--btn-primary)' : 'var(--border)',
              color: selectedStation === null ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            All Stations
          </button>
          {stations.map((st) => (
            <button
              key={st.stationId}
              onClick={() => setSelectedStation(st.stationId)}
              className="px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors duration-150 border whitespace-nowrap"
              style={{
                backgroundColor: selectedStation === st.stationId ? 'var(--btn-primary)' : 'var(--bg-card)',
                borderColor: selectedStation === st.stationId ? 'var(--btn-primary)' : 'var(--border)',
                color: selectedStation === st.stationId ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
              }}
            >
              {st.name}
            </button>
          ))}
        </div>
      </div>

      {/* Error Feedback Banner */}
      {kdsError && (
        <div
          className="p-3.5 rounded-xl border text-xs flex items-center gap-2.5 shadow-sm"
          style={{
            backgroundColor: 'var(--status-critical-bg)',
            borderColor: 'var(--status-critical-border)',
            color: 'var(--status-critical)',
          }}
        >
          <span className="font-semibold">{kdsError}</span>
        </div>
      )}

      {/* Delay Alert Banner */}
      {delayedOrders.length > 0 && (
        <div
          className="p-4 rounded-2xl border flex items-center justify-between shadow-sm"
          style={{
            backgroundColor: 'var(--status-critical-bg)',
            borderColor: 'var(--status-critical-border)',
            color: 'var(--status-critical)',
          }}
        >
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <div>
              <span className="font-bold text-sm">Critical Kitchen Delay Warning ({delayedOrders.length} ticket items):</span>
              <p className="text-xs opacity-90 mt-0.5">
                Items running over 150% expected duration. Escalation rules notified floor manager.
              </p>
            </div>
          </div>
          <span
            className="text-xs font-bold px-2.5 py-1 rounded-full border whitespace-nowrap"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--status-critical-border)',
              color: 'var(--status-critical)',
            }}
          >
            P90 Breach
          </span>
        </div>
      )}

      {/* Order Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
        {activeOrders.length === 0 ? (
          <div
            className="col-span-full rounded-2xl p-12 text-center border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <ChefHat className="w-10 h-10 mx-auto mb-3" style={{ color: 'var(--text-muted)' }} />
            <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              Kitchen Queue Clear
            </h3>
            <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
              No orders waiting for preparation right now.
            </p>
          </div>
        ) : (
          activeOrders.map((order) => {
            const isDelayed = delayedOrders.some((d) => d.orderId === order.orderId);
            const isReady = order.status === 'READY';
            const isPreparing = order.status === 'PREPARING';

            let cardBorder = '1px solid var(--border)';
            if (isDelayed) {
              cardBorder = '1px solid var(--status-critical)';
            } else if (isReady) {
              cardBorder = '1px solid var(--status-available)';
            }

            return (
              <div
                key={order.orderId}
                className="rounded-2xl p-4 flex flex-col justify-between shadow-sm transition-colors duration-150"
                style={{
                  backgroundColor: 'var(--bg-card)',
                  border: cardBorder,
                }}
              >
                <div>
                  {/* Card Header */}
                  <div className="flex items-center justify-between pb-3 border-b mb-3" style={{ borderColor: 'var(--border)' }}>
                    <div>
                      <span className="text-base font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                        {order.orderCode}
                      </span>
                      <div className="text-xs font-semibold mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                        Table T{order.tableId || '—'}
                      </div>
                    </div>

                    <div className="text-right">
                      <span
                        className="text-[11px] font-bold px-2 py-0.5 rounded-full border"
                        style={{
                          backgroundColor: isReady
                            ? 'var(--status-available-bg)'
                            : isPreparing
                            ? 'var(--status-occupied-bg)'
                            : 'var(--bg-main)',
                          borderColor: isReady
                            ? 'var(--status-available-border)'
                            : isPreparing
                            ? 'var(--status-occupied-border)'
                            : 'var(--border)',
                          color: isReady
                            ? 'var(--status-available)'
                            : isPreparing
                            ? 'var(--status-occupied)'
                            : 'var(--text-secondary)',
                        }}
                      >
                        {order.status}
                      </span>
                      <div className="text-[11px] flex items-center justify-end gap-1 mt-1 tabular" style={{ color: 'var(--text-muted)' }}>
                        <Clock className="w-3 h-3" />
                        {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </div>
                    </div>
                  </div>

                  {/* Items List */}
                  <div className="space-y-2 mb-4">
                    {order.items.map((item) => {
                      const itemDelayed = delayedOrders.some((d) => d.orderItemId === item.orderItemId);
                      return (
                        <div
                          key={item.orderItemId}
                          className="p-2.5 rounded-xl text-xs flex items-center justify-between border transition-colors duration-150"
                          style={{
                            backgroundColor: itemDelayed ? 'var(--status-critical-bg)' : 'var(--bg-main)',
                            borderColor: itemDelayed ? 'var(--status-critical-border)' : 'var(--border)',
                            color: 'var(--text-primary)',
                          }}
                        >
                          <div>
                            <div className="font-semibold flex items-center gap-1.5">
                              <span
                                className="w-5 h-5 rounded-md font-bold flex items-center justify-center text-[10px]"
                                style={{
                                  backgroundColor: 'var(--btn-primary)',
                                  color: 'var(--btn-primary-text)',
                                }}
                              >
                                {item.quantity}x
                              </span>
                              <span>{item.itemName}</span>
                            </div>
                            <div className="text-[11px] mt-0.5 pl-6" style={{ color: 'var(--text-muted)' }}>
                              Station: {item.stationName || 'General'} ({item.expectedPrepMinutes}m prep)
                            </div>
                          </div>

                          <div className="flex items-center gap-1">
                            {item.status === 'READY' ? (
                              <span
                                className="text-[10px] font-bold flex items-center gap-0.5"
                                style={{ color: 'var(--status-available)' }}
                              >
                                <Check className="w-3 h-3" /> Ready
                              </span>
                            ) : (
                              <button
                                onClick={() => handleUpdateItemStatus(item.orderItemId, 'READY')}
                                className="px-2.5 py-1 rounded text-[10px] font-semibold border transition-colors duration-150 whitespace-nowrap"
                                style={{
                                  backgroundColor: 'var(--bg-card)',
                                  borderColor: 'var(--border)',
                                  color: 'var(--text-primary)',
                                }}
                              >
                                Done
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Footer Actions */}
                <div className="pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
                  {order.status === 'CONFIRMED' && (
                    <button
                      onClick={() => handleStartOrder(order.orderId)}
                      className="w-full py-2 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--btn-primary)',
                        color: 'var(--btn-primary-text)',
                      }}
                    >
                      <Play className="w-3.5 h-3.5" /> Start Cooking
                    </button>
                  )}

                  {order.status === 'PREPARING' && (
                    <button
                      onClick={() => handleMarkReady(order.orderId)}
                      className="w-full py-2 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--btn-primary)',
                        color: 'var(--btn-primary-text)',
                      }}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" /> Mark All Ready at Pass
                    </button>
                  )}

                  {order.status === 'READY' && (
                    <div
                      className="p-2 rounded-xl text-center text-xs font-semibold flex items-center justify-center gap-1.5 border whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--status-available-bg)',
                        borderColor: 'var(--status-available-border)',
                        color: 'var(--status-available)',
                      }}
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      Ready for Waiter Pickup
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
