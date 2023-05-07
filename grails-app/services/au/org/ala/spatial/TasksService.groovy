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


import au.org.ala.spatial.process.SlaveProcess
import au.org.ala.spatial.dto.TaskWrapper
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.apache.commons.lang3.StringUtils

//@CompileStatic
class TasksService {

    SpatialObjectsService spatialObjectsService
    FieldService fieldService
    LayerIntersectService layerIntersectService

    PublishService publishService
    TaskQueueService taskQueueService

    Map<String, Task> transientTasks = [:]

    def cancel(taskId) {
        taskQueueService.cancel(taskId)
    }

    Map<String, Object> getStatus(taskId) {
        def task = transientTasks.get(taskId)
        if (!task) {
            task = Task.get(taskId)
        }
        if (task) {
            def map = [status: task.status, message: task.message, id: task.id, name: task.name]

            if (task.history) {
                map.history = task.history
            }

            if (task.output) {
                // e.g. .zip requires a download URL, .html requires link URL, .tif and .shp require mappable information
                map.put('output', task.output)
            }

            map
        } else {
            null
        }
    }

    /*
    * add a task for 'name' and 'inputs'
    * 'input' is map of [inputName: inputValue]
    * 'identifier' is used to tag the process for making this instance unique, e.g. hash of input
    */
    @Transactional(readOnly = false)
    def create(name, identifier, input, sessionId = null, userId = null, email = null) {
        if (input == null) input = [:] as Map

        //get task spec
        def spec = spec(true).get(name)

        if (spec == null) {
            log.error("failed to find spec for: " + name)
        }

        def task = null

        def tasks
        if (spec != null && spec?.private?.unique && (tasks = Task.findAllByStatusLessThanAndName(2, name)).size() > 0) {
            //is it a unique process already running or in queue?
            log.debug 'unique process:' + name + ' already running or in queue'
            task = tasks.get(0)
        } else if ((tasks = Task.findAllByStatusLessThanAndNameAndTagAndUserId(2, name, identifier, userId)).size() > 0) {
            //is it unique with name and tag and user?
            log.debug 'process with name:' + name + ' and tag: ' + ' and user: ' + userId + ' is already running or in queue'
            task = tasks.get(0)
        } else if (spec != null) {
            //create task
            task = new Task([name  : String.valueOf(name), tag: String.valueOf(identifier),
                             userId: String.valueOf(userId), sessionId: String.valueOf(sessionId),
                             email : String.valueOf(email)])

            task.history.put(System.currentTimeMillis() as String, "created")

            if (!task.save()) {
                task.errors.each {
                    log.error it
                }
            }

            def inputs = []

            input.each { k, v ->
                if (v == null || v == null) {
                    //skip
                } else if (spec.input[k]?.type == 'area') {
                    //register area pid
                    def list = []
                    v.each { a ->
                        if (a instanceof Map && a.containsKey('wkt') && a.containsKey('name') && a.wkt.length() > 0 &&
                                (!a.containsKey('pid') || a.get('pid').length() == 0)) {
                            String pid = spatialObjectsService.createUserUploadedObject(a.wkt.toString(), a.name.toString(), '', null)
                            Objects object = spatialObjectsService.getObjectByPid(pid)
                            a.put('area_km', object.area_km)
                            a.put('pid', object.pid)
                            list.push(a)
                        } else {
                            list.push(a)
                        }
                    }
                    inputs.add(new InputParameter(name: k, value: (list as JSON).toString(), task: task))
                } else if (v instanceof List) {
                    for (def item : v) {
                        registerSpeciesQid(item)
                    }
                    inputs.add(new InputParameter(name: k, value: (v as JSON).toString(), task: task))
                } else if (v instanceof Map) {
                    registerSpeciesQid(v)

                    inputs.add(new InputParameter(name: k, value: (v as JSON).toString(), task: task))
                } else {
                    inputs.add(new InputParameter(name: k, value: v, task: task))
                }
            }
            InputParameter.withTransaction {
                inputs.each {
                    if (!it.save(flush: true)) {
                        it.errors.each {
                            log.error 'create task failed', it
                        }
                    }
                }
            }
            task.input = inputs
            task.output = []
        }

        transientTasks.put(task.id as Long, task)

        taskQueueService.queue(task, spec)
    }

    def registerSpeciesQid(v) {
        if (v instanceof Map && v.containsKey('q') && v.containsKey('bs') &&
                v.q instanceof List && v.q.size() > 0) {
            def qid = Util.makeQid(v)
            if (validateQID(qid)) {
                v.put('q', 'qid:' + qid)
            } else {
                log.error("Failed to generate QID. Returned: " + qid)
                log.debug(v.toString())
                throw new Exception("Error: failed to generate qid!")
            }
        }
    }

