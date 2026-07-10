package com.staffsense.demo.dto.task;

import com.staffsense.demo.model.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusUpdateRequest {
    private TaskStatus status;
}
