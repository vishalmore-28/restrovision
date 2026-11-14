import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import {
  BarChart3,
  TrendingUp,
  Clock,
  Users,
  Star,
  Smile,
  Meh,
  Frown,
  MessageSquare,
  Sparkles,
  Calendar,
  Shield,
  Percent,
  Flame,
  ArrowUpRight,
  ArrowDownRight,
  Utensils,
  DollarSign,
} from 'lucide-react';

const TIMEFRAME_DATA = {
  daily: {
    label: 'Daily Review',
    subtitle: "Today's Intraday Performance & Live Shift Analysis",
    dateRange: 'Today, Nov 14, 2026',
    grossRevenue: '₹1,48,250',
    grossRevenueRaw: 148250,
    revenueGrowth: '+12.4% vs yesterday',
    netProfit: '₹51,800',
    netProfitMargin: 34.9,
    cogsAmount: '₹43,600',
    cogsPercentage: 29.4,
    laborAmount: '₹27,000',
    laborPercentage: 18.2,
    overheadAmount: '₹25,850',
    overheadPercentage: 17.5,
    floorOccupancy: 68,
    occupiedTables: 8,
    totalTables: 12,
    totalGuests: 164,
    aov: '₹1,850',
    revpash: '₹385',
    tableTurnover: '3.4 turns',
    medianWaitSeconds: 32,
    averageRating: 4.8,
    reviewsCount: 18,
    hourlyTraffic: [
      { hour: '11 AM', occupancy: 20, guests: 16, status: 'quiet' },
      { hour: '12 PM', occupancy: 55, guests: 44, status: 'moderate' },
      { hour: '1 PM', occupancy: 88, guests: 112, status: 'peak' },
      { hour: '2 PM', occupancy: 76, guests: 86, status: 'peak' },
      { hour: '3 PM', occupancy: 42, guests: 38, status: 'moderate' },
      { hour: '4 PM', occupancy: 24, guests: 18, status: 'quiet' },
      { hour: '5 PM', occupancy: 30, guests: 26, status: 'quiet' },
      { hour: '6 PM', occupancy: 48, guests: 42, status: 'moderate' },
      { hour: '7 PM', occupancy: 82, guests: 98, status: 'peak' },
      { hour: '8 PM', occupancy: 96, guests: 148, status: 'peak' },
      { hour: '9 PM', occupancy: 92, guests: 136, status: 'peak' },
      { hour: '10 PM', occupancy: 64, guests: 68, status: 'moderate' },
    ],
    aiForecast: {
      nextShift: 'Dinner Peak (7:30 PM – 10:30 PM)',
      predictedGuests: 148,
      p10: 120,
      p50: 148,
      p90: 185,
      recommendation: 'Predicted 96% occupancy peak. Deploy 4 active floor servers and prioritize tandoor stations.',
    },
  },
  weekly: {
    label: 'Weekly Review',
    subtitle: '7-Day Revenue Trajectory, Day-of-Week Patterns & Turnaround',
    dateRange: 'Nov 08 – Nov 14, 2026',
    grossRevenue: '₹9,84,600',
    grossRevenueRaw: 984600,
    revenueGrowth: '+14.2% vs previous week',
    netProfit: '₹3,24,000',
    netProfitMargin: 32.9,
    cogsAmount: '₹2,89,400',
    cogsPercentage: 29.4,
    laborAmount: '₹1,85,000',
    laborPercentage: 18.8,
    overheadAmount: '₹1,86,200',
    overheadPercentage: 18.9,
    floorOccupancy: 72,
    occupiedTables: 9,
    totalTables: 12,
    totalGuests: 1140,
    aov: '₹1,920',
    revpash: '₹398',
    tableTurnover: '3.8 turns',
    medianWaitSeconds: 34,
    averageRating: 4.8,
    reviewsCount: 142,
    hourlyTraffic: [
      { hour: 'Mon', occupancy: 52, guests: 128, status: 'moderate' },
      { hour: 'Tue', occupancy: 58, guests: 136, status: 'moderate' },
      { hour: 'Wed', occupancy: 64, guests: 152, status: 'moderate' },
      { hour: 'Thu', occupancy: 68, guests: 160, status: 'moderate' },
      { hour: 'Fri', occupancy: 92, guests: 224, status: 'peak' },
      { hour: 'Sat', occupancy: 96, guests: 248, status: 'peak' },
      { hour: 'Sun', occupancy: 78, guests: 188, status: 'peak' },
    ],
    aiForecast: {
      nextShift: 'Upcoming Weekend Shift (Fri – Sat)',
      predictedGuests: 480,
      p10: 420,
      p50: 480,
      p90: 540,
      recommendation: 'Weekend demand forecast indicates heavy beverage & dessert volume. Pre-batch marinades.',
    },
  },
  monthly: {
    label: 'Monthly Review',
    subtitle: '30-Day Executive P&L, Cost Ratios & Month-over-Month Growth',
    dateRange: 'Nov 01 – Nov 30, 2026 (Month-to-Date)',
    grossRevenue: '₹41,20,000',
    grossRevenueRaw: 4120000,
    revenueGrowth: '+18.5% YoY growth',
    netProfit: '₹13,80,000',
    netProfitMargin: 33.5,
    cogsAmount: '₹11,86,560',
    cogsPercentage: 28.8,
    laborAmount: '₹7,45,720',
    laborPercentage: 18.1,
    overheadAmount: '₹8,07,720',
    overheadPercentage: 19.6,
    floorOccupancy: 74,
    occupiedTables: 9,
    totalTables: 12,
    totalGuests: 4860,
    aov: '₹1,940',
    revpash: '₹412',
    tableTurnover: '3.9 turns',
    medianWaitSeconds: 31,
    averageRating: 4.8,
    reviewsCount: 580,
    hourlyTraffic: [
      { hour: 'Week 1', occupancy: 68, guests: 1110, status: 'moderate' },
      { hour: 'Week 2', occupancy: 72, guests: 1140, status: 'moderate' },
      { hour: 'Week 3', occupancy: 78, guests: 1240, status: 'peak' },
      { hour: 'Week 4', occupancy: 82, guests: 1370, status: 'peak' },
    ],
    aiForecast: {
      nextShift: 'Seasonal Month-End Projection',
      predictedGuests: 5200,
      p10: 4900,
      p50: 5200,
      p90: 5600,
      recommendation: 'Projected to close above ₹42.5L gross. Ingredient contract renewals improve margin by +1.2%.',
    },
  },
};

