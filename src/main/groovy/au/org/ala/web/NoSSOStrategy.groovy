package au.org.ala.web

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

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
