package com.example.usermanagementservice.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    List<User> findByCompany_Id(Long companyId);

    @Query("""
        SELECT u
        FROM User u
        JOIN u.roles r
        WHERE u.company.id = :companyId
          AND r.name = 'ROLE_EMPLOYEE'
    """)
    List<User> findEmployeesByCompanyId(@Param("companyId") Long companyId);

    @Query("""
        SELECT u
        FROM User u
        LEFT JOIN FETCH u.company
        LEFT JOIN FETCH u.roles
        WHERE u.id = :userId
    """)
    Optional<User> findProfileByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT u
        FROM User u
        JOIN u.roles r
        WHERE u.company.id = :companyId
          AND (
              :search IS NULL OR
              LOWER(CONCAT(u.firstName, ' ', u.lastName))
              LIKE LOWER(CONCAT('%', :search, '%'))
          )
          AND (
              :role IS NULL OR r.name = :role
          )
    """)
    Page<User> findUsersWithFilters(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("role") String role,
            Pageable pageable
    );

    // =================================================
    // NEW: ENABLED-ONLY QUERIES (SAFE DEFAULTS)
    // =================================================

    Optional<User> findByEmailAndEnabledTrue(String email);

    Optional<User> findByUsernameAndEnabledTrue(String username);

    List<User> findByCompany_IdAndEnabledTrue(Long companyId);

    @Query("""
        SELECT u
        FROM User u
        JOIN u.roles r
        WHERE u.company.id = :companyId
          AND u.enabled = true
          AND (
              :search IS NULL OR
              LOWER(CONCAT(u.firstName, ' ', u.lastName))
              LIKE LOWER(CONCAT('%', :search, '%'))
          )
          AND (
              :role IS NULL OR r.name = :role
          )
    """)
    Page<User> findEnabledUsersWithFilters(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("role") String role,
            Pageable pageable
    );
}
