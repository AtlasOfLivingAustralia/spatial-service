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

import org.hibernate.StaleObjectStateException

class MonitorService {
    def monitorFreqency = 5 * 1000

    def grailsApplication
    def masterService
    def tasksService
    def checkThread = new Thread() {
        public void run() {
            try {
                while (running) {
                    //wait
                    synchronized (lock) {
                        lock.wait(monitorFreqency)
                    }

                    log.debug 'checking tasks and slaves'

                    // check running task status (for slaves that fail to push status)
                    checkTasks()

                    // check slaves without tasks
                    checkSlaves()
                }
            } catch (InterruptedException e) {
                log.error 'checkThread interrupted'
            } catch (err) {
                log.error 'checkThread error', err
            }
            log.debug 'checkThread ending'
        }
    }

    def running = true
    def lock = new Object()

    synchronized def monitor() {
        if (!checkThread.isAlive()) {
            checkThread.start()
        }
    }

    // inform monitoring thread that a change has occurred 
    def signal() {
        synchronized (lock) {
            lock.notify()
        }
    }

    def checkTasks() {
        Task.withNewTransaction {
            def list = Task.findAllByStatus(1)
            list.each { task ->

                log.debug "checking task " + task.id
                def up = false

                // query status
                try {
                    def t = Task.get(task.id)
                    up = masterService.checkStatus(t)
                    log.debug "is up " + up
                } catch (StaleObjectStateException err) {
                    up = true
                } catch (err) {
                    log.error 'failed to query task status', err
                }

                if (!up) {
                    log.warn "not up " + task.id
                    //TODO: handle slow or tmp unavailable server. Task errors will report as up and will not get here.

                    //make this task available for another slave
                    try {
                        log.warn "making task available to another slave: " + task.id
                        def newValues = [status: 0, url: null, slave: null]
                        tasksService.update(task.id, newValues)
                    } catch (StaleObjectStateException err) {
                        //ignore
                        log.error 'ignoring stale exception'
                    } catch (err) {
                        log.error "failed to make available to another slave " + task.id
                        log.warn 'failed to reset task ' + task.id
                    }
                }
            }
        }
    }

    def checkSlaves() {
        //log.error "checking slaves"
        try {
            //update slave task lists (remove items that are no longer running)
            def taskList = Task.createCriteria().list() {
                // running tasks
                eq('status', 1)
                lock false
            }
            masterService.slaves.each { slaveUrl, slave ->
                def stasks = slave.tasks()

                stasks.each { t ->
                    def found = false
                    taskList.each { tl ->
                        if (tl.id == t.key) {
                            found = true

                        }
                    }
                    if (!found) {
                        log.warn "removing task from slave"
                        slave.remove(t.value)
                    }
                }
            }

            //start the next task
            masterService.slaves.each { slaveUrl, slave ->
                //log.error "start next task"
                // is server available?
                if (masterService.ping(slave)) {
                    //list all tasks in the queue
                    def nextTasks = Task.createCriteria().list() {
                        eq('status', 0)
                        order('created', 'asc')
                        lock false
                    }

                    //sort
                    nextTasks.toSorted(new Comparator<Task>() {
                        @Override
                        int compare(Task o1, Task o2) {
                            return slave.getPriority(o1) - slave.getPriority(o2)
                        }
                    })

                    for (def nextTask : nextTasks) {
                        //compare server capabilities against this task
                        if (slave.canAdd(nextTask)) {

                            log.debug "starting: " + nextTask.id

                            def input = []
                            Task.withNewTransaction {
                                input = InputParameter.findAllByTask(nextTask)
                            }

                            //reset output
                            OutputParameter.withNewTransaction {
                                OutputParameter.findAllByTask(nextTask).each {
                                    it.delete()
                                }
                            }

                            //clear history
                            Task.withTransaction {
                                def task = Task.get(nextTask.id)
                                if (task.history) {
                                    task.history.clear()
                                    if (!task.save()) {
                                        it.errors.each {
                                            log.error it
                                        }
                                    }
                                }
                            }

                            //insert default inputs

                            //format inputs
                            def i = [:]
                            input.each { k ->
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
                            def shpResolutions = grailsApplication.config.shpResolutions
                            if (!(shpResolutions instanceof List)) {
                                // comma separated or JSON list
                                if (shpResolutions.toString().startsWith("[")) {
                                    shpResolutions = new org.json.simple.parser.JSONParser().parse(shpResolutions.toString())
                                } else {
                                    shpResolutions = Arrays.asList(shpResolutions.toString().split(","))
                                }
                            }
                            def grdResolutions = grailsApplication.config.grdResolutions
                            if (!(grdResolutions instanceof List)) {
                                // comma separated or JSON list
                                if (grdResolutions.toString().startsWith("[")) {
                                    grdResolutions = new org.json.simple.parser.JSONParser().parse(grdResolutions.toString())
                                } else {
                                    grdResolutions = Arrays.asList(grdResolutions.toString().split(","))
                                }
                            }
                            i.put('layersServiceUrl', grailsApplication.config.grails.serverURL)
                            i.put('bieUrl', grailsApplication.config.bie.baseURL)
                            i.put('biocacheServiceUrl', grailsApplication.config.biocacheServiceUrl)
                            i.put('phyloServiceUrl', grailsApplication.config.phyloServiceUrl)
                            i.put('shpResolutions', shpResolutions)
                            i.put('grdResolutions', grdResolutions)
                            i.put('sandboxHubUrl', grailsApplication.config.sandboxHubUrl)
                            i.put('sandboxBiocacheServiceUrl', grailsApplication.config.sandboxBiocacheServiceUrl)
                            i.put('geoserverUrl', grailsApplication.config.geoserver.url)
                            i.put('userId', nextTask.userId)

                            def id = nextTask.id
                            def response = masterService.start(slave, nextTask, i)

                            log.debug "started: " + response

                            if (response != null) {
                                slave.add(nextTask)

                                def repeat = 0
                                while (repeat < 5) {
                                    try {
                                        tasksService.update(id, [status: 1, slave: slaveUrl, url: response.url, message: 'starting'])
                                        repeat = 5
                                    } catch (err) {
                                        Thread.sleep(500)

                                        repeat++
                                        if (repeat >= 5) {
                                            log.error 'failed to update task from ready to running: ' + id
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return true
        } catch (err) {
            log.error 'error checking slaves', err
        }

        return false
    }
}
