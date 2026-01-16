package com.onlinetest.questionexam.service;

import com.onlinetest.questionexam.dto.AdminExamOverviewDTO;
import com.onlinetest.questionexam.dto.DashboardTrendDTO;
import com.onlinetest.questionexam.dto.ExamBarChartDTO;
import com.onlinetest.questionexam.entity.ExamAssignment;
import com.onlinetest.questionexam.entity.ExamAttempt;
import com.onlinetest.questionexam.integration.UserClient;
import com.onlinetest.questionexam.repository.ExamAssignmentRepository;
import com.onlinetest.questionexam.repository.ExamAttemptRepository;
import com.onlinetest.questionexam.repository.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final ExamRepository examRepository;
    private final ExamAssignmentRepository assignmentRepository;
    private final ExamAttemptRepository attemptRepository;
    private final UserClient userClient;

    // OVERVIEW: Global Cards + Monthly Aggregate Trends (Last 6 Months)
    public AdminExamOverviewDTO getExamOverview(Long companyId, String token) {

        // 1. Fetch total employees
        long totalEmployees = 0;
        try {
            totalEmployees = userClient.lookupEmployeesForCompany(token).size();
        } catch (Exception e) {
            System.err.println("Failed to fetch employees: " + e.getMessage());
        }

        // 2. Fetch EVERY assignment
        List<ExamAssignment> allAssignments = assignmentRepository.findByCompanyId(companyId);

        // 3. Batch fetch attempts
        List<Long> assignmentIds = allAssignments.stream()
                .map(ExamAssignment::getId)
                .collect(Collectors.toList());

        List<ExamAttempt> allAttempts = attemptRepository.findByAssignmentIdIn(assignmentIds);

        Map<Long, List<ExamAttempt>> attemptsByAssignmentMap = allAttempts.stream()
                .collect(Collectors.groupingBy(ExamAttempt::getAssignmentId));

        // A. Global Stats (Calculated on ALL data, regardless of date)
        Map<String, Long> globalCounts = calculateStatusCounts(allAssignments, attemptsByAssignmentMap);
        double globalTotal = allAssignments.size();

        // B. Monthly Trends (Filtered for LAST 6 MONTHS)
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM-yyyy");

        // Calculate the cutoff date: 1st day of the month, 6 months ago
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6).withDayOfMonth(1);

        // Grouping creates a HashMap (Random Order)
        Map<String, List<ExamAssignment>> assignmentsByMonth = allAssignments.stream()
                .filter(a -> a.getCreatedDate() != null)
                .filter(a -> a.getCreatedDate().toLocalDate().isAfter(sixMonthsAgo)
                        || a.getCreatedDate().toLocalDate().isEqual(sixMonthsAgo))
                .collect(Collectors.groupingBy(a -> a.getCreatedDate().format(monthFormatter)));

        List<DashboardTrendDTO> trendList = new ArrayList<>();

        for (Map.Entry<String, List<ExamAssignment>> entry : assignmentsByMonth.entrySet()) {
            String monthName = entry.getKey();
            List<ExamAssignment> monthlyAssignments = entry.getValue();
            double monthTotal = monthlyAssignments.size();
            if (monthTotal > 0) {
                Map<String, Long> monthCounts = calculateStatusCounts(monthlyAssignments, attemptsByAssignmentMap);
                trendList.add(DashboardTrendDTO.builder()
                        .month(monthName)
                        .passedPercentage((monthCounts.get("PASS") * 100.0) / monthTotal)
                        .failedPercentage((monthCounts.get("FAIL") * 100.0) / monthTotal)
                        .notAttemptedPercentage((monthCounts.get("NOT_ATTEMPTED") * 100.0) / monthTotal)
                        .build());
            }
        }

        // Sort the list chronologically
        trendList.sort(Comparator.comparing(dto -> {
            try {
                return YearMonth.parse(dto.getMonth(), monthFormatter);
            } catch (Exception e) {
                return YearMonth.now();
            }
        }));
        return AdminExamOverviewDTO.builder()
                .totalEmployeeCount(totalEmployees)
                // Global Stats
                .passedPercentage(globalTotal > 0 ? (globalCounts.get("PASS") * 100.0) / globalTotal : 0)
                .failedPercentage(globalTotal > 0 ? (globalCounts.get("FAIL") * 100.0) / globalTotal : 0)
                .notAttemptedPercentage(globalTotal > 0 ? (globalCounts.get("NOT_ATTEMPTED") * 100.0) / globalTotal : 0)
                // Trend Data (Sorted & Filtered)
                .monthlyTrends(trendList)
                .build();
    }
    private Map<String, Long> calculateStatusCounts(List<ExamAssignment> assignments, Map<Long, List<ExamAttempt>> attemptsMap) {
        long passed = 0;
        long failed = 0;
        long notAttempted = 0;

        for (ExamAssignment assignment : assignments) {
            List<ExamAttempt> attempts = attemptsMap.getOrDefault(assignment.getId(), Collections.emptyList());

            Optional<ExamAttempt> lastAttemptOpt = attempts.stream()
                    .max(Comparator.comparingInt(ExamAttempt::getAttemptNumber));

            if (lastAttemptOpt.isPresent()) {
                if (Boolean.TRUE.equals(lastAttemptOpt.get().getPassed())) {
                    passed++;
                } else {
                    failed++;
                }
            } else {
                notAttempted++;
            }
        }
        Map<String, Long> result = new HashMap<>();
        result.put("PASS", passed);
        result.put("FAIL", failed);
        result.put("NOT_ATTEMPTED", notAttempted);
        return result;
    }
    public ExamBarChartDTO getExamBarChart(Long companyId, Long examId) {
        long totalAssigned = assignmentRepository.countByCompanyIdAndExamId(companyId, examId);
        long passed = attemptRepository.countPassedByExam(companyId, examId);
        long failed = attemptRepository.countFailedByExam(companyId, examId);
        long notAttempted = Math.max(0, totalAssigned - (passed + failed));
        return ExamBarChartDTO.builder()
                .examId(examId)
                .passedCount(passed)
                .failedCount(failed)
                .notAttemptedCount(notAttempted)
                .build();
    }
}