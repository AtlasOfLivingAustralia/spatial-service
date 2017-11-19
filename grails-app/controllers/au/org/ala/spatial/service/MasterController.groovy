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
import grails.transaction.Transactional
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity
import org.codehaus.groovy.grails.web.json.JSONObject

@Transactional(readOnly = true)
class MasterController {

    def grailsApplication
    def fileService
    def publishService
    def masterService
    def serviceAuthService
    def authService
    def tasksService

    /**
     * for slaves to check in the server is alive
     * @return
     */
    def ping() {
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        // TODO: check stuff

        Map map = [status: 'alive']
        render map as JSON
    }

    /**
     * for slaves to send task outputs for installation onto
     * geoserver (shape and grid files, slds), layersdb (e.g. tabulation processing),
     * file system (metadata.html, pdfs)
     * @return
     */

    @Transactional(readOnly = false)
    publish() {
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        Map map = [:]

        //Task task = Task.get(params.id as Integer)
        Boolean isPublic = params?.isPublic ? params.isPublic : false

        String pth = grailsApplication.config.data.dir + "/" + (isPublic ? 'public' : 'private') + "/" + params.id + "/"

        String file = "${pth}${params.id}.zip"
        File f = new File(file)
        f.getParentFile().mkdirs()

        try {
            // save steam as a zip
            def out
            try {
                if (request instanceof MultipartRequestEntity)
                    out = request.getFile('file')
            } catch (e) {
                log.error(e.getMessage(), e)
                //ignore, may not exist when slave is local to master service
            }

            if (out != null) {
                out.transferTo(f)
            }
        } catch (err) {
            log.error 'failed to receive published files', err
            map.put('status', 'failed')
        }

        if (map.size() == 0) {
            try {
                // save stream as a zip
                if (!f.exists()) {
                    //May need to swap public/private if slave is local to master service
                    pth = grailsApplication.config.data.dir + "/" + (!isPublic ? 'public' : 'private') + "/" + params.id + "/"
                    file = "${pth}${params.id}.zip"
                    f = new File(file)
                }

                // do publishing
                Map spec = publishService.publish(f)

                map.put('status', 'successful')

                //update log and outputs
                tasksService.afterPublish(params.id, spec)
            } catch (err) {
                log.error 'failed process published files', err
                map.put('status', 'failed')
            }
        }

        render map as JSON
    }

    /**
     * to deliver resources (area WKT and layer files) to slaves in a zip
     * a layer: 'cl...', 'el...', will provide the sample-able files (original extents) - shape files or diva grids
     * a layer: 'cl..._res', 'el..._res', will provide the standardized files at the requested resolution
     *          (or next detailed) - shape files or diva grids
     * @return
     */
    def resource() {
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

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
     * @return
     */
    def resourcePeek() {
        if (!serviceAuthService.isValid(params.api_key)) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        //write resource
        render fileService.info(params.resource.toString()) as JSON
    }

    /**
     * recieves slave registration requests containing [limit:, capabilities:]
     * @return
     */
    @Transactional
    register() {
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        JSONObject json = (JSONObject) request.getJSON()

        def cap = [:]
        json.capabilities.each { k ->
            cap.put(k.name, k)
        }

        def limits = [:]
        json.limits.queue.each { name, lim ->
            def pool = [:]
            if (lim.containsKey('pool') != null) {
                lim.pool.each { pk, pv ->
                    pool.put(pk, pv)
                }
            }

            limits.put(name, [total: lim.total, pool: pool, tasks: [:]])
        }


        Slave slave = masterService.slaves.get(json.url)

        if (slave == null) {
            slave = new Slave([url         : json.url,
                               capabilities: cap,
                               limits      : [queue: limits, priority: json.limits?.priority ?: [:]],
                               key         : json.key,
                               created     : new Date()])
        } else {
            slave.capabilities = cap
            slave.created = new Date()
        }

        masterService.slaves.put(json.url.toString(), slave)

        render slave as JSON
    }

    /**
     * allows slave to update the status of a task
     *
     * @param id
     * @return
     */
    @Transactional
    task(Long id) {
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

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

        render {} as JSON
    }

}
