/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial


import au.org.ala.spatial.dto.GridClass
import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.dto.LayerFilter
import au.org.ala.spatial.util.SpatialConversionUtils
import au.org.ala.spatial.util.SpatialUtils
import grails.converters.JSON
import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.geotools.data.DataUtilities
import org.geotools.data.FeatureReader
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geojson.feature.FeatureJSON
import org.geotools.geojson.geom.GeometryJSON
import org.geotools.kml.KML
import org.geotools.kml.KMLConfiguration
import org.geotools.xsd.Encoder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.util.UriEncoder

import java.sql.ResultSet
import java.util.Map.Entry
import java.util.zip.ZipInputStream

@Slf4j
class SpatialObjectsService {

    static final String objectWmsUrl = "/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:Objects&format=image/png&viewparams=s:<pid>"
    static final String gridPolygonSld
    static final String gridClassSld

    SpatialConfig spatialConfig

    // sld substitution strings
    private static final String SUB_LAYERNAME = "*layername*"
    static final String gridPolygonWmsUrl = "/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + SUB_LAYERNAME + "&format=image/png&sld_body="
    private static final String SUB_COLOUR = "0xff0000" // "*colour*";
    private static final String SUB_MIN_MINUS_ONE = "*min_minus_one*"
    private static final String SUB_MIN = "*min*"
    private static final String SUB_MAX = "*max*"
    private static final String SUB_MAX_PLUS_ONE = "*max_plus_one*"
    private static final String KML_HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<kml xmlns=\"http://earth.google.com/kml/2.2\">" + "<Document>" + "  <name></name>" + "  <description></description>" + "  <Style id=\"style1\">" + "    <LineStyle>" + "      <color>40000000</color>" + "      <width>3</width>" + "    </LineStyle>" + "    <PolyStyle>" + "      <color>73FF0000</color>" + "      <fill>1</fill>" + "      <outline>1</outline>" + "    </PolyStyle>" + "  </Style>" + "  <Placemark>" + "    <name></name>" + "    <description></description>" + "    <styleUrl>#style1</styleUrl>"
    private static final String KML_FOOTER = "</Placemark>" + "</Document>" + "</kml>"

    static {
        String polygonSld = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><StyledLayerDescriptor xmlns=\"http://www.opengis.net/sld\">" + "<NamedLayer><Name>ALA:" + SUB_LAYERNAME + "</Name>" + "<UserStyle><FeatureTypeStyle><Rule><RasterSymbolizer><Geometry></Geometry>" + "<ColorMap>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"0\" quantity=\"" + SUB_MIN_MINUS_ONE + "\"/>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"1\" quantity=\"" + SUB_MIN + "\"/>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"0\" quantity=\"" + SUB_MAX_PLUS_ONE + "\"/>" + "</ColorMap></RasterSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>"

        String classSld = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><StyledLayerDescriptor xmlns=\"http://www.opengis.net/sld\">" + "<NamedLayer><Name>ALA:" + SUB_LAYERNAME + "</Name>" + "<UserStyle><FeatureTypeStyle><Rule><RasterSymbolizer><Geometry></Geometry>" + "<ColorMap>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"0\" quantity=\"" + SUB_MIN_MINUS_ONE + "\"/>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"1\" quantity=\"" + SUB_MIN + "\"/>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"1\" quantity=\"" + SUB_MAX + "\"/>" + "<ColorMapEntry color=\"" + SUB_COLOUR + "\" opacity=\"0\" quantity=\"" + SUB_MAX_PLUS_ONE + "\"/>" + "</ColorMap></RasterSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>"
        try {
            polygonSld = UriEncoder.encode(polygonSld)
        } catch (UnsupportedEncodingException ignored) {
            log.error("Invalid polygon sld string defined in ObjectDAOImpl.")
        }
        try {
            classSld = UriEncoder.encode(classSld)
        } catch (UnsupportedEncodingException ignored) {
            log.error("Invalid sld string defined in ObjectDAOImpl.")
        }

        gridPolygonSld = polygonSld
        gridClassSld = classSld

    }

    LayerIntersectService layerIntersectDao
    LayerService layerService
    def Holders
    def dataSource

