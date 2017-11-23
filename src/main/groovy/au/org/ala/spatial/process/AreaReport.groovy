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

@Slf4j
class AreaReport extends SlaveProcess {

    void start() {

        def area = JSON.parse(task.input.area.toString())

        //qid for this area
        def q = "qid:" + Util.makeQid([
                bs: grailsApplication.config.biocacheServiceUrl.toString(),
                q : area[0].q,
                fq: area[0].q.size() > 1 ? area[0].q.toList().subList(1, area[0].q.size()) : []
        ])

        //test for pid
        new AreaReportPDF(grailsApplication.config.geoserver.url.toString(),
                grailsApplication.config.biocacheServiceUrl.toString(),
                grailsApplication.config.biocacheUrl.toString(),
                q,
                area[0].pid.toString(),
                area[0].name.toString(),
                area[0].area_km.toString(),
                null, task.history,
                grailsApplication.config.spatialService.url.toString(),
                null,
                getTaskPath(),
                grailsApplication.config.journalmap.url.toString(),
                grailsApplication.config.data.dir.toString())

        File pdf = new File(getTaskPath() + "areaReport" + task.id + ".pdf")
        def outputStream = FileUtils.openOutputStream(pdf)

        InputStream stream = new URL(grailsApplication.config.grails.serverURL + '/slave/areaReport/' + task.id).openStream()
        outputStream << stream
        outputStream.flush()
        outputStream.close()

        File dir = new File(getTaskPath())

        if (dir.listFiles().length == 0) {
            task.history.put(System.currentTimeMillis(), "Failed.")
        } else if (!pdf.exists() || pdf.length() <= 0) {
            task.history.put(System.currentTimeMillis(), "Failed to make PDF. Exporting html instead.")
        }

        //all for download
        for (File f : dir.listFiles()) {
            if (f.getName().equals("index.html")) {
                addOutput('metadata', 'index.html', !pdf.exists())
            } else if (f.getName().endsWith(".pdf")) {
                addOutput('files', f.getName(), true)
            } else {
                addOutput('files', f.getName(), !pdf.exists())
            }
        }
    }

}
