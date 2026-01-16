package com.example.usermanagementservice.api;

import com.example.usermanagementservice.api.dto.UpdateUserProfileRequest;
import com.example.usermanagementservice.service.UserService;
import com.example.usermanagementservice.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserService userService;

    @PatchMapping("/{targetUserId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> updateEmployee(@PathVariable Long targetUserId,
                                            @Valid @RequestBody UpdateUserProfileRequest req,
                                            Authentication auth) {

        Long callerUserId = extractUserId(auth);
        Long callerCompanyId = extractCompanyId(auth);
        String callerRole = extractRole(auth);

        User updated = userService.updateEmployeeProfile(
                targetUserId, callerCompanyId, callerRole, req
        );

        return ResponseEntity.ok(Map.of(
                "message", "Employee updated",
                "updatedUserId", updated.getId()
        ));
    }

    // =================================================
    // ENABLE / DISABLE USER (NEW FUNCTIONALITY)
    // =================================================

    @PatchMapping("/{targetUserId}/disable")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> disableUser(@PathVariable Long targetUserId,
                                         Authentication auth) {

        Long callerUserId = extractUserId(auth);
        Long callerCompanyId = extractCompanyId(auth);
        String callerRole = extractRole(auth);

        userService.disableUser(
                targetUserId,
                callerUserId,
                callerCompanyId,
                callerRole
        );

        return ResponseEntity.ok(Map.of(
                "message", "User disabled successfully",
                "userId", targetUserId
        ));
    }

    @PatchMapping("/{targetUserId}/enable")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> enableUser(@PathVariable Long targetUserId,
                                        Authentication auth) {

        Long callerCompanyId = extractCompanyId(auth);
        String callerRole = extractRole(auth);

        userService.enableUser(
                targetUserId,
                callerCompanyId,
                callerRole
        );

        return ResponseEntity.ok(Map.of(
                "message", "User enabled successfully",
                "userId", targetUserId
        ));
    }

    // =================================================
    // HELPERS (UNCHANGED)
    // =================================================

    private Long extractUserId(Authentication auth) {
        return Long.valueOf(((Map<String, Object>) auth.getDetails()).get("userId").toString());
    }

    private Long extractCompanyId(Authentication auth) {
        return Long.valueOf(((Map<String, Object>) auth.getDetails()).get("companyId").toString());
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities().iterator().next().getAuthority().substring(5);
    }
}
