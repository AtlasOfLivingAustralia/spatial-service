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

import au.org.ala.layers.dto.Objects
import au.org.ala.spatial.Util
import grails.converters.JSON
import grails.transaction.Transactional

class TasksService {

    def masterService
    def objectDao

    def cancel(task) {
        def url = task.slave.url + "/task/cancel"
        def response = grails.converters.JSON.parse(Util.getUrl(url))

        // TODO: confirm the task is not finished before setting as cancelled
        update(task.id, [status: 2, message: 'cancelled'])

        if (response != null) {
            return true
        }

        return false
    }

    def getStatus(Task task) {
        def map = [status: task.status, message: task.message, id: task.id]

        if (task.history) {
            map.put('history', task.history)
        }

        if (task.output) {
            // TODO: cleanup task.output, add resource information for each type,
            // e.g. .zip requires a download URL, .html requires link URL, .tif and .shp require mappable information
            map.put('output', task.output)
        }

        map
    }

    @Transactional(readOnly = false)
    synchronized def update(id, newValues) {
        Task.withTransaction {
            Task t = Task.get(id)
            if (t != null) {
                def m = [:]
                newValues.each { k, v ->
                    if ('status'.equals(k)) t.status = v
                    else if ('message'.equals(k)) t.message = v
                    else if ('url'.equals(k)) t.url = v
                    else if ('err'.equals(k)) t.err.putAll(v)
                    else if ('history'.equals(k)) {
                        if (v.size() == 1) {
                            def n = 1
                        }
                        t.history.putAll(v)
                    } else if ('slave'.equals(k)) t.slave = v
                    else if ('output'.equals(k)) t.output = v
                }
            }
            if (!t.save(flush: true)) {
                t.errors.each {
                    log.error 'failed update status for task:' + id
                }
            }
        }
    }

    /*
    * add a task for 'name' and 'inputs'
    * 'input' is map of [inputName: inputValue]
    *  'identifier' is used to tag the process for making this instance unique, e.g. hash of input
     */

