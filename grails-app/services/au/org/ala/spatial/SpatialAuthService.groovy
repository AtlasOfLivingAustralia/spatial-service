package au.org.ala.spatial

class SpatialAuthService {

    def authService
    SpatialConfig spatialConfig

    // handle instance where role is not copied into userDetails
    boolean userInRole(String role) {
        if (!authService.userInRole(role)) {
            try {
                return authService.delegateService.getAttribute('role').replaceAll('[\\[\\] ]','').split(',').contains(role)
            } catch (Exception ignored) {

            }
            return false
        }
        return true
    }

    boolean isAdmin() {
        return userInRole(spatialConfig.auth.admin_role) || userInRole("ala/internal")
    }
}
