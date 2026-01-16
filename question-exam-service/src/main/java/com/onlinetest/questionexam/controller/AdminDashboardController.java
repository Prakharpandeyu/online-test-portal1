package com.onlinetest.questionexam.controller;

import com.onlinetest.questionexam.dto.AdminExamOverviewDTO;
import com.onlinetest.questionexam.dto.ApiResponseDTO;
import com.onlinetest.questionexam.dto.ExamBarChartDTO;
import com.onlinetest.questionexam.service.AdminDashboardService;
import com.onlinetest.questionexam.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    // Overview cards
    @GetMapping("/exam-overview")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDTO<AdminExamOverviewDTO>> getExamOverview(
            @RequestHeader("Authorization") String token // ADDED
    ) {

        Long companyId = SecurityContextUtil.getCompanyId();

        // Pass token to service
        AdminExamOverviewDTO data = dashboardService.getExamOverview(companyId, token);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Exam overview retrieved", data)
        );
    }
    // Per-exam bar chart
    @GetMapping("/exam/{examId}/bar-chart")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponseDTO<ExamBarChartDTO>> getExamBarChart(
            @PathVariable Long examId
    ) {
        Long companyId = SecurityContextUtil.getCompanyId();

        ExamBarChartDTO data =
                dashboardService.getExamBarChart(companyId, examId);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Exam bar chart data retrieved", data)
        );
    }
}