    @Transactional(readOnly = false)
    def create(name, identifier, input) {
        if (input == null) input = [:] as Map

        //get task spec
        def spec = masterService.spec(true).get(name)

        def task = null

        def tasks
        if (spec != null && spec?.private?.unique && (tasks = Task.findAllByStatusLessThanAndName(2, name)).size() > 0) {
            //is it a unique process already running or in queue?
            log.debug 'unique process:' + name + ' already running or in queue'
            task = tasks.get(0)
        } else if ((tasks = Task.findAllByStatusLessThanAndNameAndTag(2, name, identifier)).size() > 0) {
            //is it unique with name and tag?
            log.debug 'process with name:' + name + ' and tag: ' + 'is already running or in queue'
            task = tasks.get(0)
        } else {
            //create task
            task = new Task([name: String.valueOf(name), tag: String.valueOf(identifier)])
            // Task.withTransaction {
            if (!task.save()) {
                task.errors.each {
                    log.error it
                }
                }
            //}

            //inputs
            def inputs = []
            //input init from spec
            spec.input.each { k, v ->

                if (v.containsKey('constraints')) {
                    def i = !input.containsKey(k) ? null : input.get(k)

                    //mandatory
                    if (i == null && v.constraints.containsKey('mandatory') && v.constraints.mandatory.toString().toBoolean()) {
                        //TODO: error
                    }

                    //default
                    if (i == null && v.constraints.containsKey('default')) {
                        input.put(k, v.constraints.default)
                    }

                    //restrictions
                    if (i != null) {
                        def number = null
                        if ("double".equals(v.type)) {
                            number = i.toString().toBigDecimal()
                        } else if ("integer".equals(v.type)) {
                            number = i.toString().toBigInteger()
                        } else if ("string".equals(v.type)) {
                            number = i.toString().length()
                        } else if ("layers".equals(v.type)) {
                            number = i.length
                        } else if ("areas".equals(v.type)) {
                            number = i.length
                        } else if ("species".equals(v.type)) {
                            number = i.length
                        }
                        //min/max
                        if (number != null && v.constraints.containsKey('min') && v.constraints.min > number) {
                            //TODO: error for min
                        }
                        if (number != null && v.constraints.containsKey('max') && v.constraints.max < number) {
                            //TODO: error for max
                        }

                        if ("areas".equals(v.type)) {
                            //TODO: make sure each area exists and is a valid area for this spec
                            if (v.constraints.containsKey('wkt') && v.constraints.wkt) {

                            }
                            if (v.constraints.containsKey('pid') && v.constraints.pid) {

                            }
                            if (v.constraints.containsKey('envelope') && v.constraints.envelope) {

                            }
                        }

                        if ("upload".equals(v.type)) {
                            //TODO: confirm 'upload' value exists
                        }

                        if ("layers".equals(v.type)) {
                            i.each { layer ->
                                //TODO: make sure each layer exists and is a valid layer for this spec
                                if (v.constraints.containsKey('environmental') && v.constraints.environmental) {

                                }
                                if (v.constraints.containsKey('contextual') && v.constraints.contextual) {

                                }
                                if (v.constraints.containsKey('analysis') && v.constraints.analysis) {

                                }
                                if (v.constraints.containsKey('indb') && v.constraints.indb) {

                                }
                            }
                        }

                        if ("species".equals(v.type)) {
                            int speciesCount = 0
                            if (number != null && v.constraints.containsKey('minSpecies') && v.constraints.minSpecies > speciesCount) {
                                //TODO: error for min species count
                            }

                            int occurrenceCount = 0
                            if (number != null && v.constraints.containsKey('maxOccurrences') && v.constraints.maxOccurrences < occurrenceCount) {
                                //TODO: error for max occurrence count
                            }

                            if (number != null && v.constraints.containsKey('minOccurrences') && v.constraints.minOccurrences > occurrenceCount) {
                                //TODO: error for min occurrence count
                            }
                        }

                        if ("process".equals(v.type)) {
                            //TODO: check process is valid and finished
                            String processName = 'todo'
                            if (!v.constraints.name.equals(processName)) {
                                //TODO: error
                            }
                        }

                        if ("stringList".equals(v.type)) {
                            if (!v.constraints.list.contains(i)) {
                                //TODO: error, not a valid list value
                            }
                        }
                    }
                }
            }
            input.each { k, v ->
                if (v instanceof List) {
                    inputs.add(new InputParameter(name: k, value: (v as JSON).toString(), task: task))
                } else if (v instanceof Map) {
                    if (!v.containsKey('pid') && v.containsKey('wkt') && v.containsKey('name') && v.wkt.length() > 0) {
                        String pid = objectDao.createUserUploadedObject(v.wkt.toString(), v.name.toString(), '', null);
                        Objects object = objectDao.getObjectByPid(pid);
                        v.put('area_km', object.area_km)
                        v.put('pid', object.pid)
                    }
                    inputs.add(new InputParameter(name: k, value: (v as JSON).toString(), task: task))
                } else {
                    inputs.add(new InputParameter(name: k, value: v, task: task))
                }
            }
            InputParameter.withTransaction {
                inputs.each {
                    if (!it.save(flush: true)) {
                        it.errors.each {
                            log.error it
                        }
                    }
                }
            }
        }

        //TODO: signal for required actions only, not a full refresh
        //monitorService.signal()

        task
    }

    // attach final log, message and outputs to a task
    @Transactional(readOnly = false)
    def afterPublish(taskId, spec) {

        def task = Task.get(taskId)
        //task.err = spec.err

        def newValues = [:]
        if (spec.history != null && spec.history.size() > 0) newValues.put('history', spec.history)

        def formattedOutput = []
        spec.output.each { k, out ->
            if (k.equals('layers') || k.equals('layer')) {
                out.files.each { f1 ->
                    if (f1.endsWith('.tif')) {
                        //an environmental file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: task))
                    } else if (f1.endsWith('.shp')) {
                        //contextual file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: task))
                    }
                }
            } else if (k.equals('metadata')) {
                out.files.each { f1 ->
                    if (f1.endsWith('.html')) {
                        //a metadata file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: task))
                    }
                }
            } else if (k.equals('download')) {
                //a download zip exists
                formattedOutput.push(new OutputParameter(name: 'download.zip', file: 'download.zip', task: task))
            } else if (k.equals('areas')) {
                out.files.each { f1 ->
                    formattedOutput.push(new OutputParameter(name: 'area', file: f1, task: task))
                }
            } else if (k.equals('species')) {
                out.files.each { f1 ->
                    formattedOutput.push(new OutputParameter(name: 'species', file: f1, task: task))
                }
            }

        }
        OutputParameter.withTransaction {
            formattedOutput.each {
                if (!it.save(flush: true)) {
                    it.errors.each {
                        log.error it
                    }
                }
            }
        }

        if (formattedOutput.size() > 0) newValues.put('output', formattedOutput)

        if (newValues.size() > 0) update(taskId, newValues)
    }
}
