package com.example.usermanagementservice.api;

import com.example.usermanagementservice.api.dto.ChangePasswordRequest;
import com.example.usermanagementservice.api.dto.UpdateCompanyRequest;
import com.example.usermanagementservice.api.dto.UpdateUserProfileRequest;
import com.example.usermanagementservice.api.dto.UserProfileResponse;
import com.example.usermanagementservice.company.CompanyRepository;
import com.example.usermanagementservice.service.CompanyService;
import com.example.usermanagementservice.service.UserService;
import com.example.usermanagementservice.user.Role;
import com.example.usermanagementservice.user.User;
import com.example.usermanagementservice.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final CompanyService companyService;
    private final CompanyRepository companyRepository;

    // ---------------------------
    // GET MY PROFILE
    // ---------------------------
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication auth) {

        Long userId = extractUserId(auth);

        User user = userRepository.findProfileByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        var company = user.getCompany();

        List<String> roles = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        UserProfileResponse response = new UserProfileResponse(
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

        return ResponseEntity.ok(response);
    }

    // ---------------------------
    // UPDATE MY PROFILE
    // ---------------------------
    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ResponseEntity<?> updateMyProfile(
            @Valid @RequestBody UpdateUserProfileRequest req,
            Authentication auth
    ) {

        Long userId = extractUserId(auth);

        User updated = userService.updateProfile(userId, req);

        return ResponseEntity.ok(Map.of(
                "message", "Profile updated",
                "userId", updated.getId()
        ));
    }

    // ---------------------------
    // CHANGE PASSWORD
    // ---------------------------
    @PatchMapping("/password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','EMPLOYEE')")
    public ResponseEntity<?> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication auth
    ) {

        Long userId = extractUserId(auth);

        userService.changePassword(
                userId,
                req.getOldPassword(),
                req.getNewPassword()
        );

        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    // ---------------------------
    // UPDATE COMPANY (SUPER ADMIN)
    // ---------------------------
    @PatchMapping("/company")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateMyCompany(
            @Valid @RequestBody UpdateCompanyRequest req,
            Authentication auth
    ) {

        Long userId = extractUserId(auth);
        Long companyId = extractCompanyId(auth);

        User caller = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (caller.getCompany() == null ||
                !caller.getCompany().getId().equals(companyId)) {

            return ResponseEntity.status(403).body(Map.of(
                    "message", "You cannot update another company's details"
            ));
        }

        companyService.updateCompany(companyId, req);

        return ResponseEntity.ok(Map.of(
                "message", "Company updated",
                "companyId", companyId
        ));
    }

    // ==================================================
    // SAFE AUTH EXTRACTION (THIS FIXES YOUR 400 ERROR)
    // ==================================================

    private Long extractUserId(Authentication auth) {
        Map<String, Object> details = extractDetails(auth);
        Object v = details.get("userId");

        if (v == null) {
            throw new RuntimeException("userId missing in authentication details");
        }

        return Long.valueOf(v.toString());
    }

    private Long extractCompanyId(Authentication auth) {
        Map<String, Object> details = extractDetails(auth);
        Object v = details.get("companyId");

        if (v == null) {
            throw new RuntimeException("companyId missing in authentication details");
        }

        return Long.valueOf(v.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDetails(Authentication auth) {
        if (auth == null || auth.getDetails() == null) {
            throw new RuntimeException("Authentication details missing");
        }

        if (!(auth.getDetails() instanceof Map)) {
            throw new RuntimeException("Invalid authentication details format");
        }

        return (Map<String, Object>) auth.getDetails();
    }
}
