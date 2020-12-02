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

import java.text.MessageFormat

class ServiceAuthService {

    def grailsApplication
    def authService

    def testedKeys = [:]

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
        if (grailsApplication.config.security.cas.disableCAS.toBoolean() || grailsApplication.config.security.cas.bypass.toBoolean()) {
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
     *
     * @return true when cas is disabled, api key is valid or user is logged in
     */
    @Deprecated
    boolean isLoggedIn(params) {
        if (isAdmin(params)) {
            return true
        }

        // is logged in
        if (authService.getUserId()) {
            return true
        }

        return false
    }

    /**
     *
     * @return true when is logged in
     */
    boolean isLoggedIn() {
        if (authService.getUserId()) {
            return true
        }
        false
    }

    /**
     *
     * @return true when user is the role of given
     */
    boolean isRoleOf(role) {
        if (authService.userInRole(role)) {
            return true
        }
        return false
    }

}
