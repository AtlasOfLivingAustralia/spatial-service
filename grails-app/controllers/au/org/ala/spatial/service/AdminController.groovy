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

import au.org.ala.RequireAdmin
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
    @RequireAdmin
    def slaves() {
        render masterService.slaves as JSON
    }

    /**
     * information about all tasks waiting and running
     *
     * @return
     */
    @RequireAdmin
    def tasks() {
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
    @RequireAdmin
    def reRegisterSlaves() {
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
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def defaultGeoserverStyles() {
        manageLayersService.fixLayerStyles()
    }
}
