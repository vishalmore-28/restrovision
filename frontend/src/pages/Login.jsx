import React, { useState } from 'react';
import { useAuth, ROLE_HIERARCHY, ROLE_METADATA } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { Lock, Mail, ShieldAlert, LogIn, Moon, Sun, Shield, CheckCircle2 } from 'lucide-react';

const ROLE_PORTALS = [
  {
    role: 'OWNER',
    label: 'Restaurant Owner',
    title: 'Executive & Strategic Portal',
    email: 'owner@restrovision.local',
    pass: 'Admin@123',
    level: 5,
    scope: 'Full Enterprise Clearance (Level 5)',
    accessible: 'Analytics & KPIs, Floor Plan, KDS, Waiters, Cleaning, CCTV, QR Portal',
  },
  {
    role: 'MANAGER',
    label: 'Floor Manager',
    title: 'Operations & Staff Portal',
    email: 'manager@restrovision.local',
    pass: 'Manager@123',
    level: 4,
    scope: 'Floor Operations Clearance (Level 4)',
    accessible: 'Analytics & KPIs, Floor Plan, Alert Center, Kitchen KDS, Waiters, Cleaning, CCTV, QR Portal',
  },
  {
    role: 'KITCHEN',
    label: 'Head Chef',
    title: 'Kitchen Display (KDS) Portal',
    email: 'chef@restrovision.local',
    pass: 'Chef@123',
    level: 3,
    scope: 'Kitchen Station Clearance (Level 3)',
    accessible: 'Kitchen KDS Kanban, Cleaning Tasks, Kitchen Alerts, CCTV Zones',
  },
  {
    role: 'WAITER',
    label: 'Server / Waiter',
    title: 'Floor Service Portal',
    email: 'waiter1@restrovision.local',
    pass: 'Waiter@123',
    level: 2,
    scope: 'Table Service Clearance (Level 2)',
    accessible: 'Waiter Station, Assigned Table Status, Customer QR Requests',
  },
  {
    role: 'CLEANING',
    label: 'Housekeeping',
    title: 'Sanitization & Turnover Portal',
    email: 'cleaning@restrovision.local',
    pass: 'Clean@123',
    level: 1,
    scope: 'Turnover & Hygiene Clearance (Level 1)',
    accessible: 'Cleaning Tasks, Table Turnover Status',
  },
];

