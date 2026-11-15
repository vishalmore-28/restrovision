const API_BASE = '/api/v1';

export function getCsrfToken() {
  if (typeof document === 'undefined') return '';
  const match = document.cookie.match(new RegExp('(^| )restro_csrf_token=([^;]+)'));
  return match ? decodeURIComponent(match[2]) : '';
}

let inMemoryAccessToken = '';
let inMemoryRefreshToken = '';

export function getAuthToken() {
  if (inMemoryAccessToken) return inMemoryAccessToken;
  try {
    if (typeof window !== 'undefined') {
      return sessionStorage.getItem('restrovision_jwt') || localStorage.getItem('restrovision_token') || '';
    }
  } catch (_) {}
  return '';
}

export function setAuthToken(token) {
  inMemoryAccessToken = token || '';
  try {
    if (typeof window !== 'undefined') {
      if (token) {
        sessionStorage.setItem('restrovision_jwt', token);
      } else {
        sessionStorage.removeItem('restrovision_jwt');
        localStorage.removeItem('restrovision_token');
      }
    }
  } catch (_) {}
}

export function getRefreshToken() {
  if (inMemoryRefreshToken) return inMemoryRefreshToken;
  try {
    if (typeof window !== 'undefined') {
      return sessionStorage.getItem('restrovision_refresh_jwt') || localStorage.getItem('restrovision_refresh_token') || '';
    }
  } catch (_) {}
  return '';
}

export function setRefreshToken(token) {
  inMemoryRefreshToken = token || '';
  try {
    if (typeof window !== 'undefined') {
      if (token) {
        sessionStorage.setItem('restrovision_refresh_jwt', token);
      } else {
        sessionStorage.removeItem('restrovision_refresh_jwt');
        localStorage.removeItem('restrovision_refresh_token');
      }
    }
  } catch (_) {}
}

let isRefreshing = false;
let refreshSubscribers = [];

function onRefreshed(success = true) {
  refreshSubscribers.forEach((callback) => callback(success));
  refreshSubscribers = [];
}

async function request(endpoint, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const csrfToken = getCsrfToken();
  const authToken = getAuthToken();
  const isMutating = ['POST', 'PUT', 'DELETE', 'PATCH'].includes(method);

  const headers = {
    'Content-Type': 'application/json',
    ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
    ...(isMutating && csrfToken ? { 'X-CSRF-TOKEN': csrfToken } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers,
  });

  // Handle Token Expiry & Automatic Refresh via HttpOnly Cookies
  if (response.status === 401 && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/refresh')) {
    if (!isRefreshing) {
      isRefreshing = true;
      try {
        const refreshCsrf = getCsrfToken();
        const refreshRes = await fetch(`${API_BASE}/auth/refresh`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
            ...(refreshCsrf ? { 'X-CSRF-TOKEN': refreshCsrf } : {}),
          },
          body: JSON.stringify({}),
        });

        if (refreshRes.ok) {
          isRefreshing = false;
          try {
            const data = await refreshRes.json();
            if (data?.accessToken) setAuthToken(data.accessToken);
            if (data?.refreshToken) setRefreshToken(data.refreshToken);
          } catch (_) {}
          onRefreshed(true);

          // Retry original request with refreshed cookies
          return request(endpoint, options);
        } else {
          isRefreshing = false;
          onRefreshed(false);
        }
      } catch (_) {
        isRefreshing = false;
        onRefreshed(false);
      }
    } else {
      // Queue until ongoing refresh completes
      return new Promise((resolve, reject) => {
        refreshSubscribers.push((success) => {
          if (success !== false) {
            resolve(request(endpoint, options));
          } else {
            reject(new Error('Session expired. Please log in again.'));
          }
        });
      });
    }
  }

  if (!response.ok) {
    let errMessage = `Error ${response.status}: ${response.statusText}`;
    try {
      const errJson = await response.json();
      errMessage = errJson.detail || errJson.message || errJson.title || errMessage;
    } catch (parseErr) {
      console.warn(`[api] Non-JSON error body on ${endpoint} (${response.status}):`, parseErr?.message || parseErr);
    }
    throw new Error(errMessage);
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return response.json();
  }
  return null;
}

