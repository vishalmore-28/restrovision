import React from 'react';
import {
  DollarSign,
  TrendingUp,
  Percent,
  Users,
  Star,
  Shield,
} from 'lucide-react';

export default function DailyOverviewTab({ currentData }) {
  return (
    <>
      {/* KPI Ribbon */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Gross Revenue */}
        <div
          className="p-4 rounded-2xl border shadow-sm space-y-1 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
            <span>Gross Revenue</span>
            <div className="p-1.5 rounded-lg bg-emerald-50 text-emerald-600 border border-emerald-100 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800/40">
              <DollarSign className="w-3.5 h-3.5" />
            </div>
          </div>
          <div className="text-2xl font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
            {currentData.grossRevenue}
          </div>
          <div className="text-xs flex items-center gap-1 font-semibold text-emerald-600 dark:text-emerald-400">
            <TrendingUp className="w-3.5 h-3.5" /> {currentData.revenueGrowth}
          </div>
        </div>

        {/* Net Profit & Margin */}
        <div
          className="p-4 rounded-2xl border shadow-sm space-y-1 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
            <span>Net Operating Profit</span>
            <div className="p-1.5 rounded-lg bg-teal-50 text-teal-600 border border-teal-100 dark:bg-teal-950/40 dark:text-teal-400 dark:border-teal-800/40">
              <Percent className="w-3.5 h-3.5" />
            </div>
          </div>
          <div className="text-2xl font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
            {currentData.netProfit}
          </div>
          <div className="text-xs" style={{ color: 'var(--text-muted)' }}>
            <span className="font-bold text-teal-600 dark:text-teal-400">{currentData.netProfitMargin}%</span> Net Margin after COGS &amp; Labor
          </div>
        </div>

        {/* Floor Occupancy & Turnover */}
        <div
          className="p-4 rounded-2xl border shadow-sm space-y-1 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
            <span>Occupancy &amp; Turns</span>
            <div className="p-1.5 rounded-lg bg-amber-50 text-amber-600 border border-amber-100 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/40">
              <Users className="w-3.5 h-3.5" />
            </div>
          </div>
          <div className="text-2xl font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
            {currentData.floorOccupancy}%
          </div>
          <div className="text-xs" style={{ color: 'var(--text-muted)' }}>
            {currentData.tableTurnover} &bull; {currentData.totalGuests} diners served
          </div>
        </div>

        {/* Guest Rating & Speed */}
        <div
          className="p-4 rounded-2xl border shadow-sm space-y-1 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
            <span>Guest Rating (M10)</span>
            <div className="p-1.5 rounded-lg bg-amber-50 text-amber-500 border border-amber-100 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/40">
              <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
            </div>
          </div>
          <div className="text-2xl font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
            {currentData.averageRating} / 5.0
          </div>
          <div className="text-xs" style={{ color: 'var(--text-muted)' }}>
            Across {currentData.reviewsCount} verified reviews &bull; {currentData.medianWaitSeconds}s avg wait
          </div>
        </div>
      </div>

      {/* Financial Health & P&L Cost Breakdown */}
      <div
        className="rounded-2xl p-5 border shadow-sm space-y-4 transition-colors duration-150"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h2 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <div className="p-1 rounded-md bg-emerald-50 text-emerald-600 border border-emerald-100 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800/40">
                <Shield className="w-4 h-4" />
              </div>
              Profit &amp; Loss Breakdown ({currentData.label})
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              Real-time cost attribution: Food &amp; Beverage COGS, staff payroll, operational overheads, and net margins.
            </p>
          </div>
          <div className="flex items-center gap-2 text-xs font-semibold" style={{ color: 'var(--text-muted)' }}>
            <span>RevPASH: <strong className="tabular-nums" style={{ color: 'var(--text-primary)' }}>{currentData.revpash}</strong></span>
            <span>&bull;</span>
            <span>AOV: <strong style={{ color: 'var(--text-primary)' }} className="tabular-nums">{currentData.aov}</strong></span>
          </div>
        </div>

        {/* Visual Stacked Cost & Margin Bar */}
        <div className="space-y-2">
          <div className="w-full h-3 rounded-full overflow-hidden flex" style={{ backgroundColor: 'var(--border)' }}>
            <div
              className="h-full bg-amber-400 dark:bg-amber-500 transition-opacity duration-300"
              style={{ width: `${currentData.cogsPercentage}%` }}
              title={`Food Cost: ${currentData.cogsPercentage}%`}
            />
            <div
              className="h-full bg-sky-400 dark:bg-sky-500 transition-opacity duration-300"
              style={{ width: `${currentData.laborPercentage}%` }}
              title={`Labor Cost: ${currentData.laborPercentage}%`}
            />
            <div
              className="h-full bg-indigo-300 dark:bg-indigo-400 transition-opacity duration-300"
              style={{ width: `${currentData.overheadPercentage}%` }}
              title={`Overheads: ${currentData.overheadPercentage}%`}
            />
            <div
              className="h-full bg-emerald-500 dark:bg-emerald-400 transition-opacity duration-300"
              style={{ width: `${currentData.netProfitMargin}%` }}
              title={`Net Profit: ${currentData.netProfitMargin}%`}
            />
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 text-xs">
            <div className="p-3.5 rounded-xl border bg-amber-50/50 border-amber-200/70 dark:bg-amber-950/20 dark:border-amber-800/40">
              <div className="flex items-center gap-1.5 font-semibold text-amber-900 dark:text-amber-300">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-400"></span> Food &amp; COGS
              </div>
              <div className="text-base font-bold mt-1 tabular-nums" style={{ color: 'var(--text-primary)' }}>
                {currentData.cogsAmount}
              </div>
              <div className="text-[11px] text-amber-700/90 dark:text-amber-400 font-medium">
                {currentData.cogsPercentage}% of gross (Target &le; 30%)
              </div>
            </div>

            <div className="p-3.5 rounded-xl border bg-sky-50/50 border-sky-200/70 dark:bg-sky-950/20 dark:border-sky-800/40">
              <div className="flex items-center gap-1.5 font-semibold text-sky-900 dark:text-sky-300">
                <span className="w-2.5 h-2.5 rounded-full bg-sky-400"></span> Labor &amp; Payroll
              </div>
              <div className="text-base font-bold mt-1 tabular-nums" style={{ color: 'var(--text-primary)' }}>
                {currentData.laborAmount}
              </div>
              <div className="text-[11px] text-sky-700/90 dark:text-sky-400 font-medium">
                {currentData.laborPercentage}% of gross (Optimal ratio)
              </div>
            </div>

            <div className="p-3.5 rounded-xl border bg-indigo-50/50 border-indigo-200/70 dark:bg-indigo-950/20 dark:border-indigo-800/40">
              <div className="flex items-center gap-1.5 font-semibold text-indigo-900 dark:text-indigo-300">
                <span className="w-2.5 h-2.5 rounded-full bg-indigo-400"></span> Overheads &amp; IoT
              </div>
              <div className="text-base font-bold mt-1 tabular-nums" style={{ color: 'var(--text-primary)' }}>
                {currentData.overheadAmount}
              </div>
              <div className="text-[11px] text-indigo-700/90 dark:text-indigo-400 font-medium">
                {currentData.overheadPercentage}% rent &amp; utilities
              </div>
            </div>

            <div className="p-3.5 rounded-xl border bg-emerald-50/70 border-emerald-300/80 dark:bg-emerald-950/30 dark:border-emerald-700/50">
              <div className="flex items-center gap-1.5 font-bold text-emerald-950 dark:text-emerald-300">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span> Net Profit
              </div>
              <div className="text-base font-black mt-1 tabular-nums text-emerald-700 dark:text-emerald-400">
                {currentData.netProfit}
              </div>
              <div className="text-[11px] font-semibold text-emerald-700 dark:text-emerald-300">
                {currentData.netProfitMargin}% Net Margin
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
