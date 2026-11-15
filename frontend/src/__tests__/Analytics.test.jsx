import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Analytics from '../pages/Analytics';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    getAnalyticsOverview: vi.fn(),
  },
}));

async function renderAnalytics() {
  const view = render(<Analytics />);
  await waitFor(() => {
    expect(api.getAnalyticsOverview).toHaveBeenCalled();
  });
  return view;
}

describe('Analytics Decomposed Dashboard (BUG-021)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getAnalyticsOverview.mockResolvedValue(null); // Triggers fallback to default TIMEFRAME_DATA
  });

  describe('Decomposed Section Rendering', () => {
    it('renders the header with Edge AI telemetry badge', async () => {
      await renderAnalytics();

      expect(screen.getByText(/executive analytics & operations intelligence/i)).toBeInTheDocument();
      expect(screen.getByText(/edge ai telemetry/i)).toBeInTheDocument();
      expect(screen.getByText(/today's intraday performance/i)).toBeInTheDocument();
    });

    it('renders the DailyOverviewTab with KPI ribbon and P&L financial metrics', async () => {
      await renderAnalytics();

      // KPI cards (Gross Revenue and Net Profit appear in KPI ribbon and P&L sections)
      const grossRevenueElements = screen.getAllByText(/gross revenue/i);
      expect(grossRevenueElements.length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText('₹1,48,250').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText(/net operating profit/i)).toBeInTheDocument();
      expect(screen.getAllByText('₹51,800').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText(/occupancy & turns/i)).toBeInTheDocument();
      expect(screen.getAllByText('68%').length).toBeGreaterThanOrEqual(1);

      // P&L Breakdown
      expect(screen.getByText(/profit & loss breakdown/i)).toBeInTheDocument();
      expect(screen.getByText(/food & cogs/i)).toBeInTheDocument();
      expect(screen.getByText(/labor & payroll/i)).toBeInTheDocument();
      expect(screen.getByText(/overheads & iot/i)).toBeInTheDocument();
    });

    it('renders CrowdForecastTab with peak hours and footfall quantile projections', async () => {
      await renderAnalytics();

      expect(screen.getByText(/crowd density & peak dining hours/i)).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /footfall forecast/i })).toBeInTheDocument();

      // Quantile numbers from default mock
      expect(screen.getByText(/p10 \(min\): 120/i)).toBeInTheDocument();
      expect(screen.getByText(/p50 \(median\): 148/i)).toBeInTheDocument();
      expect(screen.getByText(/p90 \(rush\): 185/i)).toBeInTheDocument();

      // Staffing guidance
      expect(screen.getByText(/staffing optimization guidance:/i)).toBeInTheDocument();
      expect(screen.getByText(/deploy 4 active floor servers/i)).toBeInTheDocument();
    });

    it('renders MenuEngineeringTab with dish profitability', async () => {
      await renderAnalytics();

      expect(screen.getByText(/menu engineering & high-margin dish matrix/i)).toBeInTheDocument();
      expect(screen.getByText(/paneer tikka shashlik/i)).toBeInTheDocument();
      expect(screen.getByText(/dal makhani slow-cooked/i)).toBeInTheDocument();
      expect(screen.getByText(/butter garlic naan/i)).toBeInTheDocument();
    });

    it('renders GuestReviewsTab with rating scorecards and review feed', async () => {
      await renderAnalytics();

      expect(screen.getByText(/guest ratings & category scores/i)).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /verified guest feedback/i })).toBeInTheDocument();
      expect(screen.getByText(/food quality & taste/i)).toBeInTheDocument();
    });
  });

  describe('Timeframe Switching Navigation', () => {
    it('switches between Daily, Weekly, and Monthly timeframes and refetches API overview', async () => {
      const user = userEvent.setup();
      await renderAnalytics();

      expect(api.getAnalyticsOverview).toHaveBeenCalledWith('daily');

      // Click Weekly
      const weeklyBtn = screen.getByRole('button', { name: /weekly \(7 days\)/i });
      await user.click(weeklyBtn);

      expect(api.getAnalyticsOverview).toHaveBeenCalledWith('weekly');
      expect(await screen.findByText(/7-day revenue trajectory/i)).toBeInTheDocument();

      // Click Monthly
      const monthlyBtn = screen.getByRole('button', { name: /monthly \(30 days\)/i });
      await user.click(monthlyBtn);

      expect(api.getAnalyticsOverview).toHaveBeenCalledWith('monthly');
      expect(await screen.findByText(/30-day executive p&l/i)).toBeInTheDocument();
    });
  });

  describe('Guest Reviews Sentiment Filtering', () => {
    it('filters review stream by sentiment pills (Positive, Neutral, Critical)', async () => {
      const user = userEvent.setup();
      await renderAnalytics();

      // Initially 'ALL' should show reviews
      expect(screen.getByText(/water refill and table attendance was practically instantaneous/i)).toBeInTheDocument();
      expect(screen.getByText(/waited almost 4 minutes initially before server arrived/i)).toBeInTheDocument();

      // Click 'Positive' filter
      const positiveBtn = screen.getByRole('button', { name: /^positive$/i });
      await user.click(positiveBtn);

      expect(screen.getByText(/water refill and table attendance was practically instantaneous/i)).toBeInTheDocument();
      expect(screen.queryByText(/waited almost 4 minutes initially before server arrived/i)).not.toBeInTheDocument();

      // Click 'Critical' filter
      const criticalBtn = screen.getByRole('button', { name: /^critical$/i });
      await user.click(criticalBtn);

      expect(screen.getByText(/waited almost 4 minutes initially before server arrived/i)).toBeInTheDocument();
      expect(screen.queryByText(/water refill and table attendance was practically instantaneous/i)).not.toBeInTheDocument();

      // Click 'Neutral' filter
      const neutralBtn = screen.getByRole('button', { name: /^neutral$/i });
      await user.click(neutralBtn);

      expect(screen.getByText(/main course took about 18 minutes during the peak lunch rush/i)).toBeInTheDocument();
      expect(screen.queryByText(/water refill and table attendance was practically instantaneous/i)).not.toBeInTheDocument();
    });
  });
});
