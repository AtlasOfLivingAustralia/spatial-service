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

package au.org.ala.layers

import au.org.ala.layers.dao.UserDataDAO
import au.org.ala.layers.dto.Ud_header
import au.org.ala.web.AuthService
import grails.converters.JSON

class WorkflowController {

    private static String RECORD_TYPE = 'workflow'
    private static String REF = ''
    private static String PUBLIC = 'public'

    UserDataDAO userDataDao
    AuthService authService

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
    def save(Long id) {
        String user_id = authService.getUserId()
        def data = request.JSON

        def isPublic = data.isPublic ? PUBLIC : null
        def description = data.description?.toString()
        def metadata = data.metadata?.toString()

        Ud_header header;

        String errorMsg;
        if (data.doi?.asBoolean) {
            // test for minimum data for a DOI
            errorMsg = getErrorForDoi(data)

            if (!errorMsg) {
                String doi = mintDoi(data);
                if (doi) {
                    header = userDataDao.put(user_id, RECORD_TYPE, description, metadata, isPublic, doi)
                }
            }
        } else {
            header = userDataDao.put(user_id, RECORD_TYPE, description, metadata, isPublic, null)
        }

        def result
        if (errorMsg) {
            result = false
            def map = [successful: result, message: errorMsg]
            render map as JSON
        } else if (header) {
            result = true
            def map = [successful: result, data: header]
            render map as JSON
        } else {
            result = false
            errorMsg = "Failed to save workflow"
            def map = [successful: result, message: errorMsg]
            render map as JSON
        }
    }

    def doi(String id) {
        def list = userDataDao.searchDescAndTypeOr(null, RECORD_TYPE, null, null, id, 0, 1)

        if (list.size() > 0) {
            show(list.get(0).ud_header_id)
        } else {
            render status: HttpURLConnection.HTTP_NOT_FOUND
        }
    }

    def show(Long id) {
        String user_id = authService.getUserId()

        def header = userDataDao.get(id)

        // check authorisation
        if (header.user_id == user_id ||
                header.data_path == PUBLIC ||
                authService.userInRole(grailsApplication.config.auth.admin_role)) {

            render(header as JSON)
        } else {
            header.metadata = null
            render header as JSON
        }
    }

    def delete(Long id) {
        String user_id = authService.getUserId()

        def metadata = userDataDao.get(id)

        // check authorisation
        if (metadata.user_id == user_id ||
                authService.userInRole(grailsApplication.config.auth.admin_role)) {

            userDataDao.delete(id)

            def map = [result: true]
            render map as JSON
        } else {
            def map = [result: false]
            render map as JSON
        }
    }

    def search() {
        String user_id = authService.getUserId()

        String isPublic = authService.userInRole(grailsApplication.config.auth.admin_role) ? null : PUBLIC;

        def list = userDataDao.searchDescAndTypeOr('%' + params.q + '%', RECORD_TYPE, user_id, isPublic, null, Integer.parseInt(params.start), Integer.parseInt(params.limit))

        list.each { item -> item.metadata = null }

        render list as JSON
    }

    private def getErrorForDoi(data) {
        return ""
    }
}
