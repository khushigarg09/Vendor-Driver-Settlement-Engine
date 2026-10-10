package com.moveinsync.settlement.security;

import com.moveinsync.settlement.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component("settlementSecurity")
@RequiredArgsConstructor
public class SettlementSecurityEvaluator {

    private final DriverRepository driverRepository;

    // Default username-to-driver mappings for testing and demonstration
    private static final Map<String, UUID> USER_DRIVER_MAPPINGS = Map.of(
            "employee", UUID.fromString("44444444-4444-4444-4444-444444444441"),
            "driver2", UUID.fromString("44444444-4444-4444-4444-444444444442")
    );

    /**
     * Resource-level authorization: checks whether the current user is authorized to access the given driver's records.
     *
     * @param authentication the current Spring Security authentication
     * @param driverId       the target driver's UUID
     * @return true if authorized, false otherwise
     */
    public boolean canAccessDriver(Authentication authentication, UUID driverId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Admins and Approvers have organization-wide clearance
        boolean isPrivileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_APPROVER"));

        if (isPrivileged) {
            return true;
        }

        // Regular employee/driver: must strictly match their bound driver entity
        String username = authentication.getName();
        UUID mappedDriverId = USER_DRIVER_MAPPINGS.get(username.toLowerCase());

        if (mappedDriverId != null && mappedDriverId.equals(driverId)) {
            return true;
        }

        // Fallback: check if driver code equals username
        return driverRepository.findById(driverId)
                .map(d -> d.getDriverCode().equalsIgnoreCase(username))
                .orElse(false);
    }
}
