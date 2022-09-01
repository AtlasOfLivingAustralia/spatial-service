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
import groovy.json.JsonOutput
import org.apache.http.entity.FileEntity
import org.apache.http.entity.StringEntity

import java.util.concurrent.ConcurrentHashMap

class MasterService {

    def grailsApplication
    def publishService

    def taskService

    //avoid circular reference
    def _tasksService

    Map<String, Slave> slaves = [:] as ConcurrentHashMap

    def register(json) {
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


        Slave slave = slaves.get(json.url)

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

        slaves.put(json.url.toString(), slave)

        slave
    }

    // start the task on the slave. return status url
    def start(slave, task, input) {
        try {
            def map = [taskId: task.id, name: task.name, input: input]

            if (slave.url.equals(grailsApplication.config.grails.serverURL)) {
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
            if (url.startsWith(grailsApplication.config.grails.serverURL + "/task/status/" + task.id + "?")) {
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

    // update a task with status
    def updateTask(task, status) {

        def newValues = [:]
        if (status?.status?.finished) {
            newValues.put('status', 4)
        }

        if (status?.status?.err) {
            newValues.put('status', 4)

            //append to log
            def e = status.status.error.toString()
            def m = [:]
            m.put(String.valueOf(System.currentTimeMillis()), e.substring(0, Math.min(e.length(), 255)))
            newValues.put('err', m)
        }

        if (status?.status?.message) {
            if (!status.status.message.equals(task.message))
                newValues.put('message', status.status.message)
        }

        if (status?.status?.history) {
            //append to log
            newValues.put('history', status.status.history)
        }

        if (status?.error) {
            //cancel slave allocation

            newValues.put('url', null)
            newValues.put('status', 0)
            newValues.put('slave', null)
        }

        if (newValues.size() > 0) {
            _tasksService.update(task.id, newValues)

            if (task.status == 4 || (newValues.containsKey('status') && newValues.status == 4)) {
                finishTask(task)
            }
        }
    }

    // is the slave alive? return true/false
    def ping(slave) {
        try {
            if (slave.url.equals(grailsApplication.config.grails.serverURL)) {
                return true
            } else {
                def url = slave.url + "/slave/ping" + "?api_key=" + slave.key
                def txt = Util.getUrl(url)
                def response = grails.converters.JSON.parse(txt)
                if ("alive".equalsIgnoreCase(response.status)) {
                    return true
                }
            }
        } catch (err) {
            log.error "failed to ping slave: " + slave.url
        }
        return false
    }

    // do any task cleanup
    def finishTask(task) {
        //TODO: something to do with email for background tasks
    }

    def spec(boolean includePrivate) {

        def list = slaves

        def mergedSpec = [:]
        list.each { url, slave ->
            slave.capabilities.each { name, cap ->
                if (mergedSpec.containsKey(name)) {
                    // do version comparison
                } else {
                    boolean iPrivate = includePrivate || !cap.containsKey('private') || !cap.private.containsKey('public') || cap.private.public
                    if (iPrivate) {
                        mergedSpec.put(name, cap.findAll { i ->
                            if (!includePrivate && i.key.equals('private')) {
                                null
                            } else {
                                i
                            }
                        })
                    }
                }
            }
        }

        mergedSpec
    }

    def getSlave(url) {
        slaves[url]
    }

    Map publish(isPublic, id, request) {
        Map map = [:]

        String pth = grailsApplication.config.data.dir + "/" + (isPublic ? 'public' : 'private') + "/" + id + "/"

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
                    pth = grailsApplication.config.data.dir + "/" + (!isPublic ? 'public' : 'private') + "/" + id + "/"
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
