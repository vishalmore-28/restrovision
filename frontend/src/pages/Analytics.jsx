import React from 'react';
import { useAnalyticsData } from './analytics/useAnalyticsData';
import AnalyticsHeader from './analytics/AnalyticsHeader';
import DailyOverviewTab from './analytics/DailyOverviewTab';
import CrowdForecastTab from './analytics/CrowdForecastTab';
import MenuEngineeringTab from './analytics/MenuEngineeringTab';
import GuestReviewsTab from './analytics/GuestReviewsTab';

export default function Analytics() {
  const {
    timeframe,
    setTimeframe,
    reviewFilter,
    setReviewFilter,
    currentData,
    filteredReviews,
  } = useAnalyticsData();

  return (
    <div className="space-y-6">
      {/* Header & Timeframe Switcher */}
      <AnalyticsHeader
        currentData={currentData}
        timeframe={timeframe}
        setTimeframe={setTimeframe}
      />

      {/* KPI Ribbon & Financial P&L Cost Breakdown */}
      <DailyOverviewTab currentData={currentData} />

      {/* Crowd & Peak Timings Analytics Panel */}
      <CrowdForecastTab currentData={currentData} />

      {/* Menu Engineering & Dish Profitability */}
      <MenuEngineeringTab currentData={currentData} />

      {/* Customer Reviews & NLP Sentiment Intelligence (M10) */}
      <GuestReviewsTab
        currentData={currentData}
        reviewFilter={reviewFilter}
        setReviewFilter={setReviewFilter}
        filteredReviews={filteredReviews}
      />
    </div>
  );
}
