/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.service

import au.org.ala.spatial.Util
import org.grails.web.util.WebUtils

import java.text.MessageFormat

class ServiceAuthService {
    static final String[] USERID_HEADER_NAME = ["X-ALA-userId", "userId", "user_id"]
    static final String[] API_KEY_HEADER_NAME = ["apiKey", "api_key", "api-key"]

    def grailsApplication
    def authService

    def testedKeys = [:]

    def hasValidApiKey() {
        isValid(getApiKey())
    }

    def isValid(key) {
        if (key == null) {
            return false
        }

        Boolean result = testedKeys.get(key)

        if (result == null) {
            String url = MessageFormat.format(grailsApplication.config.apiKeyCheckUrlTemplate.toString(), key)

            result = key == grailsApplication.config.serviceKey || Util.getUrl(url).contains('"valid":true')
            testedKeys.put(key, result)
        }

        return result
    }

    /**
     *
     * @return true when cas is disabled, api key is valid or user is admin
     */
    boolean isAdmin(params) {
        // login disabled
        if (!grailsApplication.config.security.oidc.enabled.toBoolean()) {
            return true
        }

        // valid api key provided
        if (isValid(params['api_key'])) {
            return true
        }

        // user is admin
        if (authService.userInRole(grailsApplication.config.auth.admin_role)) {
            return true
        }

        return false
    }

    /**
     * authService check if user logins to spatial service
     * @return true when is logged in
     */
    boolean isLoggedIn() {
        if (authService.getUserId()) {
            return true
        }
        false
    }

    /**
     * Only works with local user
     * @return true when user is the role of given
     */
    boolean isRoleOf(role) {
        if (authService.userInRole(role)) {
            return true
        }
        return false
    }
    /**
     * Return true, if a use login (local) or a valid apikey + userId in header
     * @return
     */
    boolean isAuthenticated() {
        if (isLoggedIn()){
            return true
        }
        if ( getUserId() && hasValidApiKey() ){
            return true
        }
    }



    /**
     * a login user Id,
     * or userId in header/param
     * @return
     */
    String getUserId(){
        if (authService.getUserId()) {
            return authService.getUserId()
        } else {
            def request = WebUtils.retrieveGrailsWebRequest().getCurrentRequest()
            for (name in USERID_HEADER_NAME) {
                if (request.getHeader(name)) {
                    return request.getHeader(name).split(",|;")[0]
                }
                if (request.getParameter(name)) {
                    return request.getParameter(name).split(",|;")[0]
                }
            }
        }
    }

    private getApiKey() {
        String apikey
        def request = WebUtils.retrieveGrailsWebRequest().getCurrentRequest()

        for (name in API_KEY_HEADER_NAME) {
            if (request.getHeader(name)) {
                return request.getHeader(name).split(",|;")[0]
            }
            if (request.getParameter(name)) {
                return request.getParameter(name).split(",|;")[0]
            }
        }

        //Last try
        apikey = request.JSON?.api_key

        log.info("apiKey: " + apikey)
        apikey
    }
}
