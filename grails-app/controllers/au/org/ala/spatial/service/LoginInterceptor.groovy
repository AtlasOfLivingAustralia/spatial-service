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

    LoginInterceptor() {
        match controller: '*'
    }

    boolean before() {
        if (grailsApplication.config.security.cas.disableCAS.toBoolean() || grailsApplication.config.security.cas.bypass.toBoolean()) {
            return true
        }

        def controller = grailsApplication.getArtefactByLogicalPropertyName("Controller", controllerName)
        Class controllerClass = controller?.clazz
        def method = controllerClass?.getMethod(actionName ?: "index", [] as Class[])

        if (method?.isAnnotationPresent(SkipSecurityCheck)) {
            return true
        }

        def role  // if require a certain level of ROLE
        if ( method?.isAnnotationPresent(RequirePermission) || controllerClass?.isAnnotationPresent(RequirePermission) ) {
            if (serviceAuthService.isLoggedIn() || serviceAuthService.hasValidApiKey()) {
                return true
            } else {
                accessDenied(STATUS_UNAUTHORISED,'Forbidden, ApiKey or user login required!')
            }
        }
        // UserId is required
        // May need to check role
        if (method?.isAnnotationPresent(RequireAdmin) || controllerClass?.isAnnotationPresent(RequireAdmin)) {
            role = grailsApplication.config.auth.admin_role //recommended: ROLE_ADMIN
        } else if (method?.isAnnotationPresent(RequireLogin) || controllerClass?.isAnnotationPresent(RequireLogin)) {
            RequireLogin requireAuthentication = method.getAnnotation(RequireLogin.class)
            role = requireAuthentication?.role()
        } else {
            return true
        }

        if ( serviceAuthService.isAuthenticated() ) {
            //Check role
            if (!Strings.isNullOrEmpty(role)) {
                if ( !serviceAuthService.isRoleOf(role)) {
                    accessDenied(STATUS_FORBIDDEN, 'Forbidden, require a user with role: '+ role)
                }
            }
            return true
        } else {
            accessDenied(STATUS_UNAUTHORISED,'Forbidden, user login required!')
        }
    }


    boolean after() { true }

    void afterView() {
        // no-op
    }

    boolean accessDenied(status, message) {
        log.debug("Access denied : " + controllerName +"->" + actionName ?: "index")
        Enumeration<String> e = request.getHeaderNames()
        while(e.hasMoreElements()){
            String header = e.nextElement()
            String value = request.getHeader(header)
            log.debug(header + ":" + value)
        }

        if (!request.getHeader("accept")?.contains("application/json")) {
            String redirectUrl = grailsApplication.config.security.cas.loginUrl + "?service=" +
                    grailsApplication.config.security.cas.appServerName + request.forwardURI + (request.queryString ? '?' + request.queryString : '')
            render view: "/login.gsp", model: [status: status, url: redirectUrl]
            return false
        } else {
            response.status = status
            Map error = [error: message]
            render error as JSON
            return false
        }
    }
}