    List<SpatialObjects> getObjectsById(String id, int start, int pageSize, String filter) {
        if (filter == null) filter = ""
        String upperCaseFilter = filter.toUpperCase()
        filter = "%" + filter + "%"

        log.debug("Getting object info for fid = " + id)
        String limit_offset = " limit " + (pageSize < 0 ? "all" : pageSize) + " offset " + start
        String sql = "select o.pid as pid, o.name as name, o.desc as description, " +
                "o.fid as fid, f.name as fieldname, o.bbox, o.area_km, " +
                "ST_AsText(ST_Centroid(o.the_geom)) as centroid," +
                "GeometryType(o.the_geom) as featureType from objects o inner join fields f on o.fid = f.id " +
                "where o.fid = ? and (o.name ilike ? or o.desc ilike ? ) order by o.pid " + limit_offset
        List<SpatialObjects> objects = []
        Sql.newInstance(dataSource).query(sql, [id, filter, filter], { ResultSet it ->
            while (it.next()) {
                SpatialObjects so = new SpatialObjects()
                so.pid = it.getObject(1)
                so.name = it.getObject(2)
                so.description = it.getObject(3)
                so.fid = it.getObject(4)
                so.fieldname = it.getObject(5)
                so.bbox = it.getObject(6)
                so.area_km = it.getObject(7)
                so.centroid = it.getObject(8)
                so.featureType = it.getObject(9)
                objects.add(so)
            }
        })

        updateObjectWms(objects)

        // get grid classes
        if (objects == null || objects.isEmpty()) {
            objects = new ArrayList<SpatialObjects>()
            IntersectionFile f = layerService.getIntersectionFile(id)
            if (f != null && f.getClasses() != null) {
                //shape position
                int pos = 0

                for (Entry<Integer, GridClass> c : f.getClasses().entrySet()) {
                    File file = new File(f.getFilePath() + File.separator + c.getKey() + ".wkt.index.dat")
                    if ((f.getType() == "a" || !file.exists()) &&
                            c.getValue().getName().toUpperCase().contains(upperCaseFilter)) { // import groovy.transform.CompileStatic

                        if (pageSize == -1 || (pos >= start && pos - start < pageSize)) {
                            SpatialObjects o = new SpatialObjects()
                            o.setPid(f.getLayerPid() + ':' + c.getKey())
                            o.setId(f.getLayerPid() + ':' + c.getKey())
                            o.setName(c.getValue().getName())
                            o.setFid(f.getFieldId())
                            o.setFieldname(f.getFieldName())
                            o.setBbox(c.getValue().getBbox())
                            o.setArea_km(c.getValue().getArea_km())
                            o.setWmsurl(getGridClassWms(f.getLayerName(), c.getValue()))
                            objects.add(o)
                        }
                        pos++

                        if (pageSize != -1 && pos >= start + pageSize) {
                            break
                        }
                    } else { // polygon pid
                        RandomAccessFile raf = null
                        try {
                            raf = new RandomAccessFile(file, "r")
                            long itemSize = (4 + 4 + 4 * 4 + 4)
                            long len = (long) (raf.length() / itemSize) // group

                            if (pageSize != -1 && pos + len < start) {
                                pos += len
                            } else {
                                // number,
                                // character
                                // offset,
                                // minx,
                                // miny,
                                // maxx,
                                // maxy,
                                // area
                                // sq
                                // km
                                int i = 0
                                if (pageSize != -1 && pos < start) {
                                    //the first object requested is in this file, seek to the start
                                    i = start - pos
                                    pos += i
                                    raf.seek(i * itemSize)
                                }
                                for (; i < len; i++) {
                                    int n = raf.readInt()
                                    /* int charoffset = */
                                    raf.readInt()
                                    float minx = raf.readFloat()
                                    float miny = raf.readFloat()
                                    float maxx = raf.readFloat()
                                    float maxy = raf.readFloat()
                                    float area = raf.readFloat()

                                    if (pageSize == -1 || (pos >= start && pos - start < pageSize)) {
                                        SpatialObjects o = new SpatialObjects()
                                        o.setPid(f.getLayerPid() + ':' + c.getKey() + ':' + n)
                                        o.setId(f.getLayerPid() + ':' + c.getKey() + ':' + n)
                                        o.setName(c.getValue().getName())
                                        o.setFid(f.getFieldId())
                                        o.setFieldname(f.getFieldName())

                                        o.setBbox("POLYGON((" + minx + " " + miny + "," + minx + " " + maxy + "," + +maxx + " " + maxy + "," + +maxx + " " + miny + "," + +minx + " " + miny + "))")
                                        o.setArea_km(1.0 * area)

                                        o.setWmsurl(getGridPolygonWms(f.getLayerName(), n))

                                        objects.add(o)
                                    }

                                    pos++

                                    if (pageSize != -1 && pos >= start + pageSize) {
                                        break
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        } finally {
                            if (raf != null) {
                                try {
                                    raf.close()
                                } catch (Exception e) {
                                    log.error(e.getMessage(), e)
                                }
                            }
                        }

                        if (pageSize != -1 && pos >= start + pageSize) {
                            break
                        }
                    }
                }
            }
        }
        return objects
    }


    String getObjectsGeometryById(String id, String geomtype) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        try {
            streamObjectsGeometryById(baos, id, geomtype)
        } catch (IOException e) {
            log.error(e.getMessage(), e)
        } finally {
            try {
                baos.close()
            } catch (IOException e) {
                log.error(e.getMessage(), e)
            }
        }

        return new String(baos.toByteArray())
    }


    void streamObjectsGeometryById(OutputStream os, String id, String geomtype) throws IOException {
        log.debug("Getting object info for id = " + id + " and geometry as " + geomtype)


        List<SpatialObjects> l = SpatialObjects.findAllByPid(id)

        if (l.size() > 0) {
            if ("shp" == geomtype) {
                String wkt = l.get(0).geometry.toText()
                File zippedShapeFile = SpatialConversionUtils.buildZippedShapeFile(wkt, id, l.get(0).name, l.get(0).name)
                FileUtils.copyFile(zippedShapeFile, os)
            } else if ("kml" == geomtype) {
                String wktString = l.get(0).geometry.toText()
                String wkttype = "POLYGON"
                if (wktString.contains("MULTIPOLYGON")) {
                    wkttype = "MULTIPOLYGON"
                } else if (wktString.contains("GEOMETRYCOLLECTION")) {
                    wkttype = "GEOMETRYCOLLECTION"
                }
                final SimpleFeatureType TYPE = SpatialConversionUtils.createFeatureType(wkttype)
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE)
                featureBuilder.add(l.get(0).geometry)
                SimpleFeature feature = featureBuilder.buildFeature(null)
                List<SimpleFeature> features = new ArrayList<SimpleFeature>()
                features.add(feature)
                DefaultFeatureCollection featureCollection = new DefaultFeatureCollection()
                featureCollection.addAll(features)

                Encoder encoder = new Encoder(new KMLConfiguration())
                encoder.setIndenting(true)
                encoder.encode(featureCollection, KML.kml, os )
            } else if ("wkt" == geomtype) {
                os.write(l.get(0).geometry.toText().bytes)
            } else if ("geojson" == geomtype) {
                FeatureJSON fjson = new FeatureJSON(new GeometryJSON(16))
                StringWriter writer = new StringWriter()

                String wktString = l.get(0).geometry.toText()
                String wkttype = "POLYGON"
                if (wktString.contains("MULTIPOLYGON")) {
                    wkttype = "MULTIPOLYGON"
                } else if (wktString.contains("GEOMETRYCOLLECTION")) {
                    wkttype = "GEOMETRYCOLLECTION"
                }
                final SimpleFeatureType TYPE = SpatialConversionUtils.createFeatureType(wkttype)
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE)
                featureBuilder.add(l.get(0).geometry)
                SimpleFeature feature = featureBuilder.buildFeature(null)

                fjson.writeFeature(feature, writer)

                String json = writer.toString()
                os.write(json.bytes)
            }

        } else {
            // get grid classes
            if (id.length() > 0) {
                // grid pids are, 'layerPid:gridClassNumber'
                try {
                    String[] s = id.split(':')
                    if (s.length >= 2) {
                        int n = Integer.parseInt(s[1])
                        IntersectionFile f = layerService.getIntersectionFile(s[0])
                        if (f != null && f.getClasses() != null) {
                            GridClass gc = f.getClasses().get(n)
                            if (gc != null && ("kml" == geomtype || "wkt" == geomtype || "geojson" == geomtype || "shp" == geomtype)) {
                                // TODO: enable for type 'a' after
                                // implementation of fields table defaultLayer
                                // field

                                File file = new File(f.getFilePath() + File.separator + s[1] + "." + geomtype + ".zip")
                                if ((f.getType() == "a" || s.length == 2) && file.exists()) {
                                    ZipInputStream zis = null
                                    try {
                                        zis = new ZipInputStream(new FileInputStream(file))

                                        zis.getNextEntry()
                                        byte[] buffer = new byte[1024]
                                        int size
                                        while ((size = zis.read(buffer)) > 0) {
                                            os.write(buffer, 0, size)
                                        }
                                    } catch (Exception e) {
                                        log.error(e.getMessage(), e)
                                    } finally {
                                        if (zis != null) {
                                            try {
                                                zis.close()
                                            } catch (Exception e) {
                                                log.error(e.getMessage(), e)
                                            }
                                        }
                                    }
                                } else { // polygon
                                    BufferedInputStream bis = null
                                    InputStreamReader isr = null
                                    try {
                                        String[] cells = null

                                        HashMap<String, Object> map = s.length == 2 ? null : getGridIndexEntry(f.getFilePath() + File.separator + s[1], s[2])

                                        String wkt = null
                                        if (map != null) {
                                            cells = new String[]{s[2], String.valueOf(map.get("charoffset"))}
                                            if (cells != null) {
                                                // get polygon wkt string
                                                File file2 = new File(f.getFilePath() + File.separator + s[1] + ".wkt")
                                                bis = new BufferedInputStream(new FileInputStream(file2))
                                                isr = new InputStreamReader(bis)
                                                isr.skip(Long.parseLong(cells[1]))
                                                char[] buffer = new char[1024]
                                                int size = 0
                                                StringBuilder sb = new StringBuilder()
                                                sb.append("POLYGON")
                                                int end = -1
                                                while (end < 0 && (size = isr.read(buffer)) > 0) {
                                                    sb.append(buffer, 0, size)
                                                    end = sb.toString().indexOf("))")
                                                }
                                                end += 2

                                                wkt = sb.toString().substring(0, end)
                                            }
                                        } else {
                                            wkt = gc.getBbox()
                                        }

                                        if (geomtype == "wkt") {
                                            os.write(wkt.bytes)
                                        } else {
                                            WKTReader r = new WKTReader()
                                            Geometry g = r.read(wkt)

                                            if (geomtype == "kml") {
                                                os.write(KML_HEADER.bytes)
                                                Encoder encoder = new Encoder(new KMLConfiguration())
                                                encoder.setIndenting(true)
                                                encoder.encode(g, KML.Geometry, os)
                                                os.write(KML_FOOTER.bytes)
                                            } else if (geomtype == "geojson") {
                                                FeatureJSON fjson = new FeatureJSON()
                                                final SimpleFeatureType TYPE = DataUtilities.createType("class", "the_geom:MultiPolygon,name:String")
                                                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE)
                                                featureBuilder.add(g)
                                                featureBuilder.add(gc.getName())
                                                fjson.writeFeature(featureBuilder.buildFeature(null), os)
                                            } else if (geomtype == "shp") {
                                                File zippedShapeFile = SpatialConversionUtils.buildZippedShapeFile(wkt, id, gc.getName(), null)
                                                FileUtils.copyFile(zippedShapeFile, os)
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.error(e.getMessage(), e)
                                    } finally {
                                        if (bis != null) {
                                            try {
                                                bis.close()
                                            } catch (Exception e) {
                                                log.error(e.getMessage(), e)
                                            }
                                        }
                                        if (isr != null) {
                                            try {
                                                isr.close()
                                            } catch (Exception e) {
                                                log.error(e.getMessage(), e)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }


    SpatialObjects getObjectByPid(String pid) {
        log.debug("Getting object info for pid = " + pid)
        String sql = "select o.pid, o.name, o.desc as description, o.fid as fid, f.name as fieldname, " +
                "o.bbox, o.area_km from objects o inner join fields f on o.fid = f.id where o.pid = ?"
        List<SpatialObjects> l = []
        Sql.newInstance(dataSource).query(sql, [pid], { ResultSet rs ->
            while(rs.next()) {
                l.add(spatialObjectsFromResult(rs))
            }
        })

        updateObjectWms(l)

        // get grid classes
        if ((l == null || l.isEmpty()) && pid.length() > 0) {
            // grid pids are, 'layerPid:gridClassNumber'
            try {
                String[] s = pid.split(':')
                if (s.length >= 2) {
                    int n = Integer.parseInt(s[1])
                    IntersectionFile f = layerService.getIntersectionFile(s[0])
                    if (f != null && f.getClasses() != null) {
                        GridClass gc = f.getClasses().get(n)
                        if (gc != null) {
                            SpatialObjects o = new SpatialObjects()
                            o.setPid(pid)
                            o.setName(gc.getName())
                            o.setFid(f.getFieldId())
                            o.setFieldname(f.getFieldName())

                            if (f.getType() == "a" || s.length == 2) {
                                o.setBbox(gc.getBbox())
                                o.setArea_km(gc.getArea_km())
                                o.setWmsurl(getGridClassWms(f.getLayerName(), gc))
                            } else {
                                HashMap<String, Object> map = getGridIndexEntry(f.getFilePath() + File.separator + s[1], s[2])
                                if (!map.isEmpty()) {
                                    o.setBbox("POLYGON(" + map.get("minx") + " " + map.get("miny") + "," + map.get("minx") + " " + map.get("maxy") + "," + map.get("maxx") + " " + map.get("maxy")
                                            + "," + map.get("maxx") + " " + map.get("miny") + "," + map.get("minx") + " " + map.get("miny") + ")")

                                    o.setArea_km(((Float) map.get("area")).doubleValue())

                                    o.setWmsurl(getGridPolygonWms(f.getLayerName(), Integer.parseInt(s[2])))
                                }
                            }

                            l.add(o)
                        }
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            }
        }

        if (l.size() > 0) {
            return l.get(0)
        } else {
            return null
        }
    }


    SpatialObjects getObjectByIdAndLocation(String fid, Double lng, Double lat) {
        log.debug("Getting object info for fid = " + fid + " at loc: (" + lng + ", " + lat + ") ")
        String sql = "select o.pid, o.name, o.desc as description, o.fid as fid, f.name as fieldname, " +
                "o.bbox, o.area_km from search_objects_by_geometry_intersect(?, " +
                "ST_GeomFromText('POINT(" + lng + " " + lat + ")', 4326)) o, fields f WHERE o.fid = f.id"
        List<SpatialObjects> l = new ArrayList()

        Sql.newInstance(dataSource).query(sql, [fid], {
            while (it.next()) {
                l.add(spatialObjectsFromResult(it))
            }
        })

        updateObjectWms(l)
        if (l == null || l.isEmpty()) {
            // get grid classes intersection
            l = new ArrayList<SpatialObjects>()
            IntersectionFile f = layerService.getIntersectionFile(fid)
            if (f != null && f.getClasses() != null) {
                Vector v = layerIntersectDao.samplingFull(fid, lng, lat)
                if (v != null && v.size() > 0 && v.get(0) != null) {
                    Map m = (Map) v.get(0)
                    int key = (int) Double.parseDouble(((String) m.get("pid")).split(':')[1])
                    GridClass gc = f.getClasses().get(key)
                    if (f.getType() == "a" || !new File(f.getFilePath() + File.separator + "polygons.grd").exists()) {
                        SpatialObjects o = new SpatialObjects()
                        o.setName(gc.getName())
                        o.setFid(f.getFieldId())
                        o.setFieldname(f.getFieldName())
                        o.setPid(f.getLayerPid() + ':' + gc.getId())
                        o.setBbox(gc.getBbox())
                        o.setArea_km(gc.getArea_km())
                        o.setWmsurl(getGridClassWms(f.getLayerName(), gc))
                        l.add(o)
                    } else if (f.getType() == "b") {//polygon pid
                        Grid g = new Grid(f.getFilePath() + File.separator + "polygons")
                        if (g != null) {
                            float[] vs = g.getValues([[lng, lat]] as double[][])
                            String pid = f.getLayerPid() + ':' + gc.getId() + ':' + ((int) vs[0])
                            l.add(getObjectByPid(pid))
                        }
                    }
                }
            }
        }
        if (l.size() > 0) {
            return l.get(0)
        } else {
            return null
        }
    }


    List<SpatialObjects> getNearestObjectByIdAndLocation(String fid, int limit, Double lng, Double lat) {
        log.debug("Getting " + limit + " nearest objects in field fid = " + fid + " to loc: (" + lng + ", " + lat + ") ")

        String sql = "select fid, name, o.desc, pid, st_astext(the_geom), " +
                "ST_DistanceSphere(ST_SETSRID(ST_Point( ? , ? ),4326), the_geom) as distance, " +
                "degrees(Azimuth( ST_SETSRID(ST_Point( ? , ? ),4326), the_geom)) as degrees, " +
                "area_km " +
                "from objects o where fid= ? and GeometryType(the_geom) = 'POINT' " +
                "order by the_geom <#> st_setsrid(st_makepoint( ? , ? ),4326) limit ? "

        WKTReader reader = new WKTReader()

        List<SpatialObjects> objects = []
        Sql.newInstance(dataSource).query(sql, [lng, lat, lng, lat, fid, lng, lat, limit], { ResultSet rs ->
            while (rs.next()) {
                SpatialObjects so = new SpatialObjects()
                so.fid = rs.getObject(1)
                so.name = rs.getObject(2)
                so.description = rs.getObject(3)
                so.pid = rs.getObject(4)
                so.geometry = reader.read(rs.getObject(5))
                so.distance = rs.getObject(6)
                so.degrees = rs.getObject(7)
                so.area_km = rs.getObject(8)
                objects.add(so)
            }
        })
        updateObjectWms(objects)
        return objects
    }

    private String getGridPolygonWms(String layername, int n) {
        return spatialConfig.geoserver.url + gridPolygonWmsUrl.replace(SUB_LAYERNAME, layername)+formatSld(gridPolygonSld, layername, String.valueOf(n - 1), String.valueOf(n), String.valueOf(n), String.valueOf(n + 1))
    }

    private String getGridClassWms(String layername, GridClass gc) {
        return spatialConfig.geoserver.url + gridPolygonWmsUrl.replace(SUB_LAYERNAME, layername)+formatSld(gridClassSld, layername, String.valueOf(gc.getMinShapeIdx() - 1), String.valueOf(gc.getMinShapeIdx()), String.valueOf(gc.getMaxShapeIdx()), String.valueOf(gc.getMaxShapeIdx() + 1))
    }

    private static String formatSld(String sld, String layername, String min_minus_one, String min, String max, String max_plus_one) {
        return sld.replace(SUB_LAYERNAME, layername).replace(SUB_MIN_MINUS_ONE, min_minus_one).replace(SUB_MIN, min).replace(SUB_MAX, max).replace(SUB_MAX_PLUS_ONE, max_plus_one)
    }

    private void updateObjectWms(List<SpatialObjects> objects) {
        for (SpatialObjects o : objects) {
            String wmsurl = objectWmsUrl.replace("<pid>", o.getPid())
            //Points
            if (o.getArea_km() == null) {
                log.error("area_km cannot be null. wmsurl may be incorect.", new Exception("area_km is null"))
            } else if (o.getArea_km() == 0) {
                wmsurl = wmsurl.replace("ALA:Objects", "ALA:Points")
            }
            o.setWmsurl(spatialConfig.geoserver.url + wmsurl)
        }
    }

    private static HashMap<String, Object> getGridIndexEntry(String path, String objectId) {
        HashMap<String, Object> map = new HashMap<String, Object>()
        RandomAccessFile raf = null
        try {
            raf = new RandomAccessFile(path + ".wkt.index.dat", "r")

            int s2 = Integer.parseInt(objectId)

            // it is all in order, seek to the record
            int recordSize = 4 * 7 // 2 int + 5 float
            int start = raf.readInt()
            raf.seek(recordSize * (s2 - start))

            map.put("gn", raf.readInt())
            map.put("charoffset", raf.readInt())
            map.put("minx", raf.readFloat())
            map.put("miny", raf.readFloat())
            map.put("maxx", raf.readFloat())
            map.put("maxy", raf.readFloat())
            map.put("area", raf.readFloat())
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            try {
                if (raf != null) {
                    raf.close()
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            }
        }

        return map
    }


    List<SpatialObjects> getObjectsByIdAndArea(String id, Integer limit, String wkt) {
        String sql = "SELECT o.pid, o.name, o.desc AS description, o.fid AS fid, f.name AS fieldname, o.bbox, " +
                "o.area_km, GeometryType(o.the_geom) as featureType FROM objects o inner join fields f on o.fid = f.id  WHERE o.fid = ? AND " +
                "ST_Within(the_geom, ST_GeomFromText( ? , 4326)) limit ? "

        List<SpatialObjects> objects = []
        Sql.newInstance(dataSource).query(sql, [id, wkt, limit], { result ->
            while (result.next()) {
                objects.add(spatialObjectsFromResult(result))
            }
        })
        updateObjectWms(objects)
        return objects
    }


    List<SpatialObjects> getObjectsByIdAndIntersection(String id, Integer limit, LayerFilter layerFilter) {
        String world = "POLYGON((-180 -90,-180 90,180 90,180 -90,-180 -90))"
        List<SpatialObjects> objects = getObjectsByIdAndArea(id, Integer.MAX_VALUE, world)

        double[][] points = new double[objects.size()][2]
        for (int i = 0; i < objects.size(); i++) {
            try {
                String[] s = objects.get(i).geometry.toString().substring("POINT(".length(), objects.get(i).geometry.toString().length() - 1).split(" ")
                points[i][0] = Double.parseDouble(s[0])
                points[i][1] = Double.parseDouble(s[1])
            } catch (Exception ignored) {
                // don't intersect this one
                points[i][0] = Integer.MIN_VALUE
                points[i][1] = Integer.MIN_VALUE
            }
        }

        // sampling
        ArrayList<String> sample = layerIntersectDao.sampling(new String[]{layerFilter.getLayername()}, points)

        // filter
        List<SpatialObjects> matched = new ArrayList<SpatialObjects>()
        String[] sampling = sample.get(0).split("\n")
        IntersectionFile f = layerService.getIntersectionFile(layerFilter.getLayername())
        if (f != null && (f.getType() == "a" || f.getType() == "b")) {
            String target = f.getClasses().get((int) layerFilter.getMinimum_value()).getName()
            for (int i = 0; i < sampling.length; i++) {
                if (sampling[i].length() > 0) {
                    if (sampling[i] == target) {
                        matched.add(objects.get(i))
                    }
                }
            }
        } else {
            for (int i = 0; i < sampling.length; i++) {
                if (sampling[i].length() > 0) {
                    double v = Double.parseDouble(sampling[i])
                    if (v >= layerFilter.getMinimum_value() && v <= layerFilter.getMaximum_value()) {
                        matched.add(objects.get(i))
                    }
                }
            }
        }

        updateObjectWms(matched)
        return matched
    }

    List<SpatialObjects> getObjectsByIdAndIntersection(String id, Integer limit, String intersectingPid) {
        String sql = "SELECT o.pid, o.name, o.desc AS description, o.fid AS fid, f.name AS fieldname, o.bbox, " +
                "o.area_km, GeometryType(o.the_geom) as featureType FROM objects o inner join fields f on o.fid = f.id , (select the_geom as g from Objects where pid = ? ) t " +
                "WHERE o.fid = ? AND and ST_Within(the_geom, g) limit ? "

        List<SpatialObjects> objects = []
        Sql.newInstance(dataSource).query(sql, [intersectingPid, id, limit], { result ->
            while (result.next()) {
                objects.add(spatialObjectsFromResult(result))
            }
        })
        updateObjectWms(objects)

        return objects
    }


    String createUserUploadedObject(String wkt, String name, String description, String userid) {
        return createUserUploadedObject(wkt, name, description, userid, true)
    }

    @Transactional
    String createUserUploadedObject(String wkt, String name, String description, String userid, boolean namesearch) {

        double area_km = SpatialUtils.calculateArea(wkt) / 1000.0 / 1000.0

        try {
            int object_id = 0
            Sql.newInstance(dataSource).query("SELECT nextval('objects_id_seq'::regclass)", { result ->
                if (result.next()) {
                    object_id = result.getInt(1)
                }
            })
            int metadata_id = 0
            Sql.newInstance(dataSource).query("SELECT nextval('uploaded_objects_metadata_id_seq'::regclass)", { result ->
                if (result.next()) {
                    metadata_id = result.getInt(1)
                }
            })

            // Insert shape into geometry table
            String sql = "INSERT INTO objects (pid,  name, \"desc\", fid, the_geom, namesearch, bbox, area_km) " +
                    "values (?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ST_AsText(Box2D(ST_GeomFromText(?, 4326))), ?)"
            Sql.newInstance(dataSource).execute(sql, [object_id, name, description, spatialConfig.userObjectsField, wkt, namesearch, wkt, area_km])

            // Now write to metadata table
            String sql2 = "INSERT INTO uploaded_objects_metadata (pid, id, user_id, time_last_updated) values (?, ?, ?, now())"
            Sql.newInstance(dataSource).execute(sql2, object_id, metadata_id, userid)

            return Integer.toString(object_id)
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("Error writing to database. Check validity of wkt.", ex)
        }
    }


    @Transactional
    boolean updateUserUploadedObject(int pid, String wkt, String name, String description, String userid) {

        if (!shapePidIsForUploadedShape(pid)) {
            throw new IllegalArgumentException("Supplied pid does not match an uploaded shape.")
        }

        try {
            double area_km = SpatialUtils.calculateArea(wkt) / 1000.0 / 1000.0

            // First update metadata table
            String sql = "UPDATE uploaded_objects_metadata SET user_id = ?, time_last_updated = now() WHERE pid = ?"
            Sql.newInstance(dataSource).execute(sql, [userid, Integer.toString(pid)])

            // Then update objects table
            String sql2 = "UPDATE objects SET the_geom = ST_GeomFromText(?, 4326), " +
                    "bbox = ST_AsText(Box2D(ST_GeomFromText(?, 4326))), name = ?, \"desc\" = ?, area_km = ? where pid = ?"
            boolean rowsUpdated = Sql.newInstance(dataSource).execute(sql2, [wkt, wkt, name, description, area_km, Integer.toString(pid)])
            return (rowsUpdated)
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("Error writing to database. Check validity of wkt.", ex)
        }
    }


    @Transactional
    boolean deleteUserUploadedObject(int pid) {
        if (!shapePidIsForUploadedShape(pid)) {
            throw new IllegalArgumentException("Supplied pid does not match an uploaded shape.")
        }

        String sql = "DELETE FROM uploaded_objects_metadata WHERE pid = ?; DELETE FROM objects where pid = ?"
        Sql.newInstance(dataSource).execute(sql, [Integer.toString(pid), Integer.toString(pid)])

        // return true if the object no longer exists
        return !shapePidIsForUploadedShape(pid)
    }

    private boolean shapePidIsForUploadedShape(int pid) {
        boolean found = false
        String sql = "SELECT count(*) from uploaded_objects_metadata WHERE pid = ?"
        Sql.newInstance(dataSource).query(sql, [Integer.toString(pid)], {
            while (it.next()) {
                if (it.getObject(1) > 0) {
                    found = true
                }
            }
        })
        return found
    }

    List<SpatialObjects> getObjectsWithinRadius(String fid, double latitude, double longitude, double radiusKm) {
        String sql = "SELECT o.pid, o.name, o.desc AS description, o.fid AS fid, f.name AS fieldname, o.bbox, " +
                "o.area_km, GeometryType(o.the_geom) as featureType FROM objects o inner join fields f on o.fid = f.id  WHERE o.fid = ? AND " +
                "ST_DWithin(ST_GeographyFromText('POINT(" + longitude + " " + latitude + ")'), geography(the_geom), ?, true)"
        List<SpatialObjects> l = []
        Sql.newInstance(dataSource).query(sql, [fid, radiusKm * 1000], {
            while (it.next()) {
                l.add(spatialObjectsFromResult(it))
            }
        })
        updateObjectWms(l)
        return l
    }


    List<SpatialObjects> getObjectsIntersectingWithGeometry(String fid, String wkt) {
        String sql = "SELECT o.pid, o.name, o.desc AS description, o.fid AS fid, f.name AS fieldname, " +
                "o.bbox, o.area_km from search_objects_by_geometry_intersect(?, ST_GeomFromText(?, 4326)) o inner join fields f on o.fid = f.id"
        List<SpatialObjects> l = []
        Sql.newInstance(dataSource).query(sql, [fid, wkt], {
            while (it.next()) {
                l.add(spatialObjectsFromResult(it))
            }
        })
        updateObjectWms(l)
        return l
    }


    List<SpatialObjects> getObjectsIntersectingWithObject(String fid, String objectPid) {
        String sql = "SELECT o.pid, o.name, o.desc AS description, o.fid AS fid, f.name AS fieldname, " +
                "o.bbox, o.area_km FROM search_objects_by_geometry_intersect(?, (SELECT the_geom FROM objects WHERE pid = ?)) o inner join fields f on o.fid = f.id"
        List<SpatialObjects> l = []
        Sql.newInstance(dataSource).query(sql, [fid, objectPid], {
            while (it.next()) {
                l.add(spatialObjectsFromResult(it))
            }
        })
        updateObjectWms(l)
        return l
    }

    SpatialObjects spatialObjectsFromResult(ResultSet rs) {
        SpatialObjects so = new SpatialObjects()
        so.pid = rs.getObject(1)
        so.name = rs.getObject(2)
        so.description = rs.getObject(3)
        so.fid = rs.getObject(4)
        so.fieldname = rs.getObject(5)
        so.bbox = rs.getObject(6)
        so.area_km = rs.getObject(7)
        if (rs.fields.length >= 8) {
            so.featureType = rs.getObject(8)
        }
        so
    }

    SpatialObjects intersectObject(String pid, double latitude, double longitude) {
        String sql = "SELECT o.pid, o.name, o.desc AS description, o.fid AS fid, f.name AS fieldname, o.bbox, " +
                "o.area_km, GeometryType(o.the_geom) as featureType FROM objects o inner join fields f on o.fid = f.id WHERE o.pid = ? AND " +
                "ST_Intersects(the_geom, ST_GeomFromText('POINT(" + longitude + " " + latitude + ")', 4326))"
        List<SpatialObjects> l = []
        Sql.newInstance(dataSource).query(sql, [pid], { ResultSet it ->
            while (it.next()) {
                l.add(spatialObjectsFromResult(it))
            }
        })
        if (l.size() > 0) {
            return l.get(0)
        } else {
            return null
        }
    }

    void wkt(String id, OutputStream os) {
        if (id.startsWith("ENVELOPE")) {
            streamEnvelope(os, id.replace("ENVELOPE", ""), 'wkt')
        } else {
            if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it).toString() }

                String query = "select st_astext(st_collect(geom)) as wkt from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                Sql.newInstance(dataSource).eachRow(query, { GroovyResultSet row ->
                    os.write(row.getObject(0).toString().bytes)
                })
            } else {
                streamObjectsGeometryById(os, cleanObjectId(id).toString(), 'wkt')
            }
        }
    }

    static String cleanObjectId(String id) {
        id.replaceAll("[^a-zA-Z0-9]:", "")
    }

    void streamEnvelope(OutputStream os, String envelopeTaskId, String type) {
        String dir = spatialConfig.data.dir + "/public/" + envelopeTaskId.toInteger()
        String filePrefix = dir + "/envelope"

        if (type == 'shp') {
            def file = new File(filePrefix + "-shp.zip")
            if (!file.exists()) {
                Util.zip(filePrefix + "-shp.zip",
                        [filePrefix + ".shp", filePrefix + ".shx", filePrefix + ".dbf", filePrefix + ".fix"] as String[],
                        ['envelope.shp', "envelope.shx", "envelope.dbf", "envelope.fix"] as String)
            }
            InputStream is
            try {
                is = FileUtils.openInputStream(new File(filePrefix + "-shp.zip"))

                int len
                byte[] bytes = new byte[1024]
                while ((len = is.read(bytes)) > 0) {
                    os.write(bytes, 0, len)
                }
            } catch (Exception e) {
                log.error("failed to make shapefile for " + envelopeTaskId, e)
            } finally {
                if (is != null) {
                    is.close()
                }
            }
        } else {
            def file = new File(filePrefix + ".shp")

            ShapefileDataStore sds = new ShapefileDataStore(file.toURI().toURL())
            FeatureReader reader = sds.featureReader

            if (reader.hasNext()) {
                def f = reader.next()
                Geometry g = f.getDefaultGeometry() as Geometry

                if (type == 'wkt') {
                    os.write(g.toText().bytes)
                } else if (type == 'geojson') {
                    GeometryJSON geojson = new GeometryJSON()
                    geojson.write(g, os)
                } else {
                    // kml
                    DefaultFeatureCollection collection = new DefaultFeatureCollection()
                    SimpleFeatureType sft = DataUtilities.createType("location", "geom:Geometry,name:String")
                    List<Object> list = [g, "envelope"] as List<Object>
                    collection.add(SimpleFeatureBuilder.build(sft, list, null))

                    Encoder encoder = new Encoder(new KMLConfiguration())

                    encoder.encode(collection, KML.kml, os)
                }
            }

            sds.dispose()
        }
    }
}
