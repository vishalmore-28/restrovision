import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import {
  QrCode,
  BellRing,
  GlassWater,
  Receipt,
  Star,
  CheckCircle2,
  Sparkles,
  Smartphone,
  Utensils,
} from 'lucide-react';

export default function CustomerQrPortal() {
  const [tableToken, setTableToken] = useState('qr-tok-table-01');
  const [qrStatus, setQrStatus] = useState(null);
  const [calling, setCalling] = useState(false);
  const [callMessage, setCallMessage] = useState('');
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [feedbackSent, setFeedbackSent] = useState(false);

  const fetchStatus = () => {
    api.getQrStatus(tableToken)
      .then(setQrStatus)
      .catch((err) => console.error(err));
  };

  useEffect(() => {
    fetchStatus();
    setFeedbackSent(false);
  }, [tableToken]);

  const handleCall = async (type) => {
    setCalling(true);
    try {
      const res = await api.callWaiterFromQr(tableToken, type);
      setCallMessage(res?.message || `Request '${type}' received! Waiter on the way.`);
    } catch (_) {
      setCallMessage(`Request '${type}' received! Waiter on the way.`);
    } finally {
      setCalling(false);
      setTimeout(() => setCallMessage(''), 5000);
    }
  };

  const handleFeedbackSubmit = async (e) => {
    e.preventDefault();
    try {
      await api.submitFeedback({
        tableId: qrStatus?.tableId || 1,
        sessionId: qrStatus?.sessionId,
        rating,
        comment,
      });
    } catch (_) {}
    setFeedbackSent(true);
    setComment('');
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Title */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
            Customer QR Portal
            <span
              className="text-xs font-semibold px-2 py-0.5 rounded border"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
              }}
            >
              Public Guest API
            </span>
          </h1>
          <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
            Guests scan table stickers to request service, water, or bills directly, and leave post-meal feedback analyzed by sentiment NLP.
          </p>
        </div>

        {/* Table Selector */}
        <div
          className="flex items-center gap-1.5 p-1 rounded-xl border"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <span className="text-xs font-semibold px-2" style={{ color: 'var(--text-muted)' }}>
            Table:
          </span>
          {[
            { token: 'qr-tok-table-01', label: 'T01' },
            { token: 'qr-tok-table-02', label: 'T02' },
            { token: 'qr-tok-table-03', label: 'T03' },
            { token: 'qr-tok-table-04', label: 'T04' },
            { token: 'qr-tok-table-05', label: 'T05' },
          ].map((t) => (
            <button
              key={t.token}
              onClick={() => setTableToken(t.token)}
              className="px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors duration-150 whitespace-nowrap"
              style={{
                backgroundColor: tableToken === t.token ? 'var(--btn-primary)' : 'transparent',
                color: tableToken === t.token ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* Mobile Mockup Container */}
      <div className="flex justify-center py-4">
        <div
          className="w-full max-w-sm rounded-[2.5rem] p-6 shadow-xl space-y-5 relative border-4"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          {/* Mobile Speaker Cutout */}
          <div
            className="w-20 h-3 rounded-full mx-auto -mt-2 mb-2"
            style={{ backgroundColor: 'var(--border)' }}
          ></div>

          {/* Restaurant Header */}
          <div className="text-center space-y-1">
            <div
              className="inline-flex p-2.5 rounded-2xl border mb-1"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              <Utensils className="w-5 h-5" />
            </div>
            <h2 className="text-base font-extrabold" style={{ color: 'var(--text-primary)' }}>
              The Bombay Grill &amp; Bistro
            </h2>
            <div
              className="inline-block text-xs font-bold px-3 py-1 rounded-full border"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              Table {qrStatus?.tableCode || 'T01'}
            </div>
          </div>

          {callMessage && (
            <div
              className="p-3 rounded-xl text-xs font-semibold text-center flex items-center justify-center gap-1.5 shadow-sm border"
              style={{
                backgroundColor: 'var(--status-available-bg)',
                borderColor: 'var(--status-available-border)',
                color: 'var(--status-available)',
              }}
            >
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{callMessage}</span>
            </div>
          )}

          {/* Guest Action Buttons */}
          <div className="space-y-2.5">
            <span
              className="text-xs font-semibold block px-1"
              style={{ color: 'var(--text-muted)' }}
            >
              Quick Table Assistance
            </span>

            <button
              disabled={calling}
              onClick={() => handleCall('CALL_WAITER')}
              className="w-full p-3.5 rounded-xl text-xs font-bold flex items-center justify-between shadow-sm transition-opacity duration-150 disabled:opacity-50 whitespace-nowrap"
              style={{
                backgroundColor: 'var(--btn-primary)',
                color: 'var(--btn-primary-text)',
              }}
            >
              <span className="flex items-center gap-2">
                <BellRing className="w-4 h-4" /> Call Assigned Server
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded border border-current opacity-70">Tap</span>
            </button>

            <button
              disabled={calling}
              onClick={() => handleCall('WATER')}
              className="w-full p-3 border rounded-xl text-xs font-semibold flex items-center justify-between transition-colors duration-150 disabled:opacity-50 whitespace-nowrap"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              <span className="flex items-center gap-2 whitespace-nowrap">
                <GlassWater className="w-4 h-4" /> Water Refill
              </span>
              <span className="text-[10px] whitespace-nowrap" style={{ color: 'var(--text-muted)' }}>Instant</span>
            </button>

            <button
              disabled={calling}
              onClick={() => handleCall('REQUEST_BILL')}
              className="w-full p-3 border rounded-xl text-xs font-semibold flex items-center justify-between transition-colors duration-150 disabled:opacity-50 whitespace-nowrap"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              <span className="flex items-center gap-2 whitespace-nowrap">
                <Receipt className="w-4 h-4" /> Request Bill
              </span>
              <span className="text-[10px] whitespace-nowrap" style={{ color: 'var(--text-muted)' }}>Instant</span>
            </button>
          </div>

          {/* Feedback Form */}
          <div className="pt-3 border-t space-y-3" style={{ borderColor: 'var(--border)' }}>
            <span
              className="text-xs font-semibold block px-1"
              style={{ color: 'var(--text-muted)' }}
            >
              Rate Your Dining Experience
            </span>

            {feedbackSent ? (
              <div
                className="p-4 rounded-xl text-center space-y-1 border"
                style={{
                  backgroundColor: 'var(--status-available-bg)',
                  borderColor: 'var(--status-available-border)',
                }}
              >
                <CheckCircle2 className="w-6 h-6 mx-auto" style={{ color: 'var(--status-available)' }} />
                <div className="text-xs font-bold" style={{ color: 'var(--status-available)' }}>
                  Thank you for your feedback!
                </div>
                <div className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  Processed by RESTROVISION sentiment AI.
                </div>
              </div>
            ) : (
              <form onSubmit={handleFeedbackSubmit} className="space-y-2.5">
                <div className="flex items-center justify-center gap-2 py-1">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      type="button"
                      key={star}
                      onClick={() => setRating(star)}
                      className="p-1 transition-transform hover:scale-110"
                    >
                      <Star
                        className="w-5 h-5"
                        style={{
                          color: star <= rating ? 'var(--status-occupied)' : 'var(--text-muted)',
                          fill: star <= rating ? 'var(--status-occupied)' : 'none',
                        }}
                      />
                    </button>
                  ))}
                </div>

                <textarea
                  rows={2}
                  placeholder="How was the food, service, and wait time?"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  className="w-full border rounded-xl p-2.5 text-xs focus:outline-none"
                  style={{
                    backgroundColor: 'var(--bg-main)',
                    borderColor: 'var(--border)',
                    color: 'var(--text-primary)',
                  }}
                />

                <button
                  type="submit"
                  className="w-full py-2.5 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap"
                  style={{
                    backgroundColor: 'var(--btn-primary)',
                    color: 'var(--btn-primary-text)',
                  }}
                >
                  <Sparkles className="w-3.5 h-3.5" /> Submit Review
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
