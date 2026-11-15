/**
 * Kitchen Inventory & 86'd (Out-of-Stock) Item Management Service
 * Provides single-source-of-truth stock counters, low-stock threshold triggers,
 * and 1-click "86" toggles synchronized across KDS, Waiter, and Customer stations.
 */

const INITIAL_INVENTORY = [
  {
    itemId: 1,
    name: 'Paneer Tikka Shashlik',
    stationId: 1,
    stationName: 'Tandoor & Grill',
    category: 'Starters',
    price: 340,
    portionsRemaining: 18,
    lowStockThreshold: 5,
    is86d: false,
    reason: null,
  },
  {
    itemId: 2,
    name: 'Tandoori Malai Chaap',
    stationId: 1,
    stationName: 'Tandoor & Grill',
    category: 'Starters',
    price: 320,
    portionsRemaining: 3,
    lowStockThreshold: 5,
    is86d: false,
    reason: null,
  },
  {
    itemId: 3,
    name: 'Butter Garlic Naan',
    stationId: 1,
    stationName: 'Tandoor & Grill',
    category: 'Breads',
    price: 90,
    portionsRemaining: 45,
    lowStockThreshold: 10,
    is86d: false,
    reason: null,
  },
  {
    itemId: 4,
    name: 'Dal Makhani Slow-Cooked',
    stationId: 2,
    stationName: 'Curry & Saute',
    category: 'Mains',
    price: 380,
    portionsRemaining: 12,
    lowStockThreshold: 5,
    is86d: false,
    reason: null,
  },
  {
    itemId: 5,
    name: 'Paneer Lababdar',
    stationId: 2,
    stationName: 'Curry & Saute',
    category: 'Mains',
    price: 390,
    portionsRemaining: 0,
    lowStockThreshold: 5,
    is86d: true,
    reason: 'Fresh paneer batch depleted during peak lunch',
  },
  {
    itemId: 6,
    name: 'Jeera Rice',
    stationId: 2,
    stationName: 'Curry & Saute',
    category: 'Mains',
    price: 210,
    portionsRemaining: 24,
    lowStockThreshold: 8,
    is86d: false,
    reason: null,
  },
  {
    itemId: 7,
    name: 'Gulab Jamun with Rabri',
    stationId: 3,
    stationName: 'Dessert & Cold',
    category: 'Desserts',
    price: 180,
    portionsRemaining: 8,
    lowStockThreshold: 5,
    is86d: false,
    reason: null,
  },
  {
    itemId: 8,
    name: 'Mango Kulfi Slice',
    stationId: 3,
    stationName: 'Dessert & Cold',
    category: 'Desserts',
    price: 195,
    portionsRemaining: 2,
    lowStockThreshold: 4,
    is86d: false,
    reason: null,
  },
];

class InventoryService {
  constructor() {
    this.items = [...INITIAL_INVENTORY];
    this.listeners = [];

    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        const saved = localStorage.getItem('restro_inventory_items');
        if (saved) {
          const parsed = JSON.parse(saved);
          if (Array.isArray(parsed) && parsed.length > 0) {
            this.items = parsed;
          }
        }
      } catch (_) {}
    }
  }

  save() {
    if (typeof window !== 'undefined' && window.localStorage) {
      try {
        localStorage.setItem('restro_inventory_items', JSON.stringify(this.items));
      } catch (_) {}
    }
    this.notify();
  }

  notify() {
    this.listeners.forEach((cb) => cb(this.items));
  }

  subscribe(callback) {
    this.listeners.push(callback);
    callback(this.items);
    return () => {
      this.listeners = this.listeners.filter((cb) => cb !== callback);
    };
  }

  getItems() {
    return [...this.items];
  }

  get86dItems() {
    return this.items.filter((i) => i.is86d || i.portionsRemaining === 0);
  }

  getLowStockItems() {
    return this.items.filter(
      (i) => !i.is86d && i.portionsRemaining > 0 && i.portionsRemaining <= i.lowStockThreshold
    );
  }

  isItem86d(itemNameOrId) {
    const found = this.items.find(
      (i) => i.itemId === itemNameOrId || i.name.toLowerCase() === String(itemNameOrId).toLowerCase()
    );
    return found ? found.is86d || found.portionsRemaining === 0 : false;
  }

  toggle86(itemId, reason = 'Chef manual 86') {
    this.items = this.items.map((item) => {
      if (item.itemId === itemId) {
        const nextState = !item.is86d;
        return {
          ...item,
          is86d: nextState,
          portionsRemaining: nextState ? 0 : Math.max(10, item.portionsRemaining),
          reason: nextState ? reason : null,
        };
      }
      return item;
    });
    this.save();
    return this.items.find((i) => i.itemId === itemId);
  }

  adjustPortions(itemId, delta) {
    this.items = this.items.map((item) => {
      if (item.itemId === itemId) {
        const newCount = Math.max(0, item.portionsRemaining + delta);
        return {
          ...item,
          portionsRemaining: newCount,
          is86d: newCount === 0 ? true : item.is86d,
          reason: newCount === 0 ? 'Portions depleted to 0' : item.reason,
        };
      }
      return item;
    });
    this.save();
  }

  restockItem(itemId, newPortions = 20) {
    this.items = this.items.map((item) => {
      if (item.itemId === itemId) {
        return {
          ...item,
          portionsRemaining: Math.max(1, newPortions),
          is86d: false,
          reason: null,
        };
      }
      return item;
    });
    this.save();
  }

  resetDefaults() {
    this.items = [...INITIAL_INVENTORY];
    this.save();
  }
}

export const inventoryService = new InventoryService();
