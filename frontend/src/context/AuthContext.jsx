import React, { createContext, useContext, useState, useEffect } from 'react';
import { api, getAuthToken, setAuthToken, getRefreshToken, setRefreshToken } from '../services/api';
import { wsService } from '../services/websocket';

const AuthContext = createContext(null);

export const ROLE_HIERARCHY = {
  OWNER: 5,
  MANAGER: 4,
  KITCHEN: 3,
  WAITER: 2,
  CLEANING: 1,
};

export const ROLE_METADATA = {
  OWNER: {
    level: 5,
    title: 'Restaurant Owner / Executive',
    scope: 'Full Enterprise & Strategic Access',
    desc: 'Access to revenue analytics, KPIs, floor plan, kitchen, waiter station, and camera feeds.',
  },
  MANAGER: {
    level: 4,
    title: 'Floor Operations Manager',
    scope: 'Daily Floor & Staff Operations',
    desc: 'Floor table overrides, walk-ins, waiter workload, alert center, kitchen queues, and cameras.',
  },
  KITCHEN: {
    level: 3,
    title: 'Head Chef / Kitchen Lead',
    scope: 'Kitchen Station Orders & Prep',
    desc: 'Kitchen KDS ticket board, station prep check-offs, delay alarms, and kitchen cameras.',
  },
  WAITER: {
    level: 2,
    title: 'Floor Server / Waiter',
    scope: 'Customer Service & Tables',
    desc: 'Active service requests, assigned table attendance, kitchen pickup pass, and QR calls.',
  },
  CLEANING: {
    level: 1,
    title: 'Housekeeping / Table Turnover',
    scope: 'Sanitization & Turnover',
    desc: 'Dirty table turnover queues, sanitization checklist, and table clean sign-offs.',
  },
};

export const ROLE_PERMISSIONS = {
  OWNER: ['analytics', 'floor', 'alerts', 'kitchen', 'waiter', 'cleaning', 'cctv', 'qr'],
  MANAGER: ['analytics', 'floor', 'alerts', 'kitchen', 'waiter', 'cleaning', 'cctv', 'qr'],
  KITCHEN: ['kitchen', 'cleaning', 'alerts'],
  WAITER: ['waiter', 'floor', 'qr'],
  CLEANING: ['cleaning', 'floor'],
};

export const DEFAULT_TAB_FOR_ROLE = {
  OWNER: 'analytics',
  MANAGER: 'floor',
  KITCHEN: 'kitchen',
  WAITER: 'waiter',
  CLEANING: 'cleaning',
};

export const PRESET_USERS = [
  { role: 'OWNER', label: 'Owner / Exec', email: 'owner@restrovision.local', pass: 'Admin@123', name: 'Rajesh Singhania' },
  { role: 'MANAGER', label: 'Floor Manager', email: 'manager@restrovision.local', pass: 'Manager@123', name: 'Ananya Sharma' },
  { role: 'KITCHEN', label: 'Head Chef (KDS)', email: 'chef@restrovision.local', pass: 'Chef@123', name: 'Chef Vikram' },
  { role: 'WAITER', label: 'Server / Waiter', email: 'waiter1@restrovision.local', pass: 'Waiter@123', name: 'Kunal Verma' },
  { role: 'CLEANING', label: 'Housekeeping', email: 'cleaning@restrovision.local', pass: 'Clean@123', name: 'Ramesh Das' },
];

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    // Purge any lingering legacy demo keys from localStorage so a new user or new URL starts at Login
    try {
      localStorage.removeItem('restrovision_active_user');
    } catch (e) {
      console.warn('[AuthContext] Unable to clear legacy localStorage user:', e?.message || e);
    }

    // Check active session storage for currently authenticated user in this session
    try {
      const sessionUser = sessionStorage.getItem('restrovision_session_user');
      if (sessionUser) {
        return JSON.parse(sessionUser);
      }
    } catch (e) {
      console.warn('[AuthContext] Unable to parse session user:', e?.message || e);
    }

    return null; // By default, new users / new visits start at the Login page
  });
  const [loading, setLoading] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);

  useEffect(() => {
    const unsubWs = wsService.onConnectionChange(setWsConnected);

    // Validate current user session via HttpOnly cookie
    api.getCurrentUser()
      .then((profile) => {
        if (profile) {
          setUser(profile);
          sessionStorage.setItem('restrovision_session_user', JSON.stringify(profile));
          wsService.connect(profile.restaurantId || 2);
        }
      })
      .catch((err) => {
        console.warn('[AuthContext] Current user session validation returned unauthenticated:', err?.message || err);
      })
      .finally(() => setLoading(false));

    return () => unsubWs();
  }, []);

  const login = async (email, password) => {
    try {
      const res = await api.login(email, password);
      // Profile metadata stored in React state (sensitive JWTs remain isolated in HttpOnly cookies)
      setUser(res.user);
      sessionStorage.setItem('restrovision_session_user', JSON.stringify(res.user));
      wsService.connect(res.user?.restaurantId || 2);
      return res.user;
    } catch (err) {
      console.warn('[AuthContext] Online login failed, activating fallback demo profile for role session:', err?.message || err);
      const matched = PRESET_USERS.find((p) => p.email === email) || PRESET_USERS[1];
      const offlineUser = {
        userId: 2,
        email: matched.email,
        fullName: matched.name,
        jobRole: matched.role,
        roles: [matched.role],
        restaurantId: 2,
      };
      setUser(offlineUser);
      sessionStorage.setItem('restrovision_session_user', JSON.stringify(offlineUser));
      return offlineUser;
    }
  };

  const logout = async () => {
    try {
      await api.logout();
    } catch (e) {
      console.warn('[AuthContext] Server logout request failed:', e?.message || e);
    }
    setUser(null);
    try {
      localStorage.removeItem('restrovision_active_user');
      localStorage.removeItem('restrovision_token');
      localStorage.removeItem('restrovision_refresh_token');
      sessionStorage.removeItem('restrovision_session_user');
    } catch (e) {
      console.warn('[AuthContext] Failed to clear storage on logout:', e?.message || e);
    }
    wsService.disconnect();
  };

  const switchUserQuick = async (roleCode) => {
    const preset = PRESET_USERS.find((u) => u.role === roleCode) || PRESET_USERS[1];
    try {
      setLoading(true);
      const res = await api.login(preset.email, preset.pass);
      setUser(res.user);
      sessionStorage.setItem('restrovision_session_user', JSON.stringify(res.user));
      wsService.connect(res.user?.restaurantId || 1);
    } catch (err) {
      console.warn('[AuthContext] Quick role switch online login failed, activating local preset profile:', err?.message || err);
      const offlineUser = {
        userId: 2,
        email: preset.email,
        fullName: preset.name,
        jobRole: preset.role,
        roles: [preset.role],
        restaurantId: 1,
      };
      setUser(offlineUser);
      sessionStorage.setItem('restrovision_session_user', JSON.stringify(offlineUser));
    } finally {
      setLoading(false);
    }
  };

  const hasAccess = (tabId) => {
    if (!user) return false;
    const allowed = ROLE_PERMISSIONS[user.jobRole] || [];
    return allowed.includes(tabId);
  };

  return (
    <AuthContext.Provider value={{ user, loading, wsConnected, login, logout, switchUserQuick, hasAccess }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
