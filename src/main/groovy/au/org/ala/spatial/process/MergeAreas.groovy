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
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader

@Slf4j
//@CompileStatic
class MergeAreas extends SlaveProcess {

    void start() {

        //area to restrict
        List<AreaInput> areas = JSON.parse(getInput('area').toString()) as List<AreaInput>
        String name = getInput('name')
        String description = getInput('description')
        String type = getInput('type')

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
            } catch (Exception ignored) {

            }
        }

        if (geometry != null) {
            String wkt = geometry.toString()
            new File(getTaskPath() + "area.wkt").write(wkt)

            def values = [file: "area.wkt", name: name ?: "Merged area", description: description ?: "Created by Merge Areas Tool (" + type + ')']
            addOutput("areas", (values as JSON).toString(), true)
        }
    }

}
