import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import { soundService } from '../services/soundService';
import { inventoryService } from '../services/inventoryService';
import {
  Flame,
  Clock,
  AlertCircle,
  CheckCircle2,
  ChefHat,
  Filter,
  Play,
  Check,
  Package,
  AlertTriangle,
  RotateCcw,
  Plus,
  Minus,
  Search,
  Volume2,
  Ban,
  Sparkles,
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
  const auth = useAuth ? useAuth() : null;
  const restId = auth?.user?.restaurantId || 2;
  const [activeTab, setActiveTab] = useState('QUEUE'); // 'QUEUE' | 'INVENTORY'
  const [orders, setOrders] = useState(INITIAL_ORDERS);
  const [stations, setStations] = useState(INITIAL_STATIONS);
  const [selectedStation, setSelectedStation] = useState(null);
  const [delayedOrders, setDelayedOrders] = useState(INITIAL_DELAYED);
  const [kdsError, setKdsError] = useState('');
  const [inventoryActionMsg, setInventoryActionMsg] = useState('');

  // 86'd & Inventory State
  const [inventoryItems, setInventoryItems] = useState(inventoryService.getItems());
  const [inventorySearch, setInventorySearch] = useState('');
  const [inventoryFilter, setInventoryFilter] = useState('ALL'); // 'ALL' | 'LOW' | '86'

  const flashError = (msg) => {
    setKdsError(msg);
    setTimeout(() => setKdsError(''), 4000);
  };

  const flashInventoryMsg = (msg) => {
    setInventoryActionMsg(msg);
    setTimeout(() => setInventoryActionMsg(''), 4000);
  };

  const loadData = (playChime = false) => {
    api.getOrders()
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setOrders(data);
          if (playChime) soundService.playKitchenChime();
        }
      })
      .catch((err) => console.warn('[KitchenKds] Failed to load orders:', err?.message || err));

    api.getKitchenStations()
      .then((data) => { if (Array.isArray(data) && data.length > 0) setStations(data); })
      .catch((err) => console.warn('[KitchenKds] Failed to load kitchen stations:', err?.message || err));

    api.getDelayedOrders()
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setDelayedOrders(data);
        }
      })
      .catch((err) => console.warn('[KitchenKds] Failed to load delayed orders:', err?.message || err));
  };

  useEffect(() => {
    loadData();

    // Subscribe to inventory changes
    const unsubInventory = inventoryService.subscribe((items) => {
      setInventoryItems(items);
    });

    const unsubOrders = wsService.subscribe(`/topic/restaurants/${restId}/orders`, () => {
      loadData(true);
    });

    const timer = setInterval(() => {
      api.getDelayedOrders()
        .then((data) => {
          if (Array.isArray(data)) {
            setDelayedOrders(data);
            if (data.length > 0) {
              soundService.playDelayAlert();
            }
          }
        })
        .catch((err) => console.warn('[KitchenKds] Delayed orders poll error:', err?.message || err));
    }, 12000);

    return () => {
      unsubInventory();
      unsubOrders();
      clearInterval(timer);
    };
  }, []);

  const handleStartOrder = async (orderId) => {
    try {
      await api.startOrderPrep(orderId);
      setOrders((prev) => prev.map((o) => (o.orderId === orderId ? { ...o, status: 'PREPARING' } : o)));
      soundService.playKitchenChime();
    } catch (err) {
      console.error('[KitchenKds] Failed to start order prep:', err);
      flashError(`Failed to update order #${orderId} to preparing. Please try again.`);
    }
  };

  const handleMarkReady = async (orderId) => {
    try {
      await api.markOrderReady(orderId);
      setOrders((prev) => prev.map((o) => (o.orderId === orderId ? { ...o, status: 'READY' } : o)));
      soundService.playSuccessChime();
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
      if (status === 'READY') {
        soundService.playSuccessChime();
      }
    } catch (err) {
      console.error('[KitchenKds] Failed to update item status:', err);
      flashError('Failed to update item status. Please try again.');
    }
  };

  // 86'd & Inventory Actions
  const handleToggle86 = (itemId, itemName) => {
    const isCurrently86 = inventoryService.isItem86d(itemId);
    const updated = inventoryService.toggle86(itemId, 'Chef 86: Out of stock during shift');
    if (updated?.is86d) {
      soundService.playDelayAlert();
      flashInventoryMsg(`Item 86'd: "${itemName}" is now marked OUT OF STOCK across all menus!`);
    } else {
      soundService.playSuccessChime();
      flashInventoryMsg(`Item Restocked: "${itemName}" is now available on floor menu.`);
    }
  };

  const handleAdjustPortions = (itemId, delta, itemName) => {
    inventoryService.adjustPortions(itemId, delta);
    const updated = inventoryService.getItems().find((i) => i.itemId === itemId);
    if (updated?.is86d) {
      soundService.playDelayAlert();
      flashInventoryMsg(`"${itemName}" reached 0 portions and was automatically 86'd!`);
    }
  };

  const handleRestock = (itemId, portions, itemName) => {
    inventoryService.restockItem(itemId, portions);
    soundService.playSuccessChime();
    flashInventoryMsg(`Restocked "${itemName}" with ${portions} portions! Returned to active menu.`);
  };

  // Derived counts
  const total86dCount = inventoryItems.filter((i) => i.is86d || i.portionsRemaining === 0).length;
  const totalLowStockCount = inventoryItems.filter(
    (i) => !i.is86d && i.portionsRemaining > 0 && i.portionsRemaining <= i.lowStockThreshold
  ).length;

  const activeOrders = orders.filter(
    (o) => {
      const matchStatus = o.status === 'CONFIRMED' || o.status === 'PREPARING' || o.status === 'READY';
      if (!matchStatus) return false;
      if (!selectedStation) return true;
      return o.items && o.items.some((i) => {
        const st = stations.find((s) => s.stationId === selectedStation);
        return st && i.stationName === st.name;
      });
    }
  );

  // Filtered inventory list
  const filteredInventory = inventoryItems.filter((item) => {
    if (inventorySearch) {
      const q = inventorySearch.toLowerCase();
      const matchName = item.name.toLowerCase().includes(q);
      const matchStation = item.stationName.toLowerCase().includes(q);
      if (!matchName && !matchStation) return false;
    }
    if (selectedStation) {
      if (item.stationId !== selectedStation) return false;
    }
    if (inventoryFilter === '86') {
      return item.is86d || item.portionsRemaining === 0;
    }
    if (inventoryFilter === 'LOW') {
      return !item.is86d && item.portionsRemaining > 0 && item.portionsRemaining <= item.lowStockThreshold;
    }
    return true;
  });

  return (
    <div className="space-y-6">
      {/* Header with Main Navigation & Station Selector */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
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
              KDS Station &amp; Pass
            </span>
          </h1>
          <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
            Real-time station order queue, instant 86'd out-of-stock management, and audible ticket chimes.
          </p>
        </div>

        {/* Primary View Switcher: Active Tickets vs 86'd Inventory */}
        <div className="flex items-center gap-2">
          <div
            className="flex items-center p-1 rounded-xl border text-xs font-bold"
            style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
          >
            <button
              onClick={() => setActiveTab('QUEUE')}
              className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors ${
                activeTab === 'QUEUE'
                  ? 'bg-neutral-800 text-emerald-400 font-bold shadow-xs'
                  : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              <ChefHat className="w-3.5 h-3.5" />
              <span>Ticket Queue ({activeOrders.length})</span>
            </button>
            <button
              onClick={() => setActiveTab('INVENTORY')}
              className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors ${
                activeTab === 'INVENTORY'
                  ? 'bg-neutral-800 text-red-400 font-bold shadow-xs'
                  : 'text-neutral-400 hover:text-neutral-200'
              }`}
            >
              <Package className="w-3.5 h-3.5" />
              <span>86'd &amp; Inventory</span>
              {total86dCount > 0 && (
                <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-red-500 text-white font-black">
                  {total86dCount}
                </span>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Station Selector Bar */}
      <div className="flex items-center justify-between gap-2 border-b pb-3 flex-wrap" style={{ borderColor: 'var(--border)' }}>
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

        {/* Live 86'd Status Pill in Queue View */}
        {activeTab === 'QUEUE' && total86dCount > 0 && (
          <div
            onClick={() => setActiveTab('INVENTORY')}
            className="cursor-pointer text-[11px] font-bold px-2.5 py-1 rounded-xl border flex items-center gap-1.5 transition-all hover:opacity-90"
            style={{
              backgroundColor: 'var(--status-occupied-bg)',
              borderColor: 'var(--status-occupied-border)',
              color: 'var(--status-occupied)',
            }}
          >
            <Ban className="w-3.5 h-3.5" />
            <span>{total86dCount} Dishes 86'd / Out of Stock (Click to view)</span>
          </div>
        )}
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

      {/* Inventory Action Confirmation Banner */}
      {inventoryActionMsg && (
        <div
          className="p-3 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-400 text-xs flex items-center gap-2 shadow-sm animate-fade-in"
        >
          <Sparkles className="w-4 h-4 shrink-0" />
          <span className="font-semibold">{inventoryActionMsg}</span>
        </div>
      )}

      {/* Delay Alert Warning Banner */}
      {delayedOrders.length > 0 && activeTab === 'QUEUE' && (
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

      {/* ==================================================================== */}
      {/* TAB 1: KITCHEN TICKETS QUEUE                                         */}
      {/* ==================================================================== */}
      {activeTab === 'QUEUE' && (
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

              // Check if any item in this ticket is 86'd
              const has86dItem = order.items && order.items.some((it) => inventoryService.isItem86d(it.itemName));

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

                    {/* Warning if ticket has an 86'd item */}
                    {has86dItem && (
                      <div className="mb-2 p-1.5 rounded-lg border border-red-500/40 bg-red-500/10 text-red-400 text-[10px] font-bold flex items-center gap-1.5">
                        <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                        <span>Contains an 86'd item! Verify batch with Chef.</span>
                      </div>
                    )}

                    {/* Items List */}
                    <div className="space-y-2 mb-4">
                      {order.items.map((item) => {
                        const itemDelayed = delayedOrders.some((d) => d.orderItemId === item.orderItemId);
                        const isThisItem86d = inventoryService.isItem86d(item.itemName);

                        return (
                          <div
                            key={item.orderItemId}
                            className="p-2.5 rounded-xl text-xs flex items-center justify-between border transition-colors duration-150"
                            style={{
                              backgroundColor: isThisItem86d
                                ? 'rgba(239, 68, 68, 0.12)'
                                : itemDelayed
                                ? 'var(--status-critical-bg)'
                                : 'var(--bg-main)',
                              borderColor: isThisItem86d
                                ? 'rgba(239, 68, 68, 0.4)'
                                : itemDelayed
                                ? 'var(--status-critical-border)'
                                : 'var(--border)',
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
                                <span className={isThisItem86d ? 'line-through text-red-400' : ''}>
                                  {item.itemName}
                                </span>
                                {isThisItem86d && (
                                  <span className="text-[9px] px-1.5 py-0.2 rounded bg-red-500/20 text-red-400 font-bold border border-red-500/30">
                                    86'd
                                  </span>
                                )}
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
                                  className="px-2.5 py-1 rounded text-[10px] font-semibold border transition-colors duration-150 whitespace-nowrap hover:opacity-85"
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
                        className="w-full py-2 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap hover:opacity-90"
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
                        className="w-full py-2 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap hover:opacity-90"
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
      )}

      {/* ==================================================================== */}
      {/* TAB 2: 86'd & KITCHEN INVENTORY MANAGEMENT                           */}
      {/* ==================================================================== */}
      {activeTab === 'INVENTORY' && (
        <div className="space-y-4">
          {/* Subheader Controls: Search & Filter Pills */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="relative w-full sm:w-72">
              <Search className="w-4 h-4 absolute left-3 top-2.5 text-neutral-400" />
              <input
                type="text"
                value={inventorySearch}
                onChange={(e) => setInventorySearch(e.target.value)}
                placeholder="Search dish or ingredient…"
                className="w-full pl-9 pr-3 py-1.5 rounded-xl border text-xs focus:outline-none focus:ring-1 focus:ring-emerald-500"
                style={{
                  backgroundColor: 'var(--bg-card)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-primary)',
                }}
              />
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setInventoryFilter('ALL')}
                className={`px-3 py-1 rounded-xl text-xs font-semibold border transition-colors ${
                  inventoryFilter === 'ALL'
                    ? 'bg-neutral-800 text-neutral-200 border-neutral-600'
                    : 'text-neutral-400 border-neutral-800'
                }`}
              >
                All ({inventoryItems.length})
              </button>
              <button
                onClick={() => setInventoryFilter('LOW')}
                className={`px-3 py-1 rounded-xl text-xs font-semibold border transition-colors ${
                  inventoryFilter === 'LOW'
                    ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                    : 'text-neutral-400 border-neutral-800'
                }`}
              >
                Low Stock ({totalLowStockCount})
              </button>
              <button
                onClick={() => setInventoryFilter('86')}
                className={`px-3 py-1 rounded-xl text-xs font-semibold border transition-colors ${
                  inventoryFilter === '86'
                    ? 'bg-red-500/20 text-red-400 border-red-500/40'
                    : 'text-neutral-400 border-neutral-800'
                }`}
              >
                86'd Out of Stock ({total86dCount})
              </button>
            </div>
          </div>

          {/* Inventory Items Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {filteredInventory.map((item) => {
              const is86d = item.is86d || item.portionsRemaining === 0;
              const isLowStock = !is86d && item.portionsRemaining <= item.lowStockThreshold;

              return (
                <div
                  key={item.itemId}
                  className={`rounded-2xl border p-4 flex flex-col justify-between space-y-3 transition-all shadow-sm ${
                    is86d
                      ? 'border-red-500/50 bg-red-950/20'
                      : isLowStock
                      ? 'border-amber-500/40 bg-amber-950/15'
                      : ''
                  }`}
                  style={{
                    backgroundColor: is86d ? undefined : isLowStock ? undefined : 'var(--bg-card)',
                    borderColor: is86d ? undefined : isLowStock ? undefined : 'var(--border)',
                  }}
                >
                  <div>
                    {/* Item Header & Status Pill */}
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className={`text-sm font-bold block leading-snug ${is86d ? 'line-through text-red-300' : ''}`} style={{ color: is86d ? undefined : 'var(--text-primary)' }}>
                          {item.name}
                        </span>
                        <div className="flex items-center gap-1.5 mt-1 text-[11px]" style={{ color: 'var(--text-muted)' }}>
                          <span>{item.stationName}</span>
                          <span>•</span>
                          <span>₹{item.price}</span>
                        </div>
                      </div>

                      {is86d ? (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-red-500 text-white border border-red-600 shadow-xs uppercase shrink-0">
                          86'd
                        </span>
                      ) : isLowStock ? (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-400 border border-amber-500/40 shrink-0">
                          Low Stock
                        </span>
                      ) : (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 shrink-0">
                          Available
                        </span>
                      )}
                    </div>

                    {/* Stock Portions Counter */}
                    <div className="my-3 p-2.5 rounded-xl border flex items-center justify-between" style={{ backgroundColor: 'var(--bg-main)', borderColor: 'var(--border)' }}>
                      <div>
                        <span className="text-[10px] block" style={{ color: 'var(--text-muted)' }}>Portions In Kitchen:</span>
                        <span className={`text-base font-black tabular ${is86d ? 'text-red-400' : isLowStock ? 'text-amber-400' : 'text-emerald-400'}`}>
                          {item.portionsRemaining} portions
                        </span>
                      </div>

                      <div className="flex items-center gap-1">
                        <button
                          disabled={item.portionsRemaining <= 0}
                          onClick={() => handleAdjustPortions(item.itemId, -1, item.name)}
                          className="w-7 h-7 rounded-lg border flex items-center justify-center text-xs font-bold transition-colors hover:bg-neutral-800 disabled:opacity-30"
                          style={{ borderColor: 'var(--border)' }}
                          title="Decrement 1 portion"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <button
                          onClick={() => handleAdjustPortions(item.itemId, 5, item.name)}
                          className="px-2 h-7 rounded-lg border flex items-center justify-center text-[10px] font-bold transition-colors hover:bg-neutral-800"
                          style={{ borderColor: 'var(--border)' }}
                          title="Add 5 portions"
                        >
                          +5
                        </button>
                      </div>
                    </div>

                    {/* Reason if 86'd */}
                    {is86d && item.reason && (
                      <p className="text-[10px] italic text-red-400/90 leading-tight">
                        Reason: {item.reason}
                      </p>
                    )}
                  </div>

                  {/* Primary 86 Action Button */}
                  <div className="pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
                    {is86d ? (
                      <button
                        onClick={() => handleRestock(item.itemId, 20, item.name)}
                        className="w-full py-1.5 px-3 rounded-xl text-xs font-bold bg-emerald-500 hover:bg-emerald-600 text-black transition-colors flex items-center justify-center gap-1.5 shadow-sm"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        <span>Restock (+20) &amp; Return to Menu</span>
                      </button>
                    ) : (
                      <button
                        onClick={() => handleToggle86(item.itemId, item.name)}
                        className="w-full py-1.5 px-3 rounded-xl text-xs font-bold bg-red-500/20 hover:bg-red-500/30 text-red-400 border border-red-500/40 transition-colors flex items-center justify-center gap-1.5"
                      >
                        <Ban className="w-3.5 h-3.5" />
                        <span>86 It (Mark Out of Stock)</span>
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
