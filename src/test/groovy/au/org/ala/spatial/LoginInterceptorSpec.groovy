package au.org.ala.spatial

import au.org.ala.web.AuthService
import grails.testing.web.interceptor.InterceptorUnitTest
import org.grails.core.artefact.ControllerArtefactHandler
import spock.lang.Specification
import spock.lang.Unroll

class LoginInterceptorSpec extends Specification implements InterceptorUnitTest<LoginInterceptor> {

    AuthService authService = Stub(AuthService) {
        loginUrl(_) >> '/login'
    }
    SpatialAuthService spatialAuthService = Stub(SpatialAuthService)

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', 'converters'].toSet()
    }

    def setup() {
        [FieldController, IntersectController, ManageLayersController, TasksController].each {
            grailsApplication.addArtefact(ControllerArtefactHandler.TYPE, it)
        }
        interceptor.authService = authService
        interceptor.spatialAuthService = spatialAuthService
        interceptor.spatialConfig = config(true)
    }

    static SpatialConfig config(boolean oidcEnabled) {
        SpatialConfig config = new SpatialConfig()
        config.security = new SpatialConfig.DotSecurity()
        config.security.oidc = new SpatialConfig.DotEnabled(enabled: oidcEnabled)
        config.security.cas = new SpatialConfig.DotEnabled(enabled: false)
        config.auth = new SpatialConfig.DotRole(admin_role: 'ROLE_ADMIN')
        config
    }

    void requestTo(String controller, String action) {
        withRequest(controller: controller, action: action)
        webRequest.controllerName = controller
        webRequest.actionName = action
    }

    void loggedIn(String userId, boolean admin) {
        authService.getUserId() >> userId
        spatialAuthService.isAdmin() >> admin
    }

    void "the interceptor matches every controller"() {
        when:
        withRequest(controller: 'field', action: 'index')

        then:
        interceptor.doesMatch()
    }

    void "everything is allowed when authentication is disabled"() {
        given:
        interceptor.spatialConfig = config(false)
        requestTo('manageLayers', 'layers')

        expect:
        interceptor.before()
    }

    void "public actions are allowed without login"() {
        given:
        requestTo('field', 'index')

        expect:
        interceptor.before()
    }

    void "actions marked with SkipSecurityCheck are allowed without login"() {
        given:
        requestTo('intersect', 'batch')

        expect:
        interceptor.before()
    }

    void "admin actions require login, answering JSON clients with 401"() {
        given:
        request.addHeader('accept', 'application/json')
        requestTo('manageLayers', 'layers')

        when:
        boolean allowed = interceptor.before()

        then:
        !allowed
        response.status == 401
        response.json.error == 'Forbidden, user login required!'
    }

    void "admin actions redirect browsers to the login page"() {
        given:
        requestTo('manageLayers', 'layers')

        when:
        boolean allowed = interceptor.before()

        then:
        !allowed
        response.redirectedUrl == '/login'
    }

    void "admin actions are forbidden to users without the admin role"() {
        given:
        loggedIn('1', false)
        request.addHeader('accept', 'application/json')
        requestTo('manageLayers', 'layers')

        when:
        boolean allowed = interceptor.before()

        then:
        !allowed
        response.status == 403
        response.json.error == 'Forbidden, require a user with role: ROLE_ADMIN'
    }

    void "admin actions are allowed to admins"() {
        given:
        loggedIn('1', true)
        requestTo('manageLayers', 'layers')

        expect:
        interceptor.before()
    }

    @Unroll
    void "#controller/#action requires login"() {
        given:
        request.addHeader('accept', 'application/json')
        requestTo(controller, action)

        when:
        boolean allowed = interceptor.before()

        then:
        !allowed
        response.status == 401

        where:
        controller     | action
        'tasks'        | 'show'        // action with a parameter
        'manageLayers' | 'deleteUpload' // action with a parameter
    }

    void "login actions are allowed to any logged in user"() {
        given:
        loggedIn('1', false)
        requestTo('tasks', 'show')

        expect:
        interceptor.before()
    }
}
