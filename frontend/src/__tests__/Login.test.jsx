import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import Login from '../pages/Login';
import { ThemeProvider } from '../context/ThemeContext';

// Mock AuthContext
const mockLogin = vi.fn();
vi.mock('../context/AuthContext', async () => {
  const actual = await vi.importActual('../context/AuthContext');
  return {
    ...actual,
    useAuth: () => ({
      login: mockLogin,
    }),
  };
});

function renderLogin() {
  return render(
    <ThemeProvider>
      <Login />
    </ThemeProvider>
  );
}

describe('Login Component (BUG-021)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Form Accessibility & Labels', () => {
    it('renders form inputs associated with accessible label text', () => {
      renderLogin();

      const emailInput = screen.getByLabelText(/work email/i);
      expect(emailInput).toBeInTheDocument();
      expect(emailInput).toHaveAttribute('id', 'login-email');
      expect(emailInput).toHaveAttribute('type', 'email');
      expect(emailInput).toHaveAttribute('aria-required', 'true');

      const passwordInput = screen.getByLabelText(/security password/i);
      expect(passwordInput).toBeInTheDocument();
      expect(passwordInput).toHaveAttribute('id', 'login-password');
      expect(passwordInput).toHaveAttribute('type', 'password');
      expect(passwordInput).toHaveAttribute('aria-required', 'true');
    });

    it('renders role portal selector buttons with aria-pressed and accessible labels', () => {
      renderLogin();

      const ownerButton = screen.getByRole('button', { name: /select restaurant owner \(owner\)/i });
      const managerButton = screen.getByRole('button', { name: /select floor manager \(manager\)/i });
      const chefButton = screen.getByRole('button', { name: /select head chef \(kitchen\)/i });
      const waiterButton = screen.getByRole('button', { name: /select server \/ waiter \(waiter\)/i });
      const cleaningButton = screen.getByRole('button', { name: /select housekeeping \(cleaning\)/i });

      expect(ownerButton).toBeInTheDocument();
      expect(managerButton).toBeInTheDocument();
      expect(chefButton).toBeInTheDocument();
      expect(waiterButton).toBeInTheDocument();
      expect(cleaningButton).toBeInTheDocument();

      // Manager is default selected
      expect(managerButton).toHaveAttribute('aria-pressed', 'true');
      expect(ownerButton).toHaveAttribute('aria-pressed', 'false');
    });

    it('renders theme toggle with accessible label', () => {
      renderLogin();
      const themeToggle = screen.getByRole('button', { name: /switch to (dark|light) mode/i });
      expect(themeToggle).toBeInTheDocument();
    });
  });

  describe('Role Selection & Form Pre-fill', () => {
    it('updates email and password fields when clicking a different role portal', async () => {
      const user = userEvent.setup();
      renderLogin();

      const emailInput = screen.getByLabelText(/work email/i);
      const passwordInput = screen.getByLabelText(/security password/i);

      // Default is Manager
      expect(emailInput).toHaveValue('manager@restrovision.local');
      expect(passwordInput).toHaveValue('Manager@123');

      // Click Head Chef role
      const chefButton = screen.getByRole('button', { name: /select head chef \(kitchen\)/i });
      await user.click(chefButton);

      expect(chefButton).toHaveAttribute('aria-pressed', 'true');
      expect(emailInput).toHaveValue('chef@restrovision.local');
      expect(passwordInput).toHaveValue('Chef@123');

      // Click Owner role
      const ownerButton = screen.getByRole('button', { name: /select restaurant owner \(owner\)/i });
      await user.click(ownerButton);

      expect(ownerButton).toHaveAttribute('aria-pressed', 'true');
      expect(emailInput).toHaveValue('owner@restrovision.local');
      expect(passwordInput).toHaveValue('Admin@123');
    });
  });

  describe('Authentication Flow & Submission States', () => {
    it('dispatches login call with entered credentials upon submit', async () => {
      mockLogin.mockResolvedValueOnce({ userId: 1, email: 'owner@restrovision.local' });
      const user = userEvent.setup();
      renderLogin();

      const ownerButton = screen.getByRole('button', { name: /select restaurant owner \(owner\)/i });
      await user.click(ownerButton);

      const submitButton = screen.getByRole('button', { name: /sign in as restaurant owner/i });
      await user.click(submitButton);

      expect(mockLogin).toHaveBeenCalledTimes(1);
      expect(mockLogin).toHaveBeenCalledWith('owner@restrovision.local', 'Admin@123');
    });

    it('disables submit button and displays authenticating spinner/text during submission', async () => {
      let resolveLogin;
      mockLogin.mockImplementationOnce(
        () => new Promise((resolve) => { resolveLogin = resolve; })
      );

      const user = userEvent.setup();
      renderLogin();

      const submitButton = screen.getByRole('button', { name: /sign in as/i });
      await user.click(submitButton);

      // Button should now be disabled and text changed to Authenticating…
      expect(submitButton).toBeDisabled();
      expect(screen.getByText(/authenticating…/i)).toBeInTheDocument();

      // Resolve login
      resolveLogin({ userId: 2 });
      await waitFor(() => {
        expect(submitButton).not.toBeDisabled();
      });
    });

    it('displays error alert with role="alert" and aria-live="assertive" when login fails', async () => {
      mockLogin.mockRejectedValueOnce(new Error('Invalid credentials. Bad password.'));
      const user = userEvent.setup();
      renderLogin();

      const submitButton = screen.getByRole('button', { name: /sign in as/i });
      await user.click(submitButton);

      // Error alert should be visible
      const errorAlert = await screen.findByRole('alert');
      expect(errorAlert).toBeInTheDocument();
      expect(errorAlert).toHaveAttribute('aria-live', 'assertive');
      expect(errorAlert).toHaveTextContent('Invalid credentials. Bad password.');

      // Inputs should have aria-invalid="true" and aria-describedby pointing to error
      const emailInput = screen.getByLabelText(/work email/i);
      const passwordInput = screen.getByLabelText(/security password/i);
      expect(emailInput).toHaveAttribute('aria-invalid', 'true');
      expect(emailInput).toHaveAttribute('aria-describedby', 'login-error-alert');
      expect(passwordInput).toHaveAttribute('aria-invalid', 'true');
      expect(passwordInput).toHaveAttribute('aria-describedby', 'login-error-alert');
    });

    it('clears error alert when switching to a different role portal', async () => {
      mockLogin.mockRejectedValueOnce(new Error('Failed login'));
      const user = userEvent.setup();
      renderLogin();

      const submitButton = screen.getByRole('button', { name: /sign in as/i });
      await user.click(submitButton);

      expect(await screen.findByRole('alert')).toBeInTheDocument();

      // Click Housekeeping role
      const cleaningButton = screen.getByRole('button', { name: /select housekeeping \(cleaning\)/i });
      await user.click(cleaningButton);

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });
});
