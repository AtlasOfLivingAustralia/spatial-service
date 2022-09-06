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


import au.org.ala.RequirePermission
import grails.converters.JSON

class AdminController {

    def masterService
    def serviceAuthService
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
     * admin only
     *
     * @return
     */
    @RequirePermission
    def defaultGeoserverStyles() {
        manageLayersService.fixLayerStyles()
    }
}
