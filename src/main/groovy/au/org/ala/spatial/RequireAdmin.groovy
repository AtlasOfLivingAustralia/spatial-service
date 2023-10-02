package au.org.ala.spatial

import java.lang.annotation.*

/**
 * Equivalent to RequireLogin(role=ROLE_ADMIN)
 * Annotation to check that a valid api key has been provided.
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface RequireAdmin {
}
