import React from 'react';
import { Clock, Sparkles } from 'lucide-react';

export default function CrowdForecastTab({ currentData }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Crowd Curve by Hour / Day */}
      <div
        className="lg:col-span-2 rounded-2xl p-5 border shadow-sm space-y-4 transition-colors duration-150"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h2 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <div className="p-1 rounded-md bg-sky-50 text-sky-600 border border-sky-100 dark:bg-sky-950/40 dark:text-sky-400 dark:border-sky-800/40">
                <Clock className="w-4 h-4" />
              </div>
              Crowd Density &amp; Peak Dining Hours
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              CCTV-measured table occupancy curve and customer footfall distribution.
            </p>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="flex items-center gap-1 text-sky-800 dark:text-sky-300 font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-sky-400"></span> Quiet (&lt;50%)
            </span>
            <span className="flex items-center gap-1 text-amber-800 dark:text-amber-300 font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-amber-400"></span> Moderate (50-79%)
            </span>
            <span className="flex items-center gap-1 text-rose-800 dark:text-rose-300 font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span> Peak Rush (&ge;80%)
            </span>
          </div>
        </div>

        {/* Visual Occupancy Bar Chart */}
        <div className="pt-2">
          <div className="grid grid-cols-4 sm:grid-cols-6 lg:grid-cols-12 gap-2 items-end h-44 border-b pb-2" style={{ borderColor: 'var(--border)' }}>
            {currentData.hourlyTraffic.map((bar, idx) => {
              const isPeak = bar.status === 'peak' || bar.occupancy >= 80;
              const isMod = bar.status === 'moderate' || bar.occupancy >= 50;
              const barClass = isPeak
                ? 'bg-rose-400 dark:bg-rose-500 group-hover:bg-rose-500'
                : isMod
                ? 'bg-amber-300 dark:bg-amber-400 group-hover:bg-amber-400'
                : 'bg-sky-200 dark:bg-sky-700 group-hover:bg-sky-300';
              return (
                <div key={idx} className="flex flex-col items-center h-full justify-end group">
                  <span className="text-[10px] tabular-nums font-semibold mb-1" style={{ color: 'var(--text-muted)' }}>
                    {bar.occupancy}%
                  </span>
                  <div
                    className={`w-full rounded-t-lg transition-opacity duration-300 ${barClass}`}
                    style={{
                      height: `${Math.max(12, (bar.occupancy / 100) * 120)}px`,
                    }}
                    title={`${bar.hour}: ${bar.occupancy}% occupancy (${bar.guests} guests)`}
                  />
                  <span className="text-[11px] font-semibold mt-2 truncate w-full text-center" style={{ color: 'var(--text-secondary)' }}>
                    {bar.hour}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Shift Comparison Highlights */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2 text-xs">
          <div className="p-3 rounded-xl border bg-amber-50/40 border-amber-200/70 dark:bg-amber-950/15 dark:border-amber-800/30">
            <div className="font-bold text-amber-900 dark:text-amber-300">
              Lunch Shift (12:30 – 3:00 PM)
            </div>
            <div className="text-xs mt-1 space-y-0.5 text-zinc-600 dark:text-zinc-400">
              <div>Peak: <strong className="text-amber-700 dark:text-amber-400 font-bold">88% occupancy</strong></div>
              <div>Avg party: 2.8 diners | Waitlist: ~8 min</div>
            </div>
          </div>

          <div className="p-3 rounded-xl border bg-sky-50/40 border-sky-200/70 dark:bg-sky-950/15 dark:border-sky-800/30">
            <div className="font-bold text-sky-900 dark:text-sky-300">
              Afternoon Lull (3:30 – 6:30 PM)
            </div>
            <div className="text-xs mt-1 space-y-0.5 text-zinc-600 dark:text-zinc-400">
              <div>Avg: <strong className="text-sky-700 dark:text-sky-400 font-bold">24% occupancy</strong></div>
              <div>Prep &amp; shift handover window</div>
            </div>
          </div>

          <div className="p-3 rounded-xl border bg-rose-50/40 border-rose-200/70 dark:bg-rose-950/15 dark:border-rose-800/30">
            <div className="font-bold text-rose-900 dark:text-rose-300">
              Dinner Peak (7:30 – 10:30 PM)
            </div>
            <div className="text-xs mt-1 space-y-0.5 text-zinc-600 dark:text-zinc-400">
              <div>Peak: <strong className="text-rose-700 dark:text-rose-400 font-bold">96% occupancy</strong></div>
              <div>Avg party: 4.2 diners | Waitlist: ~18 min</div>
            </div>
          </div>
        </div>
      </div>

      {/* AI Traffic Prediction & Staffing Advice (M8) */}
      <div
        className="rounded-2xl p-5 border shadow-sm space-y-4 flex flex-col justify-between transition-colors duration-150"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <div className="p-1 rounded-md bg-violet-50 text-violet-600 border border-violet-100 dark:bg-violet-950/40 dark:text-violet-400 dark:border-violet-800/40">
              <Sparkles className="w-4 h-4" />
            </div>
            <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              AI Footfall Forecast (M8)
            </h2>
          </div>
          <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
            LightGBM quantile regression forecaster estimating incoming patron density.
          </p>

          <div className="p-3.5 rounded-xl border bg-violet-50/40 border-violet-200/70 dark:bg-violet-950/20 dark:border-violet-800/40 space-y-2">
            <div className="text-xs font-semibold text-violet-900 dark:text-violet-300">
              Target Shift: <span className="font-bold">{currentData.aiForecast.nextShift}</span>
            </div>
            <div className="flex items-baseline justify-between">
              <span className="text-2xl font-black tabular-nums text-violet-950 dark:text-violet-100">
                {currentData.aiForecast.predictedGuests} diners
              </span>
              <span className="text-[11px] font-semibold text-violet-700 dark:text-violet-300 bg-violet-100/80 dark:bg-violet-900/50 px-2 py-0.5 rounded">Expected (P50)</span>
            </div>

            {/* Quantile range */}
            <div className="flex justify-between text-[11px] pt-1 border-t border-violet-200/50 dark:border-violet-800/40 text-violet-700/80 dark:text-violet-300">
              <span>P10 (Min): {currentData.aiForecast.p10}</span>
              <span>P50 (Median): {currentData.aiForecast.p50}</span>
              <span>P90 (Rush): {currentData.aiForecast.p90}</span>
            </div>
          </div>

          <div className="p-3 rounded-xl border bg-emerald-50/40 border-emerald-200/70 dark:bg-emerald-950/20 dark:border-emerald-800/40 text-xs space-y-1">
            <div className="font-bold text-emerald-950 dark:text-emerald-300 flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
              Staffing Optimization Guidance:
            </div>
            <p className="text-xs leading-relaxed text-emerald-900/90 dark:text-emerald-300">
              {currentData.aiForecast.recommendation}
            </p>
          </div>
        </div>

        <div className="pt-2 text-[11px]" style={{ color: 'var(--text-muted)' }}>
          Model: <span className="font-semibold">traffic-lgbm-1.0.0</span> &bull; 95% Confidence Interval
        </div>
      </div>
    </div>
  );
}
