package com.onlinetest.questionexam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExamTopicSummaryDTO {
    private Long topicId;
    private String topicName;
    private Integer questionCount;
}
