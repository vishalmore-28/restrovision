// ==========================================================================
// APPENDIX C: RESTROVISION React Frontend STOMP Client & Floor Plan
// Module: websocket.js & FloorPlan.jsx
// ==========================================================================

import { Client } from '@stomp/stompjs';
import { getAuthToken } from './api';

export class WebSocketService {
  constructor() {
    this.client = null;
    this.connected = false;
    this.currentRestaurantId = this._detectRestaurantId() || 2;
    // Map of topic (string) -> Map of subId (number) -> callback (function)
    this.subscribers = new Map();
    // Map of topic (string) -> StompSubscription
    this.activeStompSubscriptions = new Map();
    this.connectionListeners = new Set();
    this._subCounter = 0;
  }

  _detectRestaurantId() {
    try {
      const stored = typeof window !== 'undefined' ? sessionStorage.getItem('restrovision_session_user') : null;
      if (stored) {
        const parsed = JSON.parse(stored);
        if (parsed?.restaurantId) return Number(parsed.restaurantId);
      }
    } catch (_) {}
    return null;
  }

  connect(restaurantId) {
    const detected = this._detectRestaurantId();
    this.currentRestaurantId = restaurantId || detected || 2;

    if (this.client && this.connected) {
      this._flushPendingSubscriptions();
      return;
    }

    let brokerURL;
    if (typeof window !== 'undefined' && window.__RESTROVISION_WS_URL__) {
      brokerURL = window.__RESTROVISION_WS_URL__;
    } else if (import.meta.env?.VITE_WS_URL) {
      brokerURL = import.meta.env.VITE_WS_URL;
    } else if (typeof window !== 'undefined' && window.location.hostname.includes('vercel.app')) {
      brokerURL = 'wss://restrovision-production.up.railway.app/ws';
    } else {
      const protocol = typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = typeof window !== 'undefined' ? window.location.host : 'localhost:8080';
      brokerURL = `${protocol}//${host}/ws`;
    }

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
    const activeRestId = this.currentRestaurantId || this._detectRestaurantId() || 2;
    if (activeRestId && /^\/topic\/restaurants\/\d+\//.test(topic)) {
      return topic.replace(/^\/topic\/restaurants\/\d+\//, `/topic/restaurants/${activeRestId}/`);
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


// --------------------------------------------------------------------------
// FloorPlan.jsx
// --------------------------------------------------------------------------

import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import {
  Users,
  Clock,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  Sparkles,
  Settings2,
  PlusCircle,
  HelpCircle,
  Eye,
  X,
  Camera,
  Layers,
} from 'lucide-react';

const INITIAL_TABLES = [
  { tableId: 1, tableCode: 'T-01', capacity: 4, status: 'OCCUPIED', statusSource: 'CCTV_AI', waitingSeconds: 204, assignedWaiterName: 'Kunal V.', sessionSeatedAt: new Date(Date.now() - 204000).toISOString() },
  { tableId: 2, tableCode: 'T-02', capacity: 2, status: 'AVAILABLE', statusSource: 'STAFF', waitingSeconds: 0, assignedWaiterName: null },
  { tableId: 3, tableCode: 'T-03', capacity: 2, status: 'OCCUPIED', statusSource: 'CCTV_AI', waitingSeconds: 312, waitingAlertLevel: 'ESCALATED', assignedWaiterName: 'Kunal V.', sessionSeatedAt: new Date(Date.now() - 312000).toISOString() },
  { tableId: 4, tableCode: 'T-04', capacity: 6, status: 'AVAILABLE', statusSource: 'CCTV_AI', waitingSeconds: 0, assignedWaiterName: null },
  { tableId: 5, tableCode: 'T-05', capacity: 4, status: 'OCCUPIED', statusSource: 'STAFF', waitingSeconds: 108, assignedWaiterName: 'Ananya S.', sessionSeatedAt: new Date(Date.now() - 108000).toISOString() },
  { tableId: 6, tableCode: 'T-06', capacity: 4, status: 'CLEANING', statusSource: 'STAFF', waitingSeconds: 0, assignedWaiterName: 'Ramesh D.' },
];

export default function FloorPlan({ onSelectTab }) {
  const auth = useAuth ? useAuth() : null;
  const restId = auth?.user?.restaurantId || 1;
  const [tables, setTables] = useState(INITIAL_TABLES);
  const [loading, setLoading] = useState(true);
  const [selectedTable, setSelectedTable] = useState(null);
  const [inspectorTable, setInspectorTable] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [overrideModalOpen, setOverrideModalOpen] = useState(false);
  const [overrideStatus, setOverrideStatus] = useState('AVAILABLE');
  const [overrideReason, setOverrideReason] = useState('');
  const [seatModalOpen, setSeatModalOpen] = useState(false);
  const [partySize, setPartySize] = useState(2);
  const [actionSuccess, setActionSuccess] = useState('');
  const [actionError, setActionError] = useState('');

  const loadTables = () => {
    api.getTables()
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) setTables(data);
      })
      .catch((err) => {
        console.warn('[FloorPlan] Failed to fetch tables:', err?.message || err);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadTables();

    // Subscribe to STOMP real-time table updates
    const unsub = wsService.subscribe(`/topic/restaurants/${restId}/tables`, (data) => {
      if (Array.isArray(data)) {
        setTables(data);
      } else if (data && data.tableId) {
        setTables((prev) => prev.map((t) => (t.tableId === data.tableId ? { ...t, ...data } : t)));
      } else {
        loadTables();
      }
    });

    const timer = setInterval(() => {
      // Local tick for waiting seconds
      setTables((prev) =>
        prev.map((t) => {
          if (t.status === 'OCCUPIED' && t.sessionSeatedAt && !t.sessionAttendedAt) {
            const elapsed = Math.floor((Date.now() - new Date(t.sessionSeatedAt).getTime()) / 1000);
            let alertLevel = 'NONE';
            if (elapsed >= 600) alertLevel = 'CRITICAL';
            else if (elapsed >= 300) alertLevel = 'ESCALATED';
            else if (elapsed >= 120) alertLevel = 'ATTENTION';
            return { ...t, waitingSeconds: elapsed, waitingAlertLevel: alertLevel };
          }
          return t;
        })
      );
    }, 1000);

    return () => {
      unsub();
      clearInterval(timer);
    };
  }, [restId]);

  const handleAttend = async (tableId) => {
    setTables((prev) =>
      prev.map((t) => (t.tableId === tableId ? { ...t, waitingSeconds: 0, waitingAlertLevel: null } : t))
    );
    flashSuccess(`Table attended! Waiting timer stopped.`);
    soundService.playSuccessChime();
    try {
      await api.attendTable(tableId);
    } catch (err) {
      console.warn('[FloorPlan] Backend attendTable sync note:', err?.message || err);
    }
  };

  const handleOpenInspector = (table) => {
    setInspectorTable(table);
    setTimeout(() => {
      const el = document.getElementById('table-inspector');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }, 60);
  };

  const handleSeatParty = async () => {
    if (!selectedTable) return;
    const targetTable = selectedTable;
    setTables((prev) =>
      prev.map((t) =>
        t.tableId === targetTable.tableId
          ? {
              ...t,
              status: 'OCCUPIED',
              statusSource: 'STAFF',
              waitingSeconds: 0,
              assignedWaiterName: 'Ananya S.',
              sessionSeatedAt: new Date().toISOString(),
            }
          : t
      )
    );
    setSeatModalOpen(false);
    flashSuccess(`Party of ${partySize} seated at ${targetTable.tableCode}`);
    soundService.playSuccessChime();
    try {
      await api.seatParty(targetTable.tableId, partySize, 2);
    } catch (err) {
      console.warn('[FloorPlan] Backend seatParty sync note:', err?.message || err);
    }
  };

  const handleOverrideStatus = async () => {
    if (!selectedTable) return;
    const targetTable = selectedTable;
    const newStatus = overrideStatus;
    setTables((prev) =>
      prev.map((t) =>
        t.tableId === targetTable.tableId
          ? { ...t, status: newStatus, statusSource: 'STAFF', waitingSeconds: 0 }
          : t
      )
    );
    setOverrideModalOpen(false);
    setOverrideReason('');
    flashSuccess(`Table ${targetTable.tableCode} status overridden to ${newStatus}. AI locked for 120s.`);
    soundService.playSuccessChime();
    try {
      await api.overrideTableStatus(
        targetTable.tableId,
        newStatus,
        overrideReason || 'Staff manual floor override'
      );
    } catch (err) {
      console.warn('[FloorPlan] Backend overrideStatus sync note:', err?.message || err);
    }
  };

  const flashSuccess = (msg) => {
    setActionSuccess(msg);
    setTimeout(() => setActionSuccess(''), 4000);
  };

  const flashError = (msg) => {
    setActionError(msg);
    setTimeout(() => setActionError(''), 4000);
  };

  const formatWaitTime = (seconds) => {
    if (!seconds || seconds <= 0) return '0s';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'AVAILABLE':
        return (
          <span
            className="text-xs px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--status-available-bg)',
              borderColor: 'var(--status-available-border)',
              color: 'var(--status-available)',
            }}
          >
            AVAILABLE
          </span>
        );
      case 'OCCUPIED':
        return (
          <span
            className="text-xs px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--status-occupied-bg)',
              borderColor: 'var(--status-occupied-border)',
              color: 'var(--status-occupied)',
            }}
          >
            OCCUPIED
          </span>
        );
      case 'CLEANING':
        return (
          <span
            className="text-xs px-2.5 py-0.5 rounded-full font-bold border flex items-center gap-1"
            style={{
              backgroundColor: 'var(--status-info-bg)',
              borderColor: 'var(--status-info-border)',
              color: 'var(--status-info)',
            }}
          >
            <Sparkles className="w-3 h-3 motion-safe:animate-spin motion-reduce:animate-none" /> CLEANING
          </span>
        );
      case 'RESERVED':
        return (
          <span
            className="text-xs px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--status-info-bg)',
              borderColor: 'var(--status-info-border)',
              color: 'var(--status-info)',
            }}
          >
            RESERVED
          </span>
        );
      case 'OUT_OF_SERVICE':
        return (
          <span
            className="text-xs px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--bg-main)',
              borderColor: 'var(--border)',
              color: 'var(--text-muted)',
            }}
          >
            OUT OF SERVICE
          </span>
        );
      default:
        return <span className="text-xs">{status}</span>;
    }
  };

  // Filter tables
  const filteredTables = tables.filter((t) => {
    if (statusFilter === 'ALL') return true;
    return t.status === statusFilter;
  });

  const totalOccupied = tables.filter((t) => t.status === 'OCCUPIED').length;
  const occupancyPercent = tables.length > 0 ? Math.round((totalOccupied / tables.length) * 100) : 0;

  return (
    <div className="space-y-6">
      {/* Header & Floor Status */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
            Floor Operations
            <span
              className="text-xs font-semibold px-2 py-0.5 rounded border"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
              }}
            >
              State Machine Active
            </span>
          </h1>
          <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
            Real-time visual table grid, seating management, and staff precedence overrides.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 sm:gap-4 text-xs">
          <div
            className="px-3.5 py-1.5 rounded-xl border flex items-center gap-3 font-medium"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
          >
            <span>Floor Seated: <strong style={{ color: 'var(--text-primary)' }}>{occupancyPercent}% Capacity</strong></span>
            <span style={{ color: 'var(--border)' }}>|</span>
            <span>Total Tables: <strong style={{ color: 'var(--text-primary)' }}>{tables.length}</strong></span>
          </div>

          <button
            onClick={loadTables}
            className="px-3 py-1.5 rounded-xl border text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
              color: 'var(--text-primary)',
            }}
          >
            Refresh
          </button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1">
        {['ALL', 'AVAILABLE', 'OCCUPIED', 'CLEANING', 'RESERVED'].map((f) => (
          <button
            key={f}
            onClick={() => setStatusFilter(f)}
            className="px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors duration-150 border whitespace-nowrap"
            style={{
              backgroundColor: statusFilter === f ? 'var(--btn-primary)' : 'var(--bg-card)',
              borderColor: statusFilter === f ? 'var(--btn-primary)' : 'var(--border)',
              color: statusFilter === f ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            {f === 'ALL' ? `All Tables (${tables.length})` : `${f.charAt(0) + f.slice(1).toLowerCase()} (${tables.filter(t => t.status === f).length})`}
          </button>
        ))}
      </div>

      {actionSuccess && (
        <div
          className="p-3.5 rounded-xl border text-xs flex items-center gap-2.5 shadow-sm transition-opacity duration-150"
          style={{
            backgroundColor: 'var(--status-available-bg)',
            borderColor: 'var(--status-available-border)',
            color: 'var(--status-available)',
          }}
        >
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span className="font-semibold">{actionSuccess}</span>
        </div>
      )}

      {actionError && (
        <div
          className="p-3.5 rounded-xl border text-xs flex items-center gap-2.5 shadow-sm transition-opacity duration-150"
          style={{
            backgroundColor: 'var(--status-critical-bg)',
            borderColor: 'var(--status-critical-border)',
            color: 'var(--status-critical)',
          }}
        >
          <span className="font-semibold">{actionError}</span>
        </div>
      )}

      {/* Main Grid & Drawer Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Table Cards Grid */}
        <div className={`${inspectorTable ? 'lg:col-span-8' : 'lg:col-span-12'} grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4`}>
          {filteredTables.map((table) => {
            const isWaiting = table.status === 'OCCUPIED' && !table.sessionAttendedAt && table.waitingSeconds > 0;
            let waitingColor = 'var(--text-secondary)';
            let borderStyle = '1px solid var(--border)';

            if (isWaiting) {
              if (table.waitingAlertLevel === 'CRITICAL') {
                waitingColor = 'var(--status-critical)';
                borderStyle = '1px solid var(--status-critical)';
              } else if (table.waitingAlertLevel === 'ESCALATED') {
                waitingColor = 'var(--status-occupied)';
                borderStyle = '1px solid var(--status-occupied)';
              } else if (table.waitingAlertLevel === 'ATTENTION') {
                waitingColor = 'var(--status-occupied)';
              }
            }

            return (
              <div
                key={table.tableId}
                className="rounded-2xl p-4 flex flex-col justify-between shadow-sm transition-colors duration-150 relative"
                style={{
                  backgroundColor: 'var(--bg-card)',
                  border: borderStyle,
                }}
              >
                <div>
                  {/* Card Header */}
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <span className="text-lg font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                        {table.tableCode}
                      </span>
                      <div className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                        Main Dining
                      </div>
                    </div>
                    {getStatusBadge(table.status)}
                  </div>

                  {/* Operational Metrics */}
                  <div className="space-y-1.5 text-xs py-2 border-t border-b mb-3" style={{ borderColor: 'var(--border)' }}>
                    <div className="flex justify-between">
                      <span style={{ color: 'var(--text-secondary)' }}>Capacity / Seats:</span>
                      <span className="font-semibold tabular" style={{ color: 'var(--text-primary)' }}>
                        {table.capacity} seated
                      </span>
                    </div>

                    <div className="flex justify-between">
                      <span style={{ color: 'var(--text-secondary)' }}>Status Source:</span>
                      <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                        {table.statusSource || 'CCTV_AI'}
                      </span>
                    </div>

                    {table.status === 'OCCUPIED' && (
                      <>
                        <div className="flex justify-between">
                          <span style={{ color: 'var(--text-secondary)' }}>Dining Wait Time:</span>
                          <span className="font-bold tabular" style={{ color: waitingColor }}>
                            {table.sessionAttendedAt ? (
                              <span style={{ color: 'var(--status-available)' }}>Attended</span>
                            ) : (
                              formatWaitTime(table.waitingSeconds)
                            )}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span style={{ color: 'var(--text-secondary)' }}>Assigned Server:</span>
                          <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                            {table.assignedWaiterName || 'Unassigned'}
                          </span>
                        </div>
                      </>
                    )}
                  </div>

                  {table.status === 'CLEANING' && (
                    <div
                      className="p-2 rounded-xl text-xs mb-3 flex items-center gap-2 border"
                      style={{
                        backgroundColor: 'var(--status-info-bg)',
                        borderColor: 'var(--status-info-border)',
                        color: 'var(--status-info)',
                      }}
                    >
                      <Sparkles className="w-3.5 h-3.5 shrink-0 motion-safe:animate-spin motion-reduce:animate-none" />
                      <span>Turnover dispatched to housekeeping.</span>
                    </div>
                  )}
                </div>

                {/* Card Action Buttons */}
                <div className="space-y-2 pt-1">
                  {table.status === 'AVAILABLE' && (
                    <button
                      onClick={() => {
                        setSelectedTable(table);
                        setPartySize(table.capacity > 2 ? 4 : 2);
                        setSeatModalOpen(true);
                      }}
                      className="w-full py-2 px-3 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--btn-primary)',
                        color: 'var(--btn-primary-text)',
                      }}
                    >
                      <PlusCircle className="w-3.5 h-3.5" /> Seat Walk-In
                    </button>
                  )}

                  {table.status === 'OCCUPIED' && !table.sessionAttendedAt && (
                    <button
                      onClick={() => handleAttend(table.tableId)}
                      className="w-full py-2 px-3 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--btn-primary)',
                        color: 'var(--btn-primary-text)',
                      }}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" /> Mark Attended
                    </button>
                  )}

                  <div className="grid grid-cols-2 gap-2">
                    <button
                      onClick={() => handleOpenInspector(table)}
                      className="py-1.5 px-2 rounded-xl text-xs font-semibold border transition-colors duration-150 flex items-center justify-center gap-1 whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--bg-main)',
                        borderColor: 'var(--border)',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      <Eye className="w-3 h-3" /> Details
                    </button>

                    <button
                      onClick={() => {
                        setSelectedTable(table);
                        setOverrideStatus(table.status);
                        setOverrideModalOpen(true);
                      }}
                      className="py-1.5 px-2 rounded-xl text-xs font-semibold border transition-colors duration-150 flex items-center justify-center gap-1 whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--bg-main)',
                        borderColor: 'var(--border)',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      <Settings2 className="w-3 h-3" /> Override
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Right: Table Inspector Drawer */}
        {inspectorTable && (
          <div
            id="table-inspector"
            className="lg:col-span-4 rounded-2xl p-5 border space-y-4 shadow-sm lg:sticky lg:top-4 self-start animate-fade-in"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <div className="flex items-center justify-between pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
              <div>
                <h3 className="font-bold text-base" style={{ color: 'var(--text-primary)' }}>
                  {inspectorTable.tableCode} Inspector
                </h3>
                <div className="text-xs" style={{ color: 'var(--text-muted)' }}>
                  Main Dining Room · Camera CAM-01
                </div>
              </div>
              <button
                onClick={() => setInspectorTable(null)}
                className="p-1 rounded-lg hover:opacity-75"
                style={{ color: 'var(--text-secondary)' }}
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Simulated Live RTSP Camera Crop with YOLO Box */}
            <div
              className="relative rounded-xl overflow-hidden aspect-video border flex items-center justify-center"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
              }}
            >
              <div
                className="border-2 rounded-lg p-2.5 text-center text-xs font-bold"
                style={{
                  borderColor: 'var(--status-available)',
                  backgroundColor: 'var(--status-available-bg)',
                  color: 'var(--status-available)',
                }}
              >
                YOLOv8 Inference: 98.4% confidence
              </div>
              <div
                className="absolute bottom-2 left-2 text-[10px] px-1.5 py-0.5 rounded border"
                style={{
                  backgroundColor: 'var(--bg-card)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-muted)',
                }}
              >
                Stream: 1080p RTSP
              </div>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between py-1 border-b" style={{ borderColor: 'var(--border)' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Lifecycle Status:</span>
                <strong style={{ color: 'var(--text-primary)' }}>{inspectorTable.status}</strong>
              </div>
              <div className="flex justify-between py-1 border-b" style={{ borderColor: 'var(--border)' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Wait Time Elapsed:</span>
                <strong className="tabular" style={{ color: 'var(--text-primary)' }}>
                  {formatWaitTime(inspectorTable.waitingSeconds)}
                </strong>
              </div>
              <div className="flex justify-between py-1 border-b" style={{ borderColor: 'var(--border)' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Assigned Server:</span>
                <strong style={{ color: 'var(--text-primary)' }}>{inspectorTable.assignedWaiterName || 'Unassigned'}</strong>
              </div>
              <div className="flex justify-between py-1 border-b" style={{ borderColor: 'var(--border)' }}>
                <span style={{ color: 'var(--text-secondary)' }}>Status Origin:</span>
                <strong className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  {inspectorTable.statusSource || 'EDGE_CCTV'}
                </strong>
              </div>
            </div>

            <div className="pt-2 space-y-2">
              <button
                onClick={() => {
                  setSelectedTable(inspectorTable);
                  setPartySize(inspectorTable.capacity > 2 ? 4 : 2);
                  setSeatModalOpen(true);
                }}
                className="w-full py-2.5 rounded-xl text-xs font-bold transition-colors duration-150 shadow-sm whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--btn-primary)',
                  color: 'var(--btn-primary-text)',
                }}
              >
                Modify Seating / Server
              </button>

              <button
                onClick={() => {
                  setSelectedTable(inspectorTable);
                  setOverrideStatus(inspectorTable.status);
                  setOverrideModalOpen(true);
                }}
                className="w-full py-2.5 rounded-xl border text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-primary)',
                }}
              >
                Override AI State (120s)
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Staff Override Modal */}
      {overrideModalOpen && selectedTable && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div
            className="rounded-2xl w-full max-w-md p-6 space-y-4 border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                <Settings2 className="w-4 h-4" />
                Staff Override: {selectedTable.tableCode}
              </h3>
              <button
                onClick={() => setOverrideModalOpen(false)}
                className="text-xs hover:opacity-70"
                style={{ color: 'var(--text-secondary)' }}
              >
                ✕
              </button>
            </div>

            <p className="text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              Per specification §6.2, <strong>Staff beats AI</strong>. Changing status manually applies a 120s AI lockout to prevent flapping, and writes an audit log entry.
            </p>

            <div className="space-y-3">
              <div>
                <label className="text-xs font-semibold block mb-1.5" style={{ color: 'var(--text-primary)' }}>
                  New Table Status
                </label>
                <select
                  value={overrideStatus}
                  onChange={(e) => setOverrideStatus(e.target.value)}
                  className="w-full border rounded-xl px-3 py-2 text-xs focus:outline-none"
                  style={{
                    backgroundColor: 'var(--bg-main)',
                    borderColor: 'var(--border)',
                    color: 'var(--text-primary)',
                  }}
                >
                  <option value="AVAILABLE">AVAILABLE (Empty &amp; Ready)</option>
                  <option value="OCCUPIED">OCCUPIED (Guests seated)</option>
                  <option value="CLEANING">CLEANING (Bussing in progress)</option>
                  <option value="RESERVED">RESERVED (Incoming booking)</option>
                  <option value="OUT_OF_SERVICE">OUT_OF_SERVICE (Maintenance)</option>
                </select>
              </div>

              <div>
                <label className="text-xs font-semibold block mb-1.5" style={{ color: 'var(--text-primary)' }}>
                  Reason for Audit Log
                </label>
                <input
                  type="text"
                  placeholder="e.g. Guests arrived early; AI missed corner seat"
                  value={overrideReason}
                  onChange={(e) => setOverrideReason(e.target.value)}
                  className="w-full border rounded-xl px-3 py-2 text-xs focus:outline-none"
                  style={{
                    backgroundColor: 'var(--bg-main)',
                    borderColor: 'var(--border)',
                    color: 'var(--text-primary)',
                  }}
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                onClick={() => setOverrideModalOpen(false)}
                className="px-4 py-2 border rounded-xl text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-secondary)',
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleOverrideStatus}
                className="px-4 py-2 rounded-xl text-xs font-semibold transition-colors duration-150 shadow-sm whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--btn-primary)',
                  color: 'var(--btn-primary-text)',
                }}
              >
                Confirm Override
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Seat Party Modal */}
      {seatModalOpen && selectedTable && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div
            className="rounded-2xl w-full max-w-sm p-6 space-y-4 border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <h3 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <PlusCircle className="w-4 h-4" />
              Seat Party at {selectedTable.tableCode}
            </h3>

            <div>
              <label className="text-xs font-semibold block mb-2" style={{ color: 'var(--text-primary)' }}>
                Party Size (Guests)
              </label>
              <div className="grid grid-cols-4 gap-2">
                {[1, 2, 3, 4, 5, 6, 7, 8].map((num) => (
                  <button
                    key={num}
                    onClick={() => setPartySize(num)}
                    className="py-2 rounded-xl text-xs font-bold border transition-colors duration-150"
                    style={{
                      backgroundColor: partySize === num ? 'var(--btn-primary)' : 'var(--bg-main)',
                      borderColor: partySize === num ? 'var(--btn-primary)' : 'var(--border)',
                      color: partySize === num ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
                    }}
                  >
                    {num}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3">
              <button
                onClick={() => setSeatModalOpen(false)}
                className="px-4 py-2 border rounded-xl text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-secondary)',
                }}
              >
                Cancel
              </button>
              <button
                onClick={handleSeatParty}
                className="px-4 py-2 rounded-xl text-xs font-semibold transition-colors duration-150 shadow-sm whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--btn-primary)',
                  color: 'var(--btn-primary-text)',
                }}
              >
                Open Session
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
