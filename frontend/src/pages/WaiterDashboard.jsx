import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import { soundService } from '../services/soundService';
import { inventoryService } from '../services/inventoryService';
import {
  UserCheck,
  CheckCircle,
  Clock,
  BellRing,
  Utensils,
  Gauge,
  AlertTriangle,
  Send,
  Check,
  Ban,
} from 'lucide-react';

const INITIAL_WORKLOADS = [
  { waiterId: 1, displayName: 'Kunal Verma', employeeCode: 'EMP-01', workloadScore: 8.5, occupiedTables: 2, pendingRequests: 1, undeliveredReadyOrders: 1, isOverloaded: true, shiftMedian: 5.5 },
  { waiterId: 2, displayName: 'Ananya Sharma', employeeCode: 'EMP-02', workloadScore: 4.0, occupiedTables: 1, pendingRequests: 0, undeliveredReadyOrders: 0, isOverloaded: false, shiftMedian: 5.5 },
  { waiterId: 3, displayName: 'Pooja Mehta', employeeCode: 'EMP-03', workloadScore: 3.5, occupiedTables: 1, pendingRequests: 0, undeliveredReadyOrders: 0, isOverloaded: false, shiftMedian: 5.5 },
  { waiterId: 4, displayName: 'Rohan Joshi', employeeCode: 'EMP-04', workloadScore: 5.0, occupiedTables: 1, pendingRequests: 1, undeliveredReadyOrders: 0, isOverloaded: false, shiftMedian: 5.5 },
];

const INITIAL_REQUESTS = [
  { serviceRequestId: 1, tableId: 3, tableCode: 'T-03', requestType: 'REQUEST_BILL', source: 'QR_PORTAL', status: 'OPEN', createdAt: new Date(Date.now() - 260000).toISOString() },
  { serviceRequestId: 2, tableId: 1, tableCode: 'T-01', requestType: 'WATER', source: 'QR_PORTAL', status: 'ACKNOWLEDGED', createdAt: new Date(Date.now() - 90000).toISOString() },
];

const INITIAL_READY_ORDERS = [
  { orderId: 1046, orderCode: '#1046', tableId: 8, items: [{ quantity: 2, itemName: 'Gulab Jamun with Rabri' }] }
];

