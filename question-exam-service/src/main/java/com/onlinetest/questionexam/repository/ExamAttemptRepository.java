package com.onlinetest.questionexam.repository;

import com.onlinetest.questionexam.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    long countByAssignmentId(Long assignmentId);

    Optional<ExamAttempt> findTopByAssignmentIdOrderByAttemptNumberDesc(Long assignmentId);

    List<ExamAttempt> findByAssignmentIdOrderByAttemptNumberAsc(Long assignmentId);

    // Batch fetch for AdminDashboardService optimization
    List<ExamAttempt> findByAssignmentIdIn(List<Long> assignmentIds);

    // Employee dashboard (latest attempt only)
    @Query("""
        SELECT COUNT(ea)
        FROM ExamAttempt ea
        WHERE ea.companyId = :companyId
          AND ea.employeeId = :employeeId
          AND ea.percentage BETWEEN :min AND :max
          AND ea.attemptNumber = (
              SELECT MAX(sub.attemptNumber)
              FROM ExamAttempt sub
              WHERE sub.assignmentId = ea.assignmentId
          )
    """)
    long countLatestAttemptsByPercentageRange(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("min") int min,
            @Param("max") int max
    );

    // Admin dashboard (per exam bar chart)
    @Query("""
        SELECT COUNT(ea)
        FROM ExamAttempt ea
        WHERE ea.companyId = :companyId
          AND ea.examId = :examId
          AND ea.passed = true
          AND ea.attemptNumber = (
              SELECT MAX(sub.attemptNumber)
              FROM ExamAttempt sub
              WHERE sub.assignmentId = ea.assignmentId
          )
    """)
    long countPassedByExam(
            @Param("companyId") Long companyId,
            @Param("examId") Long examId
    );

    @Query("""
        SELECT COUNT(ea)
        FROM ExamAttempt ea
        WHERE ea.companyId = :companyId
          AND ea.examId = :examId
          AND ea.passed = false
          AND ea.attemptNumber = (
              SELECT MAX(sub.attemptNumber)
              FROM ExamAttempt sub
              WHERE sub.assignmentId = ea.assignmentId
          )
    """)
    long countFailedByExam(
            @Param("companyId") Long companyId,
            @Param("examId") Long examId
    );
}
