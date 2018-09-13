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

import au.org.ala.layers.intersect.SimpleShapeFile
import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.httpclient.NameValuePair

@Slf4j
class GeneratePoints extends SlaveProcess {

    void start() {

        //area to restrict
        def area = JSON.parse(task.input.area.toString())

        def distance = task.input.distance.toString().toDouble()
        def userId = task.input.userId
        def sandboxBiocacheServiceUrl = task.input.sandboxBiocacheServiceUrl
        def sandboxHubUrl = task.input.sandboxHubUrl

        double[] bbox = area[0].bbox

        def wkt = getAreaWkt(area[0])
        def simpleArea = SimpleShapeFile.parseWKT(wkt)

        // dump the data to a file
        task.message = "Loading area ..."

        def points = []
        for (double x = bbox[0]; x <= bbox[2]; x += distance) {
            for (double y = bbox[1]; y <= bbox[3]; y += distance) {
                if (simpleArea.isWithin(x, y)) {
                    points.push([x, y])
                }
            }
        }

        uploadPoints(sandboxBiocacheServiceUrl, sandboxHubUrl, userId, points, area.name, distance)
    }

    def uploadPoints(sandboxBiocacheServiceUrl, sandboxHubUrl, userId, points, areaName, distance) {
        //upload
        StringBuilder sb = new StringBuilder()
        points.each {
            if (sb.size() > 0) sb.append("\n")
            sb.append(it[0]).append(",").append(it[1])
        }

        def name = "Points in ${areaName} on ${distance} degree grid"
        List nameValuePairs = [
                new NameValuePair("csvData", sb.toString()),
                new NameValuePair("headers", "decimalLongitude,decimalLatitude"),
                new NameValuePair("datasetName", name),
                new NameValuePair("separator", ","),
                new NameValuePair("firstLineIsData", "false"),
                new NameValuePair("customIndexedFields", ""),
                new NameValuePair("uiUrl", grailsApplication.config.spatialServiceUrl.toString()),
                new NameValuePair("alaId", userId.toString())
        ]

        def response = Util.urlResponse("POST", "${sandboxBiocacheServiceUrl}/upload/",
                nameValuePairs.toArray(new NameValuePair[0]))

        if (response) {
            def dataResourceUid = JSON.parse(response.text).uid

            //wait
            def statusUrl = "${sandboxBiocacheServiceUrl}/upload/status/${dataResourceUid}"
            def start = System.currentTimeMillis()
            def maxTime = 60 * 60 * 1000 //2hr
            while (start + maxTime > System.currentTimeMillis()) {
                def txt = Util.getUrl(statusUrl)
                if (txt.contains("COMPLETE")) {
                    task.message = "upload successful"

                    //add species layer
                    def species = [q   : "data_resource_uid:${dataResourceUid}",
                                   ws  : sandboxHubUrl,
                                   bs  : sandboxBiocacheServiceUrl,
                                   name: name]

                    addOutput("species", (species as JSON).toString())
                    break
                } else if (txt.contains("FAILED")) {
                    task.message = "failed upload " + statusUrl
                    break
                } else {
                    def json = txt as JSON
                    task.message = json.status + ": " + json.description
                }
            }
        }
    }
}
