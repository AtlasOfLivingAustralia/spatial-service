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
import grails.converters.JSON
import grails.util.Holders
import groovy.json.JsonOutput
import org.apache.http.entity.FileEntity
import org.apache.http.entity.StringEntity

class MasterService {

    def publishService

    def taskService

    //avoid circular reference
    def _tasksService

    // start the task on the slave. return status url
    def start(slave, task, input) {
        try {
            def map = [taskId: task.id, name: task.name, input: input]

            if (slave.url.equals(Holders.config.grails.serverURL)) {
                def t = taskService.create(map, null)
                return t
            } else {
                String url = slave.url + "/task/create" + "?api_key=" + slave.key

                JsonOutput jsonOutput = new JsonOutput()
                def json = jsonOutput.toJson(map)
                StringEntity entity = new StringEntity(json, "application/json; charset=UTF-8", "UTF-8")

                String text = Util.urlResponse("POST", url, null,
                        ["Content-Type": "application/json; charset=UTF-8"], entity)?.text

                if (text) {
                    def response = JSON.parse(text)
                    if (response != null && response.status != null) {
                        return response
                    }
                }
            }
            log.error "failed to start task: " + task.id + " on slave: " + slave.url
        } catch (err) {
            log.error "failed to start task: " + task.id + " on slave: " + slave.url, err
        }

        return null
    }

    // get status object from task.url
    def checkStatus(task) {
        def url = task.url

        try {
            if (url.startsWith(Holders.config.grails.serverURL + "/task/status/" + task.id + "?")) {
                updateTask(task, taskService.status(task.id))
                return true
            } else {
                def response = grails.converters.JSON.parse(Util.getUrl(url))
                if (response != null) {
                    updateTask(task, response)
                    return true
                }
            }
        } catch (err) {
            log.error "failed to check task status for: " + url + ", " + err.getMessage(), err
        }
        return false
    }

    def _spec = [:]
    def _specAdmin = [:]

    def spec(boolean includePrivate) {
        if (!_spec) {
            _specAdmin = taskService.allSpec(true)


            taskService.allSpec(false).each { name, cap ->
                    boolean iPrivate = !cap.containsKey('private') || !cap.private.containsKey('public') || cap.private.public
                    if (iPrivate) {
                        _spec.put(name, cap.findAll { i ->
                            if (!includePrivate && i.key.equals('private')) {
                                null
                            } else {
                                i
                            }
                        })
                    }
                }

        }

        if (includePrivate) {
            _specAdmin
        } else {
            _spec
        }
    }

    Map publish(isPublic, id, request) {
        Map map = [:]

        String pth = Holders.config.data.dir + "/" + (isPublic ? 'public' : 'private') + "/" + id + "/"

        String file = "${pth}${id}.zip"
        File f = new File(file)
        f.getParentFile().mkdirs()

        try {
            // save steam as a zip
            def out
            try {
                if (request != null && request instanceof FileEntity)
                    request.writeTo(out)
            } catch (e) {
                log.error(e.getMessage(), e)
                //ignore, may not exist when slave is local to master service
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
                    pth = Holders.config.data.dir + "/" + (!isPublic ? 'public' : 'private') + "/" + id + "/"
                    file = "${pth}${id}.zip"
                    f = new File(file)
                }

                // do publishing
                Map spec = publishService.publish(f)

                map.put('status', 'successful')

                //update log and outputs
                _tasksService.afterPublish(id, spec)
            } catch (err) {
                log.error 'failed process published files', err
                map.put('status', 'failed')
            }

            map
        }
    }
}
