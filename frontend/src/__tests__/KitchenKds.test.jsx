import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import KitchenKds from '../pages/KitchenKds';
import { soundService } from '../services/soundService';
import { inventoryService } from '../services/inventoryService';

// Mock API
vi.mock('../services/api', () => ({
  api: {
    getOrders: vi.fn(),
    getKitchenStations: vi.fn(),
    getDelayedOrders: vi.fn(),
    startOrderPrep: vi.fn(),
    markOrderReady: vi.fn(),
    updateOrderItemStatus: vi.fn(),
  },
}));

// Mock WebSocket
vi.mock('../services/websocket', () => ({
  wsService: {
    subscribe: vi.fn(() => vi.fn()),
  },
}));

import { api } from '../services/api';

describe('KitchenKds Component with Audio Alerts & 86d Inventory (Options 4 & 5)', () => {
  const mockOrders = [
    {
      orderId: 1042,
      orderCode: '#1042',
      tableId: 1,
      status: 'PREPARING',
      createdAt: new Date().toISOString(),
      items: [
        { orderItemId: 1, itemName: 'Paneer Tikka Shashlik', quantity: 2, stationName: 'Tandoor & Grill', expectedPrepMinutes: 10, status: 'READY' },
        { orderItemId: 2, itemName: 'Tandoori Malai Chaap', quantity: 1, stationName: 'Tandoor & Grill', expectedPrepMinutes: 12, status: 'PREPARING' },
      ],
    },
  ];

  const mockStations = [
    { stationId: 1, name: 'Tandoor & Grill' },
    { stationId: 2, name: 'Curry & Saute' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    inventoryService.resetDefaults();
    api.getOrders.mockResolvedValue(mockOrders);
    api.getKitchenStations.mockResolvedValue(mockStations);
    api.getDelayedOrders.mockResolvedValue([]);
    api.startOrderPrep.mockResolvedValue({ orderId: 1042, status: 'PREPARING' });
    api.markOrderReady.mockResolvedValue({ orderId: 1042, status: 'READY' });
    api.updateOrderItemStatus.mockResolvedValue({ orderItemId: 2, status: 'READY' });
  });

  it('renders KDS header and primary tab switcher', async () => {
    render(<KitchenKds />);

    expect(screen.getByText(/Kitchen Display System/i)).toBeInTheDocument();
    expect(screen.getByText(/Ticket Queue/i)).toBeInTheDocument();
    expect(screen.getByText(/86'd & Inventory/i)).toBeInTheDocument();
  });

  it('switches between Ticket Queue and 86d Inventory tab', async () => {
    render(<KitchenKds />);

    const inventoryTabBtn = screen.getByRole('button', { name: /86'd & Inventory/i });
    fireEvent.click(inventoryTabBtn);

    expect(screen.getByPlaceholderText(/Search dish or ingredient/i)).toBeInTheDocument();
    expect(screen.getByText(/Paneer Tikka Shashlik/i)).toBeInTheDocument();
  });

  it('toggles an item to 86d directly from the Inventory view', async () => {
    const playDelaySpy = vi.spyOn(soundService, 'playDelayAlert');
    render(<KitchenKds />);

    // Go to Inventory tab
    const inventoryTabBtn = screen.getByRole('button', { name: /86'd & Inventory/i });
    fireEvent.click(inventoryTabBtn);

    // Find the 86 action button for first available item
    const buttons = screen.getAllByRole('button', { name: /86 It/i });
    expect(buttons.length).toBeGreaterThan(0);

    fireEvent.click(buttons[0]);

    await waitFor(() => {
      expect(playDelaySpy).toHaveBeenCalled();
      expect(screen.getByText(/is now marked OUT OF STOCK across all menus/i)).toBeInTheDocument();
    });
  });

  it('marks an order ready and triggers success chime', async () => {
    const playSuccessSpy = vi.spyOn(soundService, 'playSuccessChime');
    render(<KitchenKds />);

    await waitFor(() => {
      expect(screen.getByText('#1042')).toBeInTheDocument();
    });

    const markReadyBtn = screen.getByRole('button', { name: /Mark All Ready at Pass/i });
    fireEvent.click(markReadyBtn);

    await waitFor(() => {
      expect(api.markOrderReady).toHaveBeenCalledWith(1042);
      expect(playSuccessSpy).toHaveBeenCalled();
    });
  });
});
