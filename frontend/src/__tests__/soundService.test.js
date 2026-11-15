import { describe, it, expect, vi, beforeEach } from 'vitest';
import { soundService } from '../services/soundService';

describe('SoundService (Option 4: Real-time Audio Chimes & Sound Alerts)', () => {
  beforeEach(() => {
    soundService.muted = false;
    soundService.setVolume(0.7);
  });

  it('initializes with default volume and unmuted state', () => {
    expect(soundService.isMuted()).toBe(false);
    expect(soundService.getVolume()).toBe(0.7);
  });

  it('toggles mute state and notifies subscribers', () => {
    const subscriber = vi.fn();
    const unsub = soundService.subscribe(subscriber);

    const isMuted = soundService.toggleMute();
    expect(isMuted).toBe(true);
    expect(soundService.isMuted()).toBe(true);
    expect(subscriber).toHaveBeenCalledWith(expect.objectContaining({ muted: true }));

    soundService.toggleMute();
    expect(soundService.isMuted()).toBe(false);
    unsub();
  });

  it('updates volume between 0.0 and 1.0 clamped', () => {
    soundService.setVolume(0.45);
    expect(soundService.getVolume()).toBe(0.45);

    soundService.setVolume(1.5);
    expect(soundService.getVolume()).toBe(1.0);

    soundService.setVolume(-0.2);
    expect(soundService.getVolume()).toBe(0);
  });

  it('triggers kitchen ticket chime without throwing errors', () => {
    expect(() => soundService.playKitchenChime()).not.toThrow();
  });

  it('triggers waiter service bell without throwing errors', () => {
    expect(() => soundService.playWaiterBell()).not.toThrow();
  });

  it('triggers SLA delay warning chime without throwing errors', () => {
    expect(() => soundService.playDelayAlert()).not.toThrow();
  });

  it('triggers order ready success chime without throwing errors', () => {
    expect(() => soundService.playSuccessChime()).not.toThrow();
  });

  it('executes testSound helper for all sound keys', () => {
    expect(() => soundService.testSound('KITCHEN')).not.toThrow();
    expect(() => soundService.testSound('WAITER')).not.toThrow();
    expect(() => soundService.testSound('DELAY')).not.toThrow();
    expect(() => soundService.testSound('SUCCESS')).not.toThrow();
    expect(() => soundService.testSound('UNKNOWN')).not.toThrow();
  });
});
