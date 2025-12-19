package com.example.usermanagementservice.api;

import com.example.usermanagementservice.api.dto.UserProfileResponse;
import com.example.usermanagementservice.api.dto.UserSummaryDTO;
import com.example.usermanagementservice.user.Role;
import com.example.usermanagementservice.user.User;
import com.example.usermanagementservice.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserRepository userRepository;

    @GetMapping("/company/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<UserSummaryDTO>> getUsersForMyCompany(Authentication auth) {

        Long companyId = extractCompanyId(auth);

        List<UserSummaryDTO> dtos = userRepository.findByCompany_Id(companyId)
                .stream()
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(dtos);
    }
    @GetMapping("/company/me/paged")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<Page<UserSummaryDTO>> getUsersForMyCompanyPaged(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role
    ) {

        Long companyId = extractCompanyId(auth);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("id").descending()
        );

        Page<UserSummaryDTO> result = userRepository
                .findUsersWithFilters(companyId, search, role, pageable)
                .map(this::toSummary);

        return ResponseEntity.ok(result);
    }


    @GetMapping("/employees/company/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<List<UserSummaryDTO>> getEmployeesForMyCompany(Authentication auth) {

        Long companyId = extractCompanyId(auth);

        List<UserSummaryDTO> dtos = userRepository.findByCompany_Id(companyId)
                .stream()
                .filter(u -> u.getRoles().stream()
                        .anyMatch(r -> r.getName().equals("ROLE_EMPLOYEE")))
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public ResponseEntity<UserProfileResponse> getUserById(
            @PathVariable Long userId,
            Authentication auth
    ) {

        Long callerCompanyId = extractCompanyId(auth);
        String callerRole = extractRole(auth);

        User user = userRepository.findProfileByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getCompanyId().equals(callerCompanyId)) {
            return ResponseEntity.status(403).build();
        }

        if (callerRole.equals("ADMIN")) {
            boolean restricted = user.getRoles().stream().anyMatch(r ->
                    r.getName().equals("ROLE_ADMIN") ||
                            r.getName().equals("ROLE_SUPER_ADMIN")
            );
            if (restricted) {
                throw new RuntimeException("Admins cannot manage Admins or Super Admins");
            }
        }
        var company = user.getCompany();
        var roles = user.getRoles().stream().map(Role::getName).toList();

        UserProfileResponse dto = new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDateOfBirth(),
                user.getGender(),
                company != null ? company.getId() : null,
                company != null ? company.getName() : null,
                company != null ? company.getGstNumber() : null,
                roles
        );

        return ResponseEntity.ok(dto);
    }

    private UserSummaryDTO toSummary(User u) {
        return new UserSummaryDTO(
                u.getId(),
                u.getCompanyId(),
                u.getRoles().stream().map(Role::getName).toList(),
                u.getEmail(),
                u.getName()
        );
    }

    @SuppressWarnings("unchecked")
    private Long extractCompanyId(Authentication auth) {
        Map<String, Object> map = (Map<String, Object>) auth.getDetails();
        return Long.valueOf(map.get("companyId").toString());
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities()
                .iterator()
                .next()
                .getAuthority()
                .replace("ROLE_", "");
    }
}