export const api = {
  // Auth
  login: async (email, password) => {
    const res = await request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    if (res?.accessToken) setAuthToken(res.accessToken);
    if (res?.refreshToken) setRefreshToken(res.refreshToken);
    return res;
  },
  logout: async () => {
    try {
      await request('/auth/logout', { method: 'POST' });
    } finally {
      setAuthToken('');
      setRefreshToken('');
    }
  },
  getCurrentUser: () => request('/auth/me'),

  // Tables
  getTables: () => request('/tables'),
  getTable: (id) => request(`/tables/${id}`),
  overrideTableStatus: (id, status, reason) => request(`/tables/${id}/status`, { method: 'PUT', body: JSON.stringify({ status, reason }) }),
  seatParty: (id, partySize, assignedWaiterId) => request(`/tables/${id}/seat`, { method: 'POST', body: JSON.stringify({ partySize, assignedWaiterId }) }),
  attendTable: (id) => request(`/tables/${id}/attend`, { method: 'POST' }),
  assignWaiter: (id, waiterId) => request(`/tables/${id}/waiter`, { method: 'PUT', body: JSON.stringify({ waiterId }) }),

  // Orders
  getOrders: () => request('/orders'),
  createOrder: (data) => request('/orders', { method: 'POST', body: JSON.stringify(data) }),
  updateOrderStatus: (id, status) => request(`/orders/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),
  deliverOrder: (id) => request(`/orders/${id}/deliver`, { method: 'POST' }),
  getDelayedOrders: () => request('/orders/delayed'),

  // Kitchen
  getKitchenQueue: (stationId) => request(`/kitchen/queue${stationId ? `?stationId=${stationId}` : ''}`),
  getKitchenStations: () => request('/kitchen/stations'),
  startOrderPrep: (id) => request(`/kitchen/orders/${id}/start`, { method: 'PUT' }),
  markOrderReady: (id) => request(`/kitchen/orders/${id}/ready`, { method: 'PUT' }),
  updateOrderItemStatus: (id, status) => request(`/kitchen/items/${id}/status`, { method: 'PUT', body: JSON.stringify({ status }) }),

  // Alerts
  getActiveAlerts: () => request('/alerts'),
  getAllAlerts: () => request('/alerts/all'),
  acknowledgeAlert: (id) => request(`/alerts/${id}/acknowledge`, { method: 'PUT' }),
  resolveAlert: (id) => request(`/alerts/${id}/resolve`, { method: 'PUT' }),
  markFalseAlarm: (id, comment) => request(`/alerts/${id}/false-alarm`, { method: 'PUT', body: JSON.stringify({ comment }) }),

  // Tasks
  getTasks: () => request('/tasks'),
  createTask: (data) => request('/tasks', { method: 'POST', body: JSON.stringify(data) }),
  assignTask: (id, employeeId) => request(`/tasks/${id}/assign`, { method: 'PUT', body: JSON.stringify({ employeeId }) }),
  startTask: (id) => request(`/tasks/${id}/start`, { method: 'PUT' }),
  completeTask: (id) => request(`/tasks/${id}/complete`, { method: 'PUT' }),

  // Service Requests
  getServiceRequests: () => request('/service-requests'),
  acknowledgeServiceRequest: (id) => request(`/service-requests/${id}/acknowledge`, { method: 'PUT' }),
  completeServiceRequest: (id) => request(`/service-requests/${id}/complete`, { method: 'PUT' }),

  // Employees & Workload
  getEmployees: () => request('/employees'),
  getWorkload: () => request('/employees/workload'),

  // Cameras & Zones
  getCameras: () => request('/cameras'),
  getZones: (cameraId) => request(`/cameras/${cameraId}/zones`),
  createZone: (cameraId, zone) => request(`/cameras/${cameraId}/zones`, { method: 'POST', body: JSON.stringify(zone) }),
  setCameraMaintenance: (cameraId, enabled) => request(`/cameras/${cameraId}/maintenance?enabled=${enabled}`, { method: 'PUT' }),

  // Analytics
  getAnalyticsOverview: (timeframe = 'daily') => request(`/analytics/overview?timeframe=${timeframe}`),
  getCustomerFeedback: () => request('/analytics/feedback'),

  // AI Events
  ingestAiEvent: (envelope) => request('/ai/events', { method: 'POST', body: JSON.stringify(envelope) }),
  getAiEvents: () => request('/ai/events'),

  // Public QR Portal
  getQrStatus: (token) => request(`/public/qr/${token}/status`),
  callWaiterFromQr: (token, requestType) => request(`/public/qr/${token}/call-waiter`, { method: 'POST', body: JSON.stringify({ requestType }) }),
  submitFeedback: (feedback) => request('/public/feedback', { method: 'POST', body: JSON.stringify(feedback) }),
};
