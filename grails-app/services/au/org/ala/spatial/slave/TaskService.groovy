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

import au.org.ala.spatial.process.SlaveProcess
import au.org.ala.spatial.service.Task
import com.google.common.util.concurrent.ThreadFactoryBuilder
import grails.converters.JSON
import grails.util.Holders

import javax.annotation.PostConstruct
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TaskService {

    ThreadGroup threadGroup = new ThreadGroup("SpatialServiceTasks")
    def masterService

    ExecutorService generalEecutor
    ExecutorService adminExecutor

    @PostConstruct
    def init() {
        generalEecutor = Executors.newFixedThreadPool(Holders.config.tasks.general.threadCount,
                new ThreadFactoryBuilder().setNameFormat("general-tasks-%d").setPriority(Thread.NORM_PRIORITY).build());

        adminExecutor = Executors.newFixedThreadPool(Holders.config.tasks.admin.threadCount,
                new ThreadFactoryBuilder().setNameFormat("general-tasks-%d").setPriority(Thread.NORM_PRIORITY).build());
    }

    void start(Task task) {
        def isAdminTask = task.spec.private.public == false

        def wrappedTask = wrapTask(task)

        if (isAdminTask) {
            adminExecutor.submit(new TaskThread(wrappedTask))
        } else {
            generalEecutor.submit(new TaskThread(wrappedTask))
        }
    }

    List getAllSpec() {
        List list = []

        def resource = TaskService.class.getResource("/processes/")
        def dir = new File(resource.getPath())

        // default processes
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".json") && !f.getName().equals("limits.json")) {
                String name = "au.org.ala.spatial.process." + f.getName().substring(0, f.getName().length() - 5)
                try {
                    Class clazz = Class.forName(name)
                    list.add(((SlaveProcess) clazz.newInstance()).spec(null))
                } catch (err) {
                    log.error("unable to instantiate $name. ${err.getMessage()}", err)
                }
            }
        }

        // Additional SlaveProcesses can be initialized with an external spec with a unique filename:
        // - /data/spatial-service/config/processes/n.ProcessName.json
        // Where:
        // - `n` is a value that makes the filename unique
        // - `ProcessName` is a valid class that extends SlaveProcess
        for (File f : new File('/data/spatial-service/config/processes').listFiles()) {
            // `ProcessName`
            def fname = f.getName().substring(f.getName().indexOf('.') + 1)

            // `n`
            def funqiue = f.getName().substring(0, f.getName().indexOf('.'))

            // add process class with this spec file
            if (f.getName().endsWith(".json") && !f.getName().equals("limits.json")) {
                String name = "au.org.ala.spatial.process." + fname.substring(0, fname.length() - 5)
                try {
                    Class clazz = Class.forName(name)
                    list.add(((SlaveProcess) clazz.newInstance()).spec(JSON.parse(f.text) as Map))
                } catch (err) {
                    log.error("unable to instantiate $name. ${err.getMessage()}", err)
                }
            }
        }

        list
    }

    def getResourcePath(inputSpec, value) {
        def dir = Holders.config.data.dir

        // look in cache dir and return this file name if it is missing
        if ('area'.equalsIgnoreCase(inputSpec.type.toString())) {
            //format value as a filename
            return dir + '/cache/' + URLEncoder.encode(value.toString(), "UTF-8")
        } else if ('layer'.equalsIgnoreCase(inputSpec.type.toString())) {
            //format value as a filename
            if (!value.toString().contains(':')) {
                return dir + '/layer/' + URLEncoder.encode(value.toString(), "UTF-8")
            } else {
                //extract resolution value
                def split = value.toString().split(':')
                return dir + '/standard_layer/' + split[1] + '/' + URLEncoder.encode(split[0].toString(), "UTF-8")
            }
        } else {
            return dir + '/' + inputSpec.type + '/' + URLEncoder.encode(value.toString(), "UTF-8")
        }
    }

    def getBasePath(task) {
        String url = Holders.config.data.dir + '/public/' + task.taskId + '/'
        return url
    }

    TaskWrapper wrapTask(Task task) {
        TaskWrapper wrapper = new TaskWrapper(taskId: task.id, input: task.input, name: task.name, id: Long.parseLong(task.id.toString()), task: task)

        //format inputs
        def i = [:]
        wrapper.input.each { k ->
            if (i.containsKey(k.name)) {
                def old = i.get(k.name)
                if (old instanceof List) {
                    old.add(k.value)
                } else {
                    old = [old, k.value]
                }
                i.put(k.name, old)
            } else {
                i.put(k.name, k.value)
            }
        }

        //add default inputs
        def shpResolutions = Holders.config.shpResolutions
        if (!(shpResolutions instanceof List)) {
            // comma separated or JSON list
            if (shpResolutions.toString().startsWith("[")) {
                shpResolutions = new org.json.simple.parser.JSONParser().parse(shpResolutions.toString())
            } else {
                shpResolutions = Arrays.asList(shpResolutions.toString().split(","))
            }
        }
        def grdResolutions = Holders.config.grdResolutions
        if (!(grdResolutions instanceof List)) {
            // comma separated or JSON list
            if (grdResolutions.toString().startsWith("[")) {
                grdResolutions = new org.json.simple.parser.JSONParser().parse(grdResolutions.toString())
            } else {
                grdResolutions = Arrays.asList(grdResolutions.toString().split(","))
            }
        }
        i.put('layersServiceUrl', Holders.config.grails.serverURL)
        i.put('bieUrl', Holders.config.bie.baseURL)
        i.put('biocacheServiceUrl', Holders.config.biocacheServiceUrl)
        i.put('phyloServiceUrl', Holders.config.phyloServiceUrl)
        i.put('shpResolutions', shpResolutions)
        i.put('grdResolutions', grdResolutions)
        i.put('sandboxHubUrl', Holders.config.sandboxHubUrl)
        i.put('sandboxBiocacheServiceUrl', Holders.config.sandboxBiocacheServiceUrl)
        i.put('namematchingUrl', Holders.config.namematching.url)
        i.put('geoserverUrl', Holders.config.geoserver.url)
        i.put('userId', task.userId)

        wrapper
    }

    class TaskThread extends Thread {
        def taskService
        def request
        Date created
        String taskId

        TaskThread(TaskService taskService, TaskWrapper task) {
            super(taskService.threadGroup, task.name) // insert into threadGroup
            this.request = task
            this.created = new Date(System.currentTimeMillis())
            this.taskId = task.taskId
        }

        void run() {
            try {
                log.debug "task:${request.id} starting"
                request.message = 'initialising'

                //create dir
                new File(getBasePath(request)).mkdirs()

                log.debug "task:${request.id} find operator"

                //find operator
                def operator
                taskService.getAllSpec().each { spec ->
                    if (spec.name.equalsIgnoreCase(request.name)) {
                        request.spec = spec
                        operator = Class.forName(spec.private.classname.toString()).newInstance()
                    }
                }

                if (operator == null) {
                    throw new Exception("missing process for task: ${request.name}")
                }

                request.message = 'getting resources'

                //init
                operator.task = request
                operator.taskService = taskService
                operator.slaveService = taskService.slaveService

                //start
                request.history.put(System.currentTimeMillis(), "running (id:${request.id})")
                request.message = 'running'
                log.debug "task:${request.id} running"
                operator.start()
                operator.taskLog("finished")

                request.message = 'publishing'
                log.debug "task:${request.id} publishing"
                masterService.publishResults(request)

                request.finished = true
                request.message = 'finished'
                request.history.put(System.currentTimeMillis(), "finished (id:${request.id})")
                log.debug "task:${request.id} finished"

                taskService.slaveService.signalMasterImmediately(request)
            } catch (err) {
                if (err instanceof InterruptedException) {
                    request.history.put(System.currentTimeMillis(), "cancelled (id:${request.id})")

                    // attempt to cancel Util.cmd
                    if (request.proc) {
                        try {
                            request.proc.destroy()
                            request.errorGobbler.interrupt()
                            request.outputGobbler.interrupt()
                        } catch (Exception ex) {
                            log.error "error cancelling cmd in progress: ${request.cmd}", ex
                        }
                    }

                } else {
                    log.error "error running request: ${request.id}", err
                    request.history.put(System.currentTimeMillis(), "failed (id:${request.id}): " + err.message)
                }

                request.finished = true
                request.message = 'finished'

                taskService.slaveService.signalMasterImmediately(request)

            }
            log.debug 'about to remove running task'

            taskService.running.remove(request.id)
        }
    }
}
