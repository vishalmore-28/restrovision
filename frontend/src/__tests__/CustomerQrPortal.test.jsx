import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import CustomerQrPortal from '../pages/CustomerQrPortal';
import { api } from '../services/api';

vi.mock('../services/api', () => ({
  api: {
    getQrStatus: vi.fn(),
    callWaiterFromQr: vi.fn(),
    submitFeedback: vi.fn(),
  },
}));

describe('CustomerQrPortal Component (BUG-021)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getQrStatus.mockResolvedValue({
      tableId: 1,
      tableCode: 'T01',
      sessionId: 'sess-abc-123',
      occupied: true,
    });
  });

  describe('Initial Rendering & Table Switching', () => {
    it('fetches initial table QR status on mount and renders table code', async () => {
      render(<CustomerQrPortal />);

      expect(api.getQrStatus).toHaveBeenCalledWith('qr-tok-table-01');
      expect(await screen.findByText(/table T01/i)).toBeInTheDocument();
      expect(screen.getByText(/the bombay grill & bistro/i)).toBeInTheDocument();
    });

    it('switches table token and refetches status when selecting a table button', async () => {
      const user = userEvent.setup();
      api.getQrStatus.mockResolvedValueOnce({
        tableId: 1,
        tableCode: 'T01',
        sessionId: 'sess-1',
      });
      api.getQrStatus.mockResolvedValueOnce({
        tableId: 2,
        tableCode: 'T02',
        sessionId: 'sess-2',
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const table2Btn = screen.getByRole('button', { name: /select table t02/i });
      await user.click(table2Btn);

      expect(api.getQrStatus).toHaveBeenCalledWith('qr-tok-table-02');
      expect(await screen.findByText(/table T02/i)).toBeInTheDocument();
    });
  });

  describe('Quick Assistance Actions', () => {
    it('dispatches call waiter request and displays status message banner', async () => {
      const user = userEvent.setup();
      api.callWaiterFromQr.mockResolvedValueOnce({
        message: 'Server Kunal has been notified and is on the way!',
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const callButton = screen.getByRole('button', { name: /call assigned server/i });
      expect(callButton).toBeInTheDocument();
      await user.click(callButton);

      expect(api.callWaiterFromQr).toHaveBeenCalledWith('qr-tok-table-01', 'CALL_WAITER');

      const statusBanner = await screen.findByRole('status');
      expect(statusBanner).toBeInTheDocument();
      expect(statusBanner).toHaveAttribute('aria-live', 'polite');
      expect(statusBanner).toHaveTextContent(/server kunal has been notified/i);
    });

    it('dispatches water refill request when clicking water refill button', async () => {
      const user = userEvent.setup();
      api.callWaiterFromQr.mockResolvedValueOnce({
        message: 'Water refill requested.',
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const waterButton = screen.getByRole('button', { name: /request water refill/i });
      await user.click(waterButton);

      expect(api.callWaiterFromQr).toHaveBeenCalledWith('qr-tok-table-01', 'WATER');
      const statusBanner = await screen.findByRole('status');
      expect(statusBanner).toHaveTextContent(/water refill requested/i);
    });

    it('dispatches bill request when clicking request bill button', async () => {
      const user = userEvent.setup();
      api.callWaiterFromQr.mockResolvedValueOnce({
        message: 'Bill requested.',
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const billButton = screen.getByRole('button', { name: /request bill/i });
      await user.click(billButton);

      expect(api.callWaiterFromQr).toHaveBeenCalledWith('qr-tok-table-01', 'REQUEST_BILL');
    });

    it('handles waiter call failure gracefully and shows error in status banner', async () => {
      const user = userEvent.setup();
      api.callWaiterFromQr.mockRejectedValueOnce({
        response: { data: { detail: 'Service temporarily busy. Please alert staff directly.' } },
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const callButton = screen.getByRole('button', { name: /call assigned server/i });
      await user.click(callButton);

      const statusBanner = await screen.findByRole('status');
      expect(statusBanner).toHaveTextContent(/service temporarily busy/i);
    });
  });

  describe('Guest Feedback Form & Star Rating', () => {
    it('renders star rating buttons with accessible names and tracks pressed state', async () => {
      const user = userEvent.setup();
      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const ratingGroup = screen.getByRole('group', { name: /rating out of 5 stars/i });
      expect(ratingGroup).toBeInTheDocument();

      const starButtons = screen.getAllByRole('button', { name: /rate \d out of 5 stars/i });
      expect(starButtons).toHaveLength(5);

      // Default rating is 5
      expect(screen.getByRole('button', { name: /rate 5 out of 5 stars/i })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByRole('button', { name: /rate 4 out of 5 stars/i })).toHaveAttribute('aria-pressed', 'false');

      // Click 4 stars
      await user.click(screen.getByRole('button', { name: /rate 4 out of 5 stars/i }));
      expect(screen.getByRole('button', { name: /rate 4 out of 5 stars/i })).toHaveAttribute('aria-pressed', 'true');
      expect(screen.getByRole('button', { name: /rate 5 out of 5 stars/i })).toHaveAttribute('aria-pressed', 'false');
    });

    it('submits guest feedback successfully and displays success confirmation banner', async () => {
      const user = userEvent.setup();
      api.submitFeedback.mockResolvedValueOnce({
        feedbackId: 101,
        sentiment: 'POSITIVE',
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      // Change rating to 4
      await user.click(screen.getByRole('button', { name: /rate 4 out of 5 stars/i }));

      // Fill in comment
      const commentInput = screen.getByLabelText(/rate your dining experience/i);
      await user.type(commentInput, 'The dal makhani was exceptionally good!');

      // Submit
      const submitBtn = screen.getByRole('button', { name: /submit review/i });
      await user.click(submitBtn);

      expect(api.submitFeedback).toHaveBeenCalledTimes(1);
      expect(api.submitFeedback).toHaveBeenCalledWith({
        tableId: 1,
        sessionId: 'sess-abc-123',
        rating: 4,
        comment: 'The dal makhani was exceptionally good!',
      });

      // Verify success status
      const successBanner = await screen.findByRole('status');
      expect(successBanner).toHaveTextContent(/thank you for your feedback!/i);
      expect(successBanner).toHaveTextContent(/processed by restrovision sentiment ai/i);
    });

    it('displays error alert with role="alert" when feedback submission fails', async () => {
      const user = userEvent.setup();
      api.submitFeedback.mockRejectedValueOnce({
        response: { data: { detail: 'Rate limit exceeded: only one feedback per session permitted.' } },
      });

      render(<CustomerQrPortal />);
      await screen.findByText(/table T01/i);

      const submitBtn = screen.getByRole('button', { name: /submit review/i });
      await user.click(submitBtn);

      const alertBanner = await screen.findByRole('alert');
      expect(alertBanner).toBeInTheDocument();
      expect(alertBanner).toHaveAttribute('aria-live', 'assertive');
      expect(alertBanner).toHaveTextContent(/rate limit exceeded/i);
    });
  });
});
