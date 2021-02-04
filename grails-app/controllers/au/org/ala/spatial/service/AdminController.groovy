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


import grails.converters.JSON

class AdminController {

    def masterService
    def serviceAuthService
    def fieldDao
    def authService
    def manageLayersService

    def index() {

    }

    /**
     * get collated capabilities specs from all registered slaves
     * @return
     */
    def capabilities() {
        render masterService.spec(serviceAuthService.isAdmin(params)) as JSON
    }

    /**
     * information about all registered slaves
     *
     * admin only
     *
     * @return
     */
    def slaves() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        render masterService.slaves as JSON
    }

    /**
     * information about all tasks waiting and running
     *
     * admin only
     *
     * @return
     */
    def tasks() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        params.max = params?.max ?: 10

        List list
        if (params.containsKey('all')) {
            list = Task.list(params)
        } else {
            list = Task.findAllByStatusOrStatus(0, 1)
        }
        render list as JSON
    }

    /**
     * trigger slave re-register
     *
     * admin only
     *
     * @return
     */
    def reRegisterSlaves() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        int count = 0
        masterService.slaves.each { url, slave ->
            if (masterService.reRegister(slave)) {
                count++
            }
        }

        Map map = [slavesReRegistered: count]
        render map as JSON
    }

    /**
     * Return true when logged in, CAS is disabled or api_key is valid.
     *
     * Otherwise redirect to CAS for login.
     *
     * @param params
     * @return
     */
    private boolean doLogin() {
        if (!serviceAuthService.isLoggedIn(params)) {
            redirect(url: grailsApplication.config.security.cas.loginUrl + "?service=" +
                    grailsApplication.config.security.cas.appServerName + request.forwardURI + (request.queryString ? '?' + request.queryString : ''))
            return false
        }

        return true
    }


    /**
     * admin only
     *
     * @return
     */
    def defaultGeoserverStyles() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        manageLayersService.fixLayerStyles()
    }
}
