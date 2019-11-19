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

import au.org.ala.layers.legend.Legend
import au.org.ala.layers.util.SpatialUtil
import au.org.ala.spatial.util.GeomMakeValid
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.geotools.data.FeatureReader
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.geometry.jts.JTSFactoryFinder

import java.nio.charset.StandardCharsets
import java.text.MessageFormat

@Slf4j
class FieldCreation extends SlaveProcess {

    void start() {
        String fieldId = task.input.fieldId
        def ignoreNullObjects = task.input.ignoreNullObjects
        //TODO: check if we need to skip SLD creation from input params
        // Query geoserver to check - SlaveService.peekFile to check

        // get layer info
        Map field = getField(fieldId)

        if (field == null) {
            task.err.put(System.currentTimeMillis(), 'field not found for ' + fieldId)
            return
        }

        String layerId = field.spid
        Map layer = getLayer(layerId)

        // get upload dir contents, and the existing files
        task.message = 'getting layer files'
        slaveService.getFile('/layer/' + layer.name)
//        slaveService.getFile('/layer/' + layer.name + '.gri')
//        slaveService.getFile('/layer/' + layer.name + '.prj')
//        slaveService.getFile('/layer/' + layer.name + '.shp')
//        slaveService.getFile('/layer/' + layer.name + '.shx')
//        slaveService.getFile('/layer/' + layer.name + '.dbf')
//        slaveService.getFile('/layer/' + layer.name + '.tif')
//        slaveService.getFile('/layer/' + layer.name + '.sld')

        //upload shp into layersdb in a table with name layer.id
        String dir = grailsApplication.config.data.dir
        File shpExisting = new File(dir + "/layer/" + layer.name + ".shp")

        //get layer short name
        String name = layer.name

        if ("a".equalsIgnoreCase(field.type.toString()) || "b".equalsIgnoreCase(field.type.toString())) {
            // all required file conversion tasks (convert to 4326, diva, create geotiff, upload to geoserver) are already done by LayerCreation
        } else if ("c".equalsIgnoreCase(field.type.toString())) {
            //create objects table values
            if (shpExisting.exists()) {
                task.message = 'cleanup scripts'
                String fname = 'fixNullNamedObjects.sql'
                FileUtils.writeStringToFile(new File(taskService.getBasePath(task) + fname),
                        "UPDATE objects SET name = '' WHERE name IS NULL; " +
                                "DELETE FROM objects WHERE fid = '" + sqlEscapeString(field.id) + "';")
                addOutput("sql", fname)

                String formattedDesc = String.valueOf(field.sdesc).equals('null') ? '' : field.sdesc
                if (formattedDesc.contains(',')) {
                    formattedDesc = ''
                    field.sdesc.toString().split(',').each {
                        if (formattedDesc.length() > 0) {
                            formattedDesc = formattedDesc + ' || \',\' || ' + it
                        } else {
                            formattedDesc = it
                        }
                    }
                } else if (formattedDesc.length() == 0) {
                    formattedDesc = 'null'
                }

                task.message = 'aggregating shapes'

                aggregateShapes(layer.name.toString(), field.sid.toString(), field.sname.toString(),
                        (field.sdesc != null) ? field.sdesc.toString() : null, field.id.toString(), field.namesearch.toString(),
                        ignoreNullObjects)

                //Todo ignoreSLDCreation check
                task.message = 'creating sld'
                createContextualFieldStyle(field.id.toString(), field.sid.toString(), name)

                if (layer.namesearch) {
                    addOutput("process", "NameSearchUpdate")
                }

                //layer.intersect, TabulationCreate. This process is triggered by StandardizeLayers
            }
        } else {
            // all required file conversion tasks (convert to 4326, diva, create geotiff, upload to geoserver) are already done by LayerCreation

        }

        Map m = [fieldId: fieldId]
        addOutput("process", "StandardizeLayers " + (m as JSON))
    }

    def createContextualFieldStyle(String fieldId, String fieldSid, String name) {
        String path = grailsApplication.config.data.dir + '/layer/'

        //sld
        String sld = createContextualLayerSlds(fieldSid.toString(), path + name + '.shp')
        if (sld != null) {
            File sldFile = new File(path + fieldId + ".sld")
            FileUtils.writeStringToFile(sldFile, sld)

            addOutput('sld', '/layer/' + fieldId + ".sld")
        }
    }

