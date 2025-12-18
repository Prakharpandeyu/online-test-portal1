package com.onlinetest.questionexam.controller;

import com.onlinetest.questionexam.dto.ApiResponseDTO;
import com.onlinetest.questionexam.dto.ExamAssignRequestDTO;
import com.onlinetest.questionexam.dto.ExamAssignmentResponseDTO;
import com.onlinetest.questionexam.dto.PaginatedResponseDTO;
import com.onlinetest.questionexam.dto.TestSessionDTO;
import com.onlinetest.questionexam.service.ExamAssignmentService;
import com.onlinetest.questionexam.util.JWTUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exam-assignments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExamAssignmentController {

    private final ExamAssignmentService service;
    private final JWTUtil jwtUtil;

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDTO<?>> assignExam(
            @Valid @RequestBody ExamAssignRequestDTO req,
            @RequestHeader("Authorization") String token) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long companyId   = jwtUtil.extractCompanyId(jwt);
        Long adminUserId = jwtUtil.extractUserId(jwt);
        String role      = jwtUtil.extractRole(jwt);

        var data = service.assignExam(req, companyId, adminUserId, role, jwt);
        return ResponseEntity.ok(ApiResponseDTO.success("Exam assigned successfully", data));
    }

    // Updated: pagination support
    @PreAuthorize("hasRole('EMPLOYEE')")
    @GetMapping("/my")
    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ExamAssignmentResponseDTO>>> myAssignments(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long companyId  = jwtUtil.extractCompanyId(jwt);
        Long employeeId = jwtUtil.extractUserId(jwt);

        var data = service.listMyAssignmentsPaginated(companyId, employeeId, page, size);
        return ResponseEntity.ok(ApiResponseDTO.success("Assignments retrieved", data));
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/{assignmentId}/start")
    public ResponseEntity<ApiResponseDTO<TestSessionDTO>> startAssignedExam(
            @PathVariable Long assignmentId,
            @RequestHeader("Authorization") String token) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long companyId  = jwtUtil.extractCompanyId(jwt);
        Long employeeId = jwtUtil.extractUserId(jwt);

        TestSessionDTO session = service.startAssignedExam(assignmentId, companyId, employeeId);
        return ResponseEntity.ok(ApiResponseDTO.success("Exam started successfully", session));
    }
}
