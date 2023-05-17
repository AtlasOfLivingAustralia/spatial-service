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

import au.org.ala.plugins.openapi.Path
import au.org.ala.spatial.dto.ProcessSpecification
import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.grails.web.json.JSONObject

import javax.ws.rs.Produces

import static au.org.ala.spatial.Util.zip
import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class TasksController {

    TasksService tasksService

    AuthService authService
    SpatialConfig spatialConfig

    /**
     * get collated capabilities specs from all registered slaves
     * @return
     */
    @Operation(
            method = "GET",
            tags = "task",
            operationId = "capabilities",
            summary = "List of tasks and their inputs and outputs",
            responses = [
                    @ApiResponse(
                            description = "List of tasks",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = ProcessSpecification))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tasks/capabilities")
    @Produces("application/json")
    def capabilities() {
        render tasksService.getSpecification(authService.userInRole(spatialConfig.auth.admin_role)) as JSON
    }

    /**
     * admin only or api_key
     *
     * @return
     */
    @RequireAdmin
    def index() {
        if (!params?.max) params.max = 10
        if (!params?.sort) params.sort = "created"
        if (!params?.order) params.order = "desc"
        if (!params?.offset) params.offset = 0

        def list = []

        // limit history and format time
        tasksService.transientTasks.each { id, item ->
            def hist = [:]

            try {
                item.history.keySet().sort().reverse().each { key ->
                    if (hist.size() < 4) {
                        hist.put(new Date(key as Long), item.history.get(key))
                    }
                }
            } catch (err) {
            }

            list.add([
                    created: item.created,
                    name   : item.name,
                    history: hist,
                    status : item.status,
                    email  : item.email,
                    userId : item.userId,
                    message: item.message,
                    id     : item.id
            ])
        }

        [taskInstanceList: list, taskInstanceCount: tasksService.transientTasks.size()]
    }

    @RequireAdmin
    def all() {
        if (!params?.max) params.max = 10
        if (!params?.sort) params.sort = "created"
        if (!params?.order) params.order = "desc"
        if (!params?.offset) params.offset = 0

        List<Task> list = Task.createCriteria().list(params) {
            and {
                if (params?.q) {
                    or {
                        ilike("message", "%${params.q}%")
                        ilike("name", "%${params.q}%")
                        ilike("tag", "%${params.q}%")
                    }
                }
                if (params?.status) {
                    eq("status", params.status as Integer)
                }
            }
            readOnly(true)
        } as List<Task>
        def count = Task.createCriteria().count() {
            and {
                if (params?.q) {
                    or {
                        ilike("message", "%${params.q}%")
                        ilike("name", "%${params.q}%")
                        ilike("tag", "%${params.q}%")
                    }
                }
                if (params?.status) {
                    eq("status", params.status.toInteger())
                }
            }
        }

        // limit history and format time
        list.each { item ->
            def hist = [:]

            item.history.keySet().sort().reverse().each { key ->
                if (hist.size() < 4) {
                    hist.put(new Date(key as Long), item.history.get(key))
                }
            }
            item.history = hist
        }

        [taskInstanceList: list, taskInstanceCount: count]
    }

    /**
     * Query status of task
     * login not required
     *
     * @param task
     * @return
     */
    @Operation(
            method = "GET",
            tags = "task",
            operationId = "status",
            summary = "Task status",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "Task ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Task status",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Map<String, Object>)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tasks/status/{id}")
    @Produces("application/json")
    def status(Long id) {
        Map<String, Object> status = tasksService.getStatus(id)

        if (params.containsKey('last')) {
            def hist = [:]
            status.history.findAll { k, v ->
                try {
                    if (Long.parseLong(k.toString()) > Long.parseLong(params?.last?.toString())) {
                        hist.put(k, v)
                    }
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }
            status.history = hist
        }

        if (status.history) {
            status.history = status.history.sort { a, b ->
                a.key ? a.key.compareTo(b.key) : "" <=> b.key
            }
        }

        render status as JSON
    }

    /**
     * must be logged in
     *
     * @param task
     * @return
     */
    @RequirePermission
    def show(Long id) {
        def task = tasksService.getStatus(id)
        if (task) {
            task.history = task.history.sort { a, b ->
                a.key ? a.key.compareTo(b.key) : "" <=> b.key
            }
            render task as JSON
        } else {
            render status: 404
        }
    }

    /**
     * @return a map of inputs to errors, or the created task
     */
    @Operation(
            method = "POST",
            tags = "task",
            operationId = "create",
            summary = "Create a task",
            parameters = [
                    @Parameter(
                            name = "name",
                            in = QUERY,
                            description = "Task name",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "inputs",
                            in = QUERY,
                            description = "task inputs as JSON",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Task status",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Map<String, Object>)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tasks/create")
    @Produces("application/json")
    @Transactional(readOnly = false)
    @RequirePermission
    create() {
        Map input = null
        if (params.containsKey('input')) {
            input = ((JSONObject) JSON.parse(params.input.toString())).findAll { k, v -> v != null }
        }

        //Validate input. It may update input
        def errors
        def userId
        if (spatialConfig.security.oidc.enabled || spatialConfig.security.cas.enabled) {
            errors = tasksService.validateInput(params.name, input, authService.userInRole(spatialConfig.auth.admin_role))
            userId = authService.getUserId() ?: params.userId
        } else {
            errors = tasksService.validateInput(params.name, input, true)
            userId = params.userId
        }

        if (errors) {
            response.status = 400
            render errors as JSON
        } else {
            Task task = tasksService.create(params.name, params.identifier, input, params.sessionId, userId, params.email).task

            render task as JSON
        }
    }

    /**
     * login requried
     *
     * @param task
     * @return
     */
    @Operation(
            method = "GET",
            tags = "task",
            operationId = "cancel",
            summary = "Cancel a task",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "Task ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Task status",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Map<String, Object>)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tasks/cancel/{id}")
    @Produces("application/json")
    @Transactional(readOnly = false)
    @RequirePermission
    cancel(Long id) {
        def task = tasksService.cancel(id)

        if (request.contentType?.equalsIgnoreCase("application/json")) {
            render task as JSON
        } else {
            redirect(action: "index", params: params)
        }
    }

    /**
     * get zip of all task outputs (zip received from slave/publish)
     *
     * login required
     *
     * @param task
     * @return
     */
    @RequirePermission
    def download(Task task) {
        String file = spatialConfig.publish.dir + task.id + ".zip"

        render file: file, contentType: 'application/zip'
    }

    /**
     *
     * admin only or api_key
     *
     * @param task
     * @return
     */
    @Transactional(readOnly = false)
    @RequireAdmin
    reRun(Long id) {
        def t = Task.get(id)
        t.history.each { it -> it }
        t.output.each { it -> it }
        t.input.each { it -> it }
        def task = tasksService.reRun(t)

        if (task) {
            render task as JSON
        } else {
            redirect(action: "index", params: params)
        }
    }

    /**
     * Download output
     * login not required
     *
     * @return
     */
    def output() {
        def path = "${spatialConfig.data.dir}/public"
        def p1 = params.p1
        def p2 = params.p2
        def p3 = params.p3
        if (params.filename) {
            if (p3) {
                p3 = params.filename
            } else if (p2) {
                p2 = params.filename
            } else if (p1) {
                p1 = params.filename
            }
        }

        def file = "${path}/${p1}"
        if (p2) file += "/${p2}"
        if (p3) file += "/${p3}"

        if (params.format) file += ".${params.format}"

        def f = new File(file)

        if (!f.canonicalPath.startsWith(new File(path).canonicalPath)) {
            response.status = 404
            render ""
        }

        if (!f.exists() && f.getName() == "download.zip") {
            //build download.zip when it is absent and there are listed files in the spec.json
            try {
                def spec = JSON.parse(new File(f.getParent() + "/spec.json").text)
                if (spec?.download) {
                    def fileList = []
                    def fileNamesList = []
                    for (String s : spec?.download) {
                        File af = new File(f.getParent() + "/" + s)
                        if (af.exists()) {
                            if (af.isDirectory()) {
                                fileList.addAll(af.listFiles().collect { it -> it.getPath() })
                                fileNamesList.addAll(af.listFiles().collect { it -> it.getPath().substring(f.getParent().length() + 1) })
                            } else {
                                fileList.add(af.getPath())
                                fileNamesList.addAll(af.getPath().substring(f.getParent().length() + 1))
                            }
                        }
                    }
                    zip(f.getParent() + "/download.zip", fileList.toArray(new String[0]) as String[],
                            fileNamesList.toArray(new String[0]) as String[])
                }
            } catch (IOException e) {
            }
        }
        if (f.exists()) {
            boolean ok = false
            if (file.endsWith('.zip')) {
                response.setContentType("application/zip")
                ok = true
            } else if (file.endsWith('.pdf')) {
                response.setContentType("application/pdf")
                ok = true
            } else if (file.endsWith('.txt')) {
                response.setContentType("text/plain")
                ok = true
            } else if (file.endsWith('.html')) {
                def htmlContent = f.text
                render(text: htmlContent, contentType: "text/html", encoding: "UTF-8")
                return
            } else if (file.endsWith('.jpg') || file.endsWith('jpeg')) {
                response.setContentType("image/jpeg")
                ok = true
            } else if (file.endsWith('.png')) {
                response.setContentType("image/png")
                ok = true
            } else if (file.endsWith('.csv')) {
                response.setContentType("application/csv")
                ok = true
            } else {
                ok = true
            }

            if (ok) {
                response.setContentLength((int) f.length())
                response.addHeader("Content-Disposition", "attachment")

                OutputStream os = response.getOutputStream()
                InputStream is = new BufferedInputStream(new FileInputStream(f))
                try {
                    os << is
                    os.flush()
                } finally {
                    is.close()
                    os.close()
                }
            }
        } else {
            render('File does not exist: ' + file, contentType: "text/html", encoding: "UTF-8")
        }
    }
}