export default function WaiterDashboard() {
  const [workloads, setWorkloads] = useState(INITIAL_WORKLOADS);
  const [requests, setRequests] = useState(INITIAL_REQUESTS);
  const [readyOrders, setReadyOrders] = useState(INITIAL_READY_ORDERS);
  const [inventoryItems, setInventoryItems] = useState(inventoryService.getItems());
  const [loading, setLoading] = useState(false);
  const [actionError, setActionError] = useState('');

  const flashError = (msg) => {
    setActionError(msg);
    setTimeout(() => setActionError(''), 4000);
  };

  const loadData = () => {
    api.getWorkload()
      .then((data) => { if (Array.isArray(data) && data.length > 0) setWorkloads(data); })
      .catch((err) => console.warn('[WaiterDashboard] Failed to fetch workload:', err?.message || err));
    api.getServiceRequests()
      .then((data) => { if (Array.isArray(data) && data.length > 0) setRequests(data); })
      .catch((err) => console.warn('[WaiterDashboard] Failed to fetch service requests:', err?.message || err));
    api.getOrders()
      .then((all) => { if (Array.isArray(all)) setReadyOrders(all.filter((o) => o.status === 'READY')); })
      .catch((err) => console.warn('[WaiterDashboard] Failed to fetch orders:', err?.message || err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();

    const unsubInventory = inventoryService.subscribe((items) => {
      setInventoryItems(items);
    });

    const unsubTasks = wsService.subscribe('/topic/restaurants/1/tasks', () => {
      loadData();
      soundService.playWaiterBell();
    });
    const unsubOrders = wsService.subscribe('/topic/restaurants/1/orders', () => {
      loadData();
    });

    return () => {
      unsubInventory();
      unsubTasks();
      unsubOrders();
    };
  }, []);

  const handleAcknowledgeRequest = async (id) => {
    setRequests((prev) =>
      prev.map((r) =>
        (r.serviceRequestId === id || r.requestId === id)
          ? { ...r, status: 'ACKNOWLEDGED' }
          : r
      )
    );
    try {
      await api.acknowledgeServiceRequest(id);
    } catch (err) {
      console.warn('[WaiterDashboard] Backend acknowledge sync note:', err?.message || err);
    }
  };

  const handleCompleteRequest = async (id) => {
    setRequests((prev) =>
      prev.filter((r) => r.serviceRequestId !== id && r.requestId !== id)
    );
    soundService.playSuccessChime();
    try {
      await api.completeServiceRequest(id);
    } catch (err) {
      console.warn('[WaiterDashboard] Backend complete sync note:', err?.message || err);
    }
  };

  const handleDeliverOrder = async (orderId) => {
    try {
      await api.deliverOrder(orderId);
      setReadyOrders((prev) => prev.filter((o) => o.orderId !== orderId));
      soundService.playSuccessChime();
    } catch (err) {
      console.error('[WaiterDashboard] Failed to deliver order:', err);
      flashError(`Failed to mark order #${orderId} as delivered. Please try again.`);
    }
  };

  const openRequests = requests.filter((r) => r.status === 'OPEN' || r.status === 'ACKNOWLEDGED');
  const items86d = inventoryItems.filter((i) => i.is86d || i.portionsRemaining === 0);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
          Waiter Service Station
          <span
            className="text-xs font-semibold px-2 py-0.5 rounded border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
          >
            Workload Balancer
          </span>
        </h1>
        <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
          Formula-weighted floor assignments: tables (1.0x), pending requests (1.5x), ready orders (1.0x), open tasks (0.5x).
        </p>
      </div>

      {/* 86'd Kitchen Alert Banner */}
      {items86d.length > 0 && (
        <div
          className="p-3.5 rounded-2xl border text-xs flex items-center justify-between shadow-sm animate-fade-in"
          style={{
            backgroundColor: 'var(--status-occupied-bg)',
            borderColor: 'var(--status-occupied-border)',
            color: 'var(--status-occupied)',
          }}
        >
          <div className="flex items-center gap-2.5">
            <Ban className="w-4 h-4 shrink-0" />
            <div>
              <span className="font-bold">Kitchen 86'd Notice ({items86d.length} dishes Sold Out): </span>
              <span className="opacity-90">
                {items86d.map((i) => i.name).join(', ')} — Do not take table orders for these items.
              </span>
            </div>
          </div>
          <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded border border-red-500/40 bg-red-500/10 whitespace-nowrap">
            Kitchen Depleted
          </span>
        </div>
      )}

      {actionError && (
        <div
          className="p-3.5 rounded-xl border text-xs flex items-center gap-2.5 shadow-sm"
          style={{
            backgroundColor: 'var(--status-critical-bg)',
            borderColor: 'var(--status-critical-border)',
            color: 'var(--status-critical)',
          }}
        >
          <span className="font-semibold">{actionError}</span>
        </div>
      )}

      {/* Waiter Workload Index Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {workloads.map((w) => (
          <div
            key={w.waiterId}
            className="p-4 rounded-2xl border shadow-sm transition-colors duration-150"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: w.isOverloaded ? 'var(--status-occupied)' : 'var(--border)',
            }}
          >
            <div className="flex items-center justify-between mb-2">
              <div>
                <span className="text-sm font-bold block" style={{ color: 'var(--text-primary)' }}>
                  {w.displayName}
                </span>
                <span className="text-[11px] font-mono" style={{ color: 'var(--text-muted)' }}>
                  {w.employeeCode}
                </span>
              </div>
              <div className="text-right">
                <span className="text-xl font-black tabular" style={{ color: 'var(--text-primary)' }}>
                  {w.workloadScore}
                </span>
                <span className="text-[11px] block" style={{ color: 'var(--text-muted)' }}>
                  Score
                </span>
              </div>
            </div>

            <div className="space-y-1.5 text-xs py-2 border-t" style={{ borderColor: 'var(--border)' }}>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Assigned Occupied:</span>
                <span className="font-semibold tabular" style={{ color: 'var(--text-primary)' }}>
                  {w.occupiedTables} tables
                </span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Pending Requests:</span>
                <span className="font-semibold tabular" style={{ color: 'var(--text-primary)' }}>
                  {w.pendingRequests}
                </span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Undelivered Ready:</span>
                <span className="font-semibold tabular" style={{ color: 'var(--text-primary)' }}>
                  {w.undeliveredReadyOrders}
                </span>
              </div>
            </div>

            {w.isOverloaded && (
              <div
                className="mt-2 p-1.5 rounded-lg text-[11px] font-bold flex items-center gap-1 border whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--status-occupied-bg)',
                  borderColor: 'var(--status-occupied-border)',
                  color: 'var(--status-occupied)',
                }}
              >
                <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                <span>Overloaded (&gt; Shift Median {w.shiftMedian})</span>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Ready Orders Pickup Queue */}
        <div
          className="rounded-2xl p-5 border space-y-4 shadow-sm"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Utensils className="w-4 h-4" style={{ color: 'var(--status-available)' }} />
              Ready Orders Pickup (KDS Pass)
            </h3>
            <span
              className="text-xs px-2.5 py-0.5 rounded-full font-bold border"
              style={{
                backgroundColor: 'var(--status-available-bg)',
                borderColor: 'var(--status-available-border)',
                color: 'var(--status-available)',
              }}
            >
              {readyOrders.length} Ready
            </span>
          </div>

          <div className="space-y-2.5">
            {readyOrders.length === 0 ? (
              <p className="text-xs text-center py-8" style={{ color: 'var(--text-muted)' }}>
                No orders awaiting delivery at the kitchen pass.
              </p>
            ) : (
              readyOrders.map((order) => (
                <div
                  key={order.orderId}
                  className="p-3.5 rounded-xl border flex items-center justify-between transition-colors duration-150"
                  style={{
                    backgroundColor: 'var(--bg-main)',
                    borderColor: 'var(--border)',
                  }}
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-sm" style={{ color: 'var(--text-primary)' }}>
                        {order.orderCode}
                      </span>
                      <span
                        className="text-xs font-semibold px-2 py-0.5 rounded border"
                        style={{
                          backgroundColor: 'var(--status-available-bg)',
                          borderColor: 'var(--status-available-border)',
                          color: 'var(--status-available)',
                        }}
                      >
                        Table T{order.tableId}
                      </span>
                    </div>
                    <div className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
                      {order.items.map((i) => `${i.quantity}x ${i.itemName}`).join(', ')}
                    </div>
                  </div>

                  <button
                    onClick={() => handleDeliverOrder(order.orderId)}
                    className="px-3.5 py-1.5 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center gap-1.5 shadow-sm whitespace-nowrap"
                    style={{
                      backgroundColor: 'var(--btn-primary)',
                      color: 'var(--btn-primary-text)',
                    }}
                  >
                    <CheckCircle className="w-3.5 h-3.5" /> Deliver
                  </button>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Customer Service Requests */}
        <div
          className="rounded-2xl p-5 border space-y-4 shadow-sm"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <BellRing className="w-4 h-4" style={{ color: 'var(--status-occupied)' }} />
              Active Guest Calls &amp; Service Requests
            </h3>
            <span
              className="text-xs px-2.5 py-0.5 rounded-full font-bold border"
              style={{
                backgroundColor: 'var(--status-occupied-bg)',
                borderColor: 'var(--status-occupied-border)',
                color: 'var(--status-occupied)',
              }}
            >
              {openRequests.length} Pending
            </span>
          </div>

          <div className="space-y-2.5">
            {openRequests.length === 0 ? (
              <p className="text-xs text-center py-8" style={{ color: 'var(--text-muted)' }}>
                No pending service calls from tables.
              </p>
            ) : (
              openRequests.map((req) => (
                <div
                  key={req.serviceRequestId}
                  className="p-3.5 rounded-xl border flex items-center justify-between transition-colors duration-150"
                  style={{
                    backgroundColor: 'var(--bg-main)',
                    borderColor: 'var(--border)',
                  }}
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-sm" style={{ color: 'var(--text-primary)' }}>
                        {req.tableCode || `T${req.tableId}`}
                      </span>
                      <span
                        className="text-xs font-semibold px-2 py-0.5 rounded border"
                        style={{
                          backgroundColor: 'var(--bg-card)',
                          borderColor: 'var(--border)',
                          color: 'var(--text-primary)',
                        }}
                      >
                        {req.requestType}
                      </span>
                      <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                        via {req.source}
                      </span>
                    </div>
                    <div className="text-xs mt-1 flex items-center gap-1 tabular" style={{ color: 'var(--text-secondary)' }}>
                      <Clock className="w-3 h-3" />
                      {new Date(req.createdAt).toLocaleTimeString()}
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {req.status === 'OPEN' && (
                      <button
                        onClick={() => handleAcknowledgeRequest(req.serviceRequestId)}
                        className="px-2.5 py-1 border rounded-lg text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
                        style={{
                          backgroundColor: 'var(--bg-card)',
                          borderColor: 'var(--border)',
                          color: 'var(--text-primary)',
                        }}
                      >
                        Ack
                      </button>
                    )}
                    <button
                      onClick={() => handleCompleteRequest(req.serviceRequestId)}
                      className="px-3 py-1 rounded-lg text-xs font-semibold transition-colors duration-150 flex items-center gap-1 shadow-sm whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--btn-primary)',
                        color: 'var(--btn-primary-text)',
                      }}
                    >
                      <Check className="w-3 h-3" /> Done
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
