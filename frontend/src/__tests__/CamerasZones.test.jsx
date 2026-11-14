import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import CamerasZones from '../pages/CamerasZones';

// Mock API service
vi.mock('../services/api', () => ({
  api: {
    getCameras: vi.fn(),
    getZones: vi.fn(),
    getAiEvents: vi.fn(),
    overrideTableStatus: vi.fn(),
    seatParty: vi.fn(),
  },
}));

// Mock WebSocket service
vi.mock('../services/websocket', () => ({
  wsService: {
    subscribe: vi.fn(() => vi.fn()),
  },
}));

import { api } from '../services/api';
import { wsService } from '../services/websocket';

describe('CamerasZones Component', () => {
  const mockCameras = [
    { cameraId: 1, name: 'CAM-01: Main Dining Floor', targetFps: 24.0, edgeNodeId: 'edge-pune-01' },
    { cameraId: 2, name: 'CAM-02: Bar & Reception', targetFps: 24.0, edgeNodeId: 'edge-pune-01' },
  ];

  const mockZones = [
    { zoneId: 1, zoneName: 'T01 Area', xMin: 10, yMin: 10, xMax: 100, yMax: 100 },
    { zoneId: 2, zoneName: 'T02 Area', xMin: 120, yMin: 10, xMax: 200, yMax: 100 },
  ];

  const mockAiEvents = [
    {
      aiEventPk: 101,
      eventType: 'TABLE_STATE_CHANGED',
      processingStatus: 'APPLIED',
      tableId: 1,
      confidence: 0.984,
      eventUid: '01HC7R9V44A68B0011C54FE891',
    },
    {
      aiEventPk: 102,
      eventType: 'PERSON_DETECTED',
      processingStatus: 'APPLIED',
      tableId: 3,
      confidence: 0.942,
      eventUid: '01HC7R9P22B89A0022D43FE902',
    },
  ];

  let mockMediaStream;
  let mockGetTracks;

  beforeEach(() => {
    vi.clearAllMocks();

    api.getCameras.mockResolvedValue(mockCameras);
    api.getZones.mockResolvedValue(mockZones);
    api.getAiEvents.mockResolvedValue(mockAiEvents);
    api.overrideTableStatus.mockResolvedValue({ tableId: 1, status: 'OCCUPIED' });

    mockGetTracks = vi.fn(() => [{ stop: vi.fn() }]);
    mockMediaStream = {
      getTracks: mockGetTracks,
    };

    // Mock HTMLMediaElement play
    HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue();

    // Mock navigator.mediaDevices
    Object.defineProperty(navigator, 'mediaDevices', {
      writable: true,
      value: {
        getUserMedia: vi.fn().mockResolvedValue(mockMediaStream),
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders CCTV header and Vision Feeds Active badge', async () => {
    render(<CamerasZones />);

    expect(screen.getByText(/CCTV Feeds & Edge Vision/i)).toBeInTheDocument();
    expect(screen.getByText(/Vision Feeds Active/i)).toBeInTheDocument();
    expect(screen.getByText(/Connect Device Camera/i)).toBeInTheDocument();
  });

  it('loads cameras, zones, and AI events on mount', async () => {
    render(<CamerasZones />);

    await waitFor(() => {
      expect(api.getCameras).toHaveBeenCalledTimes(1);
      expect(api.getZones).toHaveBeenCalledWith(1);
      expect(api.getAiEvents).toHaveBeenCalledTimes(1);
    });

    // Camera tabs rendered
    expect(screen.getByRole('button', { name: /CAM-01/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /CAM-02/i })).toBeInTheDocument();

    // Table ROI zones rendered
    expect(screen.getByText(/Zone: Table T01/i)).toBeInTheDocument();
    expect(screen.getByText(/Zone: Table T02/i)).toBeInTheDocument();

    // Ingestion card details rendered
    expect(screen.getByText(/Edge Node Ingestion Gateway/i)).toBeInTheDocument();
    expect(screen.getByText(/HMAC-SHA256 Verified/i)).toBeInTheDocument();

    // Events rendered
    expect(screen.getByText(/01HC7R9V44A68B0011C54FE891/i)).toBeInTheDocument();
  });

  it('switches cameras when clicking another camera tab', async () => {
    render(<CamerasZones />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /CAM-02/i })).toBeInTheDocument();
    });

    const cam2Btn = screen.getByRole('button', { name: /CAM-02/i });
    fireEvent.click(cam2Btn);

    await waitFor(() => {
      expect(api.getZones).toHaveBeenCalledWith(2);
      expect(screen.getByText(/Stream: CAM-02: Bar & Reception/i)).toBeInTheDocument();
    });
  });

  it('activates live device webcam when clicking Connect Device Camera', async () => {
    render(<CamerasZones />);

    const connectBtn = screen.getByText(/Connect Device Camera/i);
    fireEvent.click(connectBtn);

    await waitFor(() => {
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({
        video: { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' },
        audio: false,
      });
      expect(screen.getByText(/Disconnect Device Camera/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /CAM-LIVE/i })).toBeInTheDocument();
      expect(screen.getByText(/Stream: CAM-LIVE: Device Camera/i)).toBeInTheDocument();
    });

    // Disconnect webcam
    const disconnectBtn = screen.getByText(/Disconnect Device Camera/i);
    fireEvent.click(disconnectBtn);

    await waitFor(() => {
      expect(mockGetTracks).toHaveBeenCalled();
      expect(screen.getByText(/Connect Device Camera/i)).toBeInTheDocument();
    });
  });

  it('shows error banner when camera permission is denied', async () => {
    const permError = new Error('Permission denied');
    permError.name = 'NotAllowedError';
    navigator.mediaDevices.getUserMedia.mockRejectedValue(permError);

    render(<CamerasZones />);

    const connectBtn = screen.getByText(/Connect Device Camera/i);
    fireEvent.click(connectBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Camera permission was denied. Please allow camera access in your browser./i)
      ).toBeInTheDocument();
    });

    // Dismiss error banner
    const dismissBtn = screen.getByText(/Dismiss/i);
    fireEvent.click(dismissBtn);

    expect(
      screen.queryByText(/Camera permission was denied. Please allow camera access in your browser./i)
    ).not.toBeInTheDocument();
  });

  it('triggers AI detection of seated party and synchronizes table state to OCCUPIED', async () => {
    render(<CamerasZones />);

    const seatedBtn = screen.getByText(/AI Detect: Seated Patrons/i);
    fireEvent.click(seatedBtn);

    await waitFor(() => {
      expect(api.overrideTableStatus).toHaveBeenCalledWith(
        1,
        'OCCUPIED',
        expect.stringContaining('AI Vision')
      );
      expect(screen.getByText(/Table T01 confirmed OCCUPIED/i)).toBeInTheDocument();
    });
  });

  it('triggers AI detection of table vacated and updates table state to DIRTY', async () => {
    render(<CamerasZones />);

    const vacatedBtn = screen.getByText(/AI Detect: Table Vacated/i);
    fireEvent.click(vacatedBtn);

    await waitFor(() => {
      expect(api.overrideTableStatus).toHaveBeenCalledWith(
        1,
        'DIRTY',
        expect.stringContaining('AI Vision')
      );
      expect(screen.getByText(/Table T01 VACATED/i)).toBeInTheDocument();
    });
  });

  it('toggles continuous AI Auto-Pilot loop between enabled and paused', async () => {
    render(<CamerasZones />);

    const autoPilotBtn = screen.getByText(/ENABLED/i);
    expect(autoPilotBtn).toBeInTheDocument();

    fireEvent.click(autoPilotBtn);
    expect(screen.getByText(/PAUSED/i)).toBeInTheDocument();

    fireEvent.click(screen.getByText(/PAUSED/i));
    expect(screen.getByText(/ENABLED/i)).toBeInTheDocument();
  });
});
