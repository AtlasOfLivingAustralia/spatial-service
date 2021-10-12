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

class TaskController {

    def taskService
    def slaveService
    def slaveAuthService
    def tasksService

    // create a new task
    def create() {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        def map = taskService.create(request.getJSON(), params.api_key)
        render map as JSON
    }

    // cancel the task
    def cancel(Long id) {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        def map = [:]
        def task = taskService.tasks.get(id)

        if (task == null) {
            map.put('error', 'no task: ' + id)
        } else if (taskService.cancel(id)) {
            map.put('status', 'cancelled task: ' + id)
        } else {
            map.put('error', 'failed to cancel task: ' + id)
        }

        render map as JSON
    }

    // get status for a task
    def status(Long id) {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        render taskService.status(id) as JSON
    }

    // get task details
    def show(Long id) {
        if (!slaveAuthService.isValid(params.api_key)) {
            def err = [error: 'not authorised']
            render err as JSON
            return
        }

        if (id == null) {
            render taskService.running as JSON
        } else {
            def t = taskService.tasks.get(id)
            if (t == null) {
                def m = [error: "no task for id: " + id]
                render m as JSON
            } else {
                render t as JSON
            }
        }

    }

}
