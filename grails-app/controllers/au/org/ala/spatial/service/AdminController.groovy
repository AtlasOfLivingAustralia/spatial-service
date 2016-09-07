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

import au.org.ala.web.AuthService
import grails.converters.JSON

class AdminController {

    MasterService masterService
    AuthService authService
    ServiceAuthService serviceAuthService

    /**
     * get collated capabilities specs from all registered slaves
     * @return
     */
    def capabilities() {
        boolean includePrivate = authService.userInRole(grailsApplication.config.auth.admin_role) ||
                serviceAuthService.isValid(params['api_key'])

        render masterService.spec(includePrivate) as JSON
    }

    /**
     * information about all registered slaves
     * @return
     */
    def slaves() {
        login('slaves')

        render masterService.slaves as JSON
    }

    /**
     * information about all tasks waiting and running
     * @return
     */
    def tasks() {
        login('tasks')

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
     * @return
     */
    def reRegisterSlaves() {
        login('reRegisterSlaves')

        int count = 0
        masterService.slaves.each { url, slave ->
            if (masterService.reRegister(slave)) {
                count++
            }
        }

        Map map = [slavesReRegistered: count]
        render map as JSON
    }

    private def login(service) {
        if (serviceAuthService.isValid(params['api_key'])) {
            return
        } else if (!authService.getUserId() && !request.contentType?.equalsIgnoreCase("application/json")) {
            redirect(url: grailsApplication.config.casServerLoginUrl + "?service=" +
                    grailsApplication.config.serverName + createLink(controller: 'admin', action: service))
        } else if (!authService.userInRole(grailsApplication.config.auth.admin_role)) {
            Map err = [error: 'not authorised']
            render err as JSON
        }
    }

}
