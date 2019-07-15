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
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.geom.LineSegment
import com.vividsolutions.jts.io.WKTReader
import com.vividsolutions.jts.triangulate.DelaunayTriangulationBuilder
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.geotools.kml.KML
import org.geotools.kml.KMLConfiguration
import org.geotools.xml.Encoder

import java.awt.geom.Point2D

@Slf4j
class AooEoo extends SlaveProcess {

    void start() {

        //grid size
        def gridSize = task.input.resolution.toDouble()

        //area to restrict
        def area = JSON.parse(task.input.area.toString())

        //number of target species
        def species = JSON.parse(task.input.species.toString())

        // concave hull coverage parameter
        def alpha = task.input.coverage

        def speciesArea = getSpeciesArea(species, area[0])

        new File(getTaskPath().toString()).mkdirs()

        // eoo
        def points = facet("lat_long", speciesArea)
        StringBuilder eWkt = new StringBuilder()
        processPoints(points, eWkt)

        // aoo
        Set<Point2D> aooPoints = aooProcess(points, gridSize)
        double aoo = gridSize * gridSize * aooPoints.size() * 10000
        String aooWkt = aooWkt(aooPoints, gridSize)

        def occurrenceCount = occurrenceCount(speciesArea)

        double eoo
        WKTReader reader = new WKTReader()
        String metadata
        try {
            Geometry g = reader.read(eWkt.toString())

            Geometry convexHull = g.convexHull()
            String wkt = convexHull.toText().replace(" (", "(").replace(", ", ",")

            eoo = SpatialUtil.calculateArea(wkt)

            //aoo area
            Geometry a = reader.read(aooWkt)
            Geometry aUnion = a.union()
            String aWkt = aUnion.toText().replace(" (", "(").replace(", ", ",")

            //concave hull
            Geometry concaveHull = buildConcaveHull(g, Double.parseDouble(alpha))
            String concaveWkt = concaveHull.toText().replace(" (", "(").replace(", ", ",")
            double alphaHull = SpatialUtil.calculateArea(concaveWkt)

            if (eoo > 0) {

                def filename = "Extent of occurrence.wkt"
                FileUtils.writeStringToFile(new File(getTaskPath() + filename), wkt)
                def values = [file: "Extent of occurrence.wkt", name: "Extent of occurrence (area): " + species.name,
                              description: "Created by AOO and EOO Tool"]
                addOutput("areas", (values as JSON).toString(), true)
                addOutput("files", filename, true)

                filename = "Area of occupancy.wkt"
                FileUtils.writeStringToFile(new File(getTaskPath() + filename), aWkt)
                values = [file: "Area of occupancy.wkt", name: "Area of occupancy (area): " + species.name,
                          description: "Created by AOO and EOO Tool"]
                addOutput("areas", (values as JSON).toString(), true)
                addOutput("files", filename, true)

                filename = "Alpha Hull.wkt"
                FileUtils.writeStringToFile(new File(getTaskPath() + filename), concaveWkt)
                values = [file       : "Alpha Hull.wkt", name: "Alpha Hull: " + species.name,
                          description: "Created by AOO and EOO Tool"]
                addOutput("areas", (values as JSON).toString(), true)
                addOutput("files", filename, true)

                metadata = "<html><body>" +
                        "<div class='aooeoo'>" +
                        "<div>The Sensitive Data Service may have changed the location of taxa that have a sensitive status." +
                        " It is wise to first map the taxa and examine each record, then filter these records to create the " +
                        "desired subset, then run the tool on the new filtered taxa layer.</div><br />" +
                        "<table >" +
                        "<tr><td>Number of records used for the calculations</td><td>" + occurrenceCount + "</td></tr>" +
                        "<tr><td>Species</td><td>" + species.name + "</td></tr>" +
                        "<tr><td>Area of Occupancy (AOO: ${gridSize} degree grid)</td><td>" + String.format("%.0f", aoo) + " sq km</td></tr>" +
                        "<tr><td>Extent of Occurrence (EOO: Minimum convex hull)</td><td>" + (String.format("%.2f", eoo / 1000.0 / 1000.0)) + " sq km</td></tr>" +
                        "<tr><td>Alpha Hull (Alpha: ${alpha})</td><td>" + String.format("%.0f", alphaHull / 1000.0 / 1000.0) + " sq km</td></tr>" +
                        "</table></body></html>" +
                        "</div>"

                FileUtils.writeStringToFile(new File(getTaskPath() + "Calculated AOO and EOO.html"), metadata)

                addOutput("metadata", "Calculated AOO and EOO.html", true)

                //export areas in kml format
                filename = "Extent of occurrence.kml"
                writeWktAsKmlToFile(new File(getTaskPath() + filename), wkt)
                addOutput("files", filename, true)

                filename = "Area of occupancy.kml"
                writeWktAsKmlToFile(new File(getTaskPath() + filename), aWkt)
                addOutput("files", filename, true)
            } else {
                log.error 'Extent of occurrences is 0.'
            }

        } catch (err) {
            log.error 'failed to calculate aoo eoo ' + task.id, err
        }
    }

