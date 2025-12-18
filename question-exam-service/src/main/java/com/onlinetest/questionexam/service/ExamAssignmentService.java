package com.onlinetest.questionexam.service;

import com.onlinetest.questionexam.dto.ExamAssignRequestDTO;
import com.onlinetest.questionexam.dto.ExamAssignmentResponseDTO;
import com.onlinetest.questionexam.dto.ExamQuestionViewDTO;
import com.onlinetest.questionexam.dto.PaginatedResponseDTO;
import com.onlinetest.questionexam.dto.TestSessionDTO;
import com.onlinetest.questionexam.entity.Exam;
import com.onlinetest.questionexam.entity.ExamAssignment;
import com.onlinetest.questionexam.entity.ExamQuestion;
import com.onlinetest.questionexam.entity.Question;
import com.onlinetest.questionexam.integration.UserClient;
import com.onlinetest.questionexam.integration.UserClient.UserSummaryDTO;
import com.onlinetest.questionexam.repository.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExamAssignmentService {

    private final ExamRepository examRepository;
    private final ExamAssignmentRepository examAssignmentRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;

    // REQUIRED: Used to fetch last attempt results
    private final ExamAttemptRepository attemptRepository;

    private final UserClient userClient;

    private static final String STATUS_ASSIGNED = "ASSIGNED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";

    public List<ExamAssignmentResponseDTO> assignExam(
            ExamAssignRequestDTO req,
            Long companyId,
            Long adminUserId,
            String role,
            String jwt
    ) {
        Exam exam = examRepository.findById(req.getExamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (!exam.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Exam does not belong to your company");
        }

        LocalDateTime start = req.getStartTime();
        LocalDateTime end = req.getEndTime();

        if (start != null && end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date window");
        }

        validateEmployees(req.getEmployeeIds(), companyId, jwt);

        LocalDateTime now = LocalDateTime.now();
        List<ExamAssignmentResponseDTO> responses = new ArrayList<>();

        for (Long empId : req.getEmployeeIds()) {

            if (examAssignmentRepository.existsByCompanyIdAndExamIdAndEmployeeId(companyId, exam.getId(), empId)) {
                continue;
            }

            ExamAssignment a = new ExamAssignment();
            a.setCompanyId(companyId);
            a.setExamId(exam.getId());
            a.setEmployeeId(empId);
            a.setAssignedBy(adminUserId);
            a.setAssignedByRole(role);
            a.setStartTime(start);
            a.setEndTime(end);
            a.setMaxAttempts(req.getMaxAttempts() == null ? 1 : req.getMaxAttempts());
            a.setStatus(STATUS_ASSIGNED);
            a.setCreatedDate(now);
            a.setUpdatedDate(now);

            ExamAssignment saved = examAssignmentRepository.save(a);
            responses.add(mapToDTO(saved, exam, now));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public PaginatedResponseDTO<ExamAssignmentResponseDTO> listMyAssignmentsPaginated(
            Long companyId,
            Long employeeId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());

        Page<ExamAssignment> assignmentPage =
                examAssignmentRepository.findByCompanyIdAndEmployeeIdOrderByCreatedDateDesc(
                        companyId, employeeId, pageable);

        Set<Long> examIds = assignmentPage.getContent()
                .stream()
                .map(ExamAssignment::getExamId)
                .collect(Collectors.toSet());

        Map<Long, Exam> examMap = examRepository.findAllById(examIds)
                .stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));

        LocalDateTime now = LocalDateTime.now();

        List<ExamAssignmentResponseDTO> dtoList = assignmentPage.getContent()
                .stream()
                .map(a -> mapToDTO(a, examMap.get(a.getExamId()), now))
                .toList();

        return PaginatedResponseDTO.<ExamAssignmentResponseDTO>builder()
                .content(dtoList)
                .currentPage(assignmentPage.getNumber())
                .pageSize(assignmentPage.getSize())
                .totalPages(assignmentPage.getTotalPages())
                .totalElements(assignmentPage.getTotalElements())
                .hasNext(assignmentPage.hasNext())
                .hasPrevious(assignmentPage.hasPrevious())
                .build();
    }

    /**
     * UPDATED: Loads last attempt info for frontend display
     */
    private ExamAssignmentResponseDTO mapToDTO(ExamAssignment a, Exam exam, LocalDateTime now) {

        boolean expired = a.getEndTime() != null && now.isAfter(a.getEndTime());

        String finalStatus;
        if (STATUS_COMPLETED.equals(a.getStatus())) {
            finalStatus = STATUS_COMPLETED;
        } else if (expired) {
            finalStatus = "EXPIRED";
        } else {
            finalStatus = a.getStatus();
        }

        // NEW: Fetch last attempt
        Integer lastPercentage = null;
        String lastResult = null;

        var lastAttemptOpt = attemptRepository.findTopByAssignmentIdOrderByAttemptNumberDesc(a.getId());

        if (lastAttemptOpt.isPresent()) {
            var attempt = lastAttemptOpt.get();
            lastPercentage = attempt.getPercentage();
            lastResult = attempt.getPassed() ? "PASS" : "FAIL";
        }

        return ExamAssignmentResponseDTO.builder()
                .id(a.getId())
                .examTitle(exam.getTitle())
                .examDescription(exam.getDescription())
                .totalQuestions(exam.getTotalQuestions())
                .passingPercentage(exam.getPassingPercentage())
                .status(finalStatus)
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .canStart(canStartExam(a, now))
                .statusMessage(getStatusMessage(a, now))
                .attemptsUsed(a.getAttemptsUsed())
                .maxAttempts(a.getMaxAttempts())
                // NEW FIELDS
                .lastPercentage(lastPercentage)
                .lastResult(lastResult)
                .build();
    }

    @Transactional
    public TestSessionDTO startAssignedExam(Long assignmentId, Long companyId, Long employeeId) {
        ExamAssignment a = examAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (!a.getCompanyId().equals(companyId) || !a.getEmployeeId().equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }

        LocalDateTime now = LocalDateTime.now();
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment expired");
        }

        Exam exam = examRepository.findById(a.getExamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (STATUS_ASSIGNED.equals(a.getStatus())) {
            a.setStatus(STATUS_IN_PROGRESS);
            examAssignmentRepository.save(a);
        }

        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByPositionAsc(exam.getId());
        List<Long> qIds = eqs.stream().map(ExamQuestion::getQuestionId).toList();
        List<Question> questions = questionRepository.findAllById(qIds);

        SecureRandom rnd = new SecureRandom();
        List<ExamQuestionViewDTO> views = new ArrayList<>();

        for (ExamQuestion eq : eqs) {
            Question q = questions.stream()
                    .filter(qq -> qq.getId().equals(eq.getQuestionId()))
                    .findFirst()
                    .orElse(null);

            if (q == null) continue;

            views.add(ExamQuestionViewDTO.builder()
                    .id(q.getId())
                    .questionText(q.getQuestionText())
                    .optionA(q.getOptionA())
                    .optionB(q.getOptionB())
                    .optionC(q.getOptionC())
                    .optionD(q.getOptionD())
                    .build());
        }

        Collections.shuffle(views, rnd);

        return TestSessionDTO.builder()
                .assignmentId(a.getId())
                .examId(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .perQuestionSeconds(exam.getPerQuestionSeconds())
                .reviewMinutes(exam.getReviewMinutes())
                .startedAt(now)
                .endsAt(a.getEndTime())
                .questions(views)
                .build();
    }

    private boolean canStartExam(ExamAssignment a, LocalDateTime now) {
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) return false;
        if (!STATUS_ASSIGNED.equals(a.getStatus()) && !STATUS_IN_PROGRESS.equals(a.getStatus())) return false;
        if (a.getStartTime() != null && now.isBefore(a.getStartTime())) return false;
        return true;
    }

    private String getStatusMessage(ExamAssignment a, LocalDateTime now) {
        if (STATUS_COMPLETED.equals(a.getStatus())) return "Completed";
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) return "Expired";
        if (a.getStartTime() != null && now.isBefore(a.getStartTime()))
            return "Available from " + a.getStartTime();
        return "Ready to start";
    }

    private void validateEmployees(List<Long> ids, Long companyId, String jwt) {
        List<UserSummaryDTO> employees = userClient.lookupEmployeesForCompany(jwt);

        Map<Long, UserSummaryDTO> map = employees.stream()
                .collect(Collectors.toMap(UserSummaryDTO::id, u -> u));

        for (Long id : ids) {
            if (!map.containsKey(id)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Employee not in company: " + id
                );
            }
        }
    }
}
