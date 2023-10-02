package au.org.ala.spatial.web

import au.org.ala.web.SSOStrategy

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import groovy.transform.CompileStatic
//@CompileStatic
class NoSSOStrategy implements SSOStrategy {

    NoSSOStrategy() {}

    @Override
    boolean authenticate(HttpServletRequest request, HttpServletResponse response, boolean gateway) {
        return true
    }

    @Override
    boolean authenticate(HttpServletRequest request, HttpServletResponse response, boolean gateway, String redirectUri) {
        return true
    }
}
