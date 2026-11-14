package com.restrovision.dto;

import java.util.List;

public class OrderDtos {
    public static class CreateOrderItemRequest {
        private Long itemId;
        private Integer quantity;

        public CreateOrderItemRequest() {}
        public CreateOrderItemRequest(Long itemId, Integer quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public static class CreateOrderRequest {
        private Long tableId;
        private Long sessionId;
        private List<CreateOrderItemRequest> items;

        public CreateOrderRequest() {}

        public Long getTableId() { return tableId; }
        public void setTableId(Long tableId) { this.tableId = tableId; }
        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public List<CreateOrderItemRequest> getItems() { return items; }
        public void setItems(List<CreateOrderItemRequest> items) { this.items = items; }
    }

    public static class UpdateOrderStatusRequest {
        private String status; // CREATED, CONFIRMED, PREPARING, READY, SERVED, CANCELLED

        public UpdateOrderStatusRequest() {}
        public UpdateOrderStatusRequest(String status) { this.status = status; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class OrderItemStatusUpdateRequest {
        private String status; // IN_PREPARATION, READY, SERVED, CANCELLED
        public OrderItemStatusUpdateRequest() {}
        public OrderItemStatusUpdateRequest(String status) { this.status = status; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
