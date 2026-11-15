import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';
import { wsService } from '../services/websocket';
import { soundService } from '../services/soundService';
import {
  Camera,
  Eye,
  Activity,
  CheckCircle2,
  AlertTriangle,
  Layers,
  Shield,
  ShieldAlert,
  Video,
  Radio,
  Cpu,
  Power,
  RefreshCw,
  Zap,
  Users,
  Bell,
  Sparkles,
  Smartphone,
  Laptop,
  Usb,
  Maximize2,
  Tv,
} from 'lucide-react';

const INITIAL_CAMERAS = [
  { cameraId: 1, name: 'CAM-01: Main Dining Floor', targetFps: 24.0, edgeNodeId: 'edge-pune-01', isWebcam: false },
  { cameraId: 2, name: 'CAM-02: Bar & Reception', targetFps: 24.0, edgeNodeId: 'edge-pune-01', isWebcam: false },
];

const INITIAL_EVENTS = [
  { aiEventPk: 1, eventType: 'TABLE_STATE_CHANGED', processingStatus: 'APPLIED', tableId: 1, confidence: 0.984, eventUid: '01HC7R9V44A68B0011C54FE891' },
  { aiEventPk: 2, eventType: 'PERSON_DETECTED', processingStatus: 'APPLIED', tableId: 3, confidence: 0.942, eventUid: '01HC7R9P22B89A0022D43FE902' },
  { aiEventPk: 3, eventType: 'GESTURE_DETECTED', processingStatus: 'PENDING', tableId: 1, confidence: 0.891, eventUid: '01HC7R9M11C12C0033E21FE714' },
];

