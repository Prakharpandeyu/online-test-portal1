package com.onlinetest.questionexam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamScoreDistributionDTO {

    private long scoreBelow60;
    private long score60to75;
    private long score75to85;
    private long score85to100;
}
