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

package au.org.ala.spatial.process

import au.org.ala.spatial.Util
import au.org.ala.spatial.util.AreaReportPDF
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser
import org.springframework.util.StreamUtils

@Slf4j
class AreaReport extends SlaveProcess {

    @Override
    void updateSpec(spec) {
        // get path to config
        def configPath = spec.private.configPath ?: '/data/spatial-service/config'

        // get AreaReportDetails.json
        JSONParser jp = new JSONParser()
        def pages = (JSONArray) jp.parse(new String(getFileAsBytes("AreaReportDetails.json", configPath), "UTF-8"))

        // get page names
        def headers = []
        def pageIdx = 0
        for (def page : pages) {
            // only general pages can be excluded
            if (pageIdx > 0 && 'general'.equals(page.type)) {
                if (page.text0) {
                    // add names of all subpages
                    headers.addAll(page.text0)
                } else if (page.items && page.items.size() > 0) {
                    def bookmarks = false
                    for (def item : page.items) {
                        if ('bookmarks'.equals(item.type)) {
                            bookmarks = true
                        }
                    }
                    if (!bookmarks && page.items[0].text) {
                        // add name of this page
                        headers.add(page.items[0].text)
                    }
                }
            }
            pageIdx++
        }

        // add page names to list of available pages
        spec.input.ignoredPages.constraints.content = headers
    }

    byte[] getFileAsBytes(String file, String configPath) throws Exception {
        File overrideFile = new File(configPath + "/" + file)
        byte[] bytes = null
        if (overrideFile.exists()) {
            bytes = FileUtils.readFileToByteArray(overrideFile)
        } else {
            bytes = StreamUtils.copyToByteArray(AreaReportPDF.class.getResourceAsStream("/areareport/" + file))
        }

        return bytes
    }

    void start() {

        def area = JSON.parse(taskWrapper.input.area.toString())

        def allSpecies = [bs: grailsApplication.config.biocacheServiceUrl.toString(), q: "*:*"]
        def speciesQuery = getSpeciesArea(allSpecies, area)

        //qid for this area
        def q = "qid:" + Util.makeQid(speciesQuery)

        //override config path
        def configPath = taskWrapper.spec.private.configPath ?: '/data/spatial-service/config'
        if (taskWrapper.spec.private.configPath && !'/data/spatial-service/config'.equals(taskWrapper.spec.private.configPath)) {
            //copy resources to task dir when using a custom config
            for (File file : new File(taskWrapper.spec.private.configPath).listFiles()) {
                if (file.isFile() && !file.getName().endsWith(".json")) {
                    FileUtils.copyFileToDirectory(file, new File(getTaskPath()))
                }
            }
        }

        def ignoredPages = JSON.parse(taskWrapper.input.ignoredPages)

        //test for pid
        new AreaReportPDF(grailsApplication.config.geoserver.url.toString(),
                grailsApplication.config.openstreetmap.url.toString(),
                grailsApplication.config.biocacheServiceUrl.toString(),
                grailsApplication.config.biocacheUrl.toString(),
                grailsApplication.config.bie.baseURL.toString(),
                grailsApplication.config.lists.url.toString(),
                q,
                area[0].pid.toString(),
                area[0].name.toString(),
                area[0].area_km.toString(),
                taskWrapper.history,
                grailsApplication.config.spatialService.url.toString(),
                getTaskPath(),
                grailsApplication.config.journalmap.url.toString(),
                grailsApplication.config.data.dir.toString(),
                configPath, ignoredPages)

        File pdf = new File(getTaskPath() + "areaReport" + taskWrapper.id + ".pdf")
        def outputStream = FileUtils.openOutputStream(pdf)

        InputStream stream = new URL(grailsApplication.config.grails.serverURL + '/slave/areaReport/' + taskWrapper.id).openStream()
        outputStream << stream
        outputStream.flush()
        outputStream.close()

        File dir = new File(getTaskPath())

        if (dir.listFiles().length == 0) {
            taskWrapper.history.put(System.currentTimeMillis() as String, "Failed.")
        } else if (!pdf.exists() || pdf.length() <= 0) {
            taskWrapper.history.put(System.currentTimeMillis() as String, "Failed to make PDF. Exporting html instead.")
        }

        //all for download
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(".pdf")) {
                addOutput('files', f.getName(), true)
            }
        }
    }

}
