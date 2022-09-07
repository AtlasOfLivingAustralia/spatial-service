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

import au.org.ala.spatial.process.SlaveProcess
import au.org.ala.spatial.slave.TaskWrapper
import com.google.common.util.concurrent.ThreadFactoryBuilder
import grails.converters.JSON
import grails.util.Holders

import javax.annotation.PostConstruct
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TaskQueueService {

    ExecutorService generalEecutor
    ExecutorService adminExecutor

    @PostConstruct
    def init() {
        generalEecutor = Executors.newFixedThreadPool(Holders.config.tasks.general.threadCount,
                new ThreadFactoryBuilder().setNameFormat("general-tasks-%d").setPriority(Thread.NORM_PRIORITY).build());

        adminExecutor = Executors.newFixedThreadPool(Holders.config.tasks.admin.threadCount,
                new ThreadFactoryBuilder().setNameFormat("general-tasks-%d").setPriority(Thread.NORM_PRIORITY).build());
    }

    void queue(Task task, spec) {
        def wrappedTask = wrapTask(task, spec)

        if (wrappedTask.isPublic) {
            generalEecutor.submit(new TaskThread(wrappedTask))
        } else {
            adminExecutor.submit(new TaskThread(wrappedTask))
        }
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

    TaskWrapper wrapTask(Task task, spec) {
        def isPublic = spec.private.public

        //format inputs
        def i = [:]
        task.input.each { k ->
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

        TaskWrapper wrapper = new TaskWrapper(taskId: task.id, input: i, name: task.name, task: task, spec: spec,
                isPublic: isPublic,
                path: Holders.config.data.dir + "/" + (isPublic ? 'public' : 'private') + "/" + task.id + "/")

        wrapper
    }

    class TaskThread extends Thread {
        TasksService tasksService
        TaskWrapper taskWrapper
        Date created

        TaskThread(TasksService tasksService, TaskWrapper taskWrapper) {
            super(TaskQueueService.name, taskWrapper.name) // insert into threadGroup
            this.taskWrapper = taskWrapper
            this.created = new Date(System.currentTimeMillis())
            this.tasksService = tasksService
        }

        void run() {
            try {
                //create dir
                new File(taskWrapper.path).mkdirs()

                //find operator
                SlaveProcess operator = Class.forName(taskWrapper.spec.private.classname.toString()).newInstance()

                if (operator == null) {
                    throw new Exception("missing process for task: ${taskWrapper.name}")
                }

                //init
                operator.task = taskWrapper
                operator.tasksService = tasksService

                //start
                operator.start()

                //finish
                tasksService.publish(!taskWrapper.isPublic, taskWrapper.taskId)

                taskWrapper.message = 'finished'
                taskWrapper.history.put(System.currentTimeMillis(), "finished (id:${taskWrapper.taskId})")
            } catch (err) {
                if (err instanceof InterruptedException) {
                    taskWrapper.history[System.currentTimeMillis()] = "cancelled (id:${taskWrapper.taskId})"

                    // attempt to cancel Util.cmd
                    if (taskWrapper.proc) {
                        try {
                            taskWrapper.proc.destroy()
                            taskWrapper.errorGobbler.interrupt()
                            taskWrapper.outputGobbler.interrupt()
                        } catch (Exception ex) {
                            log.error "error cancelling cmd in progress: ${taskWrapper.cmd}", ex
                        }
                    }

                } else {
                    log.error "error running request: ${taskWrapper.taskId}", err
                    taskWrapper.history.put(System.currentTimeMillis(), "failed (id:${taskWrapper.taskId}): " + err.message)
                }

                taskWrapper.finished = true
                taskWrapper.message = 'failed'
            }
        }
    }
}
