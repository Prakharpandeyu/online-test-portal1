package com.onlinetest.questionexam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTrendDTO {

    private String month;
    private double passedPercentage;
    private double failedPercentage;
    private double notAttemptedPercentage;
}