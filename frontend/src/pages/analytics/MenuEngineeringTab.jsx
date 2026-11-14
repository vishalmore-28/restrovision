import React from 'react';
import { Flame } from 'lucide-react';
import { TOP_DISHES } from './useAnalyticsData';

export default function MenuEngineeringTab({ currentData }) {
  return (
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
            <div className="p-1 rounded-md bg-amber-50 text-amber-500 border border-amber-100 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/40">
              <Flame className="w-4 h-4" />
            </div>
            Menu Engineering &amp; High-Margin Dish Matrix
          </h2>
          <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
            Ranking dishes by popularity volume, raw ingredient food cost, and contribution margin percentage.
          </p>
        </div>
        <span className="text-xs font-semibold" style={{ color: 'var(--text-muted)' }}>
          Top 5 Revenue Drivers
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs border-collapse">
          <thead>
            <tr className="border-b" style={{ borderColor: 'var(--border)', color: 'var(--text-muted)' }}>
              <th className="py-2.5 px-3 font-semibold">Dish Name</th>
              <th className="py-2.5 px-3 font-semibold">Station / Category</th>
              <th className="py-2.5 px-3 font-semibold text-right">Units Sold</th>
              <th className="py-2.5 px-3 font-semibold text-right">Gross Revenue</th>
              <th className="py-2.5 px-3 font-semibold text-right">Food Cost (COGS)</th>
              <th className="py-2.5 px-3 font-semibold text-right">Contribution Margin</th>
              <th className="py-2.5 px-3 font-semibold text-center">Engineering Class</th>
            </tr>
          </thead>
          <tbody>
            {(currentData.menuEngineering || TOP_DISHES).map((dish, idx) => {
              const name = dish.name || dish.dishName;
              const category = dish.category || dish.stationCategory;
              const revenue = dish.revenue || dish.grossRevenue;
              const cost = dish.cost || dish.foodCost;
              const margin = dish.margin || dish.marginPercentage;
              const badge = dish.badge || dish.engineeringClass;

              const badgeClass =
                badge === 'Star Performer'
                  ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
                  : badge === 'Volume Driver'
                  ? 'bg-sky-50 text-sky-700 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300 dark:border-sky-800'
                  : badge === 'High Margin'
                  ? 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800'
                  : 'bg-purple-50 text-purple-700 border-purple-200 dark:bg-purple-950/40 dark:text-purple-300 dark:border-purple-800';

              return (
                <tr
                  key={idx}
                  className="border-b transition-colors duration-150"
                  style={{ borderColor: 'var(--border)' }}
                >
                  <td className="py-3 px-3 font-bold" style={{ color: 'var(--text-primary)' }}>
                    {name}
                  </td>
                  <td className="py-3 px-3" style={{ color: 'var(--text-secondary)' }}>
                    {category}
                  </td>
                  <td className="py-3 px-3 text-right font-semibold tabular-nums" style={{ color: 'var(--text-primary)' }}>
                    {dish.unitsSold}
                  </td>
                  <td className="py-3 px-3 text-right font-bold tabular-nums" style={{ color: 'var(--text-primary)' }}>
                    {revenue}
                  </td>
                  <td className="py-3 px-3 text-right tabular-nums font-semibold" style={{ color: 'var(--text-secondary)' }}>
                    {cost}
                  </td>
                  <td className="py-3 px-3 text-right tabular-nums font-bold text-emerald-700 dark:text-emerald-400">
                    {margin}%
                  </td>
                  <td className="py-3 px-3 text-center">
                    <span className={`text-[11px] font-semibold px-2.5 py-0.5 rounded-full border inline-block ${badgeClass}`}>
                      {badge}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
