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

package au.org.ala.spatial

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.web.AuthService
import grails.converters.JSON

class WorkflowController {

    private static String RECORD_TYPE = 'workflow'
    private static String REF = ''
    private static String PUBLIC = 'public'

    SpatialAuthService spatialAuthService
    AuthService authService
    SpatialConfig spatialConfig

    UserDataService userDataService

    /**
     * POST JSON to save or update a saved workflow.
     *
     *{*     public: boolean,
     *     description: string,
     *     metadata: string of JSON
     *     doi: optional boolean to request doi creation
     *}*
     * @param id
     * @return
     */
    @RequireApiKey
    def save(Long id) {
        String user_id = authService.getUserId()
        def data = request.JSON

        def isPublic = data['isPublic'] ? PUBLIC : null
        def description = data['description']?.toString()
        def metadata = data['metadata']?.toString()

        def header = mapping(userDataService.put(user_id, RECORD_TYPE, description, metadata, isPublic, null))

        def result
        if (header) {
            result = true
            def map = [successful: result, data: header]
            render map as JSON
        } else {
            result = false
            def map = [successful: result, message: "Failed to save workflow"]
            render map as JSON
        }
    }

    @RequireApiKey
    def show(Long id) {
        String user_id = authService.getUserId()

        def item = UDHeader.get(id)

        if (!item) {
            render status: 404
            return
        }

        def header = mapping(item)

        // check authorisation
        if (header.userId == user_id ||
                !header.isPrivate ||
                spatialAuthService.userInRole(spatialConfig.auth.admin_role)) {
            // keep metadata details
        } else {
            header.metadata = null
        }

        if (header.metadata && 'true'.equalsIgnoreCase(params.open)) {
            def hub = params.hub ? '/hub/' + params.hub : ''

            redirect url: spatialConfig.spatialHubUrl + hub + '?workflow=' + header.id
        } else if (request.getHeader('accept')?.contains('application/json')) {
            render header as JSON
        } else {
            render view: 'show', model: [workflowInstance: header]
        }
    }

    @RequireApiKey
    def delete(Long id) {
        String user_id = authService.getUserId()

        def metadata = UDHeader.get(id)

        // check authorisation and that it is not minted (no analysis_id)
        if ((metadata.user_id == user_id ||
                spatialAuthService.userInRole(spatialConfig.auth.admin_role)) &&
                metadata.analysis_id == null) {

            userDataService.delete(id)

            def map = [result: true]
            render map as JSON
        } else {
            def map = [result: false]
            render map as JSON
        }
    }

    @RequireApiKey
    def search() {
        String user_id = authService.getUserId()

        String isPublic = spatialAuthService.userInRole(spatialConfig.auth.admin_role) ? null : PUBLIC

        def list = userDataService.searchDescAndTypeOr('%' + params.q + '%', RECORD_TYPE, user_id, isPublic, null, Integer.parseInt(params.start ?: '0'), Integer.parseInt(params.limit ?: '10'))

        def formattedList = []
        list.each { item -> item.metadata = null; formattedList.push(mapping(item)) }

        render formattedList as JSON
    }

    private def mapping(header) {
        return [id     : header.ud_header_id, mintId: header.analysis_id, name: header.description,
                userId : header.user_id, isPrivate: !PUBLIC.equalsIgnoreCase(header.data_path),
                created: header.upload_dt, metadata: header.metadata,
                url    : spatialConfig.grails.serverURL + '/workflow/show/' + header.ud_header_id]
    }
}
