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

import au.org.ala.layers.tabulation.Intersection
import au.org.ala.layers.tabulation.TabulationGenerator
import au.org.ala.layers.util.SpatialUtil
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryCollection
import com.vividsolutions.jts.geom.MultiPolygon
import com.vividsolutions.jts.geom.Polygon
import com.vividsolutions.jts.io.WKTReader
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.geotools.data.DataStore
import org.geotools.data.DataStoreFinder
import org.geotools.data.FeatureSource
import org.geotools.feature.FeatureIterator
import org.opengis.feature.simple.SimpleFeature

import java.util.zip.ZipInputStream

@Slf4j
class TabulationCreateOne extends SlaveProcess {

    void start() {
        String fieldId1 = task.input.fieldId1
        String fieldId2 = task.input.fieldId2

        String layersDir = grailsApplication.config.data.dir + '/layer/'

        String layerId1 = getField(fieldId1).spid
        String layerId2 = getField(fieldId2).spid
        Map layer1 = getLayer(layerId1)
        Map layer2 = getLayer(layerId2)

        String intersectPath = "/intersect/intersection_" + layer1.name + ".shp_" + layer2.name + ".shp.zip"

        //lock files that will be created and can be created by other threads
        List filesForLocking = []
        if (!new File(grailsApplication.config.data.dir.toString() + intersectPath).exists() && !slaveService.peekFile(intersectPath)[0].exists) {
            filesForLocking.add(grailsApplication.config.data.dir + intersectPath)
        }

        if (filesForLocking.size() > 0) {
            Object lock = fileLockService.lock(filesForLocking, task)
            if (lock != null) {
                synchronized (lock) {
                    lock.wait()
                    log.debug 'lock released on task:' + task.id
                }
            }
        }

        try {
            slaveService.getFile('/layer/' + layer1.name)
            slaveService.getFile('/layer/' + layer2.name)

            File file1 = new File(layersDir + layer1.name + ".shp")
            File file2 = new File(layersDir + layer2.name + ".shp")
            File file3 = new File(grailsApplication.config.data.dir.toString() + intersectPath)

            //create an intersection file if both shapefiles exist and the intesection file does not
            if (file1.exists() && file2.exists() && !file3.exists()) {
                new File(grailsApplication.config.data.dir.toString() + "/intersect/").mkdirs()
                Intersection.intersectShapefiles(file1.getPath(), Arrays.asList(file2.getPath()),
                        grailsApplication.config.data.dir.toString() + "/intersect/")

                //keep the intersection file for use with other fields that use the same 2 layers (layers can have >1 field)
                addOutput('file', intersectPath)
            }

            importTabulation()
        } catch (err) {
            task.history.put(System.currentTimeMillis(), 'unknown error')
            log.error "failed to produce tabulation for: " + fieldId1 + " and " + fieldId2, err
        }

        if (filesForLocking.size() > 0) {
            log.debug 'releasing files locked by task: ' + task.id
            fileLockService.release(filesForLocking)
        }
    }

    void importTabulation() {
        String fieldId1 = task.input.fieldId1
        String fieldId2 = task.input.fieldId2
        Map field1 = getField(fieldId1)
        Map field2 = getField(fieldId2)
        String layerId1 = field1.spid
        String layerId2 = field2.spid
        Map layer1 = getLayer(layerId1)
        Map layer2 = getLayer(layerId2)

        String dir = grailsApplication.config.data.dir
        String intersectFile = "/intersect/intersection_" + layer1.name + ".shp_" + layer2.name + ".shp.zip"
        slaveService.getFile(intersectFile)

        try {
            File file1 = new File(dir + '/layer/' + layer1.name + ".shp")
            File file2 = new File(dir + '/layer/' + layer2.name + ".shp")
            File file3 = new File(dir + intersectFile)

            //create an intersection file if both shapefiles exist and the intesection file does not
            if (file1.exists() && file2.exists() && file3.exists()) {

                Map<String, String> p1 = new HashMap<String, String>()
                Map<String, String> p2 = new HashMap<String, String>()

                Map map1 = new HashMap()
                map1.put("url", file1.toURI().toURL())
                DataStore dataStore1 = DataStoreFinder.getDataStore(map1)
                String typeName1 = dataStore1.getTypeNames()[0]
                FeatureSource source1 = dataStore1.getFeatureSource(typeName1)
                FeatureIterator iterator1 = source1.getFeatures().features()
                String field1sid = null
                while (iterator1.hasNext()) {
                    SimpleFeature feature1 = (SimpleFeature) iterator1.next()

                    if (field1sid == null) {
                        for (Object k : feature1.value) {
                            if (k.getName().toString().equalsIgnoreCase(field1.sid.toString())) {
                                field1sid = k.getName().toString()
                            }
                        }
                    }
                    p1.put(feature1.getID(), feature1.getAttribute(field1sid).toString())
                }
                iterator1.close()
                dataStore1.dispose()

                def map2 = new HashMap()
                map2.put("url", file2.toURI().toURL())
                def dataStore2 = DataStoreFinder.getDataStore(map2)
                def typeName2 = dataStore2.getTypeNames()[0]
                def source2 = dataStore2.getFeatureSource(typeName2)
                def iterator2 = source2.getFeatures().features()
                String field2sid = null
                while (iterator2.hasNext()) {
                    SimpleFeature feature2 = (SimpleFeature) iterator2.next()

                    if (field2sid == null) {
                        for (Object k : feature2.value) {
                            if (k.getName().toString().equalsIgnoreCase(field2.sid.toString())) {
                                field2sid = k.getName().toString()
                            }
                        }
                    }

                    p2.put(feature2.getID(), feature2.getAttribute(field2sid).toString())
                }
                iterator2.close()
                dataStore2.dispose()

                HashMap<String, Pair> map = new HashMap<String, Pair>()

                //iterate over shpIntersectionFile
                ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file3)))
                zis.getNextEntry() //only one file in the zip
                InputStreamReader isr = new InputStreamReader(zis)
                BufferedReader br = new BufferedReader(isr)
                String id1
                String id2
                String wkt
                while ((id1 = br.readLine()) != null) {
                    id2 = br.readLine()
                    wkt = br.readLine()

                    String key = p1.get(id1) + "|" + p2.get(id2)
                    Pair p = map.get(key)
                    if (p == null) {
                        p = new Pair(key)
                        map.put(key, p)
                    }
                    p.area += SpatialUtil.calculateArea(wkt) / 1000000.0
                    p.geom.add(wkt)
                }

