import { useState, useEffect } from 'react';
import { api } from '../../services/api';

export const TIMEFRAME_DATA = {
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

export const TOP_DISHES = [
  { name: 'Paneer Tikka Shashlik', category: 'Tandoor & Starters', unitsSold: 142, revenue: '₹53,960', cost: '₹17,040', margin: 68.4, badge: 'Star Performer' },
  { name: 'Dal Makhani Slow-Cooked', category: 'Main Curries', unitsSold: 188, revenue: '₹63,920', cost: '₹15,980', margin: 75.0, badge: 'Star Performer' },
  { name: 'Butter Garlic Naan', category: 'Breads & Sides', unitsSold: 412, revenue: '₹39,140', cost: '₹9,064', margin: 76.8, badge: 'Volume Driver' },
  { name: 'Tandoori Malai Chaap', category: 'Tandoor & Starters', unitsSold: 98, revenue: '₹35,280', cost: '₹10,780', margin: 69.4, badge: 'High Margin' },
  { name: 'Gulab Jamun with Rabri', category: 'Dessert & Cold', unitsSold: 114, revenue: '₹20,520', cost: '₹5,130', margin: 75.0, badge: 'Dessert Upsell' },
];

export const REVIEWS_DATA = [
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

export function useAnalyticsData() {
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

  return {
    timeframe,
    setTimeframe,
    reviewFilter,
    setReviewFilter,
    currentData,
    filteredReviews,
    loading,
  };
}
