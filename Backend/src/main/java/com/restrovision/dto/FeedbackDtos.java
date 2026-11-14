package com.restrovision.dto;

public class FeedbackDtos {
    public static class CustomerFeedbackRequest {
        private Long tableId;
        private Long sessionId;
        private Integer rating; // 1 to 5
        private String comment;

        public CustomerFeedbackRequest() {}

        public Long getTableId() { return tableId; }
        public void setTableId(Long tableId) { this.tableId = tableId; }
        public Long getSessionId() { return sessionId; }
        public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
        public Integer getRating() { return rating; }
        public void setRating(Integer rating) { this.rating = rating; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class FalseAlarmRequest {
        private String feedbackType = "FALSE_ALARM"; // FALSE_ALARM, WRONG_TABLE_STATUS, MISSED_DETECTION
        private String comment;

        public FalseAlarmRequest() {}
        public FalseAlarmRequest(String feedbackType, String comment) {
            this.feedbackType = feedbackType;
            this.comment = comment;
        }

        public String getFeedbackType() { return feedbackType; }
        public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class ServiceRequestCreateDto {
        private Long tableId;
        private String requestType = "CALL_WAITER"; // CALL_WAITER, REQUEST_BILL, WATER, NAPKINS, ASSISTANCE
        private String source = "QR"; // QR, STAFF

        public ServiceRequestCreateDto() {}

        public Long getTableId() { return tableId; }
        public void setTableId(Long tableId) { this.tableId = tableId; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
