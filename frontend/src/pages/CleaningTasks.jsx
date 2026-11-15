import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import {
  Sparkles,
  CheckCircle2,
  Clock,
  Play,
  RotateCcw,
  ShieldCheck,
  AlertTriangle,
} from 'lucide-react';

const INITIAL_TASKS = [
  { taskId: 1, tableId: 6, tableCode: 'T-06', taskType: 'TABLE_CLEANING', status: 'IN_PROGRESS', createdAt: new Date(Date.now() - 360000).toISOString() },
  { taskId: 2, tableId: 8, tableCode: 'T-08', taskType: 'SPILL_CLEANUP', status: 'OPEN', createdAt: new Date(Date.now() - 120000).toISOString() },
];

export default function CleaningTasks() {
  const auth = useAuth ? useAuth() : null;
  const restId = auth?.user?.restaurantId || 2;
  const [tasks, setTasks] = useState(INITIAL_TASKS);
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  const loadTasks = () => {
    api.getTasks()
      .then((all) => {
        if (Array.isArray(all)) {
          setTasks(all.filter((t) => t.taskType === 'TABLE_CLEANING' || t.taskType === 'SPILL_CLEANUP'));
        }
      })
      .catch((err) => console.warn('[CleaningTasks] Failed to fetch tasks:', err?.message || err))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadTasks();
    const unsub = wsService.subscribe(`/topic/restaurants/${restId}/tasks`, () => loadTasks());
    return () => unsub();
  }, [restId]);

  const handleStart = async (taskId) => {
    setTasks((prev) => prev.map((t) => (t.taskId === taskId ? { ...t, status: 'IN_PROGRESS' } : t)));
    try {
      await api.startTask(taskId);
    } catch (err) {
      console.warn('[CleaningTasks] Backend start sync note:', err?.message || err);
    }
  };

  const handleComplete = async (taskId, tableCode) => {
    setTasks((prev) => prev.filter((t) => t.taskId !== taskId));
    setSuccessMsg(`Table ${tableCode} sanitized & returned to AVAILABLE on floor!`);
    setTimeout(() => setSuccessMsg(''), 4000);
    try {
      await api.completeTask(taskId);
    } catch (err) {
      console.warn('[CleaningTasks] Backend complete sync note:', err?.message || err);
    }
  };

  const openTasks = tasks.filter((t) => t.status === 'OPEN' || t.status === 'ASSIGNED' || t.status === 'IN_PROGRESS');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
          Cleaning &amp; Turnover Management
          <span
            className="text-xs font-semibold px-2 py-0.5 rounded border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
          >
            Sanitization Gate
          </span>
        </h1>
        <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
          Per specification §6.1, a table never returns to AVAILABLE from AI vacancy alone. Housekeeping confirmation is the authoritative gate.
        </p>
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
          <ShieldCheck className="w-4 h-4 shrink-0" />
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

      {/* Task Queue */}
      <div className="space-y-3">
        {openTasks.length === 0 ? (
          <div
            className="rounded-2xl p-12 text-center border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <CheckCircle2 className="w-10 h-10 mx-auto mb-3" style={{ color: 'var(--status-available)' }} />
            <h3 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              All Tables Clean &amp; Ready
            </h3>
            <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
              No pending table turnover or bussing tasks.
            </p>
          </div>
        ) : (
          openTasks.map((task) => (
            <div
              key={task.taskId}
              className="p-4 rounded-2xl border shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4 transition-colors duration-150"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: task.status === 'IN_PROGRESS' ? 'var(--status-occupied)' : 'var(--border)',
              }}
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
                    {task.tableCode || `T${task.tableId}`}
                  </span>
                  <span
                    className="text-xs font-semibold px-2 py-0.5 rounded border"
                    style={{
                      backgroundColor: 'var(--bg-main)',
                      borderColor: 'var(--border)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    {task.taskType}
                  </span>
                  <span className="text-xs flex items-center gap-1 tabular" style={{ color: 'var(--text-secondary)' }}>
                    <Clock className="w-3.5 h-3.5" />
                    Created: {new Date(task.createdAt).toLocaleTimeString()}
                  </span>
                </div>
                <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
                  Customer departed. Table dishes need bussing and surface sanitation before seating new guests.
                </p>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                {task.status !== 'IN_PROGRESS' ? (
                  <button
                    onClick={() => handleStart(task.taskId)}
                    className="px-3 py-1.5 border rounded-xl text-xs font-semibold transition-colors duration-150 flex items-center gap-1 whitespace-nowrap"
                    style={{
                      backgroundColor: 'var(--bg-main)',
                      borderColor: 'var(--border)',
                      color: 'var(--text-primary)',
                    }}
                  >
                    <Play className="w-3.5 h-3.5" /> Start Cleaning
                  </button>
                ) : (
                  <span
                    className="text-xs font-semibold px-2.5 py-1 rounded-lg border flex items-center gap-1.5 whitespace-nowrap"
                    style={{
                      backgroundColor: 'var(--status-occupied-bg)',
                      borderColor: 'var(--status-occupied-border)',
                      color: 'var(--status-occupied)',
                    }}
                  >
                    <Sparkles className="w-3.5 h-3.5 motion-safe:animate-spin motion-reduce:animate-none" /> In Progress
                  </span>
                )}

                <button
                  onClick={() => handleComplete(task.taskId, task.tableCode)}
                  className="px-3.5 py-1.5 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center gap-1.5 shadow-sm whitespace-nowrap"
                  style={{
                    backgroundColor: 'var(--btn-primary)',
                    color: 'var(--btn-primary-text)',
                  }}
                >
                  <CheckCircle2 className="w-3.5 h-3.5" /> Mark Clean
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
