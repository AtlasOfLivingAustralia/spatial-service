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
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.NameValuePair
import org.apache.commons.httpclient.methods.PostMethod

class GeneratePoints extends SlaveProcess {

    void start() {

        //area to restrict
        def area = JSON.parse(task.input.area.toString())

        def distance = task.input.distance.toString().toDouble()
        def userId = task.input.userId
        def sandboxBiocacheServiceUrl = task.input.sandboxBiocacheServiceUrl
        def sandboxHubUrl = task.input.sandboxHubUrl

        double[] bbox = area[0].bbox

        def wkt = getWkt(area[0].pid)
        def simpleArea = SimpleShapeFile.parseWKT(wkt)

        // dump the species data to a file
        task.message = "getting iterating across bbox"

        def points = []
        for (double x = bbox[0]; x <= bbox[2]; x += distance) {
            for (double y = bbox[1]; y <= bbox[3]; y += distance) {
                if (simpleArea.isWithin(x, y)) {
                    points.push([x, y])
                }
            }
        }

        uploadPoints(sandboxBiocacheServiceUrl, sandboxHubUrl, userId, points)
    }

    def uploadPoints(sandboxBiocacheServiceUrl, sandboxHubUrl, userId, points) {
        //upload
        StringBuilder sb = new StringBuilder()
        points.each {
            if (sb.size() > 0) sb.append("\n")
            sb.append(it[0]).append(",").append(it[1])
        }

        def name = "Points in ${area.name} on ${distance} degree grid"
        List nameValuePairs = [
                new NameValuePair("csvData", points),
                new NameValuePair("headers", "decimalLongitude,decimalLatitude"),
                new NameValuePair("datasetName", name),
                new NameValuePair("separator", ","),
                new NameValuePair("firstLineIsData", "false"),
                new NameValuePair("customIndexedFields", ""),
                new NameValuePair("uiUrl", grailsApplication.config.spatialServiceUrl),
                new NameValuePair("alaId", userId)
        ]

        def post = new PostMethod(sandboxBiocacheServiceUrl + "/upload/")
        post.setRequestBody(nameValuePairs.toArray(new NameValuePair[0]))

        def http = new HttpClient()
        http.executeMethod(post)

        def dataResourceUid = JSON.parse(post.getResponseBodyAsString(50000)).uid

        //wait
        def statusUrl = sandboxBiocacheServiceUrl + "/upload/status/${dataResourceUid}"
        def start = System.currentTimeMillis()
        def maxTime = 60 * 60 * 1000 //2hr
        while (start + maxTime > System.currentTimeMillis()) {
            def response = Util.getUrl(statusUrl)
            if (response.status == "COMPLETE") {
                task.message = "upload successful"

                //add species layer
                def species = [q   : "",
                               ws  : sandboxHubUrl,
                               bs  : sandboxBiocacheServiceUrl,
                               name: name]

                addOutput("species", (species as JSON).toString())
                break;
            } else if (response.status == "FAILED") {
                task.message = "failed upload " + statusUrl
                break;
            }
        }

    }
}
