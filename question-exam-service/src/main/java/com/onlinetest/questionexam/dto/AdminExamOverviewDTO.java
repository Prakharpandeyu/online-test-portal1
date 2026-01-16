package com.onlinetest.questionexam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminExamOverviewDTO {

    // Global Summary (Used for Cards/Donut Chart)
    private double passedPercentage;
    private double failedPercentage;
    private double notAttemptedPercentage;

    // Total employees count (Card display)
    private long totalEmployeeCount;

    // ADDED: List for the Line Chart (Monthly Trends)
    private List<DashboardTrendDTO> monthlyTrends;
}