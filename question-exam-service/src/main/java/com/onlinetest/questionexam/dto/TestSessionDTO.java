package com.onlinetest.questionexam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestSessionDTO {
    private Long assignmentId;
    private Long examId;
    private String title;
    private String description;

    // New: per-question seconds and review minutes — used by frontend timer logic
    private Integer perQuestionSeconds;
    private Integer reviewMinutes;

    private LocalDateTime startedAt;
    private LocalDateTime endsAt; // still optional for assignment window
    private List<ExamQuestionViewDTO> questions;
}