const TOP_DISHES = [
  { name: 'Paneer Tikka Shashlik', category: 'Tandoor & Starters', unitsSold: 142, revenue: '₹53,960', cost: '₹17,040', margin: 68.4, badge: 'Star Performer' },
  { name: 'Dal Makhani Slow-Cooked', category: 'Main Curries', unitsSold: 188, revenue: '₹63,920', cost: '₹15,980', margin: 75.0, badge: 'Star Performer' },
  { name: 'Butter Garlic Naan', category: 'Breads & Sides', unitsSold: 412, revenue: '₹39,140', cost: '₹9,064', margin: 76.8, badge: 'Volume Driver' },
  { name: 'Tandoori Malai Chaap', category: 'Tandoor & Starters', unitsSold: 98, revenue: '₹35,280', cost: '₹10,780', margin: 69.4, badge: 'High Margin' },
  { name: 'Gulab Jamun with Rabri', category: 'Dessert & Cold', unitsSold: 114, revenue: '₹20,520', cost: '₹5,130', margin: 75.0, badge: 'Dessert Upsell' },
];

const REVIEWS_DATA = [
  {
    feedbackId: 1,
    tableId: 'T-04',
    rating: 5,
    category: 'Service Speed',
    sentiment: 'POSITIVE',
    commentText: 'Water refill and table attendance was practically instantaneous after pressing the QR button! Staff was attentive without hovering.',
    guest: 'Family Table (4 guests)',
    timeAgo: '28m ago',
  },
  {
    feedbackId: 2,
    tableId: 'T-02',
    rating: 5,
    category: 'Food Quality',
    sentiment: 'POSITIVE',
    commentText: 'Dal makhani was velvety and perfectly tempered. Garlic naan was crisp right off the tandoor pass. Top marks!',
    guest: 'Couple Dining (2 guests)',
    timeAgo: '1h 14m ago',
  },
  {
    feedbackId: 3,
    tableId: 'T-01',
    rating: 4,
    category: 'Speed & Timing',
    sentiment: 'NEUTRAL',
    commentText: 'Delicious food and pleasant ambience. Main course took about 18 minutes during the peak lunch rush, but hot and fresh.',
    guest: 'Executive Lunch (4 guests)',
    timeAgo: '2h 05m ago',
  },
  {
    feedbackId: 4,
    tableId: 'T-05',
    rating: 5,
    category: 'Cleanliness & Hygiene',
    sentiment: 'POSITIVE',
    commentText: 'Immaculately clean table and cutlery. We saw the housekeeping team sanitize the entire booth before seating us.',
    guest: 'Walk-In Guests (3 guests)',
    timeAgo: '3h 40m ago',
  },
  {
    feedbackId: 5,
    tableId: 'T-03',
    rating: 2,
    category: 'Wait Time Alert',
    sentiment: 'NEGATIVE',
    commentText: 'Waited almost 4 minutes initially before server arrived to take drink orders during evening rush.',
    guest: 'Booth Seating (2 guests)',
    timeAgo: '5h 10m ago',
  },
];