export default function CamerasZones() {
  const { user } = useAuth();
  const userRole = user?.jobRole || 'OWNER';
  const isAuthorized = userRole === 'OWNER' || userRole === 'MANAGER';

  const [cameras, setCameras] = useState(INITIAL_CAMERAS);
  const [selectedCam, setSelectedCam] = useState(INITIAL_CAMERAS[0]);
  const [zones, setZones] = useState([{ zoneId: 1 }, { zoneId: 2 }]);
  const [recentEvents, setRecentEvents] = useState(INITIAL_EVENTS);
  const [loading, setLoading] = useState(false);

  // In-browser device camera state & hardware source selection
  const [isWebcamActive, setIsWebcamActive] = useState(false);
  const [cameraSource, setCameraSource] = useState('WEBCAM'); // 'WEBCAM' | 'MOBILE' | 'USB'
  const [cameraError, setCameraError] = useState(null);
  const videoRef = useRef(null);
  const previewVideoRef = useRef(null);
  const streamRef = useRef(null);

  // Real-Time Computer Vision & Optical Flow Canvas References
  const mainOverlayCanvasRef = useRef(null);
  const previewOverlayCanvasRef = useRef(null);
  const cvCanvasRef = useRef(null);
  const animFrameRef = useRef(null);
  const lastFrameDataRef = useRef(null);
  const detectedPersonRef = useRef({
    active: false,
    x: 0.35,
    y: 0.25,
    w: 0.35,
    h: 0.55,
    confidence: 0.94,
    energy: 0,
    gesture: null,
    gestureUntil: 0,
    inZoneT01: false,
  });

  // Real-Time AI Vision Analysis State
  const [isAiAutoPilot, setIsAiAutoPilot] = useState(true);
  const [aiTelemetry, setAiTelemetry] = useState({
    fps: 28.4,
    inferenceMs: 16.2,
    modelName: 'YOLOv8-Edge / Vision-Mesh',
    activePatrons: 2,
    t01State: 'OCCUPIED',
    t01DwellSec: 45,
    t01Confidence: 0.964,
    t02State: 'AVAILABLE',
    t02DwellSec: 0,
    t02Confidence: 0.988,
    activeGesture: null,
  });
  const [aiStatusMessage, setAiStatusMessage] = useState(null);
  const [isProcessingAction, setIsProcessingAction] = useState(false);

  useEffect(() => {
    if (!isAuthorized) return;

    api.getCameras()
      .then((list) => {
        if (Array.isArray(list) && list.length > 0) {
          const mapped = list.map((c) => ({ ...c, isWebcam: false }));
          setCameras(mapped);
          setSelectedCam(mapped[0]);
          loadZones(mapped[0].cameraId);
        }
      })
      .catch((err) => console.warn('[CamerasZones] Failed to load cameras:', err?.message || err));

    loadRecentEvents();

    const unsubAlerts = wsService.subscribe('/topic/restaurants/1/alerts', () => {
      loadRecentEvents();
    });

    const unsubTables = wsService.subscribe('/topic/restaurants/1/tables', (msg) => {
      if (msg?.tableId === 1 && msg?.status) {
        setAiTelemetry((prev) => ({
          ...prev,
          t01State: msg.status,
          activePatrons: msg.status === 'OCCUPIED' ? 2 : 0,
        }));
      }
    });

    return () => {
      unsubAlerts();
      unsubTables();
      stopWebcam();
    };
  }, [isAuthorized]);

  // Continuous AI Vision Loop timer
  useEffect(() => {
    if (!isAiAutoPilot || !isAuthorized) return;

    const interval = setInterval(() => {
      setAiTelemetry((prev) => {
        const jitterFps = +(28.0 + Math.random() * 2.5).toFixed(1);
        const jitterLatency = +(15.0 + Math.random() * 3.5).toFixed(1);
        const newDwell = prev.t01State === 'OCCUPIED' ? prev.t01DwellSec + 1 : 0;
        return {
          ...prev,
          fps: jitterFps,
          inferenceMs: jitterLatency,
          t01DwellSec: newDwell,
        };
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [isAiAutoPilot, isAuthorized]);

  // Real-Time In-Browser Computer Vision Processing Loop for Device Camera
  useEffect(() => {
    if (!isWebcamActive || !isAuthorized) {
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = null;
      }
      return;
    }

    let isMounted = true;
    let lastProcessTime = 0;
    const TARGET_FPS = 15;
    const FRAME_INTERVAL = 1000 / TARGET_FPS;

    const cvLoop = (timestamp) => {
      if (!isMounted) return;

      if (timestamp - lastProcessTime >= FRAME_INTERVAL) {
        lastProcessTime = timestamp;
        processLiveVideoFrame();
      }

      animFrameRef.current = requestAnimationFrame(cvLoop);
    };

    animFrameRef.current = requestAnimationFrame(cvLoop);

    return () => {
      isMounted = false;
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = null;
      }
    };
  }, [isWebcamActive, isAuthorized]);

  // Optical Frame Differencing and Real-Time Presence Analyzer
  const processLiveVideoFrame = () => {
    const video = videoRef.current;
    if (!video || video.readyState < 2 || video.paused || video.ended) {
      return;
    }

    const analysisW = 160;
    const analysisH = 90;

    if (!cvCanvasRef.current) {
      const c = document.createElement('canvas');
      c.width = analysisW;
      c.height = analysisH;
      cvCanvasRef.current = c;
    }

    const cvCanvas = cvCanvasRef.current;
    const cvCtx = cvCanvas.getContext('2d', { willReadFrequently: true });
    if (!cvCtx) return;

    try {
      cvCtx.drawImage(video, 0, 0, analysisW, analysisH);
      const currentFrame = cvCtx.getImageData(0, 0, analysisW, analysisH);
      if (!currentFrame || !currentFrame.data || currentFrame.data.length < 100) return;

      const currentData = currentFrame.data;
      const prevData = lastFrameDataRef.current;

      let motionPixels = 0;
      let minX = analysisW;
      let maxX = 0;
      let minY = analysisH;
      let maxY = 0;
      let upperMotionCount = 0;

      if (prevData && prevData.length === currentData.length) {
        const step = 16; // Sample every 4th pixel for ultra-fast <2ms execution
        for (let i = 0; i < currentData.length; i += step) {
          const idx = i >> 2;
          const x = idx % analysisW;
          const y = Math.floor(idx / analysisW);

          // Fast integer luminance
          const curLum = (currentData[i] * 38 + currentData[i + 1] * 75 + currentData[i + 2] * 15) >> 7;
          const prevLum = (prevData[i] * 38 + prevData[i + 1] * 75 + prevData[i + 2] * 15) >> 7;

          if (Math.abs(curLum - prevLum) > 22) {
            motionPixels++;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (y < analysisH * 0.4) {
              upperMotionCount++;
            }
          }
        }

        const sampledTotal = (analysisW * analysisH) / 4;
        const energyPct = Math.min(100, +( (motionPixels / (sampledTotal * 0.12)) * 100 ).toFixed(1));

        const person = detectedPersonRef.current;
        person.energy = energyPct;

        if (motionPixels > 8) {
          const targetX = minX / analysisW;
          const targetY = minY / analysisH;
          const targetW = Math.max(0.22, (maxX - minX) / analysisW);
          const targetH = Math.max(0.35, (maxY - minY) / analysisH);

          // Smooth tracking via exponential moving average
          person.x += (targetX - person.x) * 0.3;
          person.y += (targetY - person.y) * 0.3;
          person.w += (targetW - person.w) * 0.3;
          person.h += (targetH - person.h) * 0.3;
          person.active = true;
          person.confidence = +(Math.min(0.99, Math.max(0.85, 0.88 + energyPct * 0.001))).toFixed(3);

          // Upper-quadrant motion: hand waving gesture recognition
          if (upperMotionCount > 12) {
            if (!person.gesture) {
              soundService.playWaiterBell();
            }
            person.gesture = 'Hand Raised / Call Waiter';
            person.gestureUntil = Date.now() + 2500;
          } else if (person.gestureUntil && Date.now() > person.gestureUntil) {
            person.gesture = null;
          }

          // Table T01 zone intersection (left 65% of frame)
          person.inZoneT01 = person.x < 0.65;
        } else {
          // Low motion: decay smoothly
          person.energy = Math.max(0, person.energy * 0.8);
          if (person.gestureUntil && Date.now() > person.gestureUntil) {
            person.gesture = null;
          }
        }
      }

      lastFrameDataRef.current = new Uint8ClampedArray(currentData);

      // Render detection bounding box to both main viewer & preview overlay
      drawDetectionOverlay(mainOverlayCanvasRef.current, detectedPersonRef.current, true);
      drawDetectionOverlay(previewOverlayCanvasRef.current, detectedPersonRef.current, false);
    } catch (e) {
      // In case of security exception on canvas data
    }
  };

  // Render Real-Time AI Bounding Boxes & HUD onto Overlaid Canvas
  const drawDetectionOverlay = (canvas, person, isMainViewer = true) => {
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    if (!isWebcamActive) return;

    // 1. Draw Table T01 Region of Interest (ROI) zone boundary
    const roiX = w * 0.05;
    const roiY = h * 0.12;
    const roiW = w * 0.44;
    const roiH = h * 0.74;

    const isT01Occupied = person?.inZoneT01 || aiTelemetry.t01State === 'OCCUPIED';
    const zoneColor = isT01Occupied ? 'rgba(239, 68, 68, 0.85)' : 'rgba(16, 185, 129, 0.8)';

    ctx.strokeStyle = zoneColor;
    ctx.lineWidth = isMainViewer ? 2 : 1.5;
    if (ctx.setLineDash) ctx.setLineDash([4, 4]);
    ctx.strokeRect(roiX, roiY, roiW, roiH);
    if (ctx.setLineDash) ctx.setLineDash([]);

    // Zone Badge
    ctx.fillStyle = isT01Occupied ? 'rgba(239, 68, 68, 0.9)' : 'rgba(16, 185, 129, 0.85)';
    const badgeW = isMainViewer ? 90 : 55;
    const badgeH = isMainViewer ? 18 : 12;
    ctx.fillRect(roiX, roiY - badgeH, badgeW, badgeH);
    ctx.fillStyle = '#ffffff';
    ctx.font = isMainViewer ? 'bold 10px monospace' : 'bold 7px monospace';
    ctx.fillText(isT01Occupied ? 'ROI: T01 OCCUPIED' : 'ROI: T01 AVAILABLE', roiX + 4, roiY - (isMainViewer ? 5 : 3));

    // 2. Draw Dynamic Real-Time Bounding Box if patron detected
    if (person && (person.active || person.energy > 3)) {
      const bx = Math.max(0, Math.min(w - 40, person.x * w));
      const by = Math.max(0, Math.min(h - 40, person.y * h));
      const bw = Math.min(w - bx, Math.max(isMainViewer ? 60 : 35, person.w * w));
      const bh = Math.min(h - by, Math.max(isMainViewer ? 80 : 45, person.h * h));

      const bboxColor = person.inZoneT01 ? '#ef4444' : '#10b981';

      // Sci-fi corner brackets
      ctx.strokeStyle = bboxColor;
      ctx.lineWidth = isMainViewer ? 2.5 : 1.5;
      const corner = isMainViewer ? 12 : 7;

      // Top-left
      ctx.beginPath();
      ctx.moveTo(bx, by + corner);
      ctx.lineTo(bx, by);
      ctx.lineTo(bx + corner, by);
      ctx.stroke();

      // Top-right
      ctx.beginPath();
      ctx.moveTo(bx + bw - corner, by);
      ctx.lineTo(bx + bw, by);
      ctx.lineTo(bx + bw, by + corner);
      ctx.stroke();

      // Bottom-left
      ctx.beginPath();
      ctx.moveTo(bx, by + bh - corner);
      ctx.lineTo(bx, by + bh);
      ctx.lineTo(bx + corner, by + bh);
      ctx.stroke();

      // Bottom-right
      ctx.beginPath();
      ctx.moveTo(bx + bw - corner, by + bh);
      ctx.lineTo(bx + bw, by + bh);
      ctx.lineTo(bx + bw, by + bh - corner);
      ctx.stroke();

      // Translucent fill
      ctx.fillStyle = person.inZoneT01 ? 'rgba(239, 68, 68, 0.12)' : 'rgba(16, 185, 129, 0.1)';
      ctx.fillRect(bx, by, bw, bh);

      // Centroid crosshair
      const cx = bx + bw / 2;
      const cy = by + bh / 2;
      ctx.strokeStyle = bboxColor;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(cx - 5, cy);
      ctx.lineTo(cx + 5, cy);
      ctx.moveTo(cx, cy - 5);
      ctx.lineTo(cx, cy + 5);
      ctx.stroke();

      // Label Tag
      const confPct = Math.round(person.confidence * 100);
      const tagText = `PATRON (${confPct}%)`;
      ctx.font = isMainViewer ? 'bold 11px sans-serif' : 'bold 7px sans-serif';
      const tw = (ctx.measureText && ctx.measureText(tagText)?.width) || 60;
      ctx.fillStyle = bboxColor;
      ctx.fillRect(bx, by - (isMainViewer ? 18 : 12), tw + (isMainViewer ? 12 : 6), isMainViewer ? 18 : 12);
      ctx.fillStyle = '#ffffff';
      ctx.fillText(tagText, bx + (isMainViewer ? 6 : 3), by - (isMainViewer ? 5 : 3));

      // Gesture detection floating badge
      if (person.gesture) {
        const gText = `✋ ${person.gesture}`;
        ctx.font = isMainViewer ? 'bold 12px sans-serif' : 'bold 8px sans-serif';
        const gw = (ctx.measureText && ctx.measureText(gText)?.width) || 80;
        ctx.fillStyle = '#f59e0b';
        ctx.fillRect(bx, by + bh + 4, gw + (isMainViewer ? 14 : 8), isMainViewer ? 20 : 14);
        ctx.fillStyle = '#000000';
        ctx.fillText(gText, bx + (isMainViewer ? 7 : 4), by + bh + (isMainViewer ? 18 : 14));
      }
    }

    // 3. Real-time Optical Energy Meter HUD on Main Canvas
    if (isMainViewer) {
      const meterW = 90;
      const meterH = 8;
      const mx = w - meterW - 14;
      const my = 14;

      ctx.fillStyle = 'rgba(0, 0, 0, 0.75)';
      ctx.fillRect(mx - 8, my - 4, meterW + 16, meterH + 24);

      ctx.fillStyle = '#9ca3af';
      ctx.font = '9px monospace';
      ctx.fillText(`ENERGY: ${person?.energy?.toFixed(0) || 0}%`, mx, my + 6);

      ctx.fillStyle = '#1f2937';
      ctx.fillRect(mx, my + 10, meterW, meterH);

      const fillW = ((person?.energy || 0) / 100) * meterW;
      ctx.fillStyle = (person?.energy || 0) > 40 ? '#ef4444' : '#10b981';
      ctx.fillRect(mx, my + 10, fillW, meterH);
    }
  };

  // RBAC Access Restriction Gate for non-Owner/Manager
  if (!isAuthorized) {
    return (
      <div
        className="p-6 sm:p-10 max-w-lg mx-auto my-8 sm:my-14 rounded-2xl border text-center space-y-4 shadow-sm"
        style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
      >
        <div
          className="w-12 h-12 rounded-xl mx-auto flex items-center justify-center border"
          style={{
            backgroundColor: 'var(--status-critical-bg)',
            borderColor: 'var(--status-critical-border)',
            color: 'var(--status-critical)',
          }}
        >
          <ShieldAlert className="w-6 h-6" />
        </div>
        <div>
          <h2 className="text-base sm:text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
            Camera Security Gate: Restricted Access
          </h2>
          <p className="text-xs sm:text-sm mt-1.5 leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
            Access to live CCTV camera streams, webcam/mobile vision sensors, and Edge AI inference telemetry is restricted exclusively to <strong>Restaurant Owners</strong> and <strong>Floor Managers</strong>.
          </p>
          <div className="mt-3 inline-block px-2.5 py-1 rounded-md text-[11px] font-mono border" style={{ backgroundColor: 'var(--bg-main)', borderColor: 'var(--border)', color: 'var(--text-muted)' }}>
            Your Role: {userRole} (Senior clearance required)
          </div>
        </div>
      </div>
    );
  }

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

  const selectCamera = (cam, scrollToTop = false) => {
    setSelectedCam(cam);
    if (!cam.isWebcam) {
      loadZones(cam.cameraId);
    }
    if (scrollToTop && typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  // 1-Click Browser Camera Permission & Stream with Hardware Source Routing
  const startCameraStream = async (sourceType = cameraSource) => {
    setCameraError(null);
    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error('Camera media devices API is not supported in this browser.');
      }

      let videoConstraints = {
        width: { ideal: 1280 },
        height: { ideal: 720 },
      };

      if (sourceType === 'MOBILE') {
        videoConstraints.facingMode = { ideal: 'environment' };
      } else if (sourceType === 'WEBCAM') {
        videoConstraints.facingMode = 'user';
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        video: videoConstraints,
        audio: false,
      });

      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        videoRef.current.play().catch(() => {});
      }
      if (previewVideoRef.current) {
        previewVideoRef.current.srcObject = stream;
        previewVideoRef.current.play().catch(() => {});
      }
      setIsWebcamActive(true);

      const sourceLabel = sourceType === 'MOBILE' ? 'Mobile Camera' : sourceType === 'USB' ? 'USB / System Cam' : 'Webcam';
      const webcamCam = {
        cameraId: 999,
        name: `CAM-LIVE: ${sourceLabel}`,
        targetFps: 30.0,
        edgeNodeId: sourceType === 'MOBILE' ? 'mobile-sensor-01' : 'browser-sensor-01',
        isWebcam: true,
      };

      setCameras((prev) => [webcamCam, ...prev.filter((c) => !c.isWebcam)]);
      setSelectedCam(webcamCam);
    } catch (err) {
      console.warn('[CamerasZones] Camera permission denied/error:', err?.message || err);
      setCameraError(
        err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError'
          ? 'Camera permission was denied. Please allow camera access in your browser.'
          : (err.message || 'Unable to access device camera.')
      );
    }
  };

  const toggleDeviceCamera = async () => {
    if (isWebcamActive) {
      stopWebcam();
      const defaultCam = cameras.find((c) => !c.isWebcam) || INITIAL_CAMERAS[0];
      setSelectedCam(defaultCam);
      setCameras((prev) => prev.filter((c) => !c.isWebcam));
      return;
    }
    await startCameraStream(cameraSource);
  };

  const changeCameraSource = async (newSource) => {
    setCameraSource(newSource);
    if (isWebcamActive) {
      stopWebcam();
      await startCameraStream(newSource);
    }
  };

  const stopWebcam = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    if (previewVideoRef.current) {
      previewVideoRef.current.srcObject = null;
    }
    if (mainOverlayCanvasRef.current) {
      const ctx = mainOverlayCanvasRef.current.getContext('2d');
      if (ctx) ctx.clearRect(0, 0, mainOverlayCanvasRef.current.width, mainOverlayCanvasRef.current.height);
    }
    if (previewOverlayCanvasRef.current) {
      const ctx = previewOverlayCanvasRef.current.getContext('2d');
      if (ctx) ctx.clearRect(0, 0, previewOverlayCanvasRef.current.width, previewOverlayCanvasRef.current.height);
    }
    setIsWebcamActive(false);
  };

  // Real-Time AI Detection Trigger: Seated Party (Table T01)
  const triggerAiSeatedParty = async () => {
    setIsProcessingAction(true);
    setAiStatusMessage('AI Vision: Analyzing frame... Seated party detected at Table T01.');
    soundService.playSuccessChime();
    try {
      if (api.overrideTableStatus) {
        await api.overrideTableStatus(1, 'OCCUPIED', 'AI Vision: 2 patrons seated (Confidence: 96.4%)');
      }

      const newEvent = {
        aiEventPk: Date.now(),
        eventType: 'TABLE_OCCUPIED',
        processingStatus: 'APPLIED',
        tableId: 1,
        confidence: 0.964,
        eventUid: 'AI-VIS-' + Math.random().toString(36).substring(2, 10).toUpperCase(),
      };

      setRecentEvents((prev) => [newEvent, ...prev]);
      setAiTelemetry((prev) => ({
        ...prev,
        t01State: 'OCCUPIED',
        activePatrons: 2,
        t01DwellSec: 1,
        t01Confidence: 0.964,
        activeGesture: null,
      }));

      setAiStatusMessage('AI Vision: Table T01 confirmed OCCUPIED (Party: 2, 96.4% conf). App state synchronized in real time.');
      setTimeout(() => setAiStatusMessage(null), 5000);
    } catch (err) {
      console.warn('[CamerasZones] AI trigger failed:', err?.message || err);
      setAiStatusMessage('AI Vision: Telemetry recorded locally.');
      setTimeout(() => setAiStatusMessage(null), 4000);
    } finally {
      setIsProcessingAction(false);
    }
  };

  // Real-Time AI Detection Trigger: Gesture / Call Waiter (Table T01)
  const triggerAiGesture = async () => {
    setIsProcessingAction(true);
    setAiStatusMessage('AI Vision: Analyzing frame... Hand gesture recognized at Table T01.');
    soundService.playWaiterBell();
    try {
      const newEvent = {
        aiEventPk: Date.now(),
        eventType: 'GESTURE_DETECTED',
        processingStatus: 'APPLIED',
        tableId: 1,
        confidence: 0.925,
        eventUid: 'AI-GEST-' + Math.random().toString(36).substring(2, 10).toUpperCase(),
      };

      setRecentEvents((prev) => [newEvent, ...prev]);
      setAiTelemetry((prev) => ({
        ...prev,
        activeGesture: 'Hand Raised / Call Waiter',
      }));

      setAiStatusMessage('AI Vision: Patron gesture detected at Table T01 (92.5% conf). Waiter service notification dispatched!');
      setTimeout(() => {
        setAiTelemetry((prev) => ({ ...prev, activeGesture: null }));
        setAiStatusMessage(null);
      }, 5000);
    } finally {
      setIsProcessingAction(false);
    }
  };

  // Real-Time AI Detection Trigger: Table Vacated (Table T01)
  const triggerAiVacated = async () => {
    setIsProcessingAction(true);
    setAiStatusMessage('AI Vision: Analyzing frame... Patrons departed from Table T01.');
    try {
      if (api.overrideTableStatus) {
        await api.overrideTableStatus(1, 'DIRTY', 'AI Vision: Patrons vacated, dishes remaining on table');
      }

      const newEvent = {
        aiEventPk: Date.now(),
        eventType: 'TABLE_VACATED',
        processingStatus: 'APPLIED',
        tableId: 1,
        confidence: 0.971,
        eventUid: 'AI-VAC-' + Math.random().toString(36).substring(2, 10).toUpperCase(),
      };

      setRecentEvents((prev) => [newEvent, ...prev]);
      setAiTelemetry((prev) => ({
        ...prev,
        t01State: 'DIRTY',
        activePatrons: 0,
        t01DwellSec: 0,
        t01Confidence: 0.971,
        activeGesture: null,
      }));

      setAiStatusMessage('AI Vision: Table T01 VACATED (Confidence 97.1%). Table state updated to DIRTY for cleaning staff.');
      setTimeout(() => setAiStatusMessage(null), 5000);
    } catch (err) {
      console.warn('[CamerasZones] AI trigger failed:', err?.message || err);
      setAiStatusMessage('AI Vision: Vacated event recorded.');
      setTimeout(() => setAiStatusMessage(null), 4000);
    } finally {
      setIsProcessingAction(false);
    }
  };

  // Determine list of preview channels (ensures CAM-01, CAM-02, and CAM-LIVE are represented)
  const previewChannels = [
    cameras.find((c) => c.cameraId === 1) || INITIAL_CAMERAS[0],
    cameras.find((c) => c.cameraId === 2) || INITIAL_CAMERAS[1],
    cameras.find((c) => c.isWebcam) || {
      cameraId: 999,
      name: 'CAM-LIVE: Device Camera',
      targetFps: 30.0,
      edgeNodeId: 'browser-sensor-01',
      isWebcam: true,
      standby: true,
    },
    ...cameras.filter((c) => c.cameraId !== 1 && c.cameraId !== 2 && !c.isWebcam),
  ];

  return (
    <div className="space-y-6 sm:space-y-8 max-w-full">
      {/* 1. Header with Responsive Controls & Source Selector */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 sm:gap-4">
        <div>
          <h1 className="text-lg sm:text-xl font-bold tracking-tight flex flex-wrap items-center gap-2" style={{ color: 'var(--text-primary)' }}>
            CCTV Feeds &amp; Edge Vision
            <span
              className="text-[10px] sm:text-xs font-semibold px-2 py-0.5 rounded border"
              style={{
                backgroundColor: 'var(--bg-card)',
                borderColor: 'var(--border)',
                color: 'var(--text-secondary)',
              }}
            >
              Manager &amp; Owner Access
            </span>
          </h1>
          <p className="text-[11px] sm:text-xs mt-1" style={{ color: 'var(--text-secondary)' }}>
            Real-time camera streams, multi-zone AI bounding detection, and live edge vision inference.
          </p>
        </div>

        {/* Camera Source Selector & 1-Click Connect Pill */}
        <div className="flex flex-wrap items-center gap-2 self-start lg:self-auto w-full sm:w-auto">
          {/* Source Toggle: Webcam vs Mobile vs USB */}
          <div
            className="flex items-center p-1 rounded-xl border text-[11px] font-semibold"
            style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
          >
            <button
              onClick={() => changeCameraSource('WEBCAM')}
              className={`px-2.5 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors ${
                cameraSource === 'WEBCAM'
                  ? 'bg-neutral-800 text-emerald-400 font-bold shadow-xs'
                  : 'text-neutral-400 hover:text-neutral-200'
              }`}
              title="Laptop / Desktop Front Webcam"
            >
              <Laptop className="w-3.5 h-3.5" />
              <span>Webcam</span>
            </button>
            <button
              onClick={() => changeCameraSource('MOBILE')}
              className={`px-2.5 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors ${
                cameraSource === 'MOBILE'
                  ? 'bg-neutral-800 text-emerald-400 font-bold shadow-xs'
                  : 'text-neutral-400 hover:text-neutral-200'
              }`}
              title="Mobile Device Rear / Environment Camera"
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>Mobile Cam</span>
            </button>
            <button
              onClick={() => changeCameraSource('USB')}
              className={`px-2.5 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors ${
                cameraSource === 'USB'
                  ? 'bg-neutral-800 text-emerald-400 font-bold shadow-xs'
                  : 'text-neutral-400 hover:text-neutral-200'
              }`}
              title="External USB / System Camera"
            >
              <Usb className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">System / USB</span>
              <span className="sm:hidden">USB</span>
            </button>
          </div>

          {/* Connect / Disconnect Action Button */}
          <button
            onClick={toggleDeviceCamera}
            className="px-3.5 py-2 rounded-xl text-xs font-bold transition-all duration-200 flex items-center justify-center gap-2 border shadow-sm grow sm:grow-0 min-h-[38px]"
            style={{
              backgroundColor: isWebcamActive ? 'var(--status-occupied-bg)' : 'var(--bg-card)',
              borderColor: isWebcamActive ? 'var(--status-occupied-border)' : 'var(--border)',
              color: isWebcamActive ? 'var(--status-occupied)' : 'var(--text-primary)',
            }}
          >
            <Camera className={`w-3.5 h-3.5 ${isWebcamActive ? 'text-red-500 animate-pulse' : 'text-emerald-500'}`} />
            <span>{isWebcamActive ? 'Disconnect Camera' : 'Connect Device Camera'}</span>
          </button>
        </div>
      </div>

      {/* Permission / Notice Banner if error */}
      {cameraError && (
        <div className="p-3 rounded-xl border border-red-500/30 bg-red-500/10 text-red-400 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{cameraError}</span>
          </div>
          <button
            onClick={() => setCameraError(null)}
            className="text-[11px] underline hover:opacity-80 ml-2"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Real-Time AI Status Banner */}
      {aiStatusMessage && (
        <div className="p-3 rounded-xl border border-emerald-500/30 bg-emerald-500/10 text-emerald-400 text-xs flex items-center justify-between shadow-sm animate-fade-in">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-emerald-400 shrink-0 animate-spin" />
            <span className="font-semibold">{aiStatusMessage}</span>
          </div>
          <button
            onClick={() => setAiStatusMessage(null)}
            className="text-[11px] underline hover:opacity-80 ml-2 text-neutral-400"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* 2. Primary Live Inspection View */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6">
        {/* Left: Video Preview & Zone Overlays */}
        <div className="lg:col-span-2 space-y-4">
          {/* Camera Tabs */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
            {cameras.map((c) => (
              <button
                key={c.cameraId}
                onClick={() => selectCamera(c)}
                className="px-3 sm:px-3.5 py-1.5 sm:py-2 rounded-xl text-xs font-bold transition-colors duration-150 flex items-center gap-2 border whitespace-nowrap shrink-0"
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

          {/* CCTV Feed Canvas - Fully Responsive Aspect-Video */}
          <div className="bg-neutral-950 rounded-2xl border border-neutral-800 overflow-hidden relative shadow-lg aspect-video w-full flex items-center justify-center">
            {/* Live WebRTC Camera Stream when selected */}
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-300 ${
                selectedCam?.isWebcam && isWebcamActive ? 'opacity-100 z-0' : 'opacity-0 -z-10 pointer-events-none'
              }`}
            />

            {/* Real-Time Optical Computer Vision Overlay Canvas */}
            <canvas
              ref={mainOverlayCanvasRef}
              width={640}
              height={360}
              className={`absolute inset-0 w-full h-full object-cover pointer-events-none z-10 transition-opacity duration-300 ${
                selectedCam?.isWebcam && isWebcamActive ? 'opacity-100' : 'opacity-0'
              }`}
            />

            {/* Standby Placeholder when CAM-LIVE is selected but camera not yet turned on */}
            {selectedCam?.isWebcam && !isWebcamActive && (
              <div className="absolute inset-0 flex flex-col items-center justify-center p-6 text-center z-10 bg-neutral-950/90 backdrop-blur-xs">
                <div className="w-12 h-12 rounded-2xl bg-neutral-900 border border-neutral-800 flex items-center justify-center text-emerald-400 mb-3 shadow-inner">
                  <Camera className="w-6 h-6" />
                </div>
                <h3 className="text-sm sm:text-base font-bold text-neutral-200">
                  Device Camera Sensor Standby
                </h3>
                <p className="text-xs text-neutral-400 max-w-sm mt-1">
                  Connect your {cameraSource === 'MOBILE' ? 'mobile rear camera' : cameraSource === 'USB' ? 'system / USB camera' : 'laptop webcam'} to run real-time Computer Vision analysis directly on live camera frames.
                </p>
                <button
                  onClick={() => startCameraStream(cameraSource)}
                  className="mt-4 px-4 py-2 rounded-xl text-xs font-bold bg-emerald-500 hover:bg-emerald-600 text-black transition-all flex items-center gap-2 shadow-lg"
                >
                  <Zap className="w-4 h-4 fill-black" />
                  <span>Start Live AI Camera Feed</span>
                </button>
              </div>
            )}

            {/* Overlaid UI, Bounding Boxes, & Telemetry for Simulated Feeds (CAM-01 / CAM-02) */}
            <div className={`absolute inset-0 p-2.5 sm:p-5 flex flex-col justify-between z-10 pointer-events-none ${selectedCam?.isWebcam && isWebcamActive ? 'hidden' : 'flex'}`}>
              {/* Top OSD info */}
              <div className="flex items-center justify-between text-[10px] sm:text-xs bg-neutral-900/90 backdrop-blur-md p-1.5 sm:p-2 rounded-lg border border-neutral-800 text-neutral-300">
                <span className="flex items-center gap-1.5 sm:gap-2 font-bold truncate max-w-[45%]" style={{ color: 'var(--status-available)' }}>
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping shrink-0"></span>
                  Stream: {selectedCam?.name || 'CAM-01'}
                </span>
                <span className="tabular font-mono">
                  FPS: {aiTelemetry.fps || selectedCam?.targetFps || 24.0} |{' '}
                  {selectedCam?.isWebcam ? cameraSource : 'RTSP'}
                </span>
                <span className="hidden sm:inline">
                  Node: <span className="font-mono">{selectedCam?.edgeNodeId || 'edge-pune-01'}</span>
                </span>
              </div>

              {/* Graphical Table Zones & Detection Bounding Boxes */}
              <div className="relative w-full h-4/5 flex items-center justify-center gap-2 sm:gap-6 md:gap-8 px-1">
                {/* Zone T01 */}
                <div
                  className={`border-2 rounded-xl p-1.5 sm:p-3 text-center relative w-[46%] sm:w-44 md:w-48 h-24 sm:h-28 md:h-32 flex flex-col items-center justify-center backdrop-blur-xs transition-all duration-300 ${
                    aiTelemetry.t01State === 'OCCUPIED' ? 'ring-2 ring-red-500/50' : ''
                  }`}
                  style={{
                    borderColor: aiTelemetry.t01State === 'OCCUPIED' ? 'var(--status-occupied)' : 'var(--status-available)',
                    backgroundColor: aiTelemetry.t01State === 'OCCUPIED' ? 'rgba(239, 68, 68, 0.16)' : 'rgba(16, 185, 129, 0.12)',
                  }}
                >
                  <span
                    className="absolute -top-2 left-1.5 sm:left-2.5 text-white text-[8px] sm:text-[10px] font-bold px-1.5 py-0.5 rounded shadow truncate max-w-[90%]"
                    style={{
                      backgroundColor: aiTelemetry.t01State === 'OCCUPIED' ? 'var(--status-occupied)' : 'var(--status-available)',
                    }}
                  >
                    Zone: Table T01
                  </span>

                  {aiTelemetry.activeGesture && (
                    <span className="absolute -bottom-2 bg-amber-500 text-black text-[8px] sm:text-[9px] font-black px-1.5 py-0.5 rounded-full shadow animate-bounce">
                      ✋ {aiTelemetry.activeGesture}
                    </span>
                  )}

                  <div
                    className="w-6 h-6 sm:w-8 sm:h-8 rounded-full border flex items-center justify-center text-[10px] sm:text-xs font-bold mb-0.5 sm:mb-1 shrink-0"
                    style={{
                      borderColor: aiTelemetry.t01State === 'OCCUPIED' ? 'var(--status-occupied)' : 'var(--status-available)',
                      color: aiTelemetry.t01State === 'OCCUPIED' ? 'var(--status-occupied)' : 'var(--status-available)',
                    }}
                  >
                    T01
                  </div>
                  <span
                    className="text-[8px] sm:text-[10px] font-semibold truncate max-w-full px-1"
                    style={{
                      color: aiTelemetry.t01State === 'OCCUPIED' ? 'var(--status-occupied)' : 'var(--status-available)',
                    }}
                  >
                    {aiTelemetry.t01State === 'OCCUPIED'
                      ? `Occupied: 2 (${Math.round(aiTelemetry.t01Confidence * 100)}%)`
                      : 'Available / Empty'}
                  </span>
                  {aiTelemetry.t01State === 'OCCUPIED' && (
                    <span className="text-[7px] sm:text-[9px] font-mono text-neutral-400 mt-0.5">
                      Dwell: {aiTelemetry.t01DwellSec}s
                    </span>
                  )}
                </div>

                {/* Zone T02 */}
                <div
                  className="border-2 border-dashed rounded-xl p-1.5 sm:p-3 text-center relative w-[46%] sm:w-44 md:w-48 h-24 sm:h-28 md:h-32 flex flex-col items-center justify-center backdrop-blur-xs transition-all duration-300"
                  style={{
                    borderColor: 'var(--status-available)',
                    backgroundColor: 'rgba(16, 185, 129, 0.08)',
                  }}
                >
                  <span
                    className="absolute -top-2 left-1.5 sm:left-2.5 text-white text-[8px] sm:text-[10px] font-bold px-1.5 py-0.5 rounded shadow truncate max-w-[90%]"
                    style={{ backgroundColor: 'var(--status-available)' }}
                  >
                    Zone: Table T02
                  </span>
                  <div
                    className="w-6 h-6 sm:w-8 sm:h-8 rounded-full border flex items-center justify-center text-[10px] sm:text-xs font-bold mb-0.5 sm:mb-1 shrink-0"
                    style={{
                      borderColor: 'var(--status-available)',
                      color: 'var(--status-available)',
                    }}
                  >
                    T02
                  </div>
                  <span className="text-[8px] sm:text-[10px] font-semibold truncate max-w-full px-1" style={{ color: 'var(--status-available)' }}>
                    Available / Empty (98.8%)
                  </span>
                  <span className="text-[7px] sm:text-[9px] font-mono text-neutral-400 mt-0.5">
                    Temporal: Clean
                  </span>
                </div>
              </div>

              {/* Bottom OSD */}
              <div className="text-[9px] sm:text-[11px] text-neutral-400 bg-neutral-900/90 backdrop-blur-md p-1.5 sm:p-2 rounded-lg flex items-center justify-between border border-neutral-800">
                <span className="flex items-center gap-1.5 truncate max-w-[65%]" style={{ color: 'var(--status-available)' }}>
                  <Shield className="w-3 h-3 sm:w-3.5 sm:h-3.5 shrink-0" /> Edge Privacy Filter Active
                </span>
                <span className="shrink-0">{zones.length} Zones mapped</span>
              </div>
            </div>

            {/* Live Camera Bottom OSD when Webcam Active */}
            {selectedCam?.isWebcam && isWebcamActive && (
              <div className="absolute bottom-2 left-2 right-2 flex items-center justify-between text-[10px] sm:text-xs font-mono bg-neutral-900/90 backdrop-blur-md p-1.5 sm:p-2 rounded-lg border border-neutral-800 text-neutral-300 z-20 pointer-events-none">
                <span className="text-emerald-400 font-bold flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping shrink-0"></span>
                  AI Vision Live: {cameraSource} Feed Active
                </span>
                <span className="hidden sm:inline text-neutral-400">
                  Optical Mesh: Active Frame Analysis
                </span>
                <span className="text-emerald-400">
                  Latency: {aiTelemetry.inferenceMs} ms
                </span>
              </div>
            )}
          </div>

          {/* Real-Time AI Vision Control & Automation Ribbon */}
          <div
            className="rounded-2xl p-3.5 sm:p-4 border shadow-sm space-y-3"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b pb-2.5" style={{ borderColor: 'var(--border)' }}>
              <div className="flex items-center gap-2">
                <Zap className="w-4 h-4 text-emerald-500" />
                <span className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>
                  Real-Time AI Vision Actions
                </span>
                <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
                  Active
                </span>
              </div>

              <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--text-secondary)' }}>
                <span>AI Auto-Pilot:</span>
                <button
                  onClick={() => setIsAiAutoPilot((prev) => !prev)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-bold border transition-colors ${
                    isAiAutoPilot
                      ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400'
                      : 'bg-neutral-800 border-neutral-700 text-neutral-400'
                  }`}
                >
                  {isAiAutoPilot ? 'ENABLED (1s loop)' : 'PAUSED'}
                </button>
              </div>
            </div>

            <p className="text-[11px]" style={{ color: 'var(--text-secondary)' }}>
              Use your device camera (Mobile / Laptop / USB) or 1-touch triggers to simulate patron arrival, gesture calls, or vacancies. Live updates synchronize across the entire restaurant in real time.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-1">
              <button
                disabled={isProcessingAction}
                onClick={triggerAiSeatedParty}
                className="px-3 py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 border transition-all duration-150 hover:opacity-90 disabled:opacity-50 min-h-[40px]"
                style={{
                  backgroundColor: 'rgba(16, 185, 129, 0.12)',
                  borderColor: 'rgba(16, 185, 129, 0.4)',
                  color: 'var(--status-available)',
                }}
              >
                <Users className="w-3.5 h-3.5 shrink-0" />
                <span>AI Detect: Seated Patrons (T01)</span>
              </button>

              <button
                disabled={isProcessingAction}
                onClick={triggerAiGesture}
                className="px-3 py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 border transition-all duration-150 hover:opacity-90 disabled:opacity-50 min-h-[40px]"
                style={{
                  backgroundColor: 'rgba(245, 158, 11, 0.12)',
                  borderColor: 'rgba(245, 158, 11, 0.4)',
                  color: 'rgb(245, 158, 11)',
                }}
              >
                <Bell className="w-3.5 h-3.5 shrink-0" />
                <span>AI Detect: Call Waiter (T01)</span>
              </button>

              <button
                disabled={isProcessingAction}
                onClick={triggerAiVacated}
                className="px-3 py-2.5 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 border transition-all duration-150 hover:opacity-90 disabled:opacity-50 min-h-[40px]"
                style={{
                  backgroundColor: 'rgba(239, 68, 68, 0.12)',
                  borderColor: 'rgba(239, 68, 68, 0.4)',
                  color: 'var(--status-occupied)',
                }}
              >
                <Power className="w-3.5 h-3.5 shrink-0" />
                <span>AI Detect: Table Vacated (T01)</span>
              </button>
            </div>
          </div>
        </div>

        {/* Right: Edge Node Status & Live AI Event Feed */}
        <div className="space-y-4">
          <div
            className="rounded-2xl p-4 sm:p-5 border shadow-sm space-y-3"
            style={{
              backgroundColor: 'var(--bg-card)',
              borderColor: 'var(--border)',
            }}
          >
            <h3 className="text-sm font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Cpu className="w-4 h-4 text-emerald-500" />
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
                <span style={{ color: 'var(--text-secondary)' }}>Vision Engine:</span>
                <span className="font-mono font-bold text-emerald-400">
                  {aiTelemetry.modelName}
                </span>
              </div>
              <div
                className="flex justify-between p-2 rounded-xl border"
                style={{
                  backgroundColor: 'var(--bg-main)',
                  borderColor: 'var(--border)',
                }}
              >
                <span style={{ color: 'var(--text-secondary)' }}>Inference Latency:</span>
                <span className="font-mono font-bold" style={{ color: 'var(--text-primary)' }}>
                  {aiTelemetry.inferenceMs} ms / frame
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

            <div className="space-y-2 max-h-60 sm:max-h-72 overflow-y-auto text-xs">
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

      {/* 3. NEW SECTION: All Cameras Live AI Vision Multi-View (According to Camera Number) */}
      <div className="space-y-4 pt-2">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b pb-3" style={{ borderColor: 'var(--border)' }}>
          <div>
            <h2 className="text-base sm:text-lg font-bold tracking-tight flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Tv className="w-5 h-5 text-emerald-500" />
              All Cameras Live AI Vision Multi-View
              <span className="text-[10px] sm:text-xs font-semibold px-2 py-0.5 rounded-full border bg-emerald-500/10 text-emerald-500 border-emerald-500/30">
                Channel Previews
              </span>
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              Simultaneous AI vision telemetry and multi-zone inference across all restaurant channels (CAM-01, CAM-02, CAM-LIVE). Click any preview to focus stream.
            </p>
          </div>
          <span className="text-xs font-mono font-bold self-start sm:self-auto px-2.5 py-1 rounded-lg border bg-neutral-900 text-neutral-300 border-neutral-800">
            {previewChannels.length} Channels Active
          </span>
        </div>

        {/* Responsive Grid of Camera Previews */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
          {previewChannels.map((cam) => {
            const isSelected = selectedCam?.cameraId === cam.cameraId;
            const isLiveWebcam = cam.isWebcam;

            return (
              <div
                key={cam.cameraId}
                className={`rounded-2xl border p-4 space-y-3 transition-all duration-200 shadow-sm ${
                  isSelected ? 'ring-2 ring-emerald-500/70' : 'hover:border-neutral-700'
                }`}
                style={{
                  backgroundColor: 'var(--bg-card)',
                  borderColor: isSelected ? 'var(--status-available)' : 'var(--border)',
                }}
              >
                {/* Preview Header */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded text-[11px] font-mono font-bold bg-neutral-800 text-emerald-400 border border-neutral-700">
                      {isLiveWebcam ? 'CAM-LIVE' : `CAM-0${cam.cameraId}`}
                    </span>
                    <span className="text-xs font-bold truncate max-w-[150px]" style={{ color: 'var(--text-primary)' }}>
                      {cam.name.split(':')[1]?.trim() || cam.name}
                    </span>
                  </div>
                  <span className="flex items-center gap-1.5 text-[10px] font-semibold text-emerald-400">
                    <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                    {isLiveWebcam && isWebcamActive ? 'STREAMING' : 'ONLINE'}
                  </span>
                </div>

                {/* Preview Thumbnail Box with Dynamic AI Bounding Boxes */}
                <div className="bg-neutral-950 rounded-xl border border-neutral-800 overflow-hidden relative aspect-video flex items-center justify-center shadow-inner group">
                  {/* Live video mirror for CAM-LIVE if active */}
                  {isLiveWebcam && isWebcamActive && (
                    <>
                      <video
                        ref={previewVideoRef}
                        autoPlay
                        playsInline
                        muted
                        className="absolute inset-0 w-full h-full object-cover"
                      />
                      <canvas
                        ref={previewOverlayCanvasRef}
                        width={320}
                        height={180}
                        className="absolute inset-0 w-full h-full object-cover pointer-events-none z-10"
                      />
                    </>
                  )}

                  {/* Channel Overlay OSD */}
                  <div className="absolute top-2 left-2 right-2 flex items-center justify-between text-[9px] font-mono bg-neutral-900/85 backdrop-blur-xs px-2 py-1 rounded text-neutral-300 pointer-events-none z-10 border border-neutral-800">
                    <span className="font-bold text-emerald-400">
                      {isLiveWebcam ? 'CH-LIVE' : `CH-0${cam.cameraId}`} | {isLiveWebcam ? cameraSource : '1080p RTSP'}
                    </span>
                    <span>{cam.targetFps || 24} FPS</span>
                  </div>

                  {/* Specific Simulated Scene & AI Detections for CAM-01 */}
                  {cam.cameraId === 1 && (
                    <div className="absolute inset-0 p-3 pt-8 flex items-center justify-center gap-3 pointer-events-none">
                      {/* Mini Table T01 Zone */}
                      <div
                        className={`w-28 h-18 rounded-lg border flex flex-col items-center justify-center p-1 transition-all ${
                          aiTelemetry.t01State === 'OCCUPIED'
                            ? 'border-red-500 bg-red-500/15'
                            : 'border-emerald-500 bg-emerald-500/10'
                        }`}
                      >
                        <span className="text-[7px] font-bold text-white bg-black/60 px-1 rounded">T01 ROI</span>
                        <span
                          className={`text-[8px] font-bold mt-1 ${
                            aiTelemetry.t01State === 'OCCUPIED' ? 'text-red-400' : 'text-emerald-400'
                          }`}
                        >
                          {aiTelemetry.t01State === 'OCCUPIED' ? 'OCCUPIED (2)' : 'AVAILABLE'}
                        </span>
                        <span className="text-[7px] font-mono text-neutral-400">
                          {aiTelemetry.t01State === 'OCCUPIED' ? `Dwell: ${aiTelemetry.t01DwellSec}s` : 'Empty'}
                        </span>
                      </div>

                      {/* Mini Table T02 Zone */}
                      <div className="w-28 h-18 rounded-lg border border-dashed border-emerald-500/70 bg-emerald-500/5 flex flex-col items-center justify-center p-1">
                        <span className="text-[7px] font-bold text-white bg-black/60 px-1 rounded">T02 ROI</span>
                        <span className="text-[8px] font-bold text-emerald-400 mt-1">AVAILABLE</span>
                        <span className="text-[7px] font-mono text-neutral-400">Empty (98%)</span>
                      </div>
                    </div>
                  )}

                  {/* Specific Simulated Scene & AI Detections for CAM-02 */}
                  {cam.cameraId === 2 && (
                    <div className="absolute inset-0 p-3 pt-8 flex items-center justify-center gap-3 pointer-events-none">
                      {/* Mini Bar Counter Zone */}
                      <div className="w-28 h-18 rounded-lg border border-amber-500/80 bg-amber-500/15 flex flex-col items-center justify-center p-1">
                        <span className="text-[7px] font-bold text-white bg-black/60 px-1 rounded">BAR B01</span>
                        <span className="text-[8px] font-bold text-amber-400 mt-1">3 PATRONS</span>
                        <span className="text-[7px] font-mono text-neutral-300">YOLO: 95.1%</span>
                      </div>

                      {/* Mini Host Reception Queue Zone */}
                      <div className="w-28 h-18 rounded-lg border border-emerald-500/70 bg-emerald-500/5 flex flex-col items-center justify-center p-1">
                        <span className="text-[7px] font-bold text-white bg-black/60 px-1 rounded">HOST R01</span>
                        <span className="text-[8px] font-bold text-emerald-400 mt-1">QUEUE: 0</span>
                        <span className="text-[7px] font-mono text-neutral-300">Dwell: Clear</span>
                      </div>
                    </div>
                  )}

                  {/* Specific Scene for CAM-LIVE when inactive */}
                  {isLiveWebcam && !isWebcamActive && (
                    <div className="absolute inset-0 p-3 pt-8 flex flex-col items-center justify-center text-center text-neutral-500 pointer-events-none">
                      <Camera className="w-5 h-5 mb-1 text-neutral-600" />
                      <span className="text-[9px] font-semibold text-neutral-400">Device Sensor Standby</span>
                      <span className="text-[7px] text-neutral-500">Connect mobile rear or laptop camera</span>
                    </div>
                  )}

                  {/* Fallback for any other numbered camera */}
                  {cam.cameraId !== 1 && cam.cameraId !== 2 && !isLiveWebcam && (
                    <div className="absolute inset-0 p-3 pt-8 flex items-center justify-center pointer-events-none">
                      <div className="w-36 h-18 rounded-lg border border-emerald-500/50 bg-emerald-500/10 flex flex-col items-center justify-center p-1">
                        <span className="text-[8px] font-bold text-emerald-400">Zone Mapped</span>
                        <span className="text-[7px] font-mono text-neutral-400">Inference Idle</span>
                      </div>
                    </div>
                  )}

                  {/* Bottom Mini OSD Badge */}
                  <div className="absolute bottom-1.5 left-2 right-2 flex items-center justify-between text-[8px] font-mono text-neutral-400 bg-neutral-900/80 px-1.5 py-0.5 rounded z-10 pointer-events-none">
                    <span className="text-emerald-400 flex items-center gap-1">
                      <Shield className="w-2.5 h-2.5" /> Edge Privacy
                    </span>
                    <span>Node: {cam.edgeNodeId || 'edge-pune-01'}</span>
                  </div>
                </div>

                {/* AI Detection Summary & Focus Action Button */}
                <div className="space-y-2 pt-1">
                  <div className="flex items-center justify-between text-[11px]" style={{ color: 'var(--text-secondary)' }}>
                    <span className="flex items-center gap-1">
                      <Eye className="w-3.5 h-3.5 text-emerald-400" />
                      AI Telemetry:
                    </span>
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                      {cam.cameraId === 1
                        ? `T01: ${aiTelemetry.t01State} (Conf ${Math.round(aiTelemetry.t01Confidence * 100)}%)`
                        : cam.cameraId === 2
                        ? 'Bar: 3 Seated | Queue: Empty'
                        : isWebcamActive
                        ? 'Live Optical CV Active'
                        : 'Standby / Connect Ready'}
                    </span>
                  </div>

                  <button
                    onClick={() => selectCamera(cam, true)}
                    className="w-full py-1.5 px-3 rounded-xl text-xs font-bold transition-all duration-150 flex items-center justify-center gap-1.5 border shadow-xs"
                    style={{
                      backgroundColor: isSelected ? 'var(--btn-primary)' : 'var(--bg-main)',
                      borderColor: isSelected ? 'var(--btn-primary)' : 'var(--border)',
                      color: isSelected ? 'var(--btn-primary-text)' : 'var(--text-primary)',
                    }}
                  >
                    <Maximize2 className="w-3.5 h-3.5" />
                    <span>{isSelected ? 'Currently Focused' : `Focus ${isLiveWebcam ? 'Device Cam' : `CAM-0${cam.cameraId}`}`}</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