    /**
     * Create a coloured sld for a contextual layers.
     *
     * @param fieldId
     */
    String createContextualLayerSlds(String colName, String shapeFilePath) {
        File file = new File(shapeFilePath)

        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<sld:UserStyle xmlns=\"http://www.opengis.net/sld\" xmlns:sld=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:gml=\"http://www.opengis.net/gml\">\n" +
                "  <sld:Name>Default Styler</sld:Name>\n" +
                "  <sld:Title/>\n" +
                "  <sld:FeatureTypeStyle>\n" +
                "    <sld:Name>name</sld:Name>\n" +
                "    <sld:FeatureTypeName>Feature</sld:FeatureTypeName>";
        String footer = "</sld:FeatureTypeStyle>\n" +
                "</sld:UserStyle>";
        String rule = "<sld:Rule>\n" +
                "      <sld:Title>TITLE</sld:Title>\n" +
                "      <ogc:Filter>\n" +
                "        <ogc:PropertyIsEqualTo>\n" +
                "          <ogc:PropertyName>SNAME</ogc:PropertyName>\n" +
                "          <ogc:Literal>VALUE</ogc:Literal>\n" +
                "        </ogc:PropertyIsEqualTo>\n" +
                "      </ogc:Filter>\n" +
                "      <sld:PolygonSymbolizer>\n" +
                "        <sld:Fill>\n" +
                "          <sld:CssParameter name=\"fill\">#COLOUR</sld:CssParameter>\n" +
                "        </sld:Fill>\n" +
                "<sld:Stroke><sld:CssParameter name=\"stroke\">#000000</sld:CssParameter>" +
                "<sld:CssParameter name=\"stroke-width\">1</sld:CssParameter>" +
                "        </sld:Stroke>\n" +
                "      </sld:PolygonSymbolizer>\n" +
                "    </sld:Rule>";

        StringBuilder sld = new StringBuilder()
        sld.append(header)

        Set values = [] as Set

        String confirmedColName = null

        try {
            ShapefileDataStore sds = new ShapefileDataStore(file.toURI().toURL())
            FeatureReader reader = sds.featureReader
            while (reader.hasNext()) {
                def f = reader.next()
                if (confirmedColName == null) {
                    //find case insensitive match
                    f.getProperties().each { a ->
                        if (colName.equalsIgnoreCase(a.name.toString())) {
                            confirmedColName = a.name.toString()
                        }
                    }
                    //test
                    if (confirmedColName == null) {
                        task.err.put(System.currentTimeMillis(), 'column not found: ' + colName)
                        log.error shapeFilePath + ' does not have column ' + colName
                        confirmedColName = colName
                    }
                }
                values.add(f.getAttribute(confirmedColName))
            }
            reader.close()
            sds.dispose()
        } catch (IOException e) {
            log.error("failed to get dbf column names for: " + shapeFilePath, e)
        }

        List sortedValues = new ArrayList(values);

        //sort case insensitive
        Collections.sort(sortedValues, new Comparator() {
            @Override
            public int compare(Object o, Object t1) {
                return ((String) o).toLowerCase().compareTo(((String) t1).toLowerCase());
            }
        });

        if (values.size() > 0) {
            int count = 0
            for (int i = 0; i < sortedValues.size(); i++) {
                String value = (String) sortedValues.get(i)

                double range = (double) values.size()
                double a = i / range

                //10 colour steps
                int pos = (int) (a * 10)  //fit 0 to 10
                if (pos == 10) {
                    pos--
                }
                double lower = (pos / 10.0) * range
                double upper = ((pos + 1) / 10.0) * range

                //translate value to 0-1 position between the colours
                double v = (i - lower) / (upper - lower)
                double vt = 1 - v

                //there are groups+1 colours
                int red = (int) ((Legend.colours[pos] & 0x00FF0000) * vt + (Legend.colours[pos + 1] & 0x00FF0000) * v)
                int green = (int) ((Legend.colours[pos] & 0x0000FF00) * vt + (Legend.colours[pos + 1] & 0x0000FF00) * v)
                int blue = (int) ((Legend.colours[pos] & 0x00000FF) * vt + (Legend.colours[pos + 1] & 0x000000FF) * v)

                int ci = (red & 0x00FF0000) | (green & 0x0000FF00) | (blue & 0x000000FF) | 0xFF000000

                String colour = String.format("%6s", Integer.toHexString(ci).substring(2).toUpperCase()).replace(" ", "0")
                if (value != null) {
                    //some encoding for xml
                    sld.append(rule.
                            replace("TITLE", "" + value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).
                            replace("SNAME", "" + confirmedColName.replace("&", "&amp;")).
                            replace("COLOUR", "" + colour).
                            replace("VALUE", "" + value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")))
                }
            }

            int MAX_OBJECTS = 500
            if (count > 0) {
                log.error 'no objects for sld: ' + file + ', col: ' + colName
            } else if (count > MAX_OBJECTS) {
                log.debug "too many objects for sld: " + count + " for: " + file + ', col: ' + colName
            } else {
                sld.append(footer)
            }
        }

        if (sld.length() > 0) {
            sld.toString()
        } else {
            null
        }
    }

    String convertToUtf8(String rawDbfString){
        try {
//            Charset dbfCharset = (Charset) ShapefileDataStoreFactory.DBFCHARSET.getDefaultValue()
//            if (dbfCharset != null && dbfCharset != StandardCharsets.UTF_8 && rawDbfString != null) {
//                return new String(rawDbfString.getBytes(dbfCharset), StandardCharsets.UTF_8)
//                return new String(rawDbfString.getBytes(), StandardCharsets.UTF_8)
//            }
            return new String(rawDbfString.getBytes(), StandardCharsets.UTF_8)
        } catch (Exception e){
            log.debug("Unable to convert " + rawDbfString + " to UTF8")
            //ignore - this is a best effort service
        }
        return rawDbfString
    }

    void aggregateShapes(String layername, String sid, String sname, String sdesc, String id, String namesearch,
                         Boolean ignoreNullObjects) {
        //open shapefile
        try {
            String sql
            File sqlFile

            slaveService.getFile('/layer/' + layername + '.shp')

            File file = new File(grailsApplication.config.data.dir.toString() + '/layer/' + layername + '.shp')
            ShapefileDataStore sds = new ShapefileDataStore(file.toURI().toURL())
            FeatureReader reader = sds.featureReader

            Map retrievedFieldValues = [:]

            def defaultType = null

            String confirmedSid = null
            String confirmedSname = null
            String confirmedSdesc = null
            task.message = "reading shapefile"
            int countMissing = 0
            while (reader.hasNext()) {
                def f = reader.next()

                if (defaultType == null) {
                    defaultType = f.getDefaultGeometry()
                }

                //validateInput sid, sname, sdesc
                if (confirmedSid == null) {
                    f.getProperties().each { p ->
                        if (p.getName().toString().equalsIgnoreCase(sid)) {
                            confirmedSid = p.getName().toString()
                        }
                        if (p.getName().toString().equalsIgnoreCase(sname)) {
                            confirmedSname = p.getName().toString()
                        }
                        if (sdesc != null && p.getName().toString().equalsIgnoreCase(sdesc.toString())) {
                            confirmedSdesc = p.getName().toString()
                        }
                    }
                }
                String i = String.valueOf(f.getAttribute(confirmedSid))
                String name = String.valueOf(f.getAttribute(confirmedSname))
                String desc = null
                if (sdesc != null && !"null".equals(String.valueOf(sdesc)) && !sdesc.contains(',') &&
                        confirmedSdesc != null) {
                    desc = String.valueOf(f.getAttribute(confirmedSdesc))
                }

                if (i != null && i.trim().length() > 0) {
                    if (!retrievedFieldValues.containsKey(i)) {
                        retrievedFieldValues.put(i, [sid: i, sname: convertToUtf8(name), sdesc: convertToUtf8(desc), geom: []])
                    }
                    retrievedFieldValues.get(i).geom.add(f.getDefaultGeometry())
                } else {
                    countMissing++

                }
            }
            if (countMissing > 0) {
                log.warn 'task:' + task.id + ', no value for ' + countMissing + ' records (sid:' + confirmedSid + ') in layer ' + layername
            }
            reader.close()
            sds.dispose()
            if (retrievedFieldValues.size() == 0) {
                log.error 'task:' + task.id + ', no valid objects found for sid:' + confirmedSid
                task.err.put(System.currentTimeMillis(), 'no valid objects found for sid:' + confirmedSid)
                return
            }

            //aggregate default geometry while creating INSERT statements
            int sqlCount = 0
            int count = 1
            retrievedFieldValues.each { k, v ->
                task.message = "aggregating shapes: " + k + ", " + count + " of " + retrievedFieldValues.size()
                count++

                List<Polygon> union = new ArrayList();

                for (int i = 0; i < ((List) v.geom).size(); i++) {
                    Geometry g = v.geom.get(i).clone()
                    if (!g.isValid()) {

                        try {
                            g = GeomMakeValid.makeValid(g)
                        } catch (err) {
                            log.warn.put(System.currentTimeMillis(), 'some invalid objects ignored (' + i + ')')
                            log.error 'task: ' + task.id + ' failed validating wkt', err
                        }
                    }
                    if (g != null) {
                        if (g.getNumGeometries() > 0 && g instanceof MultiPolygon) {
                            for (int n = 0; n < g.getNumGeometries(); n++) {
                                union.add((Polygon) g.getGeometryN(n))
                            }
                        } else {
                            union.add((Polygon) g)
                        }
                    } else {
                        log.error 'task:' + task.id + ', invalid geometry at: ' + k + ', ' + layername + ', ' + sid
                    }
                }

                if (union.size() > 0) {
                    Geometry newg = null

                    if (union.get(0) instanceof Polygon) {
                        Polygon[] gs = new Polygon[union.size()]
                        union.toArray(gs)
                        newg = JTSFactoryFinder.getGeometryFactory().createMultiPolygon(gs)
                    } else if (union.size() == 1) {
                        newg = union.get(0)
                    } else {
                        log.error 'task:' + task.id + ', >1 non-Polygon geometry at: ' + k + ', ' + layername + ', ' + sid
                    }

                    double area = newg.getArea() == 0 ? 0 : SpatialUtil.calculateArea(newg.toText()) / 1000000.0
                    sql = MessageFormat.format("INSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch, area_km)" +
                            " VALUES (nextval(''objects_id_seq''::regclass), ''{0}'', ''{1}'', ''{2}'', ''{3}'', " +
                            "ST_GEOMFROMTEXT(''{4}'', 4326), {5}, {6});",
                            sqlEscapeString(v.sid),
                            sqlEscapeString(v.sname),
                            sqlEscapeString(v.sdesc),
                            sqlEscapeString(id),
                            sqlEscapeString(newg.toText()),
                            String.valueOf(namesearch).equals('null') ? false : namesearch,
                            String.valueOf(area))

                    sqlFile = new File(getTaskPath() + 'objects' + sqlCount + '.sql')
                    boolean append = sqlFile.exists() && sqlFile.length() < 5 * 1024 * 1024
                    if (!append) {
                        sqlCount++
                        sqlFile = new File(getTaskPath() + 'objects' + sqlCount + '.sql')
                        addOutput('sql', 'objects' + sqlCount + '.sql')
                    }
                    FileUtils.writeStringToFile(sqlFile, sql, append)
                }
            }

            sql = "UPDATE objects SET bbox = a.area FROM \n" +
                    "(SELECT pid, ST_ASTEXT(ST_EXTENT(the_geom)) AS area FROM objects WHERE bbox IS NULL AND " +
                    "ST_GEOMETRYTYPE(the_geom) <> 'ST_Point' GROUP BY pid) a\n" +
                    "WHERE a.pid = objects.pid;"
            sql += "UPDATE objects SET bbox = 'POLYGON((' || st_x(the_geom) || ' ' || st_y(the_geom) || ',' ||" +
                    " st_x(the_geom) || ' ' || st_y(the_geom) || ',' || " +
                    " st_x(the_geom) || ' ' || st_y(the_geom) || ',' || " +
                    " st_x(the_geom) || ' ' || st_y(the_geom) || ',' || " +
                    " st_x(the_geom) || ' ' || st_y(the_geom) || '))' WHERE bbox IS NULL AND ST_GEOMETRYTYPE(the_geom) = 'ST_Point'; "

            sqlFile = new File(getTaskPath() + 'objects_bbox.sql')
            addOutput('sql', 'objects_bbox.sql')
            FileUtils.writeStringToFile(sqlFile, sql, false)

        } catch (IOException e) {
            task.err.put(System.currentTimeMillis(), 'failed aggregation')
            log.error("failed to aggregate objects for field: " + id, e)
        }
    }
}
