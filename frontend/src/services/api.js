const API_BASE = '/api/v1';

export function getAuthToken() {
  return localStorage.getItem('restrovision_token') || '';
}

export function setAuthToken(token) {
  if (token) {
    localStorage.setItem('restrovision_token', token);
  } else {
    localStorage.removeItem('restrovision_token');
  }
}

export function getRefreshToken() {
  return localStorage.getItem('restrovision_refresh_token') || '';
}

export function setRefreshToken(refreshToken) {
  if (refreshToken) {
    localStorage.setItem('restrovision_refresh_token', refreshToken);
  } else {
    localStorage.removeItem('restrovision_refresh_token');
  }
}

let isRefreshing = false;
let refreshSubscribers = [];

function onRefreshed(token) {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

async function request(endpoint, options = {}) {
  const token = getAuthToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  // Handle Token Expiry & Automatic Refresh (§3.4)
  if (response.status === 401 && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/refresh')) {
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      if (!isRefreshing) {
        isRefreshing = true;
        try {
          const refreshRes = await fetch(`${API_BASE}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
          });

          if (refreshRes.ok) {
            const data = await refreshRes.json();
            setAuthToken(data.accessToken);
            isRefreshing = false;
            onRefreshed(data.accessToken);

            // Retry original request with new token
            return request(endpoint, {
              ...options,
              headers: {
                ...(options.headers || {}),
                Authorization: `Bearer ${data.accessToken}`,
              },
            });
          } else {
            setAuthToken(null);
            setRefreshToken(null);
            isRefreshing = false;
          }
        } catch (_) {
          setAuthToken(null);
          setRefreshToken(null);
          isRefreshing = false;
        }
      } else {
        // Queue until ongoing refresh completes
        return new Promise((resolve) => {
          refreshSubscribers.push((newToken) => {
            resolve(request(endpoint, {
              ...options,
              headers: {
                ...(options.headers || {}),
                Authorization: `Bearer ${newToken}`,
              },
            }));
          });
        });
      }
    }
  }

  if (!response.ok) {
    let errMessage = `Error ${response.status}: ${response.statusText}`;
    try {
      const errJson = await response.json();
      errMessage = errJson.detail || errJson.message || errJson.title || errMessage;
    } catch (_) {}
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
  login: (email, password) => request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
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
