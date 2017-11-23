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

package au.org.ala.spatial.slave

import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.json.JsonOutput
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.httpclient.methods.multipart.FilePart
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity
import org.apache.commons.httpclient.methods.multipart.Part
import org.apache.commons.io.FileUtils

import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class SlaveService {

    def grailsApplication
    def taskService

    // task for pushing status updates to the master
    def statusUpdateThread
    def statusUpdates = [:] as ConcurrentHashMap
    // time between pushing status updates to the master for a task
    def statusTime = 5000;//grailsApplication.config.statusTime
    def statusLock = new Object()

    // for validating requests from the master
    def retryCount = 10;//grailsApplication.config.retryCount
    def retryTime = 30000;//grailsApplication.config.retryTime
    def monitorTask
    def monitorLock = new Object()

    def monitor() {

        monitorTask = new Thread() {
            public void run() {
                def repeat = true
                while (repeat) {
                    repeat = !registerWithMaster()
                    if (repeat) {
                        log.error("not registered with a master, retrying in " + retryTime + "ms")
                        synchronized (monitorLock) {
                            monitorLock.sleep(retryTime)
                        }
                    }
                }
            }
        }

        monitorTask.start()

        statusUpdateThread = new Thread() {
            public void run() {
                def repeat = true
                while (repeat) {
                    synchronized (statusLock) {
                        statusLock.sleep(statusTime)
                    }
                    statusUpdates.each { k, v ->
                        if (v.time + statusTime < System.currentTimeMillis() && v.status.length() > 0) {
                            signalMasterImmediately(v.status)
                            v.time = System.currentTimeMillis()
                        }
                    }
                }
            }
        }

        statusUpdateThread.start()
    }

    def registerWithMaster() {
        def error = ''
        try {
            String url = grailsApplication.config.spatialService.url + "/master/register?api_key=" + grailsApplication.config.serviceKey

            JsonOutput jsonOutput = new JsonOutput()
            String json = jsonOutput.toJson([url         : grailsApplication.config.grails.serverURL,
                                          capabilities: taskService.getAllSpec(),
                                          limits      : getLimits(),
                                          key         : grailsApplication.config.slaveKey])

            def response = Util.urlResponse("POST", url, null,
                    ["Content-Type": "application/json; charset=UTF-8"],
                    new StringRequestEntity(json, "application/json; charset=UTF-8", "UTF-8"))

            log.debug('master register url: ' + url + ', json: ' + json)

            if (response && response?.text) {
                if (JSON.parse(response.text) != null) {
                    return true
                }
            } else {
                error = "invalid master response, statusCode: " + response?.statusCode
            }
        } catch (err) {
            error = err.message
        }

        log.error 'failed to register with master: ' + error

        return false
    }

    def signalMaster(task) {
        synchronized (statusLock) {
            def s = statusUpdates.get(task.id)
            if (s == null || s.time + statusTime < System.currentTimeMillis()) {
                signalMasterImmediately(task)
                statusUpdates.put(task.id, [time: System.currentTimeMillis(), status: ''])
            } else {
                statusUpdates.put(task.id, [time: System.currentTimeMillis(), status: taskService.getStatus(task)])
            }
        }
    }

    // POST status for a task to the master
    def signalMasterImmediately(task) {
        // no need to verify because it can be frequent
        def error = ''

        try {
            String url = grailsApplication.config.spatialService.url + '/master/task?id=' + task.taskId + "&api_key=" + grailsApplication.config.serviceKey

            JsonOutput jsonOutput = new JsonOutput()
            String json = jsonOutput.toJson([task: task])

            def response = Util.urlResponse("POST", url, null,
                    ["Content-Type": "application/json; charset=UTF-8"],
                    new StringRequestEntity(json, "application/json; charset=UTF-8", "UTF-8"))

            if (response) {
                if (JSON.parse(response?.text) != null) {
                    return true
                }
            } else {
                error = "invalid master response, statusCode: " + response?.statusCode
            }

        } catch (err) {
            error = err.message
        }

        log.error "failed to send task status to master, task: " + task + ", error: " + error

        return false
    }

    def getResources(task) {
        if (!verifyMaster()) {
            return false
        }

        def input = task.input

        input.each { k, values ->
            //value to array
            if (!(values instanceof List)) {
                //convert to array
                def newValue = []
                newValue.add(values)
                values = newValue
            }

            //find input spec
            if (task.spec.containsKey('input') && task.spec.input.containsKey(k)) {
                def inputSpec = task.spec.input.get(k)

                if ("area".equalsIgnoreCase(inputSpec.type)) {
                    values.each() { v ->
                        if (!v.startsWith('POLYGON') && !v.startsWith('MULTIPOLYGON') && !v.startsWith('GEOMETRYCOLLECTION')) {
                            if (!getFile(taskService.getResourcePath(inputSpec, v))) {
                                task.err.put(System.currentTimeMillis(), "failed to get area: " + v)
                            }
                        }
                    }
                } else if ("layer".equalsIgnoreCase(inputSpec.type)) {
                    values.each() { v ->
                        if (!getFile(taskService.getResourcePath(inputSpec, v))) {
                            task.err.put(System.currentTimeMillis(), "failed to get layer: " + v)
                        }
                    }
                } else if ("uploads".equals(inputSpec.type)) {
                    values.each() { v ->
                        if (!getFile(taskService.getResourcePath(inputSpec, v))) {
                            task.err.put(System.currentTimeMillis(), "failed to get layer: " + v)
                        }
                    }
                }
            } else {
                log.debug 'for task:' + task.name + ', no spec input for input:' + k
            }
        }

        return true
    }

    def getFile(path, String spatialServiceUrl = grailsApplication.config.spatialService.url) {
        def remote = peekFile(path, spatialServiceUrl)

        //compare p list with local files
        def fetch = []
        remote.each { file ->
            if (file.exists) {
                def local = new File(grailsApplication.config.data.dir + file.path)
                if (!local.exists() || local.lastModified() < file.lastModified) {
                    fetch.add(file.path)
                }
            }
        }

        if (fetch.size() < remote.size()) {
            //fetch only some
            fetch.each {
                getFile(it, spatialServiceUrl)
            }
        } else if (fetch.size() > 0) {
            //fetch all files

            def tmpFile = File.createTempFile('resource', '.zip')

            try {
                def shortpath = path.replace(grailsApplication.config.data.dir, '')
                def url = spatialServiceUrl + "/master/resource?resource=" + URLEncoder.encode(shortpath, 'UTF-8') +
                        "&api_key=" + grailsApplication.config.serviceKey

                def os = new BufferedOutputStream(new FileOutputStream(tmpFile))
                def streamObj = Util.getStream(url)
                try {
                    if (streamObj?.call) {
                        os << streamObj?.call?.getResponseBodyAsStream()
                    }
                    os.flush()
                    os.close()
                } catch (Exception e) {
                    log.error e.getMessage(), e
                }
                Util.closeStream(streamObj)

                def zf = new ZipInputStream(new FileInputStream(tmpFile))
                try {
                    def entry
                    while ((entry = zf.getNextEntry()) != null) {
                        def filepath = grailsApplication.config.data.dir + entry.getName()
                        def f = new File(filepath)
                        f.getParentFile().mkdirs()
                        FileUtils.copyInputStreamToFile(zf, f)
                        zf.closeEntry()

                        //update lastmodified time
                        remote.each { file ->
                            if (entry.name.equals(file.path)) {
                                f.setLastModified(file.lastModified)
                            }
                        }
                    }
                } finally {
                    try {
                        zf.close()
                    } catch (err) {}
                }
            } catch (err) {
                log.error "failed to get: " + path
            }

            tmpFile.delete()
        }

//        if (file.exists() && file.size() > 0) {
        return true
//        } else {
//            log.error "failed to get path: " + path + " (zero size)"
//            return false
//        }
    }

    List peekFile(String path, String spatialServiceUrl = grailsApplication.config.spatialService.url) {
        List map = [[path: '', exists: false, lastModified: System.currentTimeMillis()]]

        try {
            String shortpath = path.replace(grailsApplication.config.data.dir.toString(), '')
            String url = spatialServiceUrl + "/master/resourcePeek?resource=" + URLEncoder.encode(shortpath, 'UTF-8') +
                    "&api_key=" + grailsApplication.config.serviceKey

            map = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error "failed to get: " + path, err
        }

        map
    }

    def publishResults(task) {
        if (!verifyMaster()) {
            return false
        }

        try {
            String url = grailsApplication.config.spatialService.url + '/master/publish?id=' + task.taskId + '&public=' +
                    (task.spec?.public ? task.spec.public : 'false') + "&api_key=" + grailsApplication.config.serviceKey

            def file = taskService.getZip(task)

            // POST the analysis bundle to spatial-service
            //do not post if master service is local
            MultipartRequestEntity requestEntity = null
            if (!grailsApplication.config.service.enable) {
                def f = new File(file)

                Part[] parts = [new FilePart('file', f)]
                requestEntity = new MultipartRequestEntity(parts, null)
            }

            def response = Util.urlResponse("POST", url, null, [:], requestEntity)

            if (response) {
                def j = JSON.parse(response.text)
                if (j != null && 'successful'.equalsIgnoreCase(j.status)) {
                    return true
                } else if (j) {
                    j.each { k, v ->
                        task.history.put(k as String, v as String)
                    }
                }
            }
        } catch (err) {
            log.error "", err
        }

        task.history.put(System.currentTimeMillis(), "failed to publish results")

        return false
    }

    def verifyMaster() {
        def verified = false
        for (int i = 0; i < retryCount; i++) {
            try {
                def url = grailsApplication.config.spatialService.url + "/master/ping/" +
                        "?api_key=" + grailsApplication.config.serviceKey
                def response = grails.converters.JSON.parse(Util.getUrl(url))
                if ("alive".equalsIgnoreCase(response.status)) {
                    verified = true
                    break
                } else if ("reregister".equalsIgnoreCase(response.status)) {
                    registerWithMaster()
                    i = 0
                }
            } catch (err) {
            }
            Thread.sleep(retryTime)
        }

        if (!verified) {
            log.error("failed to verify the master at " + grailsApplication.config.spatialService.url)
        }

        verified
    }

    // TODO: cleanup cached files that are no longer required
    // delete old published analysis files
    // delete old error analysis files
    // delete old cached areas and layers
    def deleteOldFiles() {

    }

    def getLimits() {
        grails.converters.JSON.parse(this.class.getResource("/processes/limits.json").text)
    }
}
