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

import au.org.ala.spatial.util.AreaReportPDF
import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.transaction.Transactional
import org.codehaus.groovy.grails.web.json.JSONObject
import org.codehaus.plexus.util.IOUtil

@Transactional(readOnly = true)
class TasksController {

    TasksService tasksService
    AuthService authService
    ServiceAuthService serviceAuthService

    def index() {
        //TODO: restrict tasks by user permissions
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        params.max = Math.min(params.max == null ? 10.0 : Double.parseDouble(params.max.toString()).doubleValue(), 10.0)
        [taskInstanceList: Task.list(params), taskInstanceCount: Task.count]
    }

    def status(Task task) {
        //TODO: restrict tasks by user permissions

        def status = tasksService.getStatus(task)

        if (params.containsKey('last')) {
            def lg = status.history.findAll { k, v ->
                if (Long.parseLong(k) > Long.parseLong(params.last)) {
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
        //TODO: restrict tasks by user permissions
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        render task as JSON
    }

    @Transactional
    def create() {
        //TODO: restrict tasks by user permissions
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        JSONObject input = null
        if (params.containsKey('input')) {
            input = (JSONObject) JSON.parse(params.input.toString())
        }
        Task task = tasksService.create(params.name, params.identifier, input)

        render task as JSON
    }

    @Transactional
    def cancel(Task task) {
        //TODO: restrict tasks by user permissions
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        tasksService.cancel(task)

        render task as JSON
    }

    /**
     * get zip of all task outputs (zip received from slave/publish)
     * @param task
     * @return
     */
    def download(Task task) {
        //TODO: restrict tasks by user permissions
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        String file = grailsApplication.config.publish.dir + "/" + task.id + ".zip"

        render file: file, contentType: 'application/zip'

    }

    @Transactional
    def reRun(Task task) {
        //TODO: restrict tasks by user permissions
        if (!authService.userInRole(grailsApplication.config.auth.admin_role) && !serviceAuthService.isValid(params['api_key'])) {
            Map err = [error: 'not authorised']
            render err as JSON
            return
        }

        if (task != null) {
            def history = [:]
            history.put(String.valueOf(System.currentTimeMillis()), 'restarting task')
            tasksService.update(task.id, [status: 0, slave: null, url: null, message: 'in queue', history: history])
        }

        render task as JSON
    }

    def output() {
        def file = grailsApplication.config.data.dir + '/public/' + params.p1
        if (params.containsKey('p2')) file += '/' + params.p2
        if (params.containsKey('p3')) file += '/' + params.p3

        def f = new File(file)
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
                //response.setContentType("text/html")
                render(text: IOUtil.toString(new FileInputStream(f)), contentType: "text/html", encoding: "UTF-8")
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

    def test() {
        try {
            new AreaReportPDF("http://local.ala.org.au:8079/geoserver",
                    "http://ala-cohen.it.csiro.au/biocache-service",
                    "*:*&wkt=" +
                            //URLEncoder.encode("POLYGON((151.144554432 -33.8900501635,151.144554432 -33.8455629915,151.196584992 -33.8455629915,151.196584992 -33.8900501635,151.144554432 -33.8900501635))", "UTF-8"),
                            URLEncoder.encode("POLYGON ((149.7216796875 -22.71539001933593, 149.7216796875 -21.861498734372553, 150.82031249999997 -21.861498734372553, 150.82031249999997 -22.71539001933593, 149.7216796875 -22.71539001933593))", "UTF-8"),
                    //"685950",
                    "686494",
                    "test",
                    null, null,
                    "http://local.ala.org.au:8080/spatial-service",
                    null, "/usr/local/bin/wkhtmltopdf");
        } catch (Exception e) {
            e.printStackTrace(); ;
        }
    }
}
