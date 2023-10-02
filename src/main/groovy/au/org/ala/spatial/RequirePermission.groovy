package au.org.ala.spatial

import java.lang.annotation.*

/**
 * Annotation to check if a valid api key has been provided
 * or user has logged in
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented

/**
 * Minimum ApiKey check or login user
 */
@interface RequirePermission {
}
