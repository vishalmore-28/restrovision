import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  api,
  getCsrfToken,
  getAuthToken,
  setAuthToken,
  getRefreshToken,
  setRefreshToken,
} from '../services/api';

describe('API Service & CSRF / Auth Interceptors (BUG-021)', () => {
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    vi.clearAllMocks();
    // Clear cookies
    document.cookie.split(';').forEach((c) => {
      document.cookie = c
        .replace(/^ +/, '')
        .replace(/=.*/, `=;expires=${new Date(0).toUTCString()};path=/`);
    });
    localStorage.clear();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe('CSRF Token Extraction (getCsrfToken)', () => {
    it('returns empty string when no restro_csrf_token cookie is present', () => {
      document.cookie = 'other_cookie=value123; path=/';
      expect(getCsrfToken()).toBe('');
    });

    it('extracts and decodes restro_csrf_token correctly from document.cookie', () => {
      document.cookie = 'restro_csrf_token=csrf-secret-abc-xyz; path=/';
      expect(getCsrfToken()).toBe('csrf-secret-abc-xyz');
    });

    it('extracts token when multiple cookies exist in document.cookie', () => {
      document.cookie = 'theme=dark; path=/';
      document.cookie = 'restro_csrf_token=multi-token-456; path=/';
      document.cookie = 'user_pref=grid; path=/';
      expect(getCsrfToken()).toBe('multi-token-456');
    });

    it('decodes URI encoded characters in restro_csrf_token', () => {
      document.cookie = 'restro_csrf_token=abc%20123%2Bxyz; path=/';
      expect(getCsrfToken()).toBe('abc 123+xyz');
    });
  });

  describe('Legacy Token Stubs (Security Isolation)', () => {
    it('getAuthToken returns empty string (isolated in HttpOnly cookies)', () => {
      expect(getAuthToken()).toBe('');
    });

    it('getRefreshToken returns empty string (isolated in HttpOnly cookies)', () => {
      expect(getRefreshToken()).toBe('');
    });

    it('setAuthToken purges legacy localStorage token safely', () => {
      localStorage.setItem('restrovision_token', 'legacy-jwt');
      setAuthToken();
      expect(localStorage.getItem('restrovision_token')).toBeNull();
    });

    it('setRefreshToken purges legacy localStorage refresh token safely', () => {
      localStorage.setItem('restrovision_refresh_token', 'legacy-refresh');
      setRefreshToken();
      expect(localStorage.getItem('restrovision_refresh_token')).toBeNull();
    });
  });

  describe('Request Dispatching & CSRF Header Injection', () => {
    it('attaches X-CSRF-TOKEN header and credentials:include on mutating POST requests', async () => {
      document.cookie = 'restro_csrf_token=valid-csrf-token; path=/';
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ success: true, user: { userId: 1 } }),
      });
      globalThis.fetch = mockFetch;

      await api.login('owner@restrovision.local', 'Admin@123');

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toBe('/api/v1/auth/login');
      expect(options.method).toBe('POST');
      expect(options.credentials).toBe('include');
      expect(options.headers['X-CSRF-TOKEN']).toBe('valid-csrf-token');
      expect(options.headers['Content-Type']).toBe('application/json');
    });

    it('attaches X-CSRF-TOKEN on PUT requests (e.g. overrideTableStatus)', async () => {
      document.cookie = 'restro_csrf_token=csrf-put-token; path=/';
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ tableId: 1, status: 'CLEANING' }),
      });
      globalThis.fetch = mockFetch;

      await api.overrideTableStatus(1, 'CLEANING', 'Spill cleaned');

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toBe('/api/v1/tables/1/status');
      expect(options.method).toBe('PUT');
      expect(options.credentials).toBe('include');
      expect(options.headers['X-CSRF-TOKEN']).toBe('csrf-put-token');
    });

    it('does NOT attach X-CSRF-TOKEN on non-mutating GET requests', async () => {
      document.cookie = 'restro_csrf_token=csrf-token-should-not-attach; path=/';
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => [{ tableId: 1 }, { tableId: 2 }],
      });
      globalThis.fetch = mockFetch;

      await api.getTables();

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toBe('/api/v1/tables');
      expect(options.credentials).toBe('include');
      expect(options.headers['X-CSRF-TOKEN']).toBeUndefined();
    });

    it('dispatches public QR portal requests with correct endpoints', async () => {
      document.cookie = 'restro_csrf_token=qr-csrf-token; path=/';
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ message: 'Waiter called' }),
      });
      globalThis.fetch = mockFetch;

      await api.callWaiterFromQr('qr-tok-table-01', 'WATER');

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, options] = mockFetch.mock.calls[0];
      expect(url).toBe('/api/v1/public/qr/qr-tok-table-01/call-waiter');
      expect(options.method).toBe('POST');
      expect(options.headers['X-CSRF-TOKEN']).toBe('qr-csrf-token');
      expect(JSON.parse(options.body)).toEqual({ requestType: 'WATER' });
    });
  });

  describe('Error Handling & HTTP Status Parsing', () => {
    it('throws error with detail message from JSON response when request fails', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        statusText: 'Bad Request',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ detail: 'Invalid party size specified.' }),
      });
      globalThis.fetch = mockFetch;

      await expect(api.seatParty(1, 0, 2)).rejects.toThrow('Invalid party size specified.');
    });

    it('falls back to status code and statusText when error body is not JSON', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 502,
        statusText: 'Bad Gateway',
        headers: new Headers({ 'content-type': 'text/html' }),
        json: async () => {
          throw new Error('Not JSON');
        },
      });
      globalThis.fetch = mockFetch;

      await expect(api.getTables()).rejects.toThrow('Error 502: Bad Gateway');
    });
  });

  describe('Automatic Refresh on 401', () => {
    it('retries original request after successful token refresh on 401', async () => {
      document.cookie = 'restro_csrf_token=refresh-csrf; path=/';

      let callCount = 0;
      const mockFetch = vi.fn().mockImplementation((url, options) => {
        callCount++;
        // 1. Initial request to /tables fails with 401
        if (url === '/api/v1/tables' && callCount === 1) {
          return Promise.resolve({
            ok: false,
            status: 401,
            statusText: 'Unauthorized',
            headers: new Headers(),
          });
        }
        // 2. Automatic refresh call succeeds
        if (url === '/api/v1/auth/refresh') {
          return Promise.resolve({
            ok: true,
            status: 200,
            headers: new Headers({ 'content-type': 'application/json' }),
            json: async () => ({ refreshed: true }),
          });
        }
        // 3. Retried request to /tables succeeds
        if (url === '/api/v1/tables' && callCount === 3) {
          return Promise.resolve({
            ok: true,
            status: 200,
            headers: new Headers({ 'content-type': 'application/json' }),
            json: async () => [{ tableId: 1, name: 'Table 1' }],
          });
        }
        return Promise.reject(new Error(`Unexpected call to ${url}`));
      });
      globalThis.fetch = mockFetch;

      const result = await api.getTables();
      expect(result).toEqual([{ tableId: 1, name: 'Table 1' }]);
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/auth/refresh',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
        })
      );
    });

    it('does not trigger automatic refresh loop when /auth/login returns 401', async () => {
      const mockFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ detail: 'Invalid email or password.' }),
      });
      globalThis.fetch = mockFetch;

      await expect(api.login('bad@user.com', 'wrong')).rejects.toThrow('Invalid email or password.');
      // Should NOT have called /auth/refresh
      expect(mockFetch).not.toHaveBeenCalledWith('/api/v1/auth/refresh', expect.anything());
    });
  });
});
