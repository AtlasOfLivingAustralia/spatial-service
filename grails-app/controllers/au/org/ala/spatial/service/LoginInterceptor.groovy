package au.org.ala.spatial.service

import au.org.ala.RequireAdmin
import au.org.ala.RequireLogin
import au.org.ala.RequirePermission
import au.org.ala.SkipSecurityCheck
import com.google.common.base.Strings
import grails.converters.JSON

/**
 * Copy and simplify from plugin "ala-ws-security-plugin:2.0"
 * Remove IP/method/controller whitelist support
 * collecting api_key from POST body (backward compatiblity support)
 *
 * TODO use ALA standard apiKey method: store apiKey in hearder
 */
class LoginInterceptor {

    static final String LOCALHOST_IP = '127.0.0.1'
    static final int STATUS_UNAUTHORISED = 401
    static final int STATUS_FORBIDDEN = 403

    ServiceAuthService serviceAuthService
    def authService

    LoginInterceptor() {
        match controller: '*'
    }

    boolean before() {
        if (!grailsApplication.config.security.oidc.enabled.toBoolean()) {
            return true
        }

        def controller = grailsApplication.getArtefactByLogicalPropertyName("Controller", controllerName)
        Class controllerClass = controller?.clazz
        def method = controllerClass?.getMethod(actionName ?: "index", [] as Class[])

        if (method?.isAnnotationPresent(SkipSecurityCheck)) {
            return true
        }

        //Calculating the required permission.
        def permissionLevel = null
        //Permission on method has the top priority
        if (method?.isAnnotationPresent(RequirePermission.class)) {
            permissionLevel = RequirePermission
        } else if (method?.isAnnotationPresent(RequireLogin.class)) {
            permissionLevel = RequireLogin
        } else if (method?.isAnnotationPresent(RequireAdmin.class)) {
            permissionLevel = RequireAdmin
        }

        if (Objects.isNull(permissionLevel)) {
            if (controllerClass?.isAnnotationPresent(RequirePermission.class)) {
                permissionLevel = RequirePermission
            } else if (controllerClass?.isAnnotationPresent(RequireLogin.class)) {
                permissionLevel = RequireLogin
            } else if (controllerClass?.isAnnotationPresent(RequireAdmin.class)) {
                permissionLevel = RequireAdmin
            }
        }

        //Permission check
        def role  // if require a certain level of ROLE
        if (permissionLevel == RequirePermission) {
            if (serviceAuthService.isLoggedIn() || serviceAuthService.hasValidApiKey()) {
                return true
            } else {
                return accessDenied(STATUS_UNAUTHORISED, 'Forbidden, ApiKey or user login required!')
            }
        } else if (permissionLevel == RequireAdmin) {
            if (serviceAuthService.hasValidApiKey())
                return true

            role = grailsApplication.config.getProperty('auth.admin_role', String, 'ROLE_ADMIN')
        } else if (permissionLevel == RequireLogin) {
            RequireLogin requireAuthentication = method.getAnnotation(RequireLogin.class)
            role = requireAuthentication?.role()
        } else {
            return true
        }

        if (serviceAuthService.isAuthenticated()) {
            //Check role
            if (!Strings.isNullOrEmpty(role)) {
                if (!serviceAuthService.isRoleOf(role)) {
                    return accessDenied(STATUS_FORBIDDEN, 'Forbidden, require a user with role: ' + role)
                }
            }
            return true
        } else {
            return accessDenied(STATUS_UNAUTHORISED, 'Forbidden, user login required!')
        }
    }


    boolean after() { true }

    void afterView() {
        // no-op
    }

    boolean accessDenied(status, message) {
        log.debug("Access denied : " + controllerName + "->" + actionName ?: "index")

        if (!request.getHeader("accept")?.toLowerCase().contains("application/json")) {
            redirect(absolute: false, uri: authService.loginUrl(request))

            return false
        } else {
            response.status = status
            Map error = [error: message]
            render error as JSON
            return false
        }
    }
}
