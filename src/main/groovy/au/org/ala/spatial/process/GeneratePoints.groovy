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
import au.org.ala.spatial.dto.SandboxIngress
import au.org.ala.spatial.intersect.SimpleRegion
import au.org.ala.spatial.intersect.SimpleShapeFile
import grails.converters.JSON
import groovy.util.logging.Slf4j

@Slf4j
class GeneratePoints extends SlaveProcess {

    void start() {

        //area to restrict
        List<AreaInput> area = JSON.parse(getInput('area').toString()).collect { it as AreaInput } as List<AreaInput>

        Double distance = getInput('distance').toString().toDouble()
        String userId = getInput('userId')

        double[] bbox = JSON.parse(area[0].bbox) as double[]

        String wkt = getAreaWkt(area[0])
        SimpleRegion simpleArea = SimpleShapeFile.parseWKT(wkt)

        // dump the data to a file
        taskLog("Loading area ...")

        List<List<Double>> points = []
        for (double x = bbox[0]; x <= bbox[2]; x += distance) {
            for (double y = bbox[1]; y <= bbox[3]; y += distance) {
                if (simpleArea.isWithin(x, y)) {
                    points.push([x, y])
                }
            }
        }
        taskLog(points.size() + " points have been created.")

        uploadPoints(userId, points, area.name, distance)
    }

    def uploadPoints(String userId, List<List<Double>> points, areaName, distance) {
        taskLog("Uploading points to sandbox ...")

        // build csv content
        StringBuilder sb = new StringBuilder()
        sb.append("decimalLongitude,decimalLatitude")
        points.each {
            if (sb.size() > 0) sb.append("\n")
            sb.append(it[0]).append(",").append(it[1])
        }

        def name = "Points in ${areaName} on ${distance} degree grid"

        SandboxIngress si = sandboxService.importPoints(sb.toString(), name, userId)

        // check for error
        if (si == null || si.status != "finished") {
            taskLog("Error uploading points to sandbox")
            throw new Exception("Error uploading points to sandbox")
        }

        //add species layer
        def species = [q   : "dataResourceUid:${si.dataResourceUid}",
                       ws  : spatialConfig.sandboxHubUrl,
                       bs  : spatialConfig.sandboxBiocacheServiceUrl,
                       name: name]
        addOutput("species", (species as JSON).toString())

        taskWrapper.task.history.put(System.currentTimeMillis() as String, "Uploading completed")
    }
}