                //map (objectId => pid)
                List objects1 = getObjects(fieldId1)
                List objects2 = getObjects(fieldId2)
                Map<String, String> pids1 = new HashMap<String, String>()
                Map<String, String> pids2 = new HashMap<String, String>()

                objects1.each { o ->
                    pids1.put(o.id.toString(), o.pid.toString())
                }
                objects2.each { o ->
                    pids2.put(o.id.toString(), o.pid.toString())
                }

                // sql statements to put pairs into tabulation
                int counter = 0
                // init sql
                String fname = 'import' + counter + '.sql'
                FileUtils.writeStringToFile(new File(getTaskPath() + fname), "DELETE FROM tabulation WHERE fid1 = '" + fieldId1 + "' AND fid2 = '" + fieldId2 + "';")
                addOutput('sql', fname)
                counter++
                for (Map.Entry<String, Pair> p : map.entrySet()) {
                    StringBuilder wktSb = new StringBuilder()
                    wktSb.append("MULTIPOLYGON(")
                    int startLength = wktSb.length()
                    for (int i = 0; i < p.getValue().geom.size(); i++) {
                        String w = p.getValue().geom.get(i)
                        if (w.startsWith("POLYGON")) {
                            if (wktSb.length() > startLength) {
                                wktSb.append(",")
                            }
                            wktSb.append(w.substring(7, w.length()))
                        } else if (w.startsWith("MULTIPOLYGON")) {
                            if (wktSb.length() > startLength) {
                                wktSb.append(",")
                            }
                            wktSb.append(w.substring(14, w.length() - 1))
                        } else {
                            WKTReader wktReader = new WKTReader()
                            Geometry g = wktReader.read(w)
                            if (g instanceof GeometryCollection) {
                                //extract only multipolygons, ignore everything else
                                def n = g.getNumGeometries()
                                for (int j = 0; j < n; j++) {
                                    if (g instanceof Polygon) {
                                        String polygon = g.toText()
                                        if (wktSb.length() > startLength) {
                                            wktSb.append(",")
                                        }
                                        wktSb.append(polygon.substring(7, polygon.length()))
                                    } else if (g instanceof MultiPolygon) {
                                        String multipolygon = g.toText()
                                        if (wktSb.length() > startLength) {
                                            wktSb.append(",")
                                        }
                                        wktSb.append(multipolygon.substring(14, multipolygon.length() - 1))
                                    }
                                }
                            } else {
                                log.debug 'unhandled geom type: ' + w.substring(0, Math.min(50, w.length())) + "..."
                            }
                        }
                    }
                    //only add if there is polygon/multipolygon contents
                    if (wktSb.length() > startLength) {
                        wktSb.append(")")

                        String sql = "INSERT INTO tabulation (fid1, fid2, pid1, pid2, area, occurrences, species, the_geom) VALUES " +
                                "('" + fieldId1 + "','" + fieldId2 + "'," + "'" +
                                pids1.get(p.getValue().v1) + "','" +
                                pids2.get(p.getValue().v2) + "'," +
                                p.getValue().area + "," +
                                p.getValue().occurrences + "," +
                                p.getValue().species.cardinality() + "," +
                                "ST_GEOMFROMTEXT('" + wktSb.toString() + "', 4326));"

                        fname = 'import' + counter + '.sql'
                        FileUtils.writeStringToFile(new File(getTaskPath() + fname), sql)
                        addOutput('sql', fname)
                        counter++
                    }
                }
            } else {
                // comparisons when at least one contextual layer is a grid file
                // TODO: copy the files necessary for gridToGrid to operate on 'grid as shapefile' layers
                int counter = 0
                def fname = 'import' + counter + '.sql'
                TabulationGenerator.gridToGrid(fieldId1, fieldId2, null, getTaskPath() + "/" + fname)
                addOutput('sql', fname)
                counter++

                // init sql
                fname = 'import' + counter + '.sql'
                FileUtils.writeStringToFile(new File(getTaskPath() + fname), "DELETE FROM tabulation WHERE fid1 = '" + fieldId1 + "' AND fid2 = '" + fieldId2 + "';")
                addOutput('sql', fname)
            }
        } catch (err) {
            task.history.put(System.currentTimeMillis(), 'unknown error')
            log.error 'failed tabulation create one for :' + fieldId1 + ', ' + fieldId2, err
        }

        addOutput("process", "TabulationCounts")
    }
}
