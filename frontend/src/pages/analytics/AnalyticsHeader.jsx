import React from 'react';

export default function AnalyticsHeader({ currentData, timeframe, setTimeframe }) {
  return (
    <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b pb-5" style={{ borderColor: 'var(--border)' }}>
      <div>
        <div className="flex items-center gap-2.5">
          <h1 className="text-xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            Executive Analytics &amp; Operations Intelligence
          </h1>
          <span
            className="text-[11px] font-semibold px-2 py-0.5 rounded border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
          >
            Edge AI Telemetry
          </span>
        </div>
        <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
          {currentData.subtitle} &bull; <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>{currentData.dateRange}</span>
        </p>
      </div>

      {/* Timeframe Selector Pills */}
      <div
        className="flex items-center gap-1.5 p-1 rounded-xl border self-start md:self-auto"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        <button
          type="button"
          onClick={() => setTimeframe('daily')}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors duration-150 cursor-pointer"
          style={{
            backgroundColor: timeframe === 'daily' ? 'var(--btn-primary)' : 'transparent',
            color: timeframe === 'daily' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
          }}
        >
          Daily (Today)
        </button>
        <button
          type="button"
          onClick={() => setTimeframe('weekly')}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors duration-150 cursor-pointer"
          style={{
            backgroundColor: timeframe === 'weekly' ? 'var(--btn-primary)' : 'transparent',
            color: timeframe === 'weekly' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
          }}
        >
          Weekly (7 Days)
        </button>
        <button
          type="button"
          onClick={() => setTimeframe('monthly')}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors duration-150 cursor-pointer"
          style={{
            backgroundColor: timeframe === 'monthly' ? 'var(--btn-primary)' : 'transparent',
            color: timeframe === 'monthly' ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
          }}
        >
          Monthly (30 Days)
        </button>
      </div>
    </div>
  );
}
