import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Storage Mock implementation for Node / JSDOM compatibility
const createStorageMock = () => {
  let store = {};
  return {
    getItem: vi.fn((key) => (key in store ? store[key] : null)),
    setItem: vi.fn((key, value) => {
      store[key] = String(value);
    }),
    removeItem: vi.fn((key) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn((i) => Object.keys(store)[i] || null),
  };
};

const localMock = createStorageMock();
const sessionMock = createStorageMock();

Object.defineProperty(window, 'localStorage', {
  value: localMock,
  configurable: true,
  writable: true,
});
Object.defineProperty(globalThis, 'localStorage', {
  value: localMock,
  configurable: true,
  writable: true,
});

Object.defineProperty(window, 'sessionStorage', {
  value: sessionMock,
  configurable: true,
  writable: true,
});
Object.defineProperty(globalThis, 'sessionStorage', {
  value: sessionMock,
  configurable: true,
  writable: true,
});

// Automatically cleanup DOM and mocks after each test
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

// Suppress known React 18 act(...) warnings from background timers and microtasks
const originalError = console.error;
const originalWarn = console.warn;
console.error = (...args) => {
  if (typeof args[0] === 'string' && args[0].includes('was not wrapped in act')) return;
  originalError(...args);
};
console.warn = (...args) => {
  if (typeof args[0] === 'string' && args[0].includes('was not wrapped in act')) return;
  originalWarn(...args);
};

// Mock ResizeObserver for Lucide icons and layout components
globalThis.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Mock IntersectionObserver
globalThis.IntersectionObserver = class IntersectionObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

// Mock window.scrollTo
window.scrollTo = vi.fn();

// Mock HTMLCanvasElement.prototype.getContext for chart libraries
HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
  fillRect: vi.fn(),
  clearRect: vi.fn(),
  getImageData: vi.fn(() => ({ data: new Array(4) })),
  putImageData: vi.fn(),
  createImageData: vi.fn(() => []),
  setTransform: vi.fn(),
  drawImage: vi.fn(),
  save: vi.fn(),
  fillText: vi.fn(),
  restore: vi.fn(),
  beginPath: vi.fn(),
  moveTo: vi.fn(),
  lineTo: vi.fn(),
  closePath: vi.fn(),
  stroke: vi.fn(),
  translate: vi.fn(),
  scale: vi.fn(),
  rotate: vi.fn(),
  arc: vi.fn(),
  fill: vi.fn(),
  measureText: vi.fn(() => ({ width: 0 })),
  setLineDash: vi.fn(),
  getLineDash: vi.fn(() => []),
  transform: vi.fn(),
  rect: vi.fn(),
  clip: vi.fn(),
}));

// Mock Web Audio API for soundService
class MockAudioContext {
  constructor() {
    this.currentTime = 0;
    this.state = 'running';
    this.destination = {};
  }
  resume() {
    return Promise.resolve();
  }
  createGain() {
    return {
      gain: {
        setValueAtTime: vi.fn(),
        exponentialRampToValueAtTime: vi.fn(),
        linearRampToValueAtTime: vi.fn(),
      },
      connect: vi.fn(),
    };
  }
  createOscillator() {
    return {
      type: 'sine',
      frequency: {
        setValueAtTime: vi.fn(),
        linearRampToValueAtTime: vi.fn(),
      },
      connect: vi.fn(),
      start: vi.fn(),
      stop: vi.fn(),
    };
  }
}
window.AudioContext = MockAudioContext;
window.webkitAudioContext = MockAudioContext;

