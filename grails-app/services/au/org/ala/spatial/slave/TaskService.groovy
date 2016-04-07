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
import groovy.json.JsonOutput
import org.apache.commons.io.FileUtils
import org.codehaus.groovy.grails.commons.GrailsApplication

import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TaskService {

    GrailsApplication grailsApplication
    FileLockService fileLockService

    private getSlaveService() {
        grailsApplication.mainContext.slaveService
    }

    // tasks do not persist in the slave
    Map tasks = [:] as ConcurrentHashMap
    final Object createTaskLock = new Object()

    Map running = [:]

    void start(Task req) {

        try {

            Task request = req
            TaskThread t = new TaskThread(this, request)

            t.start()

            running.put(request.id, [request: request, thread: t])
        } catch (err) {
            log.error "failed to start thread for task: " + req.id, err
            req.err.put(System.currentTimeMillis(), "unknown error")
            req.setFinished(true)
        }
    }

    //TODO: some validation is in task create
    def validateInput(task) {
        Map input = task.input
        input.each { k, v ->
            switch (k) {
                case 'area':
                    break;
                case 'layer':
                    break;
                case 'double':
                    break;
                case 'integer':
                    break;
                case 'process':
                    break;
            }
        }
    }

    List getAllSpec() {
        List list = []

        def resource = this.class.getResource("/processes/")
        def dir = new File(resource.getPath())

        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".json")) {
                String name = "au.org.ala.spatial.process." + f.getName().substring(0, f.getName().length() - 5)
                try {
                    Class clazz = Class.forName(name);
                    list.add(((SlaveProcess) clazz.newInstance()).spec())
                } catch (err) {
                    log.error("unable to instantiate " + name)
                }
            }
        }

        list
    }

    def getZip(task) {
        String zipFile = getBasePath(task) + task.id + ".zip"

        //open zip for writing
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))

        task.output.each { name, files ->
            files.each { file ->
                //skip zipping when this is the master service and the file does not need to be moved
                if (!grailsApplication.config.service.enable) {
                    //it is a task dir file if it does not start with '/'
                    def inputFile = file.startsWith('/') ? grailsApplication.config.data.dir + file : getBasePath(task) + file
                    def flist = [:]
                    if (new File(inputFile).exists()) {
                        flist.put(inputFile, file)
                    } else {
                        //match with inputFile as prefix
                        def prefixFile = new File(inputFile)
                        def list = prefixFile.getParentFile().listFiles()
                        list.each { f ->
                            if (f.getName().startsWith(prefixFile.getName() + ".")) {
                                def extension = f.getPath().substring(inputFile.length())
                                flist.put(inputFile + extension, file + extension)
                            }
                        }
                    }
                    flist.each { k, v ->
                        //add to zip
                        ZipEntry ze = new ZipEntry(v)
                        zos.putNextEntry(ze)

                        //open and stream
                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(k))
                        byte[] bytes = new byte[1024]
                        int size
                        while ((size = bis.read(bytes)) > 0) {
                            zos.write(bytes, 0, size)
                        }

                        //close file and entry
                        zos.flush()
                        zos.closeEntry()
                        bis.close()
                    }
                }
            }
        }

        // add a zip entry containing the spec.json
        ZipEntry ze = new ZipEntry('spec.json')
        zos.putNextEntry(ze)
        JsonOutput jsonOutput = new JsonOutput()
        def map = task.spec

        //remove private parameters
        if (map.containsKey('private')) {
            map.remove('private')
        }

        //insert final log
        map.putAt('history', task.history)

        //insert output files into spec.output
        if (map.containsKey('output')) {
            map.output.each { k, v ->
                if (task.output.containsKey(k)) {
                    v.put('files', task.output.get(k))
                }
            }
        }
        def json = jsonOutput.toJson(map)
        zos.write(json.bytes)
        zos.closeEntry()

        //close
        zos.flush()
        zos.close()

        zipFile
    }

    def getResourcePath(inputSpec, value) {
        def dir = grailsApplication.config.data.dir

        // look in cache dir and return this file name if it is missing
        if ('area'.equalsIgnoreCase(inputSpec.type)) {
            //format value as a filename
            return dir + '/cache/' + URLEncoder.encode(value, "UTF-8")
        } else if ('layer'.equalsIgnoreCase(inputSpec.type)) {
            //format value as a filename
            if (!value.contains(':')) {
                return dir + '/layer/' + URLEncoder.encode(value, "UTF-8")
            } else {
                //extract resolution value
                def split = value.split(':')
                return dir + '/standard_layer/' + split[1] + '/' + URLEncoder.encode(split[0], "UTF-8")
            }
        } else {
            return dir + '/' + inputSpec.type + '/' + URLEncoder.encode(value, "UTF-8")
        }
    }

    def getBasePath(task) {
        String url = grailsApplication.config.data.dir + '/public/' + task.taskId + '/'
        return url
    }

    def getStatus(task) {
        def map = [history: task.history, message: task.message]
        if (task.finished) map.put('finished', true)

        map
    }

    def cancel(task) {
        //tasks.remove(task.id)

        true
    }

    def newTask(params) {
        def task
        synchronized (createTaskLock) {
            log.debug 'creating task: ' + params.taskId

            task = new Task()
            task.taskId = params.taskId
            task.input = params.input
            task.name = params.name

            // don't add if already running
            task.id = Long.parseLong(task.taskId)
            if (!tasks.containsKey(task.taskId)) {
                tasks.put(task.id, task)
            }
        }

        task
    }

    class TaskThread extends Thread {
        def taskService
        def request

        public TaskThread(taskService, request) {
            this.taskService = taskService
            this.request = request
        }

        public void run() {
            try {
                log.error 'task:' + request.id + ' starting'
                request.message = 'initialising'

                //create dir
                new File(getBasePath(request)).mkdirs()

                log.error 'task:' + request.id + ' find operator'

                //find operator
                def operator
                taskService.getAllSpec().each { spec ->
                    if (spec.name.equalsIgnoreCase(request.name)) {
                        request.spec = spec
                        operator = Class.forName(spec.private.classname).newInstance()
                    }
                }

                if (operator == null) {
                    log.error 'missing process for task: ' + request.name
                }

                request.message = 'getting resources'
                log.error 'task:' + request.id + ' getting resources'
                taskService.slaveService.getResources(request)

                validateInput(request)

                //init
                operator.task = request
                operator.taskService = taskService
                operator.slaveService = taskService.slaveService
                operator.grailsApplication = taskService.grailsApplication
                operator.fileLockService = taskService.fileLockService

                //start
                request.message = 'running'
                log.error 'task:' + request.id + ' running'
                operator.start()

                request.message = 'publishing'
                log.error 'task:' + request.id + ' publishing'
                taskService.slaveService.publishResults(request)

                request.finished = true
                request.message = 'finished'
                log.error 'task:' + request.id + ' finished'

                taskService.slaveService.signalMasterImmediately(request)

                taskService.slaveService.statusUpdates.remove(request.id)

                //taskService.tasks.remove(request.id)
            } catch (err) {
                log.error "error running request: " + request.id, err

                request.finished = true
                request.message = 'finished'
                request.history.put(System.currentTimeMillis(), "failed")

                taskService.slaveService.signalMasterImmediately(request)

                taskService.slaveService.statusUpdates.remove(request.id)

                //taskService.tasks.remove(request.id)
            }
            log.error 'about to remove running task'

            taskService.running.remove(request.id)

            //delete from public dir if master service is remote
            if (!grailsApplication.config.service.enable) {
                FileUtils.deleteDirectory(new File(grailsApplication.config.data.dir.toString() + '/public/' + request.id))
            }
        }
    }
}