    private validateQID(qid) {
        if (qid) {
            return qid ==~ /^-?\d+\.?\d*$/
        }
        return false
    }

    // attach final log, message and outputs to a task
    @Transactional(readOnly = false)
    def afterPublish(TaskWrapper taskWrapper) {

        def formattedOutput = []
        taskWrapper.spec.output.each { k, out ->
            if (k == 'layers' || k == 'layer') {
                out.files.each { f1 ->
                    if (f1.endsWith('.tif')) {
                        //an environmental file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: taskWrapper.task))
                    } else if (f1.endsWith('.shp')) {
                        //contextual file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: taskWrapper.task))
                    }
                }
            } else if (k == 'metadata') {
                out.files.each { f1 ->
                    if (f1.endsWith('.html')) {
                        //a metadata file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: taskWrapper.task))
                    }
                }
            } else if (k == 'download') {
                //a download zip exists
                formattedOutput.push(new OutputParameter(name: 'download.zip', file: 'download.zip', task: taskWrapper.task))
            } else if (k == 'areas' || k == 'envelopes') {
                out.files.each { f1 ->
                    formattedOutput.push(new OutputParameter(name: 'area', file: f1, task: taskWrapper.task))
                }
            } else if (k == 'species') {
                out.files.each { f1 ->
                    formattedOutput.push(new OutputParameter(name: 'species', file: f1, task: taskWrapper.task))
                }
            } else if (k == 'csv') {
                out.files.each { f1 ->
                    if (f1.endsWith('.csv')) {
                        //a csv file
                        formattedOutput.push(new OutputParameter(name: f1, file: f1, task: taskWrapper.task))
                    }
                }
            } else {
                out.files.each { f1 ->
                    formattedOutput.push(new OutputParameter(name: k, file: f1, task: taskWrapper.task))
                }
            }
        }

        // flush task because it is finished
        Task.withTransaction {
            if (!taskWrapper.task.save(flush: true)) {
                taskWrapper.task.errors.each {
                    log.error it
                }
            }
        }

        // flush outputs
        OutputParameter.withTransaction {
            formattedOutput.each {
                if (!it.save(flush: true)) {
                    it.errors.each {
                        log.error it
                    }
                }
            }
        }

        // fetch and include outputs, inputs, history
        taskWrapper.task.output = formattedOutput
    }

