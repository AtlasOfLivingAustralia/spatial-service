package au.org.ala

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation to check that a valid api key has been provided or user has logged in
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented

@interface RequireLogin {
    //If role is supplied, for example, ROLE_ADMIN, then it requires the user should be at least ROLE_ADMIN
    //If role is not supplied by default, it only requires a login user
    String role() default ""
    // Support earlier auth method which stores api key into body.
    boolean apiKeyInBody() default false
}