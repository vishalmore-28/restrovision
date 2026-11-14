import React from 'react';
import { useAuth, ROLE_PERMISSIONS, ROLE_METADATA } from '../context/AuthContext';
import {
  LayoutGrid,
  Bell,
  Flame,
  UserCheck,
  Sparkles,
  Camera,
  BarChart3,
  QrCode,
  ShieldCheck,
} from 'lucide-react';

export default function Sidebar({ activeTab, onSelectTab, mobileOpen, onCloseMobile }) {
  const { user } = useAuth();
  const userRole = user?.jobRole || 'WAITER';
  const allowedTabs = ROLE_PERMISSIONS[userRole] || ['floor'];

  const allNavItems = [
    { id: 'analytics', label: 'Analytics & KPIs', icon: BarChart3, desc: 'Revenue, turnover & margins' },
    { id: 'floor', label: 'Live Floor Plan', icon: LayoutGrid, desc: 'Tables, sessions & AI overrides' },
    { id: 'alerts', label: 'Alert Center', icon: Bell, desc: 'Realtime alerts & false alarms' },
    { id: 'kitchen', label: 'Kitchen KDS', icon: Flame, desc: 'Station orders & prep delays' },
    { id: 'waiter', label: 'Waiter Station', icon: UserCheck, desc: 'Requests, delivery & workload' },
    { id: 'cleaning', label: 'Cleaning Tasks', icon: Sparkles, desc: 'Turnover & sanitized status' },
    { id: 'cctv', label: 'CCTV & AI Zones', icon: Camera, desc: 'Vision feeds & event simulator' },
    { id: 'qr', label: 'Customer QR Demo', icon: QrCode, desc: 'Self-service mobile portal' },
  ];

  // RBAC Enforcement: Juniors cannot see Senior modules
  const visibleNavItems = allNavItems.filter((item) => allowedTabs.includes(item.id));

  return (
    <>
      {/* Mobile Drawer Overlay Backdrop */}
      {mobileOpen && (
        <div
          className="fixed inset-0 bg-black/60 z-40 md:hidden transition-opacity duration-150"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={`${
          mobileOpen
            ? 'fixed inset-y-0 left-0 z-50 w-72 shadow-2xl flex top-[61px] h-[calc(100vh-61px)]'
            : 'hidden md:flex w-64'
        } border-r p-4 flex-col justify-between h-[calc(100vh-61px)] overflow-y-auto transition-colors duration-150 shrink-0`}
        style={{
          backgroundColor: 'var(--bg-sidebar)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="space-y-1">
          <div
            className="text-xs font-semibold px-3 py-2 flex items-center justify-between"
            style={{ color: 'var(--text-muted)' }}
          >
            <span>Console Access</span>
            <span
              className="text-[10px] font-mono border px-1.5 py-0.5 rounded font-bold"
              style={{
                borderColor: 'var(--border)',
                backgroundColor: 'var(--bg-card)',
                color: 'var(--text-primary)',
              }}
            >
              {userRole}
            </span>
          </div>
          {visibleNavItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => {
                  onSelectTab(item.id);
                  if (onCloseMobile) onCloseMobile();
                }}
                className="w-full flex items-start gap-3 px-3 py-2.5 rounded-xl text-left transition-colors duration-150"
                style={{
                  backgroundColor: isActive ? 'var(--bg-card)' : 'transparent',
                  borderColor: isActive ? 'var(--border)' : 'transparent',
                  borderWidth: '1px',
                  color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
                  boxShadow: isActive ? '0 1px 2px rgba(0,0,0,0.04)' : 'none',
                }}
              >
                <Icon
                  className="w-4 h-4 mt-0.5 shrink-0 transition-colors duration-150"
                  style={{
                    color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
                  }}
                />
                <div className="min-w-0">
                  <div
                    className="text-sm font-semibold leading-tight truncate"
                    style={{ color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)' }}
                  >
                    {item.label}
                  </div>
                  <div
                    className="text-xs mt-0.5 leading-snug truncate"
                    style={{ color: 'var(--text-muted)' }}
                  >
                    {item.desc}
                  </div>
                </div>
              </button>
            );
          })}
        </div>

        {/* Edge Node Status Box */}
        <div
          className="p-3.5 rounded-xl border text-xs mt-4 transition-colors duration-150"
          style={{
            backgroundColor: 'var(--bg-card)',
            borderColor: 'var(--border)',
          }}
        >
          <div className="flex items-center justify-between font-semibold mb-1">
            <span className="flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span> Edge Node
            </span>
            <span
              className="text-[11px] font-mono px-1.5 py-0.5 rounded border"
              style={{
                backgroundColor: 'var(--bg-main)',
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
              }}
            >
              edge-pune-01
            </span>
          </div>
          <p className="text-xs leading-relaxed" style={{ color: 'var(--text-muted)' }}>
            Scope: {ROLE_METADATA[userRole]?.scope || 'Restricted Operations'}
          </p>
        </div>
      </aside>
    </>
  );
}
