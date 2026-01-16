package com.onlinetest.questionexam.repository;

import com.onlinetest.questionexam.entity.Exam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    // Pagination without search (5 per page handled in service)
    Page<Exam> findByCompanyId(Long companyId, Pageable pageable);

    // Pagination with search by exam title (case-insensitive)
    Page<Exam> findByCompanyIdAndTitleContainingIgnoreCase(
            Long companyId,
            String title,
            Pageable pageable
    );
}
