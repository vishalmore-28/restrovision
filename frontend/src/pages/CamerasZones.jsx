import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import {
  Camera,
  Eye,
  Activity,
  CheckCircle2,
  AlertTriangle,
  Layers,
  Shield,
  Video,
  Radio,
  Cpu,
} from 'lucide-react';

const INITIAL_CAMERAS = [
  { cameraId: 1, name: 'CAM-01: Main Dining', targetFps: 24.0, edgeNodeId: 'edge-pune-01' },
  { cameraId: 2, name: 'CAM-02: Bar & Reception', targetFps: 24.0, edgeNodeId: 'edge-pune-01' },
];

const INITIAL_EVENTS = [
  { aiEventPk: 1, eventType: 'TABLE_STATE_CHANGED', processingStatus: 'APPLIED', tableId: 1, confidence: 0.984, eventUid: '01HC7R9V44A68B0011C54FE891' },
  { aiEventPk: 2, eventType: 'PERSON_DETECTED', processingStatus: 'APPLIED', tableId: 3, confidence: 0.942, eventUid: '01HC7R9P22B89A0022D43FE902' },
  { aiEventPk: 3, eventType: 'GESTURE_DETECTED', processingStatus: 'PENDING', tableId: 1, confidence: 0.891, eventUid: '01HC7R9M11C12C0033E21FE714' },
];