    void writeWktAsKmlToFile(File file, String wkt) {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://earth.google.com/kml/2.2\"><Document>  <name></name>  <description></description>  <Style id=\"style1\">    <LineStyle>      <color>40000000</color>      <width>3</width>    </LineStyle>    <PolyStyle>      <color>73FF0000</color>      <fill>1</fill>      <outline>1</outline>    </PolyStyle>  </Style>  <Placemark>    <name></name>    <description></description>    <styleUrl>#style1</styleUrl>"

        Encoder encoder = new Encoder(new KMLConfiguration())
        encoder.setIndenting(true)
        WKTReader reader = new WKTReader()
        String kml = encoder.encodeAsString(reader.read(wkt), KML.Geometry)

        String footer = "</Placemark></Document></kml>"

        FileUtils.writeStringToFile(file, header + kml + footer)
    }

    int processPoints(points, StringBuilder sb) {
        int pointCount = 0
        sb.append("GEOMETRYCOLLECTION(")
        points.each { point ->
            try {
                //point=latitude,longitude
                String[] ll = point.replace("\"", "").split(",")
                String s = "POINT(" + Double.parseDouble(ll[1]) + " " + Double.parseDouble(ll[0]) + ")"
                if (pointCount > 0) {
                    sb.append(",")
                }
                sb.append(s)
                pointCount++
            } catch (err) {
            }
        }
        sb.append(")")

        return pointCount
    }

    private Set<Point2D> aooProcess(points, double gridSize) {
        Set<Point2D> set = new HashSet<Point2D>()
        points.each { point ->
            try {
                //key=latitude,longitude
                String [] ll = point.replace("\"", "").split(",")
                Point2D pt = new Point2D.Float(round(Double.parseDouble(ll[1]), gridSize),
                        round(Double.parseDouble(ll[0]), gridSize))
                set.add(pt)
            } catch (Exception e) {
            }
        }

        set
    }

    float round(double d, double by) {
        long l = (long) (d / by)
        return (float) (l * by + (l < 0 ? -by : 0))
    }

    String aooWkt(pointSet, gridsize) {
        int pointCount = 0
        StringBuilder sb = new StringBuilder()
        sb.append("MULTIPOLYGON(")
        pointSet.each { point ->
            //key=latitude,longitude
            float x = point.x
            float y = point.y

            String s = "((" + x + " " + y + "," +
                    x + " " + (y + gridsize) + "," +
                    (x + gridsize) + " " + (y + gridsize) + "," +
                    (x + gridsize) + " " + y + "," +
                    x + " " + y + "))"
            if (pointCount > 0) {
                sb.append(",")
            }
            sb.append(s)
            pointCount++
        }
        sb.append(")")

        return sb.toString()
    }

    /**
     * Alpha hull
     * 1. Connect all points with Delaunay Triangulation
     * 2. Calculate mean edge length
     * 3. Remove all triangles that have an edge with length > mean length * alpha
     * 4. Return geometry
     *
     * @param geometry
     * @param alpha
     * @return
     */
    static Geometry buildConcaveHull(Geometry geometry, Double alpha) {
        def triangulation = new DelaunayTriangulationBuilder()
        triangulation.setSites(geometry)
        def triangles = triangulation.getTriangles(new GeometryFactory())
        def edges = triangulation.getEdges(new GeometryFactory())

        //get mean edge length
        def sum = 0;
        for (int i = 0; i < edges.numGeometries; i++) {
            sum += edges.getGeometryN(i).length
        }
        def meanByAlpha = sum / (edges.numGeometries) * alpha

        //remove triangles with at least one edge length > meanByAlpha
        def union = null
        for (int i = 0; i < triangles.numGeometries; i++) {
            def triangle = triangles.getGeometryN(i)
            def valid = true
            for (int j = 1; j < 3 && valid; j++) {
                if (new LineSegment(triangle.coordinates[j], triangle.coordinates[j - 1]).length > meanByAlpha) {
                    valid = false
                }
            }
            if (valid) {
                if (union == null) union = triangle
                else union = union.union(triangle)
            }
        }

        //return geometry
        return union
    }

}
