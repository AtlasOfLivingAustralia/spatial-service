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

import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.util.LayerFilter
import au.org.ala.spatial.slave.SpatialUtils
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils

@Commons
class Envelope extends SlaveProcess {

    void start() {
        String envelope = task.input.envelope
        String resolution = task.input.resolution
        String makeShapefile = task.input.shp

        LayerFilter[] filter = new LayerFilter[0]
        if (envelope) {
            filter = LayerFilter.parseLayerFilters(envelope)
            filter.each {
                slaveService.getLayerFile(resolution, it.getLayername())
            }
        }

        File dir = new File(getTaskPath())
        dir.mkdirs()

        File grid = new File(dir.getPath() + File.separator + "envelope")

        double areaSqKm
        String [] types = new String[filter.length]
        String [] fieldIds = new String[filter.length]
        for (int i=0;i<filter.length;i++) {
            types[i] = filter[i].contextual ? "c" : "e"
            fieldIds[i] = filter[i].layername
        }
        if ((areaSqKm = GridCutter.makeEnvelope(grid.getPath(), resolution, filter, Integer.MAX_VALUE, types, fieldIds)) >= 0) {

            SpatialUtils.divaToAsc(dir.getPath() + File.separator + "envelope")
            SpatialUtils.toGeotiff(dir.getPath() + File.separator + "envelope.asc", dir.getPath() + File.separator + "envelope.tif")
            SpatialUtils.save4326prj(dir.getPath() + File.separator + "envelope.prj")

            addOutput("files", "envelope.asc")
            addOutput("files", "envelope.prj")
            addOutput("layer", "envelope.tif")

            Grid g = new Grid(grid.getPath())
            String metadata = "<html><body>Extents: " + g.xmin + "," + g.ymin + "," + g.xmax + "," + g.ymax + "<br>Area (sq km): " + areaSqKm + "</body></html>"
            FileUtils.writeStringToFile(new File(dir.getPath() + File.separator + "envelope.html"), metadata)
            addOutput("metadata", "envelope.html")

            if (makeShapefile) {
                SpatialUtils.grid2shp(grid.getPath())
                addOutput("files", "envelope.shp")
                addOutput("files", "envelope.shx")
                addOutput("files", "envelope.fix")
                addOutput("files", "envelope.dbf")
            }
        } else {
            task.err.put(System.currentTimeMillis(), "Area of the envelope is 0 sq km.")
        }
    }
}
