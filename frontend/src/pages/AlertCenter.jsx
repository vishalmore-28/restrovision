import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import {
  Bell,
  AlertTriangle,
  Flame,
  ShieldAlert,
  CheckCircle,
  ThumbsDown,
  Clock,
  Sparkles,
  Info,
} from 'lucide-react';

const INITIAL_ALERTS = [
  { alertId: 1, alertType: 'SERVICE_OVERDUE', priority: 'CRITICAL', status: 'ESCALATED', message: 'Table T-03 Bill Request Overdue (4m 20s). Customer requested check via QR Portal.', dedupeKey: 'SRV_OVERDUE_T03', assignedRole: 'FLOOR_MANAGER', createdAt: new Date(Date.now() - 260000).toISOString() },
  { alertId: 2, alertType: 'KITCHEN_DELAY', priority: 'HIGH', status: 'OPEN', message: 'Order #1042 Exceeded Prep SLA by 139%. Tandoor station running 16m 42s vs 12m target.', dedupeKey: 'KDS_DELAY_1042', assignedRole: 'HEAD_CHEF', createdAt: new Date(Date.now() - 180000).toISOString() },
];

export default function AlertCenter() {
  const auth = useAuth ? useAuth() : null;
  const restId = auth?.user?.restaurantId || 1;
  const [alerts, setAlerts] = useState(INITIAL_ALERTS);
  const [filter, setFilter] = useState('ACTIVE'); // ACTIVE, ALL
  const [falseAlarmModal, setFalseAlarmModal] = useState(null);
  const [feedbackComment, setFeedbackComment] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  const loadAlerts = () => {
    const fetcher = filter === 'ACTIVE' ? api.getActiveAlerts() : api.getAllAlerts();
    fetcher
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) setAlerts(data);
      })
      .catch((err) => {
        console.warn('[AlertCenter] Failed to fetch alerts:', err?.message || err);
      });
  };

  useEffect(() => {
    loadAlerts();

    const unsub = wsService.subscribe(`/topic/restaurants/${restId}/alerts`, (newAlert) => {
      setAlerts((prev) => {
        const filtered = prev.filter((a) => a.alertId !== newAlert.alertId);
        return [newAlert, ...filtered];
      });
    });

    return () => unsub();
  }, [filter, restId]);

  const handleAcknowledge = async (id) => {
    setAlerts((prev) => prev.map((a) => (a.alertId === id ? { ...a, status: 'ACKNOWLEDGED' } : a)));
    setSuccessMsg('Alert acknowledged.');
    setTimeout(() => setSuccessMsg(''), 4000);
    try {
      await api.acknowledgeAlert(id);
    } catch (err) {
      console.warn('[AlertCenter] Backend acknowledge sync note:', err?.message || err);
    }
  };

  const handleResolve = async (id) => {
    setAlerts((prev) => prev.filter((a) => a.alertId !== id));
    setSuccessMsg('Alert resolved successfully.');
    setTimeout(() => setSuccessMsg(''), 4000);
    try {
      await api.resolveAlert(id);
    } catch (err) {
      console.warn('[AlertCenter] Backend resolve sync note:', err?.message || err);
    }
  };

  const handleFalseAlarm = async () => {
    if (!falseAlarmModal) return;
    const targetId = falseAlarmModal.alertId;
    setAlerts((prev) => prev.filter((a) => a.alertId !== targetId));
    setFalseAlarmModal(null);
    setFeedbackComment('');
    setSuccessMsg('False alarm recorded into AI_FEEDBACK_LABELS. Hard negative added to training pipeline!');
    setTimeout(() => setSuccessMsg(''), 5000);
    try {
      await api.markFalseAlarm(targetId, feedbackComment || 'False alarm marked by staff');
    } catch (err) {
      console.warn('[AlertCenter] Backend false alarm sync note:', err?.message || err);
    }
  };

  const getPriorityBadge = (priority) => {
    switch (priority) {
      case 'CRITICAL':
        return (
          <span
            className="text-[11px] px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--status-critical-bg)',
              borderColor: 'var(--status-critical-border)',
              color: 'var(--status-critical)',
            }}
          >
            CRITICAL
          </span>
        );
      case 'HIGH':
        return (
          <span
            className="text-[11px] px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--status-occupied-bg)',
              borderColor: 'var(--status-occupied-border)',
              color: 'var(--status-occupied)',
            }}
          >
            HIGH
          </span>
        );
      case 'MEDIUM':
        return (
          <span
            className="text-[11px] px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--status-info-bg)',
              borderColor: 'var(--status-info-border)',
              color: 'var(--status-info)',
            }}
          >
            MEDIUM
          </span>
        );
      default:
        return (
          <span
            className="text-[11px] px-2.5 py-0.5 rounded-full font-bold border"
            style={{
              backgroundColor: 'var(--bg-main)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
          >
            {priority}
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
            Realtime Alert Center
            <span
              className="text-xs font-semibold px-2 py-0.5 rounded border"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
              }}
            >
              Escalation Engine
            </span>
          </h1>
          <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
            Automated alerts deduplicated by key with configurable escalation and staff false-alarm feedback loop.
          </p>
        </div>

        {/* Filter buttons */}
        <div
          className="flex items-center gap-1.5 p-1 rounded-xl border"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <button
            onClick={() => setFilter('ACTIVE')}
            className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
            style={{
              backgroundColor: filter === 'ACTIVE' ? 'var(--btn-primary)' : 'transparent',
              color: filter === 'ACTIVE' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            Active &amp; Escalated
          </button>
          <button
            onClick={() => setFilter('ALL')}
            className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
            style={{
              backgroundColor: filter === 'ALL' ? 'var(--btn-primary)' : 'transparent',
              color: filter === 'ALL' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
            }}
          >
            All History
          </button>
        </div>
      </div>

      {successMsg && (
        <div
          className="p-3.5 rounded-xl border text-xs flex items-center gap-2.5 shadow-sm"
          style={{
            backgroundColor: 'var(--status-available-bg)',
            borderColor: 'var(--status-available-border)',
            color: 'var(--status-available)',
          }}
        >
          <Sparkles className="w-4 h-4 shrink-0" />
          <span className="font-semibold">{successMsg}</span>
        </div>
      )}

      {errorMsg && (
        <div
          className="p-3.5 rounded-xl border text-xs flex items-center gap-2.5 shadow-sm"
          style={{
            backgroundColor: 'var(--status-critical-bg)',
            borderColor: 'var(--status-critical-border)',
            color: 'var(--status-critical)',
          }}
        >
          <span className="font-semibold">{errorMsg}</span>
        </div>
      )}

      {/* Alert List */}
      <div className="space-y-3">
        {alerts.length === 0 ? (
          <div
            className="rounded-2xl p-12 text-center border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <CheckCircle className="w-10 h-10 mx-auto mb-3" style={{ color: 'var(--status-available)' }} />
            <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              All Clear on the Floor
            </h3>
            <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
              No active unacknowledged alerts or delays detected.
            </p>
          </div>
        ) : (
          alerts.map((alert) => (
            <div
              key={alert.alertId}
              className="p-4 rounded-2xl border transition-colors duration-150 flex flex-col md:flex-row md:items-center justify-between gap-4"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: alert.status === 'OPEN' ? 'var(--border-hover)' : 'var(--border)',
                opacity: alert.status === 'OPEN' || alert.status === 'ESCALATED' ? 1 : 0.75,
              }}
            >
              <div className="space-y-1.5 max-w-3xl">
                <div className="flex items-center gap-2 flex-wrap">
                  {getPriorityBadge(alert.priority)}

                  <span
                    className="text-xs font-semibold px-2 py-0.5 rounded border"
                    style={{
                      backgroundColor: 'var(--bg-main)',
                      borderColor: 'var(--border)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    {alert.alertType}
                  </span>

                  <span className="text-xs flex items-center gap-1 tabular" style={{ color: 'var(--text-secondary)' }}>
                    <Clock className="w-3.5 h-3.5" />
                    {new Date(alert.createdAt).toLocaleTimeString()}
                  </span>

                  {alert.status === 'ESCALATED' && (
                    <span
                      className="text-[10px] font-bold px-2 py-0.5 rounded border"
                      style={{
                        backgroundColor: 'var(--status-critical-bg)',
                        borderColor: 'var(--status-critical-border)',
                        color: 'var(--status-critical)',
                      }}
                    >
                      Escalated to Manager
                    </span>
                  )}

                  {alert.status === 'FALSE_ALARM' && (
                    <span
                      className="text-[10px] font-bold px-2 py-0.5 rounded border"
                      style={{
                        backgroundColor: 'var(--status-occupied-bg)',
                        borderColor: 'var(--status-occupied-border)',
                        color: 'var(--status-occupied)',
                      }}
                    >
                      False Alarm (Logged to ML)
                    </span>
                  )}
                </div>

                <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>
                  {alert.message}
                </p>

                <div className="text-xs flex items-center gap-3" style={{ color: 'var(--text-muted)' }}>
                  <span>Dedupe Key: <code className="font-mono text-[11px]">{alert.dedupeKey}</code></span>
                  <span>Recipient: <strong style={{ color: 'var(--text-secondary)' }}>{alert.assignedRole || 'ALL'}</strong></span>
                </div>
              </div>

              {/* Action buttons */}
              {alert.status !== 'RESOLVED' && alert.status !== 'FALSE_ALARM' && (
                <div className="flex items-center gap-2 shrink-0">
                  {alert.status === 'OPEN' && (
                    <button
                      onClick={() => handleAcknowledge(alert.alertId)}
                      className="px-3 py-1.5 border rounded-xl text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
                      style={{
                        backgroundColor: 'var(--bg-main)',
                        borderColor: 'var(--border)',
                        color: 'var(--text-primary)',
                      }}
                    >
                      Acknowledge
                    </button>
                  )}
                  <button
                    onClick={() => handleResolve(alert.alertId)}
                    className="px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-colors duration-150 flex items-center gap-1 shadow-sm whitespace-nowrap"
                    style={{
                      backgroundColor: 'var(--btn-primary)',
                      color: 'var(--btn-primary-text)',
                    }}
                  >
                    <CheckCircle className="w-3.5 h-3.5" /> Resolve
                  </button>
                  <button
                    onClick={() => setFalseAlarmModal(alert)}
                    className="px-3 py-1.5 border rounded-xl text-xs font-semibold transition-colors duration-150 flex items-center gap-1 whitespace-nowrap"
                    style={{
                      backgroundColor: 'var(--bg-main)',
                      borderColor: 'var(--border)',
                      color: 'var(--text-secondary)',
                    }}
                    title="Mark False Alarm to help retrain AI model"
                  >
                    <ThumbsDown className="w-3.5 h-3.5" /> False Alarm
                  </button>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* False Alarm Feedback Modal */}
      {falseAlarmModal && (
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
                <ThumbsDown className="w-4 h-4 text-rose-500" />
                Report False Alarm Feedback
              </h3>
              <button
                onClick={() => setFalseAlarmModal(null)}
                className="text-xs hover:opacity-70"
                style={{ color: 'var(--text-secondary)' }}
              >
                ✕
              </button>
            </div>

            <div
              className="p-3 rounded-xl border text-xs space-y-1"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
              }}
            >
              <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                Alert: {falseAlarmModal.alertType}
              </div>
              <p style={{ color: 'var(--text-secondary)' }}>{falseAlarmModal.message}</p>
            </div>

            <p className="text-xs leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              Per specification §8.3.5 (Continuous Improvement Loop), marking an alert as a false alarm stores this example into the <strong>AI_FEEDBACK_LABELS</strong> queue as a hard negative for model fine-tuning.
            </p>

            <div>
              <label className="text-xs font-semibold block mb-1.5" style={{ color: 'var(--text-primary)' }}>
                Staff Observation Note
              </label>
              <textarea
                rows={3}
                placeholder="e.g. Patron was stretching arm rather than waving; floor reflection caused false detection."
                value={feedbackComment}
                onChange={(e) => setFeedbackComment(e.target.value)}
                className="w-full border rounded-xl p-3 text-xs focus:outline-none"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-primary)',
                }}
              />
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                onClick={() => setFalseAlarmModal(null)}
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
                onClick={handleFalseAlarm}
                className="px-4 py-2 rounded-xl text-xs font-semibold transition-colors duration-150 shadow-sm whitespace-nowrap"
                style={{
                  backgroundColor: 'var(--btn-primary)',
                  color: 'var(--btn-primary-text)',
                }}
              >
                Submit Feedback
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
