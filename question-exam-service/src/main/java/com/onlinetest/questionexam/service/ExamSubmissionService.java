package com.onlinetest.questionexam.service;

import com.onlinetest.questionexam.dto.ExamResultDTO;
import com.onlinetest.questionexam.dto.ExamScoreDistributionDTO;
import com.onlinetest.questionexam.dto.ExamSubmitRequestDTO;
import com.onlinetest.questionexam.entity.*;
import com.onlinetest.questionexam.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExamSubmissionService {

    private final ExamRepository examRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAttemptAnswerRepository attemptAnswerRepository;

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_ASSIGNED = "ASSIGNED";

    // ===============================
    // Employee score distribution
    // ===============================
    @Transactional(readOnly = true)
    public ExamScoreDistributionDTO getEmployeeScoreDistribution(Long companyId, Long employeeId) {

        long scoreBelow60 =
                attemptRepository.countLatestAttemptsByPercentageRange(companyId, employeeId, 0, 59);

        long score60to75 =
                attemptRepository.countLatestAttemptsByPercentageRange(companyId, employeeId, 60, 74);

        long score75to85 =
                attemptRepository.countLatestAttemptsByPercentageRange(companyId, employeeId, 75, 84);

        long score85to100 =
                attemptRepository.countLatestAttemptsByPercentageRange(companyId, employeeId, 85, 100);

        return ExamScoreDistributionDTO.builder()
                .scoreBelow60(scoreBelow60)
                .score60to75(score60to75)
                .score75to85(score75to85)
                .score85to100(score85to100)
                .build();
    }

    // ===============================
    // Submission logic
    // ===============================
    public ExamResultDTO submitFinalAnswers(Long companyId, Long employeeId, ExamSubmitRequestDTO req) {

        ExamAssignment a = assignmentRepository.findById(req.getAssignmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));

        if (!Objects.equals(a.getCompanyId(), companyId) || !Objects.equals(a.getEmployeeId(), employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Assignment not accessible");
        }

        if ("REVOKED".equals(a.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment revoked");
        }

        Exam exam = examRepository.findById(req.getExamId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found"));

        if (!Objects.equals(exam.getCompanyId(), companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Exam not found");
        }

        LocalDateTime now = LocalDateTime.now();

        if (a.getStartTime() != null && now.isBefore(a.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment window not started");
        }
        if (a.getEndTime() != null && now.isAfter(a.getEndTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignment window ended");
        }

        int attemptsUsed = (a.getAttemptsUsed() == null) ? 0 : a.getAttemptsUsed();

        List<ExamQuestion> eqs = examQuestionRepository.findByExamIdOrderByPositionAsc(exam.getId());
        Map<Long, Integer> questionIdToPosition = new HashMap<>();
        List<Long> qIds = new ArrayList<>(eqs.size());

        for (ExamQuestion eq : eqs) {
            qIds.add(eq.getQuestionId());
            questionIdToPosition.put(eq.getQuestionId(), eq.getPosition());
        }

        var answers = Optional.ofNullable(req.getAnswers()).orElse(List.of());
        Set<Long> uniqueQ = new HashSet<>();

        for (ExamSubmitRequestDTO.AnswerDTO ans : answers) {
            if (!questionIdToPosition.containsKey(ans.getQuestionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid question: " + ans.getQuestionId());
            }
            if (!uniqueQ.add(ans.getQuestionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate answer: " + ans.getQuestionId());
            }
            if (!Set.of("A", "B", "C", "D").contains(ans.getSelected())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid option: " + ans.getSelected());
            }
        }

        List<Question> qs = questionRepository.findAllById(qIds);
        Map<Long, Question> qMap = qs.stream().collect(Collectors.toMap(Question::getId, q -> q));

        int total = eqs.size();
        int correct = 0;

        Map<Long, String> selectedMap = answers.stream()
                .collect(Collectors.toMap(
                        ExamSubmitRequestDTO.AnswerDTO::getQuestionId,
                        ExamSubmitRequestDTO.AnswerDTO::getSelected
                ));

        List<ExamAttemptAnswer> persistedAnswers = new ArrayList<>(total);

        for (ExamQuestion eq : eqs) {
            Long qid = eq.getQuestionId();
            Question q = qMap.get(qid);
            String selected = selectedMap.get(qid);

            boolean isCorrect = selected != null &&
                    q != null &&
                    q.getCorrectAnswer().name().equalsIgnoreCase(selected);

            if (isCorrect) correct++;

            ExamAttemptAnswer aaa = new ExamAttemptAnswer();
            aaa.setQuestionId(qid);
            aaa.setSelected(selected != null ? Question.CorrectAnswer.valueOf(selected) : null);
            aaa.setIsCorrect(isCorrect);
            aaa.setPosition(eq.getPosition());

            persistedAnswers.add(aaa);
        }

        int percentage = total == 0 ? 0 : (int) Math.round(100.0 * correct / total);
        int passingThreshold = Optional.ofNullable(exam.getPassingPercentage()).orElse(0);
        boolean passed = percentage >= passingThreshold;

        int nextAttemptNum = attemptsUsed + 1;

        ExamAttempt attempt = new ExamAttempt();
        attempt.setCompanyId(companyId);
        attempt.setExamId(exam.getId());
        attempt.setAssignmentId(a.getId());
        attempt.setEmployeeId(employeeId);
        attempt.setAttemptNumber(nextAttemptNum);
        attempt.setTotalQuestions(total);
        attempt.setCorrectAnswers(correct);
        attempt.setPercentage(percentage);
        attempt.setPassed(passed);
        attempt.setStatus("SUBMITTED");

        ExamAttempt savedAttempt = attemptRepository.save(attempt);

        for (ExamAttemptAnswer aaa : persistedAnswers) {
            aaa.setAttemptId(savedAttempt.getId());
        }
        attemptAnswerRepository.saveAll(persistedAnswers);

        a.setAttemptsUsed(nextAttemptNum);
        a.setLastResult(passed ? "PASSED" : "FAILED");
        a.setLastPercentage(percentage);
        a.setLastSubmittedAt(now);

        if (passed) {
            a.setStatus(STATUS_COMPLETED);
        } else {
            if (attemptsUsed == 0) {
                a.setMaxAttempts(2);
                a.setStatus(STATUS_ASSIGNED);
            } else {
                a.setStatus(STATUS_COMPLETED);
            }
        }

        assignmentRepository.save(a);

        int remaining = Math.max(0, a.getMaxAttempts() - nextAttemptNum);

        return ExamResultDTO.builder()
                .attemptId(savedAttempt.getId())
                .attemptNumber(nextAttemptNum)
                .totalQuestions(total)
                .correctAnswers(correct)
                .percentage(percentage)
                .passed(passed)
                .status(passed ? "PASS" : "FAIL")
                .passingThreshold(passingThreshold)
                .maxAttempts(a.getMaxAttempts())
                .attemptsUsed(nextAttemptNum)
                .attemptsRemaining(remaining)
                .submittedAt(savedAttempt.getCreatedDate())
                .questions(List.of())
                .build();
    }
}
