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

import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

@Slf4j
class MergeAreas extends SlaveProcess {

    void start() {

        //area to restrict
        def areas = JSON.parse(task.input.area.toString())
        def name = task.input.name
        def description = task.input.description
        def type = task.input.type

        new File(getTaskPath()).mkdirs()

        WKTReader reader = new WKTReader()
        Geometry geometry = null

        areas.each { area ->
            try {
                Geometry g = reader.read(getAreaWkt(area))
                if (geometry == null) {
                    geometry = g
                } else {
                    if ("Union".equalsIgnoreCase(type)) {
                        Geometry union = geometry.union(g)
                        geometry = union
                    } else if ("Intersection".equalsIgnoreCase(type)) {
                        if (!geometry.intersects(g)) {
                            taskLog("ERROR: Areas do not intersect.")
                            throw new Exception("ERROR: Areas do not intersect.")
                        }
                        Geometry intersection = geometry.intersection(g)
                        geometry = intersection
                    }
                }
            } catch (Exception e) {

            }
        }

        if (geometry != null) {
            String wkt = geometry.toString()
            FileUtils.writeStringToFile(new File(getTaskPath() + "area.wkt"), wkt)

            def values = [file: "area.wkt", name: name ?: "Merged area", description: description ?: "Created by Merge Areas Tool (" + type + ')']
            addOutput("areas", (values as JSON).toString(), true)
        }
    }

}
