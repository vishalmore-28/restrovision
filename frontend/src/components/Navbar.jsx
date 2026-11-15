import React, { useState, useEffect, useRef } from 'react';
import { useAuth, ROLE_HIERARCHY } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { Bell, Wifi, WifiOff, Sun, Moon, LogOut, Menu, X, Shield, Volume2, VolumeX, Volume1, Play } from 'lucide-react';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import { soundService } from '../services/soundService';

export default function Navbar({ onSelectTab, activeAlertCount = 0, mobileMenuOpen, onToggleMobileMenu }) {
  const { user, wsConnected, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [alerts, setAlerts] = useState([]);

  // Real-Time Sound Controls State
  const [soundMuted, setSoundMuted] = useState(soundService.isMuted());
  const [soundVolume, setSoundVolume] = useState(soundService.getVolume());
  const [soundMenuOpen, setSoundMenuOpen] = useState(false);
  const [lastTestedSound, setLastTestedSound] = useState(null);
  const soundPopoverRef = useRef(null);

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

    const unsubSound = soundService.subscribe((state) => {
      setSoundMuted(state.muted);
      setSoundVolume(state.volume);
    });

    const handleOutsideClick = (e) => {
      if (soundPopoverRef.current && !soundPopoverRef.current.contains(e.target)) {
        setSoundMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);

    return () => {
      unsub();
      unsubSound();
      document.removeEventListener('mousedown', handleOutsideClick);
    };
  }, []);

  const openAlertsCount = alerts.filter(a => a.status === 'OPEN' || a.status === 'ESCALATED').length;

  return (
    <header
      className="border-b px-2.5 sm:px-4 md:px-5 py-2.5 sm:py-3 flex items-center justify-between sticky top-0 z-50 transition-colors duration-150 w-full max-w-full box-border overflow-visible"
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

        {/* Audio Alerts & Sound Controls */}
        <div className="relative" ref={soundPopoverRef}>
          <button
            onClick={() => setSoundMenuOpen((prev) => !prev)}
            className="p-2 rounded-xl border transition-colors duration-150 flex items-center justify-center"
            style={{
              backgroundColor: soundMuted ? 'var(--status-occupied-bg)' : 'var(--bg-main)',
              borderColor: soundMuted ? 'var(--status-occupied-border)' : 'var(--border)',
              color: soundMuted ? 'var(--status-occupied)' : 'var(--text-secondary)',
            }}
            title={soundMuted ? 'Sound Alerts Muted (Click to configure)' : `Sound Volume: ${Math.round(soundVolume * 100)}% (Click to configure)`}
            aria-label="Sound Alerts Configuration"
          >
            {soundMuted || soundVolume === 0 ? (
              <VolumeX className="w-4 h-4" />
            ) : soundVolume < 0.5 ? (
              <Volume1 className="w-4 h-4 text-emerald-500" />
            ) : (
              <Volume2 className="w-4 h-4 text-emerald-500" />
            )}
          </button>

          {/* Sound Controls Popover */}
          {soundMenuOpen && (
            <div
              className="absolute right-0 mt-2 w-72 rounded-2xl border p-4 shadow-xl z-50 space-y-3.5 animate-fade-in"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: 'var(--border)',
              }}
            >
              <div className="flex items-center justify-between border-b pb-2.5" style={{ borderColor: 'var(--border)' }}>
                <div className="flex items-center gap-2">
                  <Volume2 className="w-4 h-4 text-emerald-500" />
                  <span className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>
                    Kitchen &amp; Floor Chimes
                  </span>
                </div>
                <button
                  onClick={() => soundService.toggleMute()}
                  className={`px-2 py-0.5 rounded-lg text-[10px] font-bold border transition-colors ${
                    soundMuted
                      ? 'bg-red-500/20 text-red-400 border-red-500/30'
                      : 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                  }`}
                >
                  {soundMuted ? 'MUTED' : 'ACTIVE'}
                </button>
              </div>

              {/* Volume Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-xs" style={{ color: 'var(--text-secondary)' }}>
                  <span>Volume:</span>
                  <span className="font-mono font-bold text-emerald-400">
                    {soundMuted ? '0%' : `${Math.round(soundVolume * 100)}%`}
                  </span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  value={soundMuted ? 0 : soundVolume}
                  disabled={soundMuted}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value);
                    soundService.setVolume(val);
                  }}
                  className="w-full accent-emerald-500 cursor-pointer h-1.5 rounded-lg"
                />
              </div>

              {/* Quick Sound Test Matrix */}
              <div className="space-y-1.5 pt-1">
                <span className="text-[10px] font-bold uppercase tracking-wider block" style={{ color: 'var(--text-muted)' }}>
                  Test Audio Chimes:
                </span>
                <div className="grid grid-cols-2 gap-1.5">
                  <button
                    onClick={() => {
                      soundService.testSound('KITCHEN');
                      setLastTestedSound('Kitchen Bell');
                      setTimeout(() => setLastTestedSound(null), 1500);
                    }}
                    className="p-1.5 rounded-lg border text-[11px] font-medium transition-colors hover:opacity-85 text-left flex items-center gap-1.5"
                    style={{ backgroundColor: 'var(--bg-main)', borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                  >
                    <span>🔔</span> <span>Kitchen Bell</span>
                  </button>
                  <button
                    onClick={() => {
                      soundService.testSound('WAITER');
                      setLastTestedSound('Waiter Call');
                      setTimeout(() => setLastTestedSound(null), 1500);
                    }}
                    className="p-1.5 rounded-lg border text-[11px] font-medium transition-colors hover:opacity-85 text-left flex items-center gap-1.5"
                    style={{ backgroundColor: 'var(--bg-main)', borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                  >
                    <span>🛎️</span> <span>Waiter Call</span>
                  </button>
                  <button
                    onClick={() => {
                      soundService.testSound('DELAY');
                      setLastTestedSound('SLA Delay');
                      setTimeout(() => setLastTestedSound(null), 1500);
                    }}
                    className="p-1.5 rounded-lg border text-[11px] font-medium transition-colors hover:opacity-85 text-left flex items-center gap-1.5"
                    style={{ backgroundColor: 'var(--bg-main)', borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                  >
                    <span>⚠️</span> <span>SLA Delay</span>
                  </button>
                  <button
                    onClick={() => {
                      soundService.testSound('SUCCESS');
                      setLastTestedSound('Order Ready');
                      setTimeout(() => setLastTestedSound(null), 1500);
                    }}
                    className="p-1.5 rounded-lg border text-[11px] font-medium transition-colors hover:opacity-85 text-left flex items-center gap-1.5"
                    style={{ backgroundColor: 'var(--bg-main)', borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                  >
                    <span>✅</span> <span>Order Ready</span>
                  </button>
                </div>
                {lastTestedSound && (
                  <span className="text-[10px] text-emerald-400 font-mono block text-center pt-0.5 animate-pulse">
                    Played: {lastTestedSound}
                  </span>
                )}
              </div>
            </div>
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