    /**
     * Validate input against name's spec.
     *
     *
     *
     * @param name
     * @param input
     * @return map of errors
     */
    def validateInput(name, input, isAdmin) {
        if (input == null) input = [:] as Map

        //get task spec
        def spec = spec(isAdmin).get(name)

        def errors = [:]

        if (spec == null) {
            errors.put("spec", "failed to find spec for: " + name)
            return errors
        }

        //input init from spec
        spec?.input.each { k, v ->

            if (v.containsKey('constraints')) {
                def i = !input.containsKey(k) ? null : input.get(k)

                //mandatory
                if (i == null && v.constraints.containsKey('mandatory') && v.constraints.mandatory.toString().toBoolean()) {
                    errors.put(k, "Input parameter $k has no value.")
                }

                //default
                if (i == null && v.constraints.containsKey('default')) {
                    //no value provided, use the default
                    input.put(k, v.constraints.default)
                }

                //restrictions
                if (i != null) {
                    //parse a numerical value for validation
                    //- the actual value: double, integer
                    //- the number of list items: string, layers, areas, species
                    def number = null
                    if ("double" == v.type) {
                        number = i.toString().toBigDecimal()
                    } else if ("integer" == v.type) {
                        number = i.toString().toBigInteger()
                    } else if ("string" == v.type) {
                        number = i.toString().length()
                    } else if ("layers" == v.type) {
                        number = i.length
                    } else if ("areas" == v.type) {
                        number = i.length
                    } else if ("species" == v.type) {
                        //number = i.length
                    }
                    //min/max
                    if (number != null && v.constraints.containsKey('min') && v.constraints.min > number) {
                        if ("string" == v.type)
                            errors.put(k, "Input parameter $k=$v is too short. Minimum number of characters is ${v.constraints.min}.")
                        else if (v.type.toString().endsWith("s"))
                            errors.put(k, "Input parameter $k has too few ${v.type}. Only ${number} provided. Minimum number of ${v.type} is ${v.constraints.min}.")
                        else
                            errors.put(k, "Input parameter $k=$v is too small. Minimum value is ${v.constraints.min}.")
                    }
                    if (number != null && v.constraints.containsKey('max') && v.constraints.max < number) {
                        if ("string" == v.type)
                            errors.put(k, "Input parameter $k=$v is too long. Maximum number of characters is ${v.constraints.min}.")
                        else if (v.type.toString().endsWith("s"))
                            errors.put(k, "Input parameter $k has too many ${v.type}. ${number} ${v.type} provided. Maximum number of ${v.type} is ${v.constraints.min}.")
                        else
                            errors.put(k, "Input parameter $k=$v is too large. Maximum value is ${v.constraints.min}.")
                    }

                    if ("areas" == v.type) {
                        int envelopes = 0
                        int gridAsShp = 0
                        for (Object area : i) {
                            if (area.pid?.toString()?.contains(":[")) {
                                envelopes++
                            } else if (area.pid?.toString()?.contains(':')) {
                                gridAsShp++
                            }
                        }
                        //envelopes are not allowed (false)
                        if (envelopes > 0 && v.constraints.containsKey('envelope') && !v.constraints.envelope?.toString()?.toBoolean()) {
                            errors.put(k, "Input parameter $k cannot contain envelope areas.")
                        }
                        //gridAsShp are not allowed (false)
                        if (envelopes > 0 && v.constraints.containsKey('gridasshape') && !v.constraints.gridasshape?.toString()?.toBoolean()) {
                            errors.put(k, "Input parameter $k cannot contain grid as shape layer areas.")
                        }

                        //TODO: update getObjectByPid to support envelope areas
                        if (i.containsKey("pid") && spatialObjectsService.getObjectByPid(i.pid) == null) {
                            errors.put(k, "Input parameter $k=${i.pid} has an invalid pid value.")
                        } else if (i.containsKey("wkt") && !StringUtils.isEmpty(i.wkt) && SpatialUtils.calculateArea(i.wkt) <= 0 /* TODO: validateInput WKT */) {
                            errors.put(k, "Input parameter $k has invalid WKT.")
                        } else {
                            //area size constraints
                            if (v.constraints.containsKey("minArea") || v.constraints.containsKey("maxArea")) {
                                //calc area
                                double areaKm = 0
                                for (Object area : i) {
                                    if (i.containsKey("pid") && spatialObjectsService.getObjectByPid(i.pid) != null) {
                                        areaKm += spatialObjectsService.getObjectByPid(i.pid).getArea_km()
                                    } else if (i.containsKey("wkt")) {
                                        areaKm += SpatialUtils.calculateArea(i.wkt)
                                    }
                                }
                                if (v.constraints.containsKey("maxArea") && v.constraints.maxArea < areaKm) {
                                    errors.put(k, "Input parameter $k area (sq km) is too large. It is $areaKm. The maximum value is ${v.constraints.areaMax}.")
                                } else if (v.constraints.containsKey("minArea") && v.constraints.minArea > areaKm) {
                                    errors.put(k, "Input parameter $k area (sq km) is too small. It is $areaKm. The minimum value is ${v.constraints.areaMin}.")
                                }
                            }
                        }
                    }

                    if ("upload" == v.type) {
                        if (!new File(spatialConfig.data.dir + "/uploads/" + i).exists()) {
                            errors.put(k, "Input parameter $k=$i has no upload directory.")
                        }
                    }

                    if ("layers" == v.type) {
                        i.each { layer ->
                            Fields f = fieldService.getFieldById(layer, false)
                            if (f == null) {
                                if (f?.type == "e" && v.constraints.containsKey('environmental') && !v.constraints.environmental.toString().toBoolean()) {
                                    errors.put(k, "Input parameter $k cannot contain environmental layers. Remove: ${f.name} (${layer}).")
                                }
                                if (f?.type == "c" && v.constraints.containsKey('contextual') && !v.constraints.contextual.toString().toBoolean()) {
                                    errors.put(k, "Input parameter $k cannot contain contextual layers. Remove: ${f.name} (${layer}).")
                                }
                                if (!f?.indb && v.constraints.containsKey('indb') && !v.constraints.indb.toString().toBoolean()) {
                                    errors.put(k, "Input parameter $k layers must be in biocache index. Remove: ${f.name} (${layer}).")
                                }
                            } else if (layerService.getIntersectionFile(layer) != null &&
                                    v.constraints.containsKey('analysis') && !v.constraints.analysis.toString().toBoolean()) {
                                //analysis layers have an IntersectionFile but do not have a Field.
                                errors.put(k, "Input parameter $k cannot contain analysis layers. Remove: ${f.name} (${layer}).")
                            }
                        }
                    }

                    if ("species" == v.type) {
                        if (v.constraints.containsKey('minSpecies') || v.constraints.containsKey('maxSpecies')) {
                            int speciesCount = Util.speciesCount(i)
                            if (v.constraints.containsKey('minSpecies') && v.constraints.minSpecies > speciesCount) {
                                errors.put(k, "Input parameter $k has only $speciesCount species. The minimum is ${v.constrains.minSpecies}")
                            } else if (v.constraints.containsKey('maxSpecies') && v.constraints.maxSpecies < speciesCount) {
                                errors.put(k, "Input parameter $k has $speciesCount species. The maximum is ${v.constrains.maxSpecies}")
                            }
                        }

                        if (v.constraints.containsKey('maxOccurrences') || v.constraints.containsKey('minOccurrences')) {
                            int occurrenceCount = Util.occurrenceCount(i)
                            if (v.constraints.containsKey('maxOccurrences') && v.constraints.maxOccurrences < occurrenceCount) {
                                errors.put(k, "Input parameter $k has only $speciesCount species. The maximum is ${v.constrains.maxSpecies}")
                            } else if (v.constraints.containsKey('minOccurrences') && v.constraints.minOccurrences > occurrenceCount) {
                                errors.put(k, "Input parameter $k has only $speciesCount species. The minimum is ${v.constrains.minSpecies}")
                            }
                        }
                    }

                    if ("process" == v.type) {
                        def process
                        Task.withNewTransaction {
                            process = Task.findById(i)
                        }
                        if (!process) {
                            errors.put(k, "Input parameter $k=$i does not exist.")
                        } else if (process.status < 2) {
                            errors.put(k, "Input parameter $k=$i has a task that is in the queue or running. Wait until task is finished.")
                        } else if (process.status == 2) {
                            errors.put(k, "Input parameter $k=$i is a task that was cancelled so is invalid.")
                        } else if (process.status == 3) {
                            errors.put(k, "Input parameter $k=$i is a task that failed so is invalid.")
                        } else if (v.constraints.containsKey("name") && v.constraints.name != process.name) {
                            errors.put(k, "Input parameter $k=$i, with the name ${process.name}, must be a task with the name ${v.constraints.name}.")
                        }
                    }

                    if ("stringList" == v.type) {
                        if (!v.constraints.list.contains(i)) {
                            errors.put(k, "Input parameter $k=$i is not a valid value.")
                        }
                    }
                }
            }
        }

        errors
    }

