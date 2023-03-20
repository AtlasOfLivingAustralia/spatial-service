package au.org.ala.spatial

import java.lang.annotation.*

/**
 * RequireLogin()
 * Requires a valid ApiKey AND userId (Api call)
 * Or a user login
 *
 * RequireLogin(role=ROLE_ADMIN)
 * Requires an admin user
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface RequireLogin {
    //If role is supplied, for example, ROLE_ADMIN, then it requires the user should be at least ROLE_ADMIN
    //If role is not supplied by default, it only requires a login user
    String role() default ""
}
