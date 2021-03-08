package au.org.ala


import au.org.ala.spatial.service.ServiceAuthService
import com.google.common.base.Strings

/**
 * Copy and simplify from plugin "ala-ws-security-plugin:2.0"
 * Remove IP/method/controller whitelist support
 * collecting api_key from POST body (backward compatiblity support)
 *
 * TODO use ALA standard apiKey method: store apiKey in hearder
 */
class LoginInterceptor {

    static final String LOCALHOST_IP = '127.0.0.1'
    static final int STATUS_UNAUTHORISED = 403
    static final String API_KEY_HEADER_NAME = "apiKey"

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

        def role  // if require a certain level of ROLE
        def securityCheck = false // if need to do security check

        if (method?.isAnnotationPresent(RequireAdmin) || controllerClass?.isAnnotationPresent(RequireAdmin)) {
            role= grailsApplication.config.auth.admin_role //recommended: ROLE_ADMIN
            securityCheck = true
        }else if (method?.isAnnotationPresent(RequireLogin) || controllerClass?.isAnnotationPresent(RequireLogin)) {
            RequireLogin requireLogin = method.getAnnotation(RequireLogin.class)
            role=requireLogin?.role()
            securityCheck = true
        }
        //Should be the last step
        if(method?.isAnnotationPresent(SkipSecurityCheck)){
            securityCheck = false
        }

        if(securityCheck){
            String apikey = getApiKey() //collect apikey from header, param and body

            if(serviceAuthService.isValid(apikey)){
                true
            }else{
                if (!serviceAuthService.isLoggedIn()) {
                    //TODO check type of request, determine whether returns JSON or redirect to login page
                    redirect(url: grailsApplication.config.security.cas.loginUrl + "?service=" +
                            grailsApplication.config.security.cas.appServerName + request.forwardURI + (request.queryString ? '?' + request.queryString : ''))
                    return false
                }

                if (!Strings.isNullOrEmpty(role)) {
                    //Check if matches ROLE requirement
                    if (!serviceAuthService.isRoleOf(role)) {
                        //TODO check type of request, determine whether returns JSON or redirect to login page
                        render text: "Forbidden. " + role + " required", status: STATUS_UNAUTHORISED
                        return false
                    }
                }
            }
        }
        true
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }

    /**
     * ALA uses 'apiKey' as standard
     * @return
     */
    private getApiKey(){
        String apikey
        if (request.getHeader(API_KEY_HEADER_NAME) != null) {
            //Standard way
            apikey = request.getHeader(API_KEY_HEADER_NAME)
        } else if (params.containsKey("api_key")) {
            // backward compatible
            // Check if params contains api_key
            apikey = params.api_key
        }

        /*
        won't work, since it opens inputstream
        else if (request.JSON?.api_key != null) {
            // backward compatible
            // Check if body contains api_key
            // For example: ShapeController: poiRequest -> retrieve api_key from json body
            apikey = request.JSON?.api_key
        }
        */

        apikey
    }
}
