import { describe, it, expect, vi, beforeEach } from 'vitest';
import { inventoryService } from '../services/inventoryService';

describe('InventoryService (Option 5: Kitchen Inventory & 86d Item Management)', () => {
  beforeEach(() => {
    inventoryService.resetDefaults();
  });

  it('loads default inventory items across kitchen stations', () => {
    const items = inventoryService.getItems();
    expect(items.length).toBeGreaterThanOrEqual(8);
    expect(items.some((i) => i.name === 'Paneer Tikka Shashlik')).toBe(true);
    expect(items.some((i) => i.stationName === 'Curry & Saute')).toBe(true);
  });

  it('toggles an item to 86d (Out of Stock) and zeroes portions', () => {
    const updated = inventoryService.toggle86(1, 'Depleted paneer batch');
    expect(updated.is86d).toBe(true);
    expect(updated.portionsRemaining).toBe(0);
    expect(updated.reason).toContain('Depleted paneer');
    expect(inventoryService.isItem86d(1)).toBe(true);
    expect(inventoryService.isItem86d('Paneer Tikka Shashlik')).toBe(true);

    // Toggle back to available
    const restored = inventoryService.toggle86(1);
    expect(restored.is86d).toBe(false);
    expect(restored.portionsRemaining).toBeGreaterThan(0);
  });

  it('adjusts portions up and down, auto-86ing when portions reach 0', () => {
    // Malai Chaap starts with 3 portions
    inventoryService.adjustPortions(2, 5);
    const item = inventoryService.getItems().find((i) => i.itemId === 2);
    expect(item.portionsRemaining).toBe(8);

    // Decrement down to 0
    inventoryService.adjustPortions(2, -8);
    const depleted = inventoryService.getItems().find((i) => i.itemId === 2);
    expect(depleted.portionsRemaining).toBe(0);
    expect(depleted.is86d).toBe(true);
  });

  it('restocks an item with custom portion count and clears 86d state', () => {
    // Paneer Lababdar starts 86'd
    expect(inventoryService.isItem86d(5)).toBe(true);

    inventoryService.restockItem(5, 25);
    const restocked = inventoryService.getItems().find((i) => i.itemId === 5);
    expect(restocked.is86d).toBe(false);
    expect(restocked.portionsRemaining).toBe(25);
    expect(inventoryService.isItem86d(5)).toBe(false);
  });

  it('filters 86d items and low stock items', () => {
    const initial86d = inventoryService.get86dItems();
    expect(initial86d.length).toBeGreaterThanOrEqual(1);

    const lowStock = inventoryService.getLowStockItems();
    expect(lowStock.length).toBeGreaterThanOrEqual(1);
  });

  it('notifies subscribers upon inventory state updates', () => {
    const listener = vi.fn();
    const unsub = inventoryService.subscribe(listener);

    inventoryService.adjustPortions(1, -2);
    expect(listener).toHaveBeenCalled();
    unsub();
  });
});
