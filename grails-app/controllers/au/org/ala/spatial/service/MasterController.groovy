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
import grails.gorm.transactions.Transactional
import org.grails.web.json.JSONObject

@Transactional(readOnly = true)
class MasterController {

    def fileService
    def publishService
    def masterService
    def tasksService

    /**
     * for slaves to check in the server is alive
     *
     * admin only or api_key, do not redirect to CAS
     *
     * @return
     */
    @RequireAdmin
    def ping() {
        // TODO: check stuff

        Map map = [status: 'alive']
        render map as JSON
    }

    /**
     * for slaves to send task outputs for installation onto
     * geoserver (shape and grid files, slds), layersdb (e.g. tabulation processing),
     * file system (metadata.html, pdfs)
     *
     * admin only or api_key, do not redirect to CAS
     *
     * @return
     */
    @Transactional(readOnly = false)
    @RequireAdmin
    publish() {
        Boolean isPublic = params?.isPublic ? params.isPublic : false
        def map = masterService.publish(isPublic, params.id, request)

        render map as JSON
    }

    /**
     * to deliver resources (area WKT and layer files) to slaves in a zip
     * a layer: 'cl...', 'el...', will provide the sample-able files (original extents) - shape files or diva grids
     * a layer: 'cl..._res', 'el..._res', will provide the standardized files at the requested resolution
     *          (or next detailed) - shape files or diva grids
     *
     * admin only or api_key, do not redirect to CAS
     * @return
     */
    @RequireAdmin
    def resource() {
        OutputStream outputStream = null
        try {
            outputStream = response.outputStream as OutputStream
            //write resource
            response.setContentType("application/octet-stream")
            response.setHeader("Content-disposition", "attachment;filename=${params.resource}.zip")
            fileService.write(outputStream, params.resource as String)
            outputStream.flush()
        } catch (err) {
            log.error(err.getMessage(), err)
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (err) {
                    log.error(err.getMessage(), err)
                }
            }
        }
    }

    /**
     * for slaves to peek at a resource on the master
     *
     * admin only or api_key, do not redirect to CAS
     *
     * @return
     */
    @RequireAdmin
    def resourcePeek() {
        //write resource
        render fileService.info(params.resource.toString()) as JSON
    }

    /**
     * recieves slave registration requests containing [limit:, capabilities:]
     *
     * admin only or api_key, do not redirect to CAS
     *
     * @return
     */
    @Transactional
    @RequireAdmin
    register() {
        JSONObject json = (JSONObject) request.getJSON()

        def slave = masterService.register(json)

        render slave as JSON
    }

    /**
     * allows slave to update the status of a task
     *
     * admin only or api_key, do not redirect to CAS
     *
     * @param id
     * @return
     */
    @Transactional
    @RequireAdmin
    task(Long id) {
        JSONObject json = (JSONObject) request.getJSON()

        def newValues = [:]
        if (json.task?.message) newValues.put('message', json.task.message)
        if (json.task?.history) newValues.put('history', json.task.history as Map)
        if (json.task?.finished && json.task.finished) {
            newValues.put('status', 4)
        } else {
            newValues.put('status', json.task.status)
        }

        tasksService.update(id, newValues)

        render new Object() as JSON
    }
}
