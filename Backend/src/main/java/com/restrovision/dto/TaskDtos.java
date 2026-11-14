package com.restrovision.dto;

public class TaskDtos {
    public static class CreateTaskRequest {
        private Long tableId;
        private String taskType; // TABLE_CLEANING, SPILL_CLEANUP, GUEST_SERVICE, RESTOCKING
        private String priority = "MEDIUM";
        private Long assignedEmployeeId;

        public CreateTaskRequest() {}

        public Long getTableId() { return tableId; }
        public void setTableId(Long tableId) { this.tableId = tableId; }
        public String getTaskType() { return taskType; }
        public void setTaskType(String taskType) { this.taskType = taskType; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public Long getAssignedEmployeeId() { return assignedEmployeeId; }
        public void setAssignedEmployeeId(Long assignedEmployeeId) { this.assignedEmployeeId = assignedEmployeeId; }
    }

    public static class AssignTaskRequest {
        private Long employeeId;
        public AssignTaskRequest() {}
        public AssignTaskRequest(Long employeeId) { this.employeeId = employeeId; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    }
}
