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

import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.Util
import au.org.ala.spatial.intersect.SimpleRegion
import au.org.ala.spatial.intersect.SimpleShapeFile
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.httpclient.NameValuePair
import org.grails.web.json.JSONObject

//@CompileStatic
@Slf4j
class GeneratePoints extends SlaveProcess {

    void start() {

        //area to restrict
        List<AreaInput> area = JSON.parse(getInput('area').toString()).collect { it as AreaInput } as List<AreaInput>

        Double distance = getInput('distance').toString().toDouble()
        String userId = getInput('userId')
        String sandboxBiocacheServiceUrl = getInput('sandboxBiocacheServiceUrl')
        String sandboxHubUrl = getInput('sandboxHubUrl')

        double[] bbox = area[0].bbox as double[]

        String wkt = getAreaWkt(area[0])
        SimpleRegion simpleArea = SimpleShapeFile.parseWKT(wkt)

        // dump the data to a file
        taskWrapper.task.message = "Loading area ..."

        List<List<Double>> points = []
        for (double x = bbox[0]; x <= bbox[2]; x += distance) {
            for (double y = bbox[1]; y <= bbox[3]; y += distance) {
                if (simpleArea.isWithin(x, y)) {
                    points.push([x, y])
                }
            }
        }
        taskWrapper.task.history.put(System.currentTimeMillis() as String, points.size() + " points have been created.")

        uploadPoints(sandboxBiocacheServiceUrl, sandboxHubUrl, userId, points, area.name, distance)
    }

    def uploadPoints(String sandboxBiocacheServiceUrl, String sandboxHubUrl, String userId, List<List<Double>> points, areaName, distance) {
        //upload
        StringBuilder sb = new StringBuilder()
        points.each {
            if (sb.size() > 0) sb.append("\n")
            sb.append(it[0]).append(",").append(it[1])
        }

        def name = "Points in ${areaName} on ${distance} degree grid"
        NameValuePair[] nameValuePairs = [
                new NameValuePair("csvData", sb.toString()),
                new NameValuePair("headers", "decimalLongitude,decimalLatitude"),
                new NameValuePair("datasetName", name),
                new NameValuePair("separator", ","),
                new NameValuePair("firstLineIsData", "false"),
                new NameValuePair("customIndexedFields", ""),
                new NameValuePair("uiUrl", spatialConfig.spatialService.url),
                new NameValuePair("alaId", userId.toString())
        ]

        taskWrapper.task.history.put(System.currentTimeMillis() as String, "Uploading points to sandbox: ${sandboxBiocacheServiceUrl}")

        def response = Util.urlResponse("POST", "${sandboxBiocacheServiceUrl}/upload/",
                nameValuePairs)

        if (response) {
            if (response.statusCode != 200) {
                taskWrapper.task.message = "Error"
                taskWrapper.task.history.put(System.currentTimeMillis() as String, response.statusCode + " : " + response.text)
                return
            }
            def dataResourceUid = ((JSONObject) JSON.parse(response.text as String)).uid
            taskWrapper.task.history.put(System.currentTimeMillis() as String, "Sandbox data resource uid:" + dataResourceUid)
            //wait
            def statusUrl = "${sandboxBiocacheServiceUrl}/upload/status/${dataResourceUid}"
            def start = System.currentTimeMillis()
            def maxTime = 60 * 60 * 1000 //2hr
            taskWrapper.task.message = "Uploading ..."
            while (start + maxTime > System.currentTimeMillis()) {
                Thread.sleep(10000) // 10s
                taskWrapper.task.history.put(System.currentTimeMillis() as String, "checking status of " + statusUrl)
                def txt = Util.getUrl(statusUrl)
                if (txt == null) {
                    // retry
                } else if (txt.contains("COMPLETE")) {
                    taskWrapper.task.history.put(System.currentTimeMillis() as String, "Uploading completed")
                    //add species layer
                    def species = [q   : "data_resource_uid:${dataResourceUid}",
                                   ws  : sandboxHubUrl,
                                   bs  : sandboxBiocacheServiceUrl,
                                   name: name]
                    addOutput("species", (species as JSON).toString())
                    log.debug(species.inspect())
                    break
                } else if (txt.contains("FAILED")) {
                    log.error(txt)
                    taskWrapper.task.message = "failed upload " + statusUrl
                    break
                } else {
                    log.error(txt)
                    JSONObject json = JSON.parse(txt) as JSONObject
                    taskWrapper.task.message = json.status + ": " + json.description
                }
            }
        }
    }
}
