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
                q,
                area[0].pid.toString(),
                area[0].name.toString(),
                area[0].area_km.toString(),
                null, task.history,
                grailsApplication.config.layersService.url.toString(),
                null, grailsApplication.config.wkhtmltopdf.path.toString(),
                getTaskPath());

        File dir = new File(getTaskPath())

        File pdf = new File(getTaskPath() + 'output.pdf')
        if (dir.listFiles().length == 0) {
            task.history.put(System.currentTimeMillis(), "Failed.")
        } else if (!pdf.exists()) {
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
