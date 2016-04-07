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

import grails.converters.JSON

import static grails.async.Promises.onComplete
import static grails.async.Promises.task

class SlaveController {

    def slaveService
    def taskService
    def fileLockService
    def slaveAuthService

    def reRegister() {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        onComplete([task { slaveService.registerWithMaster() }]) { result ->
        }

        def map = [status: "requested re-register"]
        render map as JSON
    }

    def capabilities() {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        def m = taskService.allSpec

        render m as JSON
    }

    //TODO: limits vs running tasks, resources, etc 
    def status() {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        def map = [limits    : slaveService.getLimits(), tasks: taskService.tasks,
                   file_locks: [
                           tasks_waiting: fileLockService.locks.collect { k, v -> [id: v.task.id, files: v.files] },
                           locked_files : fileLockService.filesList.collect { k, v -> [file: k, id: v.id] }]]

        render map as JSON
    }

    //TODO: check tasks are alive
    def ping() {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        def map = [status: "alive"]

        render map as JSON
    }
}