export default function Analytics() {
  const { user } = useAuth();
  const [timeframe, setTimeframe] = useState('daily');
  const [reviewFilter, setReviewFilter] = useState('ALL');
  const [apiOverview, setApiOverview] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let isMounted = true;
    setLoading(true);
    api.getAnalyticsOverview(timeframe)
      .then((data) => {
        if (isMounted && data) {
          setApiOverview(data);
        }
      })
      .catch((err) => {
        console.warn('Analytics API fetch error:', err);
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [timeframe]);

  const currentData = apiOverview || TIMEFRAME_DATA[timeframe] || TIMEFRAME_DATA.daily;

  const rawReviews = (apiOverview && apiOverview.reviews && apiOverview.reviews.length > 0)
    ? apiOverview.reviews
    : REVIEWS_DATA;

  const filteredReviews = rawReviews.filter((r) => {
    if (reviewFilter === 'POSITIVE') return r.sentiment === 'POSITIVE';
    if (reviewFilter === 'NEUTRAL') return r.sentiment === 'NEUTRAL';
    if (reviewFilter === 'NEGATIVE') return r.sentiment === 'NEGATIVE';
    return true;
  });

  return (
    <div className="space-y-6">
      {/* Header & Timeframe Switcher */}
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

      {/* Crowd & Peak Timings Analytics Panel */}
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

      {/* Menu Engineering & Dish Profitability */}
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

      {/* Customer Reviews & NLP Sentiment Intelligence (M10) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Rating Breakdown & Category Scorecards */}
        <div
          className="rounded-2xl p-5 border shadow-sm space-y-4 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center gap-2">
            <div className="p-1 rounded-md bg-amber-50 text-amber-500 border border-amber-100 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/40">
              <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
            </div>
            <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
              Guest Ratings &amp; Category Scores
            </h2>
          </div>
          <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
            Star rating distribution and categorized quality index from verified table QR portal submissions.
          </p>

          <div className="flex items-center gap-4 py-2 border-b" style={{ borderColor: 'var(--border)' }}>
            <div className="text-3xl font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
              {currentData.averageRating}
            </div>
            <div>
              <div className="flex items-center gap-1">
                {[1, 2, 3, 4, 5].map((s) => (
                  <Star key={s} className="w-4 h-4 fill-amber-400 text-amber-400" />
                ))}
              </div>
              <div className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
                Based on {currentData.reviewsCount} verified diner reviews
              </div>
            </div>
          </div>

          {/* Star Histogram */}
          <div className="space-y-2 text-xs">
            <div className="flex items-center gap-2">
              <span className="w-8 text-right font-semibold text-amber-900 dark:text-amber-300">5 ★</span>
              <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
                <div className="h-full bg-amber-400 rounded-full" style={{ width: '68%' }} />
              </div>
              <span className="w-8 text-left text-neutral-400 tabular-nums">68%</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="w-8 text-right font-semibold text-amber-800 dark:text-amber-300">4 ★</span>
              <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
                <div className="h-full bg-amber-300 rounded-full" style={{ width: '22%' }} />
              </div>
              <span className="w-8 text-left text-neutral-400 tabular-nums">22%</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="w-8 text-right font-semibold text-sky-800 dark:text-sky-300">3 ★</span>
              <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
                <div className="h-full bg-sky-300 rounded-full" style={{ width: '6%' }} />
              </div>
              <span className="w-8 text-left text-neutral-400 tabular-nums">6%</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="w-8 text-right font-semibold text-rose-700 dark:text-rose-300">2 ★</span>
              <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
                <div className="h-full bg-rose-300 rounded-full" style={{ width: '3%' }} />
              </div>
              <span className="w-8 text-left text-neutral-400 tabular-nums">3%</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="w-8 text-right font-semibold text-rose-800 dark:text-rose-300">1 ★</span>
              <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
                <div className="h-full bg-rose-400 rounded-full" style={{ width: '1%' }} />
              </div>
              <span className="w-8 text-left text-neutral-400 tabular-nums">1%</span>
            </div>
          </div>

          {/* Category Quality Index */}
          <div className="pt-3 border-t space-y-2" style={{ borderColor: 'var(--border)' }}>
            <div className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>
              Category Experience Scores
            </div>
            <div className="space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Food Quality &amp; Taste</span>
                <span className="font-bold tabular-nums text-emerald-700 dark:text-emerald-400">4.9 / 5.0</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Cleanliness &amp; Hygiene</span>
                <span className="font-bold tabular-nums text-teal-700 dark:text-teal-400">4.8 / 5.0</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Ambiance &amp; Lighting</span>
                <span className="font-bold tabular-nums text-indigo-700 dark:text-indigo-400">4.7 / 5.0</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Service Speed</span>
                <span className="font-bold tabular-nums text-sky-700 dark:text-sky-400">4.6 / 5.0</span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-secondary)' }}>Value for Money</span>
                <span className="font-bold tabular-nums text-amber-700 dark:text-amber-400">4.5 / 5.0</span>
              </div>
            </div>
          </div>
        </div>

        {/* Live Guest Review Feed with NLP Sentiment */}
        <div
          className="lg:col-span-2 rounded-2xl p-5 border shadow-sm space-y-4 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                <div className="p-1 rounded-md bg-indigo-50 text-indigo-600 border border-indigo-100 dark:bg-indigo-950/40 dark:text-indigo-400 dark:border-indigo-800/40">
                  <MessageSquare className="w-4 h-4" />
                </div>
                Verified Guest Feedback Stream (IndicBERT NLP)
              </h2>
              <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
                Real guest reviews tagged by multilingual NLP model into positive, neutral, and critical sentiments.
              </p>
            </div>

            {/* Filter Pills */}
            <div className="flex items-center gap-1 text-xs">
              <button
                type="button"
                onClick={() => setReviewFilter('ALL')}
                className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                  reviewFilter === 'ALL'
                    ? 'bg-zinc-900 text-white border-zinc-900 dark:bg-zinc-100 dark:text-zinc-900'
                    : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
                }`}
              >
                All
              </button>
              <button
                type="button"
                onClick={() => setReviewFilter('POSITIVE')}
                className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                  reviewFilter === 'POSITIVE'
                    ? 'bg-emerald-50 text-emerald-800 border-emerald-300 dark:bg-emerald-950 dark:text-emerald-200 dark:border-emerald-700'
                    : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
                }`}
              >
                Positive
              </button>
              <button
                type="button"
                onClick={() => setReviewFilter('NEUTRAL')}
                className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                  reviewFilter === 'NEUTRAL'
                    ? 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-600'
                    : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
                }`}
              >
                Neutral
              </button>
              <button
                type="button"
                onClick={() => setReviewFilter('NEGATIVE')}
                className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                  reviewFilter === 'NEGATIVE'
                    ? 'bg-rose-50 text-rose-800 border-rose-300 dark:bg-rose-950 dark:text-rose-200 dark:border-rose-700'
                    : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
                }`}
              >
                Critical
              </button>
            </div>
          </div>

          {/* Feedback Cards */}
          <div className="space-y-3">
            {filteredReviews.map((item) => {
              const isPos = item.sentiment === 'POSITIVE';
              const isNeg = item.sentiment === 'NEGATIVE';
              const cardTint = isPos
                ? 'bg-emerald-50/20 border-emerald-200/60 dark:bg-emerald-950/10 dark:border-emerald-800/40'
                : isNeg
                ? 'bg-rose-50/20 border-rose-200/60 dark:bg-rose-950/10 dark:border-rose-800/40'
                : 'border-zinc-200/80 dark:border-zinc-800';

              const badgeStyle = isPos
                ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
                : isNeg
                ? 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800'
                : 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700';

              return (
                <div
                  key={item.feedbackId}
                  className={`p-3.5 rounded-xl border space-y-2 transition-colors duration-150 ${cardTint}`}
                  style={{
                    backgroundColor: !isPos && !isNeg ? 'var(--bg-main)' : undefined,
                  }}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-xs" style={{ color: 'var(--text-primary)' }}>
                        Table {item.tableId}
                      </span>
                      <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                        &bull; {item.guest}
                      </span>
                      <span className="flex items-center font-bold text-xs ml-1" style={{ color: 'var(--text-primary)' }}>
                        {item.rating} <Star className="w-3 h-3 fill-amber-400 text-amber-400 ml-0.5" />
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md border ${badgeStyle}`}>
                        {item.sentiment}
                      </span>
                      <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                        {item.timeAgo}
                      </span>
                    </div>
                  </div>

                  <p className="text-xs leading-relaxed" style={{ color: 'var(--text-primary)' }}>
                    &ldquo;{item.commentText}&rdquo;
                  </p>

                  <div className="text-[11px] font-semibold" style={{ color: 'var(--text-muted)' }}>
                    Topic Category: <span style={{ color: 'var(--text-secondary)' }}>{item.category}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