    TaskWrapper reRun(Task task) {
        //reset output
        OutputParameter.withNewTransaction {
            OutputParameter.findAllByTask(task).each {
                it.delete()
            }
        }

        //clear history
        Task.withTransaction {
            if (task.history) {
                task.history.clear()
                if (!task.save()) {
                    it.errors.each {
                        log.error it
                    }
                }
            }
        }

        task.status = 0
        task.message = "restarting"
        task.history.put(System.currentTimeMillis() as String, "restarted")

        transientTasks.put(task.id as Long, task)

        taskQueueService.queue(task, spec(true)[task.name])
    }

    def _spec = [:]
    def _specAdmin = [:]

    def spec(boolean includePrivate) {
        if (!_spec) {

            getAllSpec().each { it ->
                def name = it.name
                def cap = it
                _specAdmin.put(name, cap)
                boolean iPrivate = !cap.containsKey('private') || !cap.private.containsKey('public') || cap.private.public
                if (iPrivate) {
                    _spec.put(name, cap.findAll { i ->
                        if (!includePrivate && i.key == 'private') {
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

    List getAllSpec() {
        List list = []

        def resource = TaskQueueService.class.getResource("/processes/")
        def dir = new File(resource.getPath())

        // default processes
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".json") && f.getName() != "limits.json") {
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
            if (f.getName().endsWith(".json") && f.getName() != "limits.json") {
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

    Map publish(TaskWrapper taskWrapper) {
        Map map = [:]
        try {
            // update spec.output.files for files to add to download.zip
            taskWrapper.spec.output.each { k, v ->
                if (taskWrapper.task.output.containsKey(k)) {
                    v.put('files', taskWrapper.task.output.get(k))
                }
            }

            // do publishing
            publishService.publish(taskWrapper)

            //update log and outputs
            afterPublish(taskWrapper)

            map.put('status', 'published')
        } catch (err) {
            log.error 'failed to publish files', err
            map.put('status', 'failed')
        }

        map
    }
}
