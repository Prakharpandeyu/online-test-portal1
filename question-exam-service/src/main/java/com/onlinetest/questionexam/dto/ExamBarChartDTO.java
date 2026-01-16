package com.onlinetest.questionexam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamBarChartDTO {

    private Long examId;
    private String examTitle;

    private long passedCount;
    private long failedCount;
    private long notAttemptedCount;
}
