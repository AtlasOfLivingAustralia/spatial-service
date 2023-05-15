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

import au.org.ala.spatial.dto.ProcessSpecification
import au.org.ala.spatial.process.SlaveProcess
import au.org.ala.spatial.dto.TaskWrapper
import com.google.common.util.concurrent.ThreadFactoryBuilder

import javax.annotation.PostConstruct
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TaskQueueService {

    ExecutorService generalEecutor
    ExecutorService adminExecutor

    TasksService tasksService
    SpatialConfig spatialConfig

    @PostConstruct
    def init() {
        generalEecutor = Executors.newFixedThreadPool(spatialConfig.task.general.threads,
                new ThreadFactoryBuilder().setNameFormat("general-tasks-%d").setPriority(Thread.NORM_PRIORITY).build())

        adminExecutor = Executors.newFixedThreadPool(spatialConfig.task.admin.threads,
                new ThreadFactoryBuilder().setNameFormat("general-tasks-%d").setPriority(Thread.NORM_PRIORITY).build())
    }

    TaskWrapper queue(Task task, ProcessSpecification spec) {
        TaskWrapper taskWrapper = wrapTask(task, spec)

        if (taskWrapper.spec.privateSpecification.isPublic) {
            generalEecutor.submit(new TaskThread(tasksService, spatialConfig, taskWrapper))
        } else {
            adminExecutor.submit(new TaskThread(tasksService, spatialConfig, taskWrapper))
        }

        taskWrapper
    }

    def getResourcPath(inputSpec, value) {
        def dir = spatialConfig.data.dir

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

    TaskWrapper wrapTask(Task task, ProcessSpecification spec) {
        def isPublic = spec.privateSpecification.isPublic

        //format inputs
//        def i = [:]
//        task.input.each { k ->
//            if (i.containsKey(k.name)) {
//                def old = i.get(k.name)
//                if (old instanceof List) {
//                    old.add(k.value)
//                } else {
//                    old = [old, k.value]
//                }
//                i.put(k.name, old)
//            } else {
//                i.put(k.name, k.value)
//            }
//        }

//        //add default inputs
//        def shpResolutions = spatialConfig.shpResolutions
//        if (!(shpResolutions instanceof List)) {
//            // comma separated or JSON list
//            if (shpResolutions.toString().startsWith("[")) {
//                shpResolutions = new JSONParser().parse(shpResolutions.toString())
//            } else {
//                shpResolutions = Arrays.asList(shpResolutions.toString().split(","))
//            }
//        }
//        def grdResolutions = spatialConfig.gridResolutions
//        if (!(grdResolutions instanceof List)) {
//            // comma separated or JSON list
//            if (grdResolutions.toString().startsWith("[")) {
//                grdResolutions = new JSONParser().parse(grdResolutions.toString())
//            } else {
//                grdResolutions = Arrays.asList(grdResolutions.toString().split(","))
//            }
//        }
//        i.put('layersServiceUrl', spatialConfig.grails.serverURL)
//        i.put('bieUrl', spatialConfig.bie.baseURL)
//        i.put('biocacheServiceUrl', spatialConfig.biocacheServiceUrl)
//        i.put('phyloServiceUrl', spatialConfig.phyloServiceUrl)
//        i.put('shpResolutions', shpResolutions)
//        i.put('grdResolutions', grdResolutions)
//        i.put('sandboxHubUrl', spatialConfig.sandboxHubUrl)
//        i.put('sandboxBiocacheServiceUrl', spatialConfig.sandboxBiocacheServiceUrl)
//        i.put('namematchingUrl', spatialConfig.namematching.url)
//        i.put('geoserverUrl', spatialConfig.geoserver.url)
//        i.put('userId', task.userId)

        TaskWrapper wrapper = new TaskWrapper(task: task, spec: spec,
                path: spatialConfig.data.dir + "/" + (isPublic ? 'public' : 'private') + "/" + task.id + "/")

        wrapper
    }

    Object cancel(Object o) {
        //TODO
    }

    class TaskThread extends Thread {
        TasksService tasksService
        SpatialConfig spatialConfig
        TaskWrapper taskWrapper
        Date created

        TaskThread(TasksService tasksService, SpatialConfig spatialConfig, TaskWrapper taskWrapper) {
            super(new ThreadGroup(TaskQueueService.name), taskWrapper.task.name) // insert into threadGroup
            this.taskWrapper = taskWrapper
            this.created = new Date(System.currentTimeMillis())
            this.tasksService = tasksService
            this.spatialConfig = spatialConfig
        }

        void run() {
            try {
                //create dir
                new File(taskWrapper.path).mkdirs()

                //find operator
                SlaveProcess operator = Class.forName(taskWrapper.spec.privateSpecification.classname.toString()).newInstance()

                if (operator == null) {
                    throw new Exception("missing process for task: ${taskWrapper.task.name}")
                }

                //init
                operator.taskWrapper = taskWrapper
                operator.tasksService = tasksService
                operator.spatialConfig = spatialConfig
                operator.fieldService = tasksService.fieldService
                operator.layerService = tasksService.layerService
                operator.distributionsService = tasksService.distributionsService
                operator.tasksService = tasksService.tasksService
                operator.tabulationService = tasksService.tabulationService
                operator.spatialObjectsService = tasksService.spatialObjectsService
                operator.gridCutterService = tasksService.gridCutterService
                operator.tabulationGeneratorService = tasksService.tabulationGeneratorService

                //start
                operator.start()

                //finished
                taskWrapper.task.status = 4
                taskWrapper.task.message = 'finished'
                taskWrapper.task.history.put(System.currentTimeMillis() as String, "finished (id:${taskWrapper.task.id})" as String)

                //publish
                tasksService.publish(taskWrapper)

                // flush task
                if (!taskWrapper.task.save(flush: true)) {
                    taskWrapper.task.errors.each {
                        log.error 'save task failed', it
                    }
                }

                // flush outputs
                OutputParameter.withTransaction {
                    taskWrapper.task.output.each {
                        if (!it.save(flush: true)) {
                            it.errors.each {
                                log.error 'save OutputParameter failed', it
                            }
                        }
                    }
                }
            } catch (err) {
                if (err instanceof InterruptedException) {
                    taskWrapper.task.history[System.currentTimeMillis() as String] = "cancelled (id:${taskWrapper.task.id})" as String

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
                    log.error "error running request: ${taskWrapper.task.id}", err
                    taskWrapper.task.history.put(System.currentTimeMillis() as String, ("failed (id:${taskWrapper.task.id}): " + err.message) as String)
                }

                taskWrapper.task.status = 3
                taskWrapper.task.message = 'failed'

                //tasksService.publish(taskWrapper)

                if (!taskWrapper.task.save(flush: true)) {
                    it.errors.each {
                        log.error 'save task failed', it
                    }
                }
            }
        }
    }
}
