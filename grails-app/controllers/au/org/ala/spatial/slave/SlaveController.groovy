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

import javax.imageio.ImageIO

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

    /**
     * pdf area report
     *
     * @return
     */
    def areaReport(Long id) {
        def pages = []

        File file
        def i = params.start?.toInteger() ?: 1
        def end = params.end?.toInteger() ?: 500
        while (i <= end &&
                ((file = new File(grailsApplication.config.data.dir + '/public/' + id + '/report.' + i + '.html')).exists() ||
                        (file = new File(grailsApplication.config.data.dir + '/private/' + id + '/report.' + i + '.html')).exists())) {
            pages.add(cleanPageText(file.text, i, file))
            i = i + 1
        }

        pages.add(cleanPageText(new URL("${grailsApplication.config.grails.serverURL}/static/area-report/furtherLinks.html").text, i, null))

        //find class='title' for table of contents
        StringBuilder sb = new StringBuilder()
        sb.append("<div id='tableOfContents'><h1>Table of Contents</h1><ol class='toc'>")
        for (String page : pages) {
            page.findAll(" class='title' id='([^']*)'") { match, bookmark ->
                sb.append("<li><a href='#").append(bookmark).append("'>").append(bookmark).append("</a></li>")
            }
        }
        sb.append("</ol></div>")
        pages = [pages[0]] + [sb.toString()] + pages.subList(1, pages.size())
        renderPdf(template: "areaReport", model: [pages: pages, id: id], filename: "areaReport" + id + ".pdf", stream: true)
    }

    private def cleanPageText(text, i, file) {
        text.replaceAll("^.*<body>", i > 1 ? "<div class='content'>" : "<div>").
                replaceAll("</body>.*\$", "</div>").
                replaceAll("<tr></tr>", "").
                replaceAll("&([^a][^m][^p])", "&amp;\$1").
                replaceAll(" src='([^/']*)'", " src='file://${file?.getParent()}/\$1'")
    }

    def exportMap(Long id) {
        def img = new File(grailsApplication.config.data.dir + '/public/' + id + '/' + id + '.jpg')
        if (!img.exists()) img = new File(grailsApplication.config.data.dir + '/private/' + id + '/' + id + '.jpg')

        def image = ImageIO.read(img)

        renderPdf(template: 'exportMap',
                model: [imageBytes: img.bytes, height: image.height, width: image.width],
                stream: true)
    }

    def pdf(Long id) {
        def pages = []

        if (!new File(grailsApplication.config.data.dir + '/public/' + id + '/report.1.html').exists()) {
            //export map

            def img = new File(grailsApplication.config.data.dir + '/public/' + id + '/' + id + '.jpg')
            if (!img.exists()) img = new File(grailsApplication.config.data.dir + '/private/' + id + '/' + id + '.jpg')

            def image = ImageIO.read(img)

            renderPdf(template: 'exportMap',
                    model: [imageBytes: img.bytes, height: image.height, width: image.width],
                    stream: true)

        } else {
            //area report
            File file
            def i = 1
            while ((file = new File(grailsApplication.config.data.dir + '/public/' + id + '/report.' + i + '.html')).exists() ||
                    (file = new File(grailsApplication.config.data.dir + '/private/' + id + '/report.' + i + '.html')).exists()) {
                pages.add(file.text.replaceAll("^.*<body>", "").//<div style='page-break-before: always;'>").
                        replaceAll("</body>.*\$", "").//</div>").
                        replaceAll("<tr></tr>", "").
                        replaceAll(" src='([^/']*)'", " src='file://${grailsApplication.config.data.dir}/task/${id}/\$1'"))
                i = i + 1
            }

            pages.add(cleanPageText(new URL("${grailsApplication.config.grails.serverURL}/static/area-report/furtherLinks.html").text, i, null))

            //find class='title' for table of contents
            StringBuilder sb = new StringBuilder()
            sb.append("<div id='tableOfContents'><h1>Table of Contents</h1><ol class='toc'>")
            for (String page : pages) {
                page.findAll(" class='title' id='([^']*)'") { match, bookmark ->
                    sb.append("<li><a href='#").append(bookmark).append("'>").append(bookmark).append("</a></li>")
                }
            }
            sb.append("</ol></div>")
            pages = [pages[0]] + [sb.toString()] + pages.subList(1, pages.size())

            [pages: pages, id: id]
        }
    }
}
