package com.onlinetest.questionexam.controller;

import com.onlinetest.questionexam.dto.ApiResponseDTO;
import com.onlinetest.questionexam.dto.ExamCreateRequestDTO;
import com.onlinetest.questionexam.dto.ExamQuestionViewDTO;
import com.onlinetest.questionexam.dto.ExamResponseDTO;
import com.onlinetest.questionexam.dto.PaginatedResponseDTO;
import com.onlinetest.questionexam.service.ExamService;
import com.onlinetest.questionexam.util.JWTUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ExamController {

    private final ExamService examService;
    private final JWTUtil jwtUtil;

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponseDTO<ExamResponseDTO>> createExam(
            @Valid @RequestBody ExamCreateRequestDTO req,
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);
        Long userId = jwtUtil.extractUserId(jwt);
        String role = jwtUtil.extractRole(jwt);

        ExamResponseDTO dto = examService.createExam(req, companyId, userId, role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Exam created with random questions", dto));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @GetMapping("/{examId}")
    public ResponseEntity<ApiResponseDTO<ExamResponseDTO>> getExam(
            @PathVariable Long examId,
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);

        ExamResponseDTO dto = examService.getExamById(examId, companyId);
        return ResponseEntity.ok(ApiResponseDTO.success("Exam retrieved", dto));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponseDTO<List<ExamResponseDTO>>> listExams(
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Exams retrieved",
                        examService.listCompanyExams(companyId)
                )
        );
    }

    //  SEARCH + PAGINATION (PAGE SIZE = 5)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<ApiResponseDTO<PaginatedResponseDTO<ExamResponseDTO>>> searchExams(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);

        PaginatedResponseDTO<ExamResponseDTO> result =
                examService.searchExams(companyId, q, page);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Exams retrieved", result)
        );
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @PutMapping("/{examId}")
    public ResponseEntity<ApiResponseDTO<ExamResponseDTO>> updateExam(
            @PathVariable Long examId,
            @Valid @RequestBody ExamCreateRequestDTO req,
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);
        Long userId = jwtUtil.extractUserId(jwt);
        String role = jwtUtil.extractRole(jwt);

        return ResponseEntity.ok(
                ApiResponseDTO.success(
                        "Exam updated",
                        examService.updateExam(examId, req, companyId, userId, role)
                )
        );
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @DeleteMapping("/{examId}")
    public ResponseEntity<ApiResponseDTO<Void>> deleteExam(
            @PathVariable Long examId,
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);

        examService.deleteExam(examId, companyId);
        return ResponseEntity.ok(ApiResponseDTO.success("Exam deleted", null));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    @GetMapping("/{examId}/deliver")
    public ResponseEntity<ApiResponseDTO<List<ExamQuestionViewDTO>>> deliverExamQuestions(
            @PathVariable Long examId,
            @RequestHeader("Authorization") String token) {

        String jwt = token.replace("Bearer ", "");
        Long companyId = jwtUtil.extractCompanyId(jwt);

        List<ExamQuestionViewDTO> questions =
                examService.getExamQuestionsForDelivery(examId, companyId);

        return ResponseEntity.ok(
                ApiResponseDTO.success("Exam questions ready", questions)
        );
    }
}
