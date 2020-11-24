package au.org.ala

import au.org.ala.layers.intersect.IntersectConfig
import au.org.ala.spatial.Util
import org.codehaus.jackson.map.ObjectMapper

/**
 * Copy and simplify from plugin "ala-ws-security-plugin:2.0"
 * Remove IP/method/controller whitelist support
 * collecting api_key from POST body (backward compatiblity support)
 *
 */
class ApiKeyInterceptor {

    static final int STATUS_UNAUTHORISED = 403
    static final String API_KEY_HEADER_NAME = "apiKey"

    ApiKeyInterceptor() {
        matchAll()
    }

    boolean before() {
        String headerName = grailsApplication.config.navigate('security', 'apikey', 'header', 'override') ?: API_KEY_HEADER_NAME
        def controller = grailsApplication.getArtefactByLogicalPropertyName("Controller", controllerName)
        Class controllerClass = controller?.clazz

        def method = controllerClass?.getMethod(actionName ?: "index", [] as Class[])

        if (method?.isAnnotationPresent(RequireApiKey)) {
            String apikey
            if (request.getHeader(headerName) != null) {
                //Standard way
                apikey = request.getHeader(headerName)
            } else if (params.containsKey("api_key")) {
                // backward compatible
                // Check if params contains api_key
                apikey = params.api_key
            } else if (request.JSON?.api_key != null) {
                // backward compatible
                // Check if body contains api_key
                apikey = request.JSON?.api_key
            }

            boolean keyOk = checkApiKey(apikey)

            if (!keyOk) {
                response.status = STATUS_UNAUTHORISED
                response.sendError(STATUS_UNAUTHORISED, "Forbidden")
                return false
            }
        }
        true
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }

    private boolean checkApiKey(String apiKey) {
        if (IntersectConfig.getApiKeyCheckUrlTemplate() == null || IntersectConfig.getApiKeyCheckUrlTemplate().isEmpty()) {
            return true
        }

        try {
            def response = Util.urlResponse("GET", "${grailsApplication.config.security.apikey.check.serviceUrl}${apiKey}")

            if (response) {
                if (response.statusCode != 200) {
                    throw new RuntimeException("Error occurred checking api key")
                }

                ObjectMapper mapper = new ObjectMapper()
                Map parsedJSON = mapper.readValue(response.text, Map.class)

                return (Boolean) parsedJSON.get("valid")
            }
        } catch (Exception ex) {
            log.trace(ex.getMessage(), ex)
            throw new RuntimeException("Error checking API key")
        }
    }
}
