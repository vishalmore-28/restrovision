import React, { useState, useEffect } from 'react';
import { useAuth, ROLE_HIERARCHY } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { Bell, Wifi, WifiOff, Sun, Moon, LogOut, Menu, X, Shield } from 'lucide-react';
import { api } from '../services/api';
import { wsService } from '../services/websocket';

export default function Navbar({ onSelectTab, activeAlertCount = 0, mobileMenuOpen, onToggleMobileMenu }) {
  const { user, wsConnected, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [alerts, setAlerts] = useState([]);

  useEffect(() => {
    api.getActiveAlerts()
      .then(setAlerts)
      .catch((err) => console.warn('[Navbar] Failed to fetch active alerts:', err?.message || err));

    const unsub = wsService.subscribe('/topic/restaurants/1/alerts', (newAlert) => {
      setAlerts((prev) => {
        const filtered = prev.filter((a) => a.alertId !== newAlert.alertId);
        if (newAlert.status === 'OPEN' || newAlert.status === 'ESCALATED') {
          return [newAlert, ...filtered];
        }
        return filtered;
      });
    });

    return () => unsub();
  }, []);

  const openAlertsCount = alerts.filter(a => a.status === 'OPEN' || a.status === 'ESCALATED').length;

  return (
    <header
      className="border-b px-2.5 sm:px-4 md:px-5 py-2.5 sm:py-3 flex items-center justify-between sticky top-0 z-40 transition-colors duration-150 w-full max-w-full box-border overflow-hidden"
      style={{
        backgroundColor: 'var(--bg-card)',
        borderColor: 'var(--border)',
        color: 'var(--text-primary)',
      }}
    >
      {/* Brand & System Status */}
      <div className="flex items-center gap-2 sm:gap-3 min-w-0">
        {/* Mobile menu toggle button */}
        <button
          onClick={onToggleMobileMenu}
          className="md:hidden p-1.5 rounded-lg border transition-colors duration-150 flex items-center justify-center focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-400 shrink-0"
          style={{
            backgroundColor: 'var(--bg-main)',
            borderColor: 'var(--border)',
            color: 'var(--text-secondary)',
          }}
          aria-label={mobileMenuOpen ? 'Close Navigation Menu' : 'Open Navigation Menu'}
        >
          {mobileMenuOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
        </button>

        <div
          className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl flex items-center justify-center font-black text-sm tracking-tighter shrink-0"
          style={{
            backgroundColor: 'var(--btn-primary)',
            color: 'var(--btn-primary-text)',
          }}
        >
          Ψ
        </div>
        <div className="min-w-0">
          <span className="font-extrabold text-sm sm:text-base tracking-tight block leading-tight truncate" style={{ color: 'var(--text-primary)' }}>
            RESTROVISION
          </span>
          <p className="text-[10px] sm:text-xs leading-tight truncate hidden xs:block" style={{ color: 'var(--text-muted)' }}>
            Operations &amp; Edge Vision
          </p>
        </div>
      </div>

      {/* Center & Right Controls */}
      <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
        {/* WebSocket Live Status */}
        <div
          className="hidden md:flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border"
          style={{
            backgroundColor: 'var(--bg-main)',
            borderColor: 'var(--border)',
            color: 'var(--text-secondary)',
          }}
        >
          {wsConnected ? (
            <>
              <span className="w-2 h-2 rounded-full animate-pulse" style={{ backgroundColor: 'var(--status-available)' }}></span>
              <span className="flex items-center gap-1 font-semibold" style={{ color: 'var(--status-available)' }}>
                <Wifi className="w-3.5 h-3.5" /> STOMP Live
              </span>
            </>
          ) : (
            <>
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: 'var(--status-occupied)' }}></span>
              <span className="flex items-center gap-1 font-semibold" style={{ color: 'var(--status-occupied)' }}>
                <WifiOff className="w-3.5 h-3.5" /> Connecting…
              </span>
            </>
          )}
        </div>

        {/* Theme Toggle Button */}
        <button
          onClick={toggleTheme}
          className="p-2 rounded-xl border transition-colors duration-150 flex items-center justify-center"
          style={{
            backgroundColor: 'var(--bg-main)',
            borderColor: 'var(--border)',
            color: 'var(--text-secondary)',
          }}
          title={`Switch to ${theme === 'light' ? 'Dark' : 'Light'} Mode`}
          aria-label="Toggle theme"
        >
          {theme === 'light' ? (
            <Moon className="w-4 h-4" />
          ) : (
            <Sun className="w-4 h-4" />
          )}
        </button>

        {/* Alert Bell */}
        <button
          onClick={() => onSelectTab('alerts')}
          className="relative p-2 rounded-xl border transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-main)',
            borderColor: 'var(--border)',
            color: 'var(--text-secondary)',
          }}
          title="Open Alert Center"
        >
          <Bell className="w-4 h-4" />
          {openAlertsCount > 0 && (
            <span
              className="absolute -top-1 -right-1 text-white text-[10px] font-extrabold w-4 h-4 rounded-full flex items-center justify-center shadow-sm"
              style={{ backgroundColor: 'var(--status-critical)' }}
            >
              {openAlertsCount}
            </span>
          )}
        </button>

        {/* Active User Info & Sign Out */}
        <div
          className="flex items-center gap-1.5 sm:gap-2.5 pl-2 sm:pl-3 border-l"
          style={{ borderColor: 'var(--border)' }}
        >
          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs"
            style={{
              backgroundColor: 'var(--btn-primary)',
              color: 'var(--btn-primary-text)',
            }}
          >
            {user?.fullName?.charAt(0) || 'U'}
          </div>
          <div className="text-left hidden lg:block">
            <div className="text-xs font-semibold leading-none" style={{ color: 'var(--text-primary)' }}>
              {user?.fullName || 'Manager'}
            </div>
            <div className="flex items-center gap-1.5 mt-1">
              <span
                className="text-[10px] font-bold px-1.5 py-0.5 rounded border leading-none"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                  color: 'var(--text-primary)',
                }}
              >
                {user?.jobRole || 'FLOOR_MANAGER'}
              </span>
              <span className="text-[10px]" style={{ color: 'var(--text-muted)' }}>
                Level {ROLE_HIERARCHY[user?.jobRole] || 1}
              </span>
            </div>
          </div>

          <button
            onClick={logout}
            className="p-1.5 rounded-lg border ml-1 transition-colors duration-150 flex items-center justify-center hover:opacity-80"
            style={{
              backgroundColor: 'var(--bg-main)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
            title="Sign Out to Login Page"
            aria-label="Sign Out"
          >
            <LogOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </header>
  );
}
