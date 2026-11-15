/**
 * Web Audio API Sound Synthesizer for RESTROVISION
 * Generates instant, zero-latency harmonic chimes, diner bells, and alerts
 * without external audio asset downloads.
 */

class SoundService {
  constructor() {
    this.audioCtx = null;
    this.muted = false;
    this.volume = 0.7; // 0.0 to 1.0
    this.listeners = [];

    // Load persisted settings
    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        const savedMute = localStorage.getItem('restro_sound_muted');
        if (savedMute !== null) this.muted = savedMute === 'true';
        const savedVol = localStorage.getItem('restro_sound_volume');
        if (savedVol !== null) this.volume = parseFloat(savedVol) || 0.7;
      } catch (_) {}
    }
  }

  getAudioContext() {
    if (typeof window === 'undefined') return null;
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return null;

    if (!this.audioCtx) {
      this.audioCtx = new AudioContextClass();
    }
    if (this.audioCtx.state === 'suspended') {
      this.audioCtx.resume().catch(() => {});
    }
    return this.audioCtx;
  }

  isMuted() {
    return this.muted;
  }

  getVolume() {
    return this.volume;
  }

  setVolume(vol) {
    this.volume = Math.max(0, Math.min(1, vol));
    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        localStorage.setItem('restro_sound_volume', String(this.volume));
      } catch (_) {}
    }
    this.notify();
  }

  toggleMute() {
    this.muted = !this.muted;
    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        localStorage.setItem('restro_sound_muted', String(this.muted));
      } catch (_) {}
    }
    this.notify();
    return this.muted;
  }

  subscribe(callback) {
    this.listeners.push(callback);
    return () => {
      this.listeners = this.listeners.filter((cb) => cb !== callback);
    };
  }

  notify() {
    this.listeners.forEach((cb) => cb({ muted: this.muted, volume: this.volume }));
  }

  /**
   * 1. Kitchen Ticket Bell: Dual-tone diner bell (D5 -> A5)
   * Resembles an authentic mechanical diner bell when an order fires to the kitchen.
   */
  playKitchenChime() {
    if (this.muted || this.volume <= 0) return;
    const ctx = this.getAudioContext();
    if (!ctx) return;

    try {
      const now = ctx.currentTime;
      const masterGain = ctx.createGain();
      masterGain.gain.setValueAtTime(this.volume * 0.8, now);
      masterGain.connect(ctx.destination);

      // Low strike tone (587.33 Hz - D5)
      const osc1 = ctx.createOscillator();
      const gain1 = ctx.createGain();
      osc1.type = 'triangle';
      osc1.frequency.setValueAtTime(587.33, now);
      gain1.gain.setValueAtTime(1, now);
      gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
      osc1.connect(gain1);
      gain1.connect(masterGain);

      // High bell shimmer tone (880.00 Hz - A5)
      const osc2 = ctx.createOscillator();
      const gain2 = ctx.createGain();
      osc2.type = 'sine';
      osc2.frequency.setValueAtTime(880.00, now + 0.05);
      gain2.gain.setValueAtTime(0.9, now + 0.05);
      gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.65);
      osc2.connect(gain2);
      gain2.connect(masterGain);

      osc1.start(now);
      osc1.stop(now + 0.5);
      osc2.start(now + 0.05);
      osc2.stop(now + 0.7);
    } catch (_) {}
  }

  /**
   * 2. Waiter Service Call Bell: Crystal double ding (C6 -> E6)
   * High-pitch polite chime for guest assistance and CCTV waving gestures.
   */
  playWaiterBell() {
    if (this.muted || this.volume <= 0) return;
    const ctx = this.getAudioContext();
    if (!ctx) return;

    try {
      const now = ctx.currentTime;
      const masterGain = ctx.createGain();
      masterGain.gain.setValueAtTime(this.volume * 0.75, now);
      masterGain.connect(ctx.destination);

      // Ding 1 (C6 - 1046.5 Hz)
      const osc1 = ctx.createOscillator();
      const gain1 = ctx.createGain();
      osc1.type = 'sine';
      osc1.frequency.setValueAtTime(1046.5, now);
      gain1.gain.setValueAtTime(0.8, now);
      gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
      osc1.connect(gain1);
      gain1.connect(masterGain);

      // Ding 2 (E6 - 1318.5 Hz)
      const osc2 = ctx.createOscillator();
      const gain2 = ctx.createGain();
      osc2.type = 'sine';
      osc2.frequency.setValueAtTime(1318.5, now + 0.12);
      gain2.gain.setValueAtTime(1.0, now + 0.12);
      gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.6);
      osc2.connect(gain2);
      gain2.connect(masterGain);

      osc1.start(now);
      osc1.stop(now + 0.4);
      osc2.start(now + 0.12);
      osc2.stop(now + 0.65);
    } catch (_) {}
  }

  /**
   * 3. Kitchen SLA Delay Warning: Pulsed cautionary chime (440Hz / 466Hz)
   * Warning alert when a ticket breaches expected preparation duration.
   */
  playDelayAlert() {
    if (this.muted || this.volume <= 0) return;
    const ctx = this.getAudioContext();
    if (!ctx) return;

    try {
      const now = ctx.currentTime;
      const masterGain = ctx.createGain();
      masterGain.gain.setValueAtTime(this.volume * 0.85, now);
      masterGain.connect(ctx.destination);

      [0, 0.18].forEach((offset) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(440, now + offset);
        osc.frequency.linearRampToValueAtTime(370, now + offset + 0.12);
        gain.gain.setValueAtTime(0.7, now + offset);
        gain.gain.exponentialRampToValueAtTime(0.001, now + offset + 0.15);
        osc.connect(gain);
        gain.connect(masterGain);
        osc.start(now + offset);
        osc.stop(now + offset + 0.16);
      });
    } catch (_) {}
  }

  /**
   * 4. Order Ready & Success Chime: Upbeat major triad arpeggio (C5 -> E5 -> G5)
   * Celebratory chime when a dish is ready for pickup or table sanitized.
   */
  playSuccessChime() {
    if (this.muted || this.volume <= 0) return;
    const ctx = this.getAudioContext();
    if (!ctx) return;

    try {
      const now = ctx.currentTime;
      const masterGain = ctx.createGain();
      masterGain.gain.setValueAtTime(this.volume * 0.7, now);
      masterGain.connect(ctx.destination);

      const notes = [523.25, 659.25, 783.99]; // C5, E5, G5
      notes.forEach((freq, idx) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        const startTime = now + idx * 0.08;
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(freq, startTime);
        gain.gain.setValueAtTime(0.8, startTime);
        gain.gain.exponentialRampToValueAtTime(0.001, startTime + 0.35);
        osc.connect(gain);
        gain.connect(masterGain);
        osc.start(startTime);
        osc.stop(startTime + 0.4);
      });
    } catch (_) {}
  }

  testSound(type = 'KITCHEN') {
    switch (type) {
      case 'KITCHEN':
        this.playKitchenChime();
        break;
      case 'WAITER':
        this.playWaiterBell();
        break;
      case 'DELAY':
        this.playDelayAlert();
        break;
      case 'SUCCESS':
        this.playSuccessChime();
        break;
      default:
        this.playKitchenChime();
    }
  }
}

export const soundService = new SoundService();
