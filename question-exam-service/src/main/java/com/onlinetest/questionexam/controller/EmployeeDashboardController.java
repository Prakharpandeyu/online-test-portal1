package com.onlinetest.questionexam.controller;

import com.onlinetest.questionexam.dto.ApiResponseDTO;
import com.onlinetest.questionexam.dto.ExamAssignmentResponseDTO;
import com.onlinetest.questionexam.dto.ExamScoreDistributionDTO;
import com.onlinetest.questionexam.dto.PaginatedResponseDTO;
import com.onlinetest.questionexam.service.ExamAssignmentService;
import com.onlinetest.questionexam.service.ExamSubmissionService;
import com.onlinetest.questionexam.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employee/dashboard")
@RequiredArgsConstructor
public class EmployeeDashboardController {

    private final ExamSubmissionService submissionService;
    private final ExamAssignmentService assignmentService;

    @GetMapping("/score-distribution")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponseDTO<ExamScoreDistributionDTO>> getScoreDistribution() {

        Long companyId = SecurityContextUtil.getCompanyId();
        Long employeeId = SecurityContextUtil.getUserId();

        ExamScoreDistributionDTO data =
                submissionService.getEmployeeScoreDistribution(companyId, employeeId);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Score distribution retrieved", data)
        );
    }

    @GetMapping("/assignments/timeline")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ExamAssignmentResponseDTO>>> getAssignmentTimeline(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size
    ) {
        Long companyId = SecurityContextUtil.getCompanyId();
        Long employeeId = SecurityContextUtil.getUserId();

        PaginatedResponseDTO<ExamAssignmentResponseDTO> data =
                assignmentService.listMyAssignmentsTimeline(
                        companyId,
                        employeeId,
                        page,
                        size
                );

        return ResponseEntity.ok(
                ApiResponseDTO.success("Assignment timeline retrieved", data)
        );
    }
}
