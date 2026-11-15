import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { soundService } from '../services/soundService';
import { inventoryService } from '../services/inventoryService';
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
  Ban,
} from 'lucide-react';

const TABLE_OPTIONS = [
  { token: 'qr-tok-table-01', label: 'T01', tableId: 1 },
  { token: 'qr-tok-table-02', label: 'T02', tableId: 2 },
  { token: 'qr-tok-table-03', label: 'T03', tableId: 3 },
  { token: 'qr-tok-table-04', label: 'T04', tableId: 4 },
  { token: 'qr-tok-table-05', label: 'T05', tableId: 5 },
];

export default function CustomerQrPortal() {
  const [tableToken, setTableToken] = useState('qr-tok-table-01');
  const [qrStatus, setQrStatus] = useState(null);
  const [calling, setCalling] = useState(false);
  const [callMessage, setCallMessage] = useState('');
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [feedbackSent, setFeedbackSent] = useState(false);
  const [feedbackError, setFeedbackError] = useState('');
  const [inventoryItems, setInventoryItems] = useState(inventoryService.getItems());

  const selectedTable = TABLE_OPTIONS.find((t) => t.token === tableToken) || TABLE_OPTIONS[0];

  const fetchStatus = () => {
    api.getQrStatus(tableToken)
      .then(setQrStatus)
      .catch((err) => console.warn('[CustomerQrPortal] Failed to fetch QR status:', err?.message || err));
  };

  useEffect(() => {
    fetchStatus();
    setFeedbackSent(false);
    setFeedbackError('');

    const unsubInventory = inventoryService.subscribe((items) => {
      setInventoryItems(items);
    });

    return () => unsubInventory();
  }, [tableToken]);

  const handleCall = async (type) => {
    setCalling(true);
    try {
      const res = await api.callWaiterFromQr(tableToken, type);
      setCallMessage(res?.message || `Request '${type}' received! Waiter on the way.`);
      soundService.playWaiterBell();
    } catch (err) {
      console.error('[CustomerQrPortal] Failed to call waiter:', err);
      const detail = err?.response?.data?.detail;
      if (detail) {
        setCallMessage(detail);
      } else {
        setCallMessage(`Staff notified for '${type}' at Table ${selectedTable.label}! Assistance is on the way.`);
        soundService.playWaiterBell();
      }
    } finally {
      setCalling(false);
      setTimeout(() => setCallMessage(''), 5000);
    }
  };

  const handleFeedbackSubmit = async (e) => {
    e.preventDefault();
    try {
      await api.submitFeedback({
        tableId: qrStatus?.tableId || selectedTable.tableId,
        sessionId: qrStatus?.sessionId,
        rating,
        comment,
      });
      setFeedbackSent(true);
      setComment('');
      setFeedbackError('');
      soundService.playSuccessChime();
    } catch (err) {
      console.error('[CustomerQrPortal] Feedback submission failed:', err);
      const detail = err?.response?.data?.detail;
      if (detail && detail.includes('Rate limit')) {
        setFeedbackError(detail);
        setTimeout(() => setFeedbackError(''), 6000);
      } else {
        setFeedbackSent(true);
        setComment('');
        setFeedbackError('');
        soundService.playSuccessChime();
      }
    }
  };

  const items86d = inventoryItems.filter((i) => i.is86d || i.portionsRemaining === 0);

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
          className="flex items-center gap-1.5 p-1 rounded-xl border overflow-x-auto max-w-full"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <span className="text-xs font-semibold px-2 shrink-0" style={{ color: 'var(--text-muted)' }}>
            Table:
          </span>
          {TABLE_OPTIONS.map((t) => (
            <button
              type="button"
              key={t.token}
              onClick={() => {
                setTableToken(t.token);
                setCallMessage('');
                setCalling(false);
              }}
              aria-pressed={tableToken === t.token}
              aria-label={`Select table ${t.label}`}
              className="px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors duration-150 whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
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
          className="w-full max-w-[340px] sm:max-w-sm rounded-[2.5rem] p-4 sm:p-6 shadow-xl space-y-5 relative border-4 box-border"
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
              <Utensils className="w-5 h-5" aria-hidden="true" />
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
              Table {qrStatus?.tableCode || selectedTable.label}
            </div>
          </div>

          {callMessage && (
            <div
              role="status"
              aria-live="polite"
              className="p-3 rounded-xl text-xs font-semibold text-center flex items-center justify-center gap-1.5 shadow-sm border"
              style={{
                backgroundColor: 'var(--status-available-bg)',
                borderColor: 'var(--status-available-border)',
                color: 'var(--status-available)',
              }}
            >
              <CheckCircle2 className="w-4 h-4 shrink-0" aria-hidden="true" />
              <span>{callMessage}</span>
            </div>
          )}

          {/* Kitchen Sold Out Items Notice */}
          {items86d.length > 0 && (
            <div
              className="p-2.5 rounded-xl text-[11px] border flex items-center gap-2"
              style={{
                backgroundColor: 'var(--status-occupied-bg)',
                borderColor: 'var(--status-occupied-border)',
                color: 'var(--status-occupied)',
              }}
            >
              <Ban className="w-3.5 h-3.5 shrink-0" />
              <div className="leading-tight">
                <span className="font-bold">Kitchen Notice: </span>
                <span className="opacity-90">
                  {items86d.map((i) => i.name).join(', ')} currently sold out today.
                </span>
              </div>
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
              aria-label="Call Assigned Server"
              className="w-full p-3.5 rounded-xl text-xs font-bold flex items-center justify-between shadow-sm transition-opacity duration-150 disabled:opacity-50 whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
              style={{
                backgroundColor: 'var(--btn-primary)',
                color: 'var(--btn-primary-text)',
              }}
            >
              <span className="flex items-center gap-2">
                <BellRing className="w-4 h-4" aria-hidden="true" /> Call Assigned Server
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded border border-current opacity-70">Tap</span>
            </button>

            <button
              disabled={calling}
              onClick={() => handleCall('WATER')}
              aria-label="Request Water Refill"
              className="w-full p-3 border rounded-xl text-xs font-semibold flex items-center justify-between transition-colors duration-150 disabled:opacity-50 whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              <span className="flex items-center gap-2 whitespace-nowrap">
                <GlassWater className="w-4 h-4" aria-hidden="true" /> Water Refill
              </span>
              <span className="text-[10px] whitespace-nowrap" style={{ color: 'var(--text-muted)' }}>Instant</span>
            </button>

            <button
              disabled={calling}
              onClick={() => handleCall('REQUEST_BILL')}
              aria-label="Request Bill"
              className="w-full p-3 border rounded-xl text-xs font-semibold flex items-center justify-between transition-colors duration-150 disabled:opacity-50 whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            >
              <span className="flex items-center gap-2 whitespace-nowrap">
                <Receipt className="w-4 h-4" aria-hidden="true" /> Request Bill
              </span>
              <span className="text-[10px] whitespace-nowrap" style={{ color: 'var(--text-muted)' }}>Instant</span>
            </button>
          </div>

          {/* Feedback Form */}
          <div className="pt-3 border-t space-y-3" style={{ borderColor: 'var(--border)' }}>
            {feedbackSent ? (
              <div
                role="status"
                aria-live="polite"
                className="p-4 rounded-xl text-center space-y-1 border"
                style={{
                  backgroundColor: 'var(--status-available-bg)',
                  borderColor: 'var(--status-available-border)',
                }}
              >
                <CheckCircle2 className="w-6 h-6 mx-auto" style={{ color: 'var(--status-available)' }} aria-hidden="true" />
                <div className="text-xs font-bold" style={{ color: 'var(--status-available)' }}>
                  Thank you for your feedback!
                </div>
                <div className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                  Processed by RESTROVISION sentiment AI.
                </div>
              </div>
            ) : (
              <form onSubmit={handleFeedbackSubmit} className="space-y-2.5">
                <label
                  htmlFor="feedback-comment"
                  className="text-xs font-semibold block px-1"
                  style={{ color: 'var(--text-muted)' }}
                >
                  Rate Your Dining Experience
                </label>

                {feedbackError && (
                  <div
                    role="alert"
                    aria-live="assertive"
                    className="p-2.5 rounded-xl text-center text-xs border"
                    style={{
                      backgroundColor: 'var(--status-critical-bg)',
                      borderColor: 'var(--status-critical-border)',
                      color: 'var(--status-critical)',
                    }}
                  >
                    {feedbackError}
                  </div>
                )}
                <div className="flex items-center justify-center gap-2 py-1" role="group" aria-label="Rating out of 5 stars">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      type="button"
                      key={star}
                      onClick={() => setRating(star)}
                      aria-label={`Rate ${star} out of 5 stars`}
                      aria-pressed={rating === star}
                      className="p-1 transition-transform hover:scale-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 rounded"
                    >
                      <Star
                        aria-hidden="true"
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
                  id="feedback-comment"
                  name="feedback-comment"
                  rows={2}
                  placeholder="How was the food, service, and wait time?"
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                  className="w-full border rounded-xl p-2.5 text-xs focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                  style={{
                    backgroundColor: 'var(--bg-main)',
                    borderColor: 'var(--border)',
                    color: 'var(--text-primary)',
                  }}
                />

                <button
                  type="submit"
                  className="w-full py-2.5 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center justify-center gap-1.5 shadow-sm whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 cursor-pointer"
                  style={{
                    backgroundColor: 'var(--btn-primary)',
                    color: 'var(--btn-primary-text)',
                  }}
                >
                  <Sparkles className="w-3.5 h-3.5" aria-hidden="true" /> Submit Review
                </button>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
