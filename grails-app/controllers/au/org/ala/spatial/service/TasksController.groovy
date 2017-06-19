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

import au.org.ala.spatial.Util
import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.transaction.Transactional
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.grails.web.json.JSONObject

@Transactional(readOnly = true)
class TasksController {

    TasksService tasksService
    AuthService authService
    ServiceAuthService serviceAuthService

    def index() {
        login()

        if (!params?.max) params.max = 10
        if (!params?.sort) params.sort = "created"
        if (!params?.order) params.order = "desc"
        if (!params?.offset) params.offset = 0

        def list = Task.createCriteria().list(params) {
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
        [taskInstanceList: list, taskInstanceCount: count]
    }

    def status(Task task) {
        def status = tasksService.getStatus(task)

        if (params.containsKey('last')) {
            def lg = status.history.findAll { k, v ->
                if (Long.parseLong(k.toString()) > Long.parseLong(params?.last?.toString())) {
                    [k: v]
                } else {
                    null
                }
            }
            status.history = lg
        }

        render status as JSON
    }

    def show(Task task) {
        login()

        render task as JSON
    }

    private login() {
        if (!serviceAuthService.isValid(params['api_key'])) {
            if (!authService.getUserId() && !request.contentType?.equalsIgnoreCase("application/json")) {
                redirect(url: grailsApplication.config.security.cas.loginUrl + "?service=" +
                        grailsApplication.config.serverName + createLink(controller: 'tasks', action: 'index'))
            } else if (!authService.userInRole(grailsApplication.config.auth.admin_role)) {
                Map err = [error: 'not authorised']
                render err as JSON
            }
        }
    }

    /**
     * @return a map of inputs to errors, or the created task
     */
    @Transactional
    create() {
        login()

        JSONObject input = null
        if (params.containsKey('input')) {
            input = ((JSONObject) JSON.parse(params.input.toString())).findAll { k, v -> v != null }
        }

        //Validate input. It may update input
        def errors = tasksService.validateInput(params.name, input)

        if (errors) {
            render errors as JSON
        } else {
            Task task = tasksService.create(params.name, params.identifier, input, params.sessionId, params.userId, params.email)
            render task as JSON
        }
    }

    @Transactional
    cancel(Task task) {
        login()

        if (task?.status < 2) tasksService.cancel(task)

        if (request.contentType?.equalsIgnoreCase("application/json")) {
            render task as JSON
        } else {
            redirect(action: "index", params: params)
        }
    }

    /**
     * get zip of all task outputs (zip received from slave/publish)
     * @param task
     * @return
     */
    def download(Task task) {
        login()

        String file = grailsApplication.config.publish.dir + "/" + task.id + ".zip"

        render file: file, contentType: 'application/zip'
    }

    @Transactional
    reRun(Task task) {
        login()

        if (task != null) {
            def history = [:]
            history.put(String.valueOf(System.currentTimeMillis()), 'restarting task')
            tasksService.update(task.id, [status: 0, slave: null, url: null, message: 'in queue', history: history])
        }

        if (request.contentType?.equalsIgnoreCase("application/json")) {
            render task as JSON
        } else {
            redirect(action: "index", params: params)
        }
    }

    def output() {
        def file = "${grailsApplication.config.data.dir}/public/${params.p1}"
        if (params.containsKey('p2')) file += "/${params.p2}"
        if (params.containsKey('p3')) file += "/${params.p3}"

        def f = new File(file)
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
                    Util.zip(f.getParent() + "/download.zip", (String[]) fileList.toArray(new String[0]),
                            (String[]) fileNamesList.toArray(new String[0]))
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
                render(text: IOUtils.toString(new FileInputStream(f)), contentType: "text/html", encoding: "UTF-8")
                return
            } else if (file.endsWith('.jpg')) {
                response.setContentType("image/jpeg")
                ok = true
            } else if (file.endsWith('.png')) {
                response.setContentType("image/png")
                ok = true
            } else if (file.endsWith('.csv')) {
                response.setContentType("text/plain")
                ok = true
            }

            if (ok) {
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
        }
    }

    def cancelAll() {
        login()

        def list = Task.createCriteria().list() {
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

        def cancelled = []
        list.each {
            //try to cancel tasks that are in queue or running
            if (it.status == 0 || it.status == 1) {
                cancelled.push(it)
                tasksService.cancel(it)
            }
        }

        if (request.contentType?.equalsIgnoreCase("application/json")) {
            render cancelled as JSON
        } else {
            redirect(action: "index", params: params)
        }
    }
}
