package com.onlinetest.questionexam.repository;

import com.onlinetest.questionexam.entity.ExamAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExamAssignmentRepository extends JpaRepository<ExamAssignment, Long> {
    // Existing method
    List<ExamAssignment> findByCompanyIdAndEmployeeIdOrderByCreatedDateDesc(
            Long companyId,
            Long employeeId
    );

    Page<ExamAssignment> findByCompanyIdAndEmployeeIdOrderByCreatedDateDesc(
            Long companyId,
            Long employeeId,
            Pageable pageable
    );

    List<ExamAssignment> findByCompanyIdAndEmployeeIdAndStatusIn(
            Long companyId,
            Long employeeId,
            List<String> statuses
    );

    boolean existsByCompanyIdAndExamIdAndEmployeeId(
            Long companyId,
            Long examId,
            Long employeeId
    );

    long countByCompanyIdAndExamId(Long companyId, Long examId);

    // Admin Dashboard Support
    List<ExamAssignment> findByCompanyId(Long companyId);

    // Timeline Sorting Logic
    @Query("""
        SELECT ea
        FROM ExamAssignment ea
        WHERE ea.companyId = :companyId
          AND ea.employeeId = :employeeId
          AND ea.createdDate <= :now
        ORDER BY
         CASE
           WHEN ea.status = 'COMPLETED' THEN 2
           WHEN ea.endTime IS NOT NULL AND ea.endTime < :now THEN 2
           ELSE 1
          END ASC,
          ea.createdDate DESC
    """)
    Page<ExamAssignment> findAssignmentsForTimeline(
            @Param("companyId") Long companyId,
            @Param("employeeId") Long employeeId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}