export default function Login() {
  const { login } = useAuth();
  const { theme, toggleTheme } = useTheme();

  const [selectedRole, setSelectedRole] = useState(ROLE_PORTALS[1]); // Default to Manager
  const [email, setEmail] = useState(ROLE_PORTALS[1].email);
  const [password, setPassword] = useState(ROLE_PORTALS[1].pass);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSelectRole = (portal) => {
    setSelectedRole(portal);
    setEmail(portal.email);
    setPassword(portal.pass);
    setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await login(email, password);
    } catch (err) {
      setError(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 transition-colors duration-150 relative"
      style={{
        backgroundColor: 'var(--bg-main)',
        color: 'var(--text-primary)',
      }}
    >
      {/* Top right theme toggle */}
      <div className="absolute top-4 right-4">
        <button
          onClick={toggleTheme}
          className="p-2 rounded-xl border transition-colors duration-150 flex items-center justify-center shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
            color: 'var(--text-secondary)',
          }}
          title={`Switch to ${theme === 'light' ? 'Dark' : 'Light'} Mode`}
          aria-label={`Switch to ${theme === 'light' ? 'Dark' : 'Light'} Mode`}
        >
          {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
        </button>
      </div>

      <div
        className="w-full max-w-lg rounded-3xl p-6 sm:p-8 space-y-6 border transition-colors duration-150 shadow-sm"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        {/* Brand Header */}
        <div className="text-center space-y-2">
          <div
            className="w-12 h-12 rounded-2xl mx-auto flex items-center justify-center font-black text-lg tracking-tight shadow-sm"
            style={{
              backgroundColor: 'var(--btn-primary)',
              color: 'var(--btn-primary-text)',
            }}
          >
            Ψ
          </div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            RESTROVISION
          </h1>
          <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
            Role-Based Authentication &amp; Edge Vision Operations Console
          </p>
        </div>

        {/* Security Notice */}
        <div
          className="p-3.5 rounded-2xl border text-xs flex items-center gap-2.5"
          style={{
            backgroundColor: 'var(--bg-main)',
            borderColor: 'var(--border)',
          }}
        >
          <Shield className="w-4 h-4 shrink-0" style={{ color: 'var(--text-primary)' }} />
          <div className="text-[11px] leading-snug" style={{ color: 'var(--text-muted)' }}>
            <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
              Role Security Enforcement:
            </span>{' '}
            Junior personnel cannot access senior management dashboards. Select your authorized operational role below.
          </div>
        </div>

        {/* Role Portal Selector */}
        <div className="space-y-2">
          <label className="block text-xs font-semibold" style={{ color: 'var(--text-primary)' }}>
            Select Operational Portal
          </label>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {ROLE_PORTALS.map((portal) => {
              const isSelected = selectedRole.role === portal.role;
              return (
                <button
                  type="button"
                  key={portal.role}
                  onClick={() => handleSelectRole(portal)}
                  aria-pressed={isSelected}
                  aria-label={`Select ${portal.label} (${portal.role})`}
                  className="p-2.5 rounded-xl border text-left transition-colors duration-150 flex flex-col justify-between focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                  style={{
                    backgroundColor: isSelected ? 'var(--bg-main)' : 'var(--bg-card)',
                    borderColor: isSelected ? 'var(--text-primary)' : 'var(--border)',
                    boxShadow: isSelected ? '0 1px 3px rgba(0,0,0,0.06)' : 'none',
                  }}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span
                      className="text-[10px] font-bold px-1.5 py-0.5 rounded border"
                      style={{
                        backgroundColor: isSelected ? 'var(--bg-card)' : 'var(--bg-main)',
                        borderColor: 'var(--border)',
                        color: 'var(--text-primary)',
                      }}
                    >
                      L{portal.level}
                    </span>
                    {isSelected && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />}
                  </div>
                  <div className="text-xs font-bold leading-tight" style={{ color: 'var(--text-primary)' }}>
                    {portal.label}
                  </div>
                  <div className="text-[10px] truncate mt-0.5" style={{ color: 'var(--text-muted)' }}>
                    {portal.role}
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Role Permissions Scope Pill */}
        <div
          className="p-3 rounded-xl border text-xs space-y-1"
          style={{
            backgroundColor: 'var(--bg-main)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between">
            <span className="font-semibold text-xs" style={{ color: 'var(--text-primary)' }}>
              {selectedRole.title}
            </span>
            <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
              Clearance Level {selectedRole.level}
            </span>
          </div>
          <p className="text-[11px] leading-relaxed" style={{ color: 'var(--text-muted)' }}>
            Authorized Views: {selectedRole.accessible}
          </p>
        </div>

        {/* Error Notification */}
        {error && (
          <div
            id="login-error-alert"
            role="alert"
            aria-live="assertive"
            className="p-3.5 rounded-xl border text-xs flex items-center gap-2"
            style={{
              backgroundColor: 'var(--status-critical-bg)',
              borderColor: 'var(--status-critical-border)',
              color: 'var(--status-critical)',
            }}
          >
            <ShieldAlert className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Login Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="login-email" className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
              Work Email
            </label>
            <div className="relative">
              <Mail className="w-4 h-4 absolute left-3.5 top-3.5 text-neutral-400" aria-hidden="true" />
              <input
                id="login-email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@restrovision.local"
                aria-required="true"
                aria-invalid={!!error}
                aria-describedby={error ? "login-error-alert" : undefined}
                className="w-full border rounded-xl pl-10 pr-3.5 py-2.5 text-xs focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 transition-colors"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-primary)',
                }}
              />
            </div>
          </div>

          <div>
            <label htmlFor="login-password" className="block text-xs font-semibold mb-1.5" style={{ color: 'var(--text-primary)' }}>
              Security Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 absolute left-3.5 top-3.5 text-neutral-400" aria-hidden="true" />
              <input
                id="login-password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                aria-required="true"
                aria-invalid={!!error}
                aria-describedby={error ? "login-error-alert" : undefined}
                className="w-full border rounded-xl pl-10 pr-3.5 py-2.5 text-xs focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 transition-colors"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-primary)',
                }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-3 font-bold text-xs rounded-xl shadow-sm transition-opacity duration-150 flex items-center justify-center gap-2 disabled:opacity-50 whitespace-nowrap cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
            style={{
              backgroundColor: 'var(--btn-primary)',
              color: 'var(--btn-primary-text)',
            }}
          >
            <LogIn className="w-4 h-4" aria-hidden="true" />
            {submitting ? 'Authenticating…' : `Sign In as ${selectedRole.label}`}
          </button>
        </form>
      </div>
    </div>
  );
}
