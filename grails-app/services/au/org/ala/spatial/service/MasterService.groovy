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
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams

import java.util.concurrent.ConcurrentHashMap

class MasterService {

    //avoid circular reference
    def _tasksService

    Map<String, Slave> slaves = [:] as ConcurrentHashMap

    // start the task on the slave. return status url
    def start(slave, task, input) {
        try {
            def url = slave.url + "/task/create" + "?api_key=" + slave.key
            def post = new PostMethod(url)
            def http = new HttpClient()
            http.setConnectionTimeout(10000)
            http.setTimeout(60000)
            JsonOutput jsonOutput = new JsonOutput()

            def json = jsonOutput.toJson([taskId: task.id, name: task.name, input: input])
            post.addRequestHeader("Content-Type", "application/json; charset=UTF-8")
            post.setRequestBody(json)
            http.executeMethod(post)

            def response = JSON.parse(post.getResponseBodyAsString())
            post.releaseConnection()
            if (response != null && response.status != null) {
                return response
            } else {
                log.error "failed to start task: " + task.id + " on slave: " + slave.url
            }
        } catch (err) {
            log.error "failed to start task: " + task.id + " on slave: " + slave.url, err
        }

        return null
    }

    // get status object from task.url
    def checkStatus(task) {
        def url = task.url

        try {
            def response = grails.converters.JSON.parse(Util.getUrl(url))
            if (response != null) {
                updateTask(task, response)
                return true
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
            def url = slave.url + "/slave/ping" + "?api_key=" + slave.key
            def txt = Util.getUrl(url)
            def response = grails.converters.JSON.parse(txt)
            if ("alive".equalsIgnoreCase(response.status)) {
                return true
            }
        } catch (err) {
            log.error "failed to ping slave: " + slave.url
        }
        return false
    }

    String getUrl(String url) {
        DefaultHttpClient client = new DefaultHttpClient();

        HttpParams params = client.getParams();
        HttpConnectionParams.setConnectionTimeout(params, 10000)
        HttpConnectionParams.setSoTimeout(params, 600000)

        HttpGet get = new HttpGet(url)
        HttpResponse response = client.execute(get)
        String out = IOUtils.toString(response.getEntity().getContent())
        get.releaseConnection()

        out
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
}
