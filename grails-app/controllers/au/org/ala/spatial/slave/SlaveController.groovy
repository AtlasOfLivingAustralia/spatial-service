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

        def map = [limits    : slaveService.getLimits(), tasks: taskService.running,
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

        def bookmarkStart = 0
        def bookmarks = false

        File file
        def isStream = params.stream ? params.stream : true
        def i = params.start?.toInteger() ?: 1
        def end = params.end?.toInteger() ?: 5000
        def pageHeader
        def pageFooter = new File(grailsApplication.config.data.dir + '/public/' + id + '/footer.html').text
        def tableOfContentsHeading = new File(grailsApplication.config.data.dir + '/public/' + id + '/tableOfContents.html').text

        while (i <= end &&
                ((file = new File(grailsApplication.config.data.dir + '/public/' + id + '/report.' + i + '.html')).exists() ||
                        (file = new File(grailsApplication.config.data.dir + '/private/' + id + '/report.' + i + '.html')).exists())) {

            if (file.text.contains("page-header")) {
                pageHeader = cleanPageText(file.text, i, file)
                i = i + 1
            } else {
                pages.add(cleanPageText(file.text, i, file))
                i = i + 1

                if (file.text.contains("bookmarks")) {
                    bookmarkStart = i
                    bookmarks = true
                }
            }
        }

        if (pageHeader) {
            // insert into page with bookmarks
            pageHeader = "<div class='pageheader'></div>" + pageHeader
            for (i = 1; i < pages.size(); i++) {
                if (!pages[i].contains("page-header")) {
                    def insertPos = pages[i].indexOf("<div class='content")
                    while (insertPos >= 0) {
                        pages[i] = pages[i].substring(0, insertPos) +
                                pageHeader +
                                pages[i].substring(insertPos)
                        insertPos = pages[i].indexOf("<div class='content", insertPos + pageHeader.length() + 1)
                    }
                }
            }
        }

        //find class='title' for table of contents
        StringBuilder sb = new StringBuilder()
        sb.append("<ol class='toc'>")
        for (def page : pages) {
            page.findAll(" class='title' id='([^']*)'") { match, bookmark ->
                def a1 = page.indexOf("<tr class='bookmarks")
                def a2 = page.indexOf(" class='title' id='" + bookmark + "'")
                if (page.indexOf("<tr class='bookmarks") < page.indexOf(" class='title' id='" + bookmark + "'")) {
                    sb.append("<li><a href='#").append(bookmark).append("'>").append(bookmark).append("</a></li>")
                }
            }
        }
        String s;
        sb.append("</ol>")

        if (!bookmarks && pages.size() > 0) {
            // insert bookmarks page
            def part1 = [pages[0]]
            def part2 = pages.size() > 1 ? pages.subList(1, pages.size()) : []
            pages = part1 +
                    [tableOfContentsHeading.replace("<contents/>", sb.toString())] +
                    part2
        } else {
            // insert into page with bookmarks
            for (i = 0; i < pages.size(); i++) {
                def pos = pages[i].indexOf("<tr class='bookmarks")
                if (pos >= 0) {
                    def insertPos = pages[i].indexOf("</td>", pos)
                    if (insertPos >= 0) {
                        pages[i] = pages[i].substring(0, insertPos) +
                                sb.toString() +
                                pages[i].substring(insertPos)
                    }
                }
            }
        }

        def css
        if (new File(grailsApplication.config.data.dir + '/public/' + id).exists()) {
            css = new File(grailsApplication.config.data.dir + '/public/' + id + '/areaReport.css').text
        } else {
            css = new File(grailsApplication.config.data.dir + '/private/' + id + '/areaReport.css').text
        }

        renderPdf(template: "/slave/areaReport", model: [pages: pages, id: id, css: css, footer: pageFooter], filename: "areaReport" + id + ".pdf", stream: isStream)
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
            def bookmarkStart = 0
            def bookmarks = false
            File file
            def i = 1
            def pageHeader
            while ((file = new File(grailsApplication.config.data.dir + '/public/' + id + '/report.' + i + '.html')).exists() ||
                    (file = new File(grailsApplication.config.data.dir + '/private/' + id + '/report.' + i + '.html')).exists()) {
                pages.add(file.text.replaceAll("^.*<body>", "").//<div style='page-break-before: always;'>").
                        replaceAll("</body>.*\$", "").//</div>").
                        replaceAll("<tr></tr>", "").
                        replaceAll(" src='([^/']*)'", " src='/tasks/output/1/\$1'"))
                i = i + 1

                if (file.text.contains("page-header")) {
                    pageHeader = cleanPageText(file.text, i, file)
                } else {
                    if (file.text.contains("bookmarks")) {
                        bookmarkStart = i
                        bookmarks = true
                    }
                }
            }

            if (pageHeader) {
                // insert into page with bookmarks
                pageHeader = "<div class='pageheader'></div>" + pageHeader
                for (i = 1; i < pages.size(); i++) {
                    if (!pages[i].contains("page-header")) {
                        def insertPos = pages[i].indexOf("<div class='content")
                        while (insertPos >= 0) {
                            pages[i] = pages[i].substring(0, insertPos) +
                                    pageHeader +
                                    pages[i].substring(insertPos)
                            insertPos = pages[i].indexOf("<div class='content", insertPos + pageHeader.length() + 1)
                        }
                    }
                }
            }

            //find class='title' for table of contents
            StringBuilder sb = new StringBuilder()
            sb.append("<ol class='toc'>")
            for (def page : pages) {
                page.findAll(" class='title' id='([^']*)'") { match, bookmark ->
                    def a1 = page.indexOf("<tr class='bookmarks")
                    def a2 = page.indexOf(" class='title' id='" + bookmark + "'")
                    if (page.indexOf("<tr class='bookmarks") < page.indexOf(" class='title' id='" + bookmark + "'")) {
                        sb.append("<li><a href='#").append(bookmark).append("'>").append(bookmark).append("</a></li>")
                    }
                }
            }
            String s;
            sb.append("</ol>")

            if (!bookmarks) {
                // insert bookmarks page
                pages = [pages[0]] +
                        ["<div id='tableOfContents'><h1>Table of Contents</h1>" + sb.toString() + "</div>"] +
                        pages.subList(1, pages.size())
            } else {
                // insert into page with bookmarks
                for (i = 0; i < pages.size(); i++) {
                    def pos = pages[i].indexOf("<tr class='bookmarks")
                    if (pos >= 0) {
                        def insertPos = pages[i].indexOf("</td>", pos)
                        if (insertPos >= 0) {
                            pages[i] = pages[i].substring(0, insertPos) +
                                    sb.toString() +
                                    pages[i].substring(insertPos)
                        }
                    }
                }
            }

            [pages: pages, id: id, cssFile: "/tasks/output/" + id + "/areaReport.css"]
        }
    }
}
