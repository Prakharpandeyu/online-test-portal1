package com.onlinetest.questionexam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ExamCreateRequestDTO {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    // NEW
    @NotNull(message = "Per question time is required")
    @Min(value = 5, message = "Minimum per-question time is 5 seconds")
    private Integer perQuestionSeconds;

    // NEW
    @NotNull(message = "Review duration is required")
    @Min(value = 1, message = "Minimum review duration is 1 minute")
    private Integer reviewMinutes;

    @Min(value = 0, message = "Passing percentage must be between 0 and 100")
    @Max(value = 100, message = "Passing percentage must be between 0 and 100")
    private Integer passingPercentage;

    @NotEmpty(message = "At least one topic is required")
    @Valid
    private List<ExamTopicRequestDTO> topics;

    public int getTotalQuestions() {
        return topics == null ? 0 : topics.stream()
                .mapToInt(ExamTopicRequestDTO::getQuestionsCount)
                .sum();
    }

    public List<Long> getTopicIds() {
        return topics == null ? List.of() : topics.stream()
                .map(ExamTopicRequestDTO::getTopicId)
                .toList();
    }

    @Data
    public static class ExamTopicRequestDTO {
        @NotNull(message = "Topic ID is required")
        private Long topicId;

        @NotNull(message = "Questions count is required")
        @Min(value = 1, message = "Questions count must be at least 1")
        private Integer questionsCount;
    }
}
