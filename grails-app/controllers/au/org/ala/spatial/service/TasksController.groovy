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
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.grails.web.json.JSONObject

@Transactional(readOnly = true)
class TasksController {

    TasksService tasksService
    def serviceAuthService

    /**
     * admin only or api_key
     *
     * @return
     */
    def index() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

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
            readOnly(true)
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

        // limit history and format time
        list.each { item ->
            def hist = [:]

            item.history.keySet().sort().reverse().each { key ->
                if (hist.size() < 4) {
                    hist.put(new Date(Long.parseLong(key)), item.history.get(key))
                }
            }
            item.history = hist
        }

        [taskInstanceList: list, taskInstanceCount: count]
    }

    /**
     * login not required
     *
     * @param task
     * @return
     */
    def status(Task task) {
        def status = tasksService.getStatus(task)

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
                a.key ? a.key.compareTo(b.key) : "".compareTo(b.key)
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
    def show(Task task) {
        if (!doLogin()) return

        if (task) {
            task.history = task.history.sort { a, b ->
                a.key ? a.key.compareTo(b.key) : "".compareTo(b.key)
            }

            render task as JSON
        } else {
            render status: 404
        }
    }

    /**
     * @return a map of inputs to errors, or the created task
     */
    @Transactional(readOnly = false)
    create() {
        if (!doLogin())
            return

        JSONObject input = null
        if (params.containsKey('input')) {
            input = ((JSONObject) JSON.parse(params.input.toString())).findAll { k, v -> v != null }
        }

        //Validate input. It may update input
        def errors = tasksService.validateInput(params.name, input, serviceAuthService.isAdmin(params))

        if (errors) {
            render errors as JSON
        } else {
            Task task = tasksService.create(params.name, params.identifier, input, params.sessionId, params.userId, params.email)
            render task as JSON
        }
    }

    /**
     * login requried
     *
     * @param task
     * @return
     */
    @Transactional(readOnly = false)
    cancel(Task task) {
        if (!doLogin()) return

        if (task?.status < 2) tasksService.cancel(task)

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
    def download(Task task) {
        if (!doLogin()) return

        String file = grailsApplication.config.publish.dir + task.id + ".zip"

        render file: file, contentType: 'application/zip'
    }

    /**
     * Internal use
     *
     * login required
     *
     * data.dir or publish.dir?
     * get zip of all task outputs (zip received from slave/publish)
     * @param task
     * @return
     */
    def downloadReport(String taskId) {
        if (!doLogin()) return

        def file = new File(grailsApplication.config.publish.dir + "/" + taskId + "/download.zip")

        response.setHeader("Content-Type", "application/octet-stream")
        response.setHeader("Content-disposition", "attachment;filename=${file.name}")
        response.setContentLengthLong(file.size())
        response.outputStream << file.bytes

    }

    /**
     *
     * admin only or api_key
     *
     * @param task
     * @return
     */
    @Transactional(readOnly = false)
    reRun(Task task) {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

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

    /**
     * login not required
     *
     * @return
     */
    def output() {
        def path = "${grailsApplication.config.data.dir}/public"
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

        if (params.format) file+= ".${params.format}"

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
            }else{
                ok = true
            }

            if (ok) {
                response.setContentLength((int) f.length());
                response.addHeader("Content-Disposition", "attachment");

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
            return
        }
    }

    /**
     * admin required
     *
     * @return
     */
    @Transactional(readOnly = false)
    def cancelAll() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

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

    /**
     * Return true when logged in, CAS is disabled or api_key is valid.
     *
     * Otherwise redirect to CAS for login.
     *
     * @param params
     * @return
     */
    private boolean doLogin() {
        if (!serviceAuthService.isLoggedIn(params)) {
            redirect(url: grailsApplication.config.security.cas.loginUrl + "?service=" +
                    grailsApplication.config.security.cas.appServerName + request.forwardURI + (request.queryString ? '?' + request.queryString : ''))
            return false
        }

        return true
    }
}