export default function CamerasZones() {
  const [cameras, setCameras] = useState(INITIAL_CAMERAS);
  const [selectedCam, setSelectedCam] = useState(INITIAL_CAMERAS[0]);
  const [zones, setZones] = useState([{ zoneId: 1 }, { zoneId: 2 }]);
  const [recentEvents, setRecentEvents] = useState(INITIAL_EVENTS);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.getCameras()
      .then((list) => {
        if (Array.isArray(list) && list.length > 0) {
          setCameras(list);
          setSelectedCam(list[0]);
          loadZones(list[0].cameraId);
        }
      })
      .catch((err) => console.warn('[CamerasZones] Failed to load cameras:', err?.message || err));

    loadRecentEvents();

    const unsub = wsService.subscribe('/topic/restaurants/1/alerts', () => {
      loadRecentEvents();
    });

    return () => unsub();
  }, []);

  const loadZones = (camId) => {
    api.getZones(camId)
      .then((data) => {
        if (Array.isArray(data)) setZones(data);
      })
      .catch((err) => console.warn('[CamerasZones] Failed to load zones for camera', camId, ':', err?.message || err));
  };

  const loadRecentEvents = () => {
    api.getAiEvents()
      .then((data) => setRecentEvents(Array.isArray(data) ? data : []))
      .catch((err) => console.warn('[CamerasZones] Failed to load AI events:', err?.message || err))
      .finally(() => setLoading(false));
  };

  const selectCamera = (cam) => {
    setSelectedCam(cam);
    loadZones(cam.cameraId);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-xl font-bold tracking-tight flex items-center gap-2.5" style={{ color: 'var(--text-primary)' }}>
          CCTV Feeds &amp; Edge Vision
          <span
            className="text-xs font-semibold px-2 py-0.5 rounded border"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
              color: 'var(--text-secondary)',
            }}
          >
            Vision Feeds Active
          </span>
        </h1>
        <p className="text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
          Real-time CCTV camera streams, configured polygon ROI zones, and edge node vision pipeline status. Edge nodes authenticate with HMAC-SHA256 signatures.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left: Video Preview & Zone Overlays */}
        <div className="lg:col-span-2 space-y-4">
          {/* Camera Tabs */}
          <div className="flex items-center gap-2">
            {cameras.map((c) => (
              <button
                key={c.cameraId}
                onClick={() => selectCamera(c)}
                className="px-3.5 py-2 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center gap-2 border whitespace-nowrap"
                style={{
                  backgroundColor: selectedCam?.cameraId === c.cameraId ? 'var(--btn-primary)' : 'var(--bg-card)',
                  borderColor: selectedCam?.cameraId === c.cameraId ? 'var(--btn-primary)' : 'var(--border)',
                  color: selectedCam?.cameraId === c.cameraId ? 'var(--btn-primary-text)' : 'var(--text-secondary)',
                }}
              >
                <Video className="w-3.5 h-3.5" />
                {c.name.split(':')[0]}
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              </button>
            ))}
          </div>

          {/* CCTV Feed Canvas */}
          <div className="bg-neutral-950 rounded-2xl border border-neutral-800 overflow-hidden relative shadow-lg aspect-video flex items-center justify-center">
            <div className="absolute inset-0 p-5 flex flex-col justify-between">
              {/* Top OSD info */}
              <div className="flex items-center justify-between text-xs bg-neutral-900 p-2 rounded-lg border border-neutral-800 text-neutral-300">
                <span className="flex items-center gap-2 font-bold" style={{ color: 'var(--status-available)' }}>
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
                  Stream: {selectedCam?.name || 'CAM-01'}
                </span>
                <span>FPS: <span className="font-mono tabular">{selectedCam?.targetFps || 24.0}</span> | 1080p RTSP</span>
                <span>Node: <span className="font-mono">{selectedCam?.edgeNodeId || 'edge-pune-01'}</span></span>
              </div>

              {/* Graphical Table Zones & Detection Bounding Boxes */}
              <div className="relative w-full h-4/5 flex items-center justify-center gap-8">
                {/* Zone T01 */}
                <div
                  className="border-2 rounded-xl p-3 text-center relative w-44 h-28 flex flex-col items-center justify-center"
                  style={{
                    borderColor: 'var(--status-available)',
                    backgroundColor: 'var(--status-available-bg)',
                  }}
                >
                  <span
                    className="absolute -top-2.5 left-2.5 text-white text-[10px] font-bold px-1.5 py-0.5 rounded"
                    style={{ backgroundColor: 'var(--status-available)' }}
                  >
                    Zone: Table T01
                  </span>
                  <div
                    className="w-9 h-9 rounded-full border flex items-center justify-center text-xs font-bold mb-1"
                    style={{
                      borderColor: 'var(--status-available)',
                      color: 'var(--status-available)',
                    }}
                  >
                    ROI #1
                  </div>
                  <span className="text-[10px]" style={{ color: 'var(--status-available)' }}>
                    YOLO: 4 Patrons (98.4%)
                  </span>
                </div>

                {/* Zone T02 */}
                <div
                  className="border-2 border-dashed rounded-xl p-3 text-center relative w-44 h-28 flex flex-col items-center justify-center"
                  style={{
                    borderColor: 'var(--status-occupied)',
                    backgroundColor: 'var(--status-occupied-bg)',
                  }}
                >
                  <span
                    className="absolute -top-2.5 left-2.5 text-white text-[10px] font-bold px-1.5 py-0.5 rounded"
                    style={{ backgroundColor: 'var(--status-occupied)' }}
                  >
                    Zone: Table T02
                  </span>
                  <div
                    className="w-9 h-9 rounded-full border flex items-center justify-center text-xs font-bold mb-1"
                    style={{
                      borderColor: 'var(--status-occupied)',
                      color: 'var(--status-occupied)',
                    }}
                  >
                    ROI #2
                  </div>
                  <span className="text-[10px]" style={{ color: 'var(--status-occupied)' }}>
                    Empty Glasses Detected
                  </span>
                </div>
              </div>

              {/* Bottom OSD */}
              <div className="text-[11px] text-neutral-400 bg-neutral-900 p-2 rounded-lg flex items-center justify-between border border-neutral-800">
                <span className="flex items-center gap-1.5" style={{ color: 'var(--status-available)' }}>
                  <Shield className="w-3.5 h-3.5" /> Edge Privacy Filter Active (No face storage)
                </span>
                <span>Active Zones: {zones.length} configured</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Edge Node Status & Live AI Event Feed */}
        <div className="space-y-4">
          <div
            className="rounded-2xl p-5 border shadow-sm space-y-3"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <h3 className="text-sm font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Cpu className="w-4 h-4" />
              Edge Node Ingestion Gateway
            </h3>
            <div className="space-y-2 text-xs">
              <div
                className="flex justify-between p-2 rounded-xl border"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                }}
              >
                <span style={{ color: 'var(--text-secondary)' }}>Edge Device ID:</span>
                <span className="font-mono font-bold" style={{ color: 'var(--text-primary)' }}>
                  {selectedCam?.edgeNodeId || 'edge-pune-01'}
                </span>
              </div>
              <div
                className="flex justify-between p-2 rounded-xl border"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                }}
              >
                <span style={{ color: 'var(--text-secondary)' }}>Security Gate:</span>
                <span className="font-bold flex items-center gap-1" style={{ color: 'var(--status-available)' }}>
                  <CheckCircle2 className="w-3.5 h-3.5" /> HMAC-SHA256 Verified
                </span>
              </div>
              <div
                className="flex justify-between p-2 rounded-xl border"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                }}
              >
                <span style={{ color: 'var(--text-secondary)' }}>Protocol:</span>
                <span className="text-[11px]" style={{ color: 'var(--text-primary)' }}>POST /api/v1/ai/events</span>
              </div>
              <div
                className="flex justify-between p-2 rounded-xl border"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                }}
              >
                <span style={{ color: 'var(--text-secondary)' }}>Confidence Gate:</span>
                <span style={{ color: 'var(--text-primary)' }}>&gt;= <span className="font-mono tabular">0.70</span></span>
              </div>
            </div>
          </div>

          {/* Live Ingested AI Events Feed */}
          <div
            className="rounded-2xl p-4 border shadow-sm space-y-3"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-semibold flex items-center gap-1.5" style={{ color: 'var(--text-primary)' }}>
                <Radio className="w-3.5 h-3.5 animate-pulse text-emerald-500" />
                Live Ingested Edge Events
              </h4>
              <button
                onClick={loadRecentEvents}
                className="text-xs font-semibold hover:opacity-80 transition-opacity whitespace-nowrap"
                style={{ color: 'var(--text-secondary)' }}
              >
                Refresh
              </button>
            </div>

            <div className="space-y-2 max-h-72 overflow-y-auto text-xs">
              {recentEvents.length === 0 ? (
                <p className="py-6 text-center" style={{ color: 'var(--text-muted)' }}>
                  No AI events recorded yet for this session.
                </p>
              ) : (
                recentEvents.slice(0, 10).map((evt) => (
                  <div
                    key={evt.aiEventPk || evt.eventUid}
                    className="p-2.5 rounded-xl border space-y-1 transition-colors duration-150"
                    style={{
                      backgroundColor: 'var(--bg-main)',
                      borderColor: 'var(--border)',
                    }}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold" style={{ color: 'var(--text-primary)' }}>
                        {evt.eventType}
                      </span>
                      <span
                        className="px-1.5 py-0.5 rounded text-[10px] font-bold border"
                        style={{
                          backgroundColor: evt.processingStatus === 'APPLIED' ? 'var(--status-available-bg)' : 'var(--status-occupied-bg)',
                          borderColor: evt.processingStatus === 'APPLIED' ? 'var(--status-available-border)' : 'var(--status-occupied-border)',
                          color: evt.processingStatus === 'APPLIED' ? 'var(--status-available)' : 'var(--status-occupied)',
                        }}
                      >
                        {evt.processingStatus}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                      <span>Table: {evt.tableId ? `T${evt.tableId}` : 'Zone ' + (evt.zoneId || 'N/A')}</span>
                      <span>Conf: <span className="font-mono tabular">{evt.confidence ? Math.round(evt.confidence * 100) + '%' : 'N/A'}</span></span>
                    </div>
                    <div className="text-[10px] truncate" style={{ color: 'var(--text-muted)' }}>
                      UID: <span className="font-mono">{evt.eventUid}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
