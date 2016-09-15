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

import au.org.ala.layers.util.SpatialUtil
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import org.apache.commons.io.FileUtils
import org.json.simple.parser.JSONParser

class AooEoo extends SlaveProcess {

    void start() {

        //area to restrict (only interested in area.q part)
        JSONParser jp = new JSONParser()
        String area = jp.parse(task.input.area.toString())

        //number of target species
        def species = JSON.parse(task.input.species.toString())

        new File(getTaskPath()).mkdirs()

        def pointCount = facetCount("point-0.02", species)
        // aoo = 2km * 2km * number of 2km by 2km grid cells with an occurrence
        double aoo = 2.0 * 2.0 * pointCount

        def occurrenceCount = occurrenceCount(species)

        // eoo, use actual points
        def points = facet("lat_long", species)
        StringBuilder sb = new StringBuilder();
        processPoints(points, sb);

        double eoo = 0;
        WKTReader reader = new WKTReader();
        String metadata = null;
        try {
            Geometry g = reader.read(sb.toString());
            Geometry convexHull = g.convexHull();
            String wkt = convexHull.toText().replace(" (", "(").replace(", ", ",");

            eoo = SpatialUtil.calculateArea(wkt);

            if (eoo > 0) {

                FileUtils.writeStringToFile(new File(getTaskPath() + "Area of Occupancy.wkt"), wkt)
                addOutput("areas", "Area of Occupancy.wkt", true)

                metadata = "<html><body>" +
                        "<div class='aooeoo'>" +
                        "<div>The Sensitive Data Service may have changed the location of taxa that have a sensitive status." +
                        " It is wise to first map the taxa and examine each record, then filter these records to create the " +
                        "desired subset, then run the tool on the new filtered taxa layer.</div><br />" +
                        "<table >" +
                        "<tr><td>Number of records used for the calculations</td><td>" + occurrenceCount + "</td></tr>" +
                        "<tr><td>Species</td><td>" + species.name + "</td></tr>" +
                        "<tr><td>Area of Occupancy (AOO: 0.02 degree grid)</td><td>" + String.format("%.0f", aoo) + " sq km</td></tr>" +
                        "<tr><td>Extent of Occurrence (EOO: Minimum convex hull)</td><td>" + (String.format("%.2f", eoo / 1000.0 / 1000.0)) + " sq km</td></tr></table></body></html>" +
                        "</div>";

                FileUtils.writeStringToFile(new File(getTaskPath() + "Calculated AOO and EOO.html"), metadata)

                addOutput("metadata", "Calculated AOO and EOO.html", true)
            } else {
                //trigger eoo unavailable message
                //pointCount = 2;
            }

        } catch (err) {
            log.error 'failed to calculate aoo eoo ' + task.id, err
        }
    }

    private int processPoints(points, StringBuilder sb) {
        int pointCount = 0;
        sb.append("GEOMETRYCOLLECTION(");
        points.each { point ->
            try {
                //point=latitude,longitude
                String[] ll = point.replace("\"", "").split(",");
                String s = "POINT(" + Double.parseDouble(ll[1]) + " " + Double.parseDouble(ll[0]) + ")";
                if (pointCount > 0) {
                    sb.append(",");
                }
                sb.append(s);
                pointCount++;
            } catch (err) {
            }
        }
        sb.append(")");

        return pointCount;
    }

}
