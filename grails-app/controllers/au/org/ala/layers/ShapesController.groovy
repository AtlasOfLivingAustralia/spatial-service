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

package au.org.ala.layers


import au.org.ala.RequirePermission
import au.org.ala.layers.util.SpatialConversionUtils
import au.org.ala.spatial.slave.SpatialUtils
import au.org.ala.spatial.util.GeomMakeValid
import au.org.ala.spatial.util.JSONRequestBodyParser
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.ParseException
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.jackson.map.ObjectMapper
import org.geotools.data.DataUtilities
import org.geotools.data.FeatureReader
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geojson.geom.GeometryJSON
import org.geotools.graph.util.ZipUtil
import org.geotools.kml.KML
import org.geotools.kml.KMLConfiguration
import org.geotools.xml.Encoder
import org.grails.web.json.JSONObject
import org.opengis.feature.simple.SimpleFeatureType
import org.springframework.dao.DataAccessException
import org.springframework.web.multipart.MultipartFile
import groovy.json.JsonOutput

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class ShapesController {

    def objectDao

    def groovySql

    static final String KML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<kml xmlns=\"http://earth.google.com/kml/2.2\">" +
            "<Document>" +
            "  <name></name>" +
            "  <description></description>" +
            "  <Style id=\"style1\">" +
            "    <LineStyle>" +
            "      <color>40000000</color>" +
            "      <width>3</width>" +
            "    </LineStyle>" +
            "    <PolyStyle>" +
            "      <color>73FF0000</color>" +
            "      <fill>1</fill>" +
            "      <outline>1</outline>" +
            "    </PolyStyle>" +
            "  </Style>" +
            "  <Placemark>" +
            "    <name></name>" +
            "    <description></description>" +
            "    <styleUrl>#style1</styleUrl>"

    private static final String KML_FOOTER = "</Placemark></Document></kml>"

    def wkt(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = null
        try {
            os = response.getOutputStream()
            response.setContentType("application/wkt")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".wkt\"")
            if (id.startsWith("ENVELOPE")) {
                streamEnvelope(os, id.replace("ENVELOPE", ""), 'wkt')
            } else {
                if (id.contains('~')) {
                    List ids = id.split('~').collect { cleanObjectId(it).toString() }

                    String query = "select st_astext(st_collect(geom)) as wkt from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                    groovySql.eachRow(query, { row ->
                        os.write(row.wkt.toString().getBytes())
                    })
                } else {
                    objectDao.streamObjectsGeometryById(os, cleanObjectId(id).toString(), 'wkt')
                }
            }
            os.flush()
        } catch (err) {
            log.error 'failed to get wkt for object: ' + id, err
            response.status = 400
            Map error = [error: 'failed to get wkt for object: ' + id + "(" + err +")"]
            render error as JSON
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }
    }

    def kml(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = null
        try {
            os = response.getOutputStream()
            response.setContentType("application/vnd.google-earth.kml+xml")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".kml\"")
            if (id.startsWith("ENVELOPE")) {
                streamEnvelope(os, id.replace("ENVELOPE", ""), 'kml')
            } else if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it).toString() }

                os.write(KML_HEADER
                        .replace("<name></name>", "<name><![CDATA[" + filename + "]]></name>")
                        .replace("<description></description>", "<description><![CDATA[" + ids.join(',') + "]]></description>").getBytes())

                String query = "select st_askml(st_collect(geom)) as kml from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                groovySql.eachRow(query, { row ->
                    os.write(row.kml.toString().getBytes())
                })

                os.write(KML_FOOTER.getBytes())
            } else {
                objectDao.streamObjectsGeometryById(os, cleanObjectId(id).toString(), 'kml')
            }
            os.flush()
        } catch (err) {
            log.error 'failed to get kml for object: ' + id, err
            response.status = 400
            Map error = [error: 'failed to get kml for object: ' + id + "(" + err +")"]
            render error as JSON
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }
    }

    def geojson(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = null
        try {
            os = response.getOutputStream()
            response.setContentType("application/json; subtype=geojson")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".geojson\"")
            if (id.startsWith("ENVELOPE")) {
                streamEnvelope(os, id.replace("ENVELOPE", ""), 'geojson')
            } else if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it).toString() }

                String query = "select st_asgeojson(st_collect(geom)) as geojson from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                groovySql.eachRow(query, { row ->
                    os.write(row.geojson.toString().getBytes())
                })
            } else {
                objectDao.streamObjectsGeometryById(os, cleanObjectId(id).toString(), 'geojson')
            }

            os.flush()
        } catch (err) {
            log.error 'failed to get geojson for object: ' + id, err
            response.status = 400
            Map error = [error: 'failed to get geojson for object: ' + id + "(" + err +")"]
            render error as JSON
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }
    }

    def shp(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = null
        try {
            os = response.getOutputStream()
            response.setContentType("application/zip")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".zip\"")
            if (id.startsWith("ENVELOPE")) {
                streamEnvelope(os, id.replace("ENVELOPE", ""), 'shp')
            } else if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it).toString() }

                String query = "select st_astext(st_collect(geom)) as wkt from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                String wkt = ""
                groovySql.eachRow(query, { row ->
                    wkt = row.wkt
                })

                File zippedShapeFile = SpatialConversionUtils.buildZippedShapeFile(wkt, 'area', filename, ids.join(','))
                FileUtils.copyFile(zippedShapeFile, os);
            } else {
                objectDao.streamObjectsGeometryById(os, cleanObjectId(id).toString(), 'shp')
            }
            os.flush()
        } catch (err) {
            log.error 'failed to get shapefile zip for object: ' + id, err
            response.status = 400
            Map error = [error: 'failed to get shapefile for object: ' + id + "(" + err +")"]
            render error as JSON
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }
    }

    def cleanObjectId(id) {
        id.replaceAll("[^a-zA-Z0-9]:", "")
    }

    private Map<String, Object> processGeoJSONRequest(json, Integer pid) {
        Map<String, Object> retMap = new HashMap<String, Object>()

        Map<String, Object> geojsonAsMap = (Map<String, Object>) json.geojson
        String wkt = null
        try {
            String geojsonString = new ObjectMapper().writeValueAsString(geojsonAsMap)
            GeometryJSON gJson = new GeometryJSON()
            Geometry geometry = gJson.read(new StringReader(geojsonString))

            wkt = fixWkt(geometry.toText())

            if (!isWKTValid(wkt)) {
                retMap.put("error", "Invalid geometry")
                return retMap
            }
        } catch (Exception ex) {
            log.error("Malformed GeoJSON geometry. Note that only GeoJSON geometries can be supplied here. Features and FeatureCollections cannot.", ex)
            retMap.put("error", "Malformed GeoJSON geometry. Note that only GeoJSON geometries can be supplied here. Features and FeatureCollections cannot.")
            return retMap
        }

        String name = json.name
        String description = json.description
        String user_id = json.user_id

        try {
            if (pid != null) {
                objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id)
                objectDao.updateObjectNames()
                retMap.put("updated", true)
            } else {
                String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id)
                objectDao.updateObjectNames()
                retMap.put("id", Integer.parseInt(generatedPid))
            }

        } catch (Exception ex) {
            log.error("Error uploading geojson", ex)
            retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
        }

        return retMap
    }

    // Create from geoJSON
    @RequirePermission
    def uploadGeojson(Integer id) {
        //id can be null
        processGeoJSONRequest(request.getJSON(), id)
    }

    private Map<String, Object> processWKTRequest(json, Integer pid, boolean namesearch) {
        Map<String, Object> retMap = new HashMap<String, Object>()

        String wkt = json.wkt
        String name = json.name
        String description = json.description
        String user_id = json.user_id
        try {
            wkt = fixWkt(wkt)

            if (!isWKTValid(wkt)) {
                retMap.put("error", "Invalid WKT")
                return retMap
            }

            if (pid != null) {
                objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id)
                objectDao.updateObjectNames()
                retMap.put("updated", true)
            } else {
                String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id, namesearch)
                objectDao.updateObjectNames()
                retMap.put("id", Integer.parseInt(generatedPid))
            }

        } catch (ParseException e) {
            log.error("Invalid WKT:", e.message)
            retMap.put("error", "Invalid WKT:" + e.message)
        } catch (Exception e) {
            log.error("Error in processing WKT", e.message)
            retMap.put("error", "Unexpected error: " + e.message)
        }

        return retMap
    }

    @RequirePermission
    def uploadWkt(Integer id) {
        def namesearch = params.containsKey('namesearch') ? params.namesearch.toString().toBoolean() : false

        //id can be null
        render processWKTRequest(request.JSON, id, namesearch) as JSON
    }

    @RequirePermission
    def uploadGeoJSON() throws Exception {
        render processGeoJSONRequest(request.JSON, null) as JSON
    }

    @RequirePermission
    def updateWithGeojson(Integer pid) {
        if (pid == null) {
            render status: 400, text: "Path parameter `pid` is not an integer."
            return
        }
        render processGeoJSONRequest(request.JSON, pid) as JSON
    }

    @RequirePermission
    def updateWithWKT(Integer pid) {
        if (pid == null) {
            render status: 400, text: "Path parameter `pid` is not an integer."
            return
        }
        def namesearch = params.containsKey('namesearch') ? params.namesearch.toString().toBoolean() : false
        render processWKTRequest(request.JSON, pid, namesearch) as JSON
    }

    @RequirePermission
    def uploadShapeFile() {
        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>()

        File tmpZipFile = File.createTempFile("shpUpload", ".zip")

        if (!ServletFileUpload.isMultipartContent(request)) {
            String jsonRequestBody = JsonOutput.toJson(request.getJSON())
            JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser()
            reqBodyParser.addParameter("user_id", String.class, false)
            reqBodyParser.addParameter("shp_file_url", String.class, false)
            reqBodyParser.addParameter("api_key", String.class, false)

            if (reqBodyParser.parseJSON(jsonRequestBody)) {

                String shpFileUrl = (String) reqBodyParser.getParsedValue("shp_file_url")
                apiKey = (String) reqBodyParser.getParsedValue("api_key")

                // Use shape file url from json body
                FileUtils.copyURLToFile(new URL(shpFileUrl), tmpZipFile)
                retMap.putAll(handleZippedShapeFile(tmpZipFile))
            } else {
                retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","))
            }

        } else {
            // Parse the request
            Map<String, MultipartFile> items = request.getFileMap()

            if (items.size() == 1) {
                MultipartFile fileItem = items.values()[0]
                IOUtils.copy(fileItem.getInputStream(), new FileOutputStream(tmpZipFile))
                retMap.putAll(handleZippedShapeFile(tmpZipFile))
            } else {
                retMap.put("error", "Multiple files sent in request. A single zipped shape file should be supplied.")
            }
        }

        render retMap as JSON
    }

    @RequirePermission
    def uploadKMLFile() {
        String userId = params.containsKey("user_id") ? params.user_id : null

        String name = params.containsKey("name") ? params.name : null
        String description = params.containsKey("description") ? params.description : null

        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>()

        // Parse the request
        Map<String, MultipartFile> items = request.getFileMap()

        if (items.size() == 1) {
            MultipartFile fileItem = items.values()[0]
            try {
                String kml = IOUtils.toString(fileItem.getInputStream())
                String wkt = SpatialUtils.getKMLPolygonAsWKT(kml)

                wkt = fixWkt(wkt)

                if (!isWKTValid(wkt)) {
                    retMap.put("error", "Invalid geometry")
                    return retMap
                } else {
                    String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, userId)
                    retMap.put("id", Integer.parseInt(generatedPid))
                }
            }catch (Exception e) {
                response.status = 400
                retMap.put("error", "KML parsing failure: " + e.message)
            }
        } else {
            response.status = 400
            retMap.put("error", "Multiple files sent in request. A single unzipped kml file should be supplied.")
        }

        render retMap as JSON
    }

    private Map<Object, Object> handleZippedShapeFile(File zippedShp) throws IOException {
        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>()

        Pair<String, File> idFilePair = SpatialConversionUtils.extractZippedShapeFile(zippedShp)
        String uploadedShpId = idFilePair.getLeft()
        File shpFile = idFilePair.getRight()

        retMap.put("shp_id", uploadedShpId)

        List<List<Pair<String, Object>>> manifestData = SpatialConversionUtils.getShapeFileManifest(shpFile)

        int featureIndex = 0
        for (List<Pair<String, Object>> featureData : manifestData) {
            // Use linked hash map to maintain key ordering
            Map<String, Object> featureDataMap = new LinkedHashMap<String, Object>()

            for (Pair<String, Object> fieldData : featureData) {
                featureDataMap.put(fieldData.getLeft(), fieldData.getRight())
            }

            retMap.put(featureIndex, featureDataMap)

            featureIndex++
        }

        return retMap
    }
    private Map<String, Object> processShapeFileFeatureRequest(JSONObject json, Integer pid, String shapeFileId, String featureIndex) {
        processShapeFileFeatureRequest( JsonOutput.toJson(json), pid, shapeFileId, featureIndex)
    }

    private Map<String, Object> processShapeFileFeatureRequest(String json, Integer pid, String shapeFileId, String featureIndex) {
        Map<String, Object> retMap = new HashMap<String, Object>()

        JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser()
        reqBodyParser.addParameter("name", String.class, false)
        reqBodyParser.addParameter("description", String.class, false)
        reqBodyParser.addParameter("user_id", String.class, false)

        if (reqBodyParser.parseJSON(json)) {

            String name = (String) reqBodyParser.getParsedValue("name")
            String description = (String) reqBodyParser.getParsedValue("description")
            String user_id = (String) reqBodyParser.getParsedValue("user_id")

            try {
                File shpFileDir = new File(System.getProperty("java.io.tmpdir"), shapeFileId)

                String wkt = SpatialUtils.getShapeFileFeaturesAsWkt(shpFileDir, featureIndex)

                wkt = fixWkt(wkt)

                if (wkt == null || !isWKTValid(wkt)) {
                    retMap.put("error", "Invalid geometry")
                    return retMap
                }

                if (pid != null) {
                    objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id)
                    retMap.put("updated", true)
                } else {
                    String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id)
                    retMap.put("id", Integer.parseInt(generatedPid))
                }

            } catch (Exception ex) {
                log.error("Error processsing shapefile feature request", ex)
                retMap.put("error", ex.getMessage())
            }
        } else {
            retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","))
        }

        return retMap
    }

    def shapeImage(String shapeId, String featureIndexes) {
        try {
            File shpFileDir = new File(System.getProperty("java.io.tmpdir"), shapeId)

            BufferedImage bi = SpatialUtils.getShapeFileFeaturesAsImage(shpFileDir, featureIndexes,
                    (params?.width ?: 640) as Integer, (params?.height ?: 480) as Integer)

            response.setContentType("image/png")

            ImageIO.write(bi, "png", response.outputStream)
            response.outputStream.flush()

        } catch (Exception ex) {
            log.error("Error processsing shapefile feature request", ex)

            render status: 404
        }
    }

    /**
     * Process features in a shape file.
     *
     * Clint should post featureIndex via body, not queryString to avoid possible oversize url
     *
     * @param shapeId
     * @param featureIndex
     * @return
     */
    @RequirePermission
    def saveFeatureFromShapeFile(String shapeId, String featureIndex) {
        JSONObject json = request.getJSON()
        if ( !featureIndex) {
            if (json["featureIdx"]) {
                featureIndex = json["featureIdx"]
            } else {
                render status: 400, text: "Feature Index is not provided"
            }
        }
        render processShapeFileFeatureRequest(json, null, shapeId, featureIndex) as JSON
    }

    @RequirePermission
    def updateFromShapeFileFeature(Integer objectPid, String shapeId, String featureIndex) throws Exception {
        if (objectPid == null) {
            render status: 400, text: "Path parameter `objectPid` is not an integer."
            return
        }
        render processShapeFileFeatureRequest(request.getJSON(), objectPid, shapeId, featureIndex) as JSON
    }
    @RequirePermission
    def createPointRadius(Double latitude, Double longitude, Double radius) {
        if (latitude == null) {
            render status: 400, text: "Path parameter `latitude` is not a number."
            return
        }
        if (longitude == null) {
            render status: 400, text: "Path parameter `longitude` is not a number."
            return
        }
        if (radius == null) {
            render status: 400, text: "Path parameter `radius` is not a number."
            return
        }
        render processPointRadiusRequest(request.json, null, latitude, longitude, radius) as JSON
    }
    @RequirePermission
    def updateWithPointRadius(Double latitude, Double longitude, Double radius, Integer objectPid) {
        if (latitude == null) {
            render status: 400, text: "Path parameter `latitude` is not a number."
            return
        }
        if (longitude == null) {
            render status: 400, text: "Path parameter `longitude` is not a number."
            return
        }
        if (radius == null) {
            render status: 400, text: "Path parameter `radius` is not a number."
            return
        }
        render processPointRadiusRequest(request.json, objectPid, latitude, longitude, radius) as JSON
    }

    private Map<String, Object> processPointRadiusRequest(String json, Integer pid, double latitude, double longitude, double radiusKm) {
        Map<String, Object> retMap = new HashMap<String, Object>()

        JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser()
        reqBodyParser.addParameter("name", String.class, false)
        reqBodyParser.addParameter("description", String.class, false)
        reqBodyParser.addParameter("user_id", String.class, false)
        reqBodyParser.addParameter("api_key", String.class, false)

        if (reqBodyParser.parseJSON(json)) {

            String name = (String) reqBodyParser.getParsedValue("name")
            String description = (String) reqBodyParser.getParsedValue("description")
            String user_id = (String) reqBodyParser.getParsedValue("user_id")

            try {
                String wkt = SpatialConversionUtils.createCircleJs(longitude, latitude, radiusKm * 1000)
                if (pid == null) {
                    String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id)
                    retMap.put("id", Integer.parseInt(generatedPid))
                } else {
                    objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id)
                    retMap.put("updated", true)
                }

            } catch (Exception ex) {
                ex.printStackTrace()
            }
        } else {
            retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","))
        }
        return retMap
    }

    @RequirePermission
    def deleteShape(Integer pid) {
        if (pid == null) {
            render status: 400, text: "Path parameter `pid` is not an integer."
            return
        }
        if (request.method == "DELETE") {
            Map<String, Object> retMap = new HashMap<String, Object>()
            try {
                boolean success = objectDao.deleteUserUploadedObject(pid)
                retMap.put("success", success)
            } catch (Exception ex) {
                log.error("Error deleting shape " + pid, ex)
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
            }
            render retMap as JSON
        }
    }

    @RequirePermission
    def poi() {
        Map<String, Object> retMap = new HashMap<String, Object>()

        JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser()
        reqBodyParser.addParameter("object_id", String.class, true)
        reqBodyParser.addParameter("name", String.class, false)
        reqBodyParser.addParameter("type", String.class, false)
        reqBodyParser.addParameter("latitude", Double.class, false)
        reqBodyParser.addParameter("longitude", Double.class, false)
        reqBodyParser.addParameter("bearing", Double.class, true)
        reqBodyParser.addParameter("user_id", String.class, false)
        reqBodyParser.addParameter("description", String.class, true)
        reqBodyParser.addParameter("focal_length", Double.class, true)
        reqBodyParser.addParameter("api_key", String.class, false)

        if (reqBodyParser.parseJSON(request.json)) {

            String object_id = (String) reqBodyParser.getParsedValue("object_id")
            String name = (String) reqBodyParser.getParsedValue("name")
            String type = (String) reqBodyParser.getParsedValue("type")
            Double latitude = (Double) reqBodyParser.getParsedValue("latitude")
            Double longitude = (Double) reqBodyParser.getParsedValue("longitude")
            Double bearing = (Double) reqBodyParser.getParsedValue("bearing")
            String user_id = (String) reqBodyParser.getParsedValue("user_id")
            String description = (String) reqBodyParser.getParsedValue("description")
            Double focal_length = (Double) reqBodyParser.getParsedValue("focal_length")

            try {
                int id = objectDao.createPointOfInterest(object_id, name, type, latitude, longitude, bearing, user_id, description, focal_length)
                retMap.put("id", id)
            } catch (Exception ex) {
                log.error("Error creating point of interest", ex)
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
            }
        } else {
            retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","))
        }

        return retMap
    }

    @RequirePermission
    def poiRequest(Integer id) {
        if (id == null) {
            render status: 400, text: "Path parameter `id` is not an integer."
            return
        }
        if (request.method == "DELETE") {
            Map<String, Object> retMap = new HashMap<String, Object>()

            try {
                boolean success = objectDao.deletePointOfInterest(id)
                retMap.put("deleted", success)
            } catch (Exception ex) {
                log.error("Error uploading point of interest " + id, ex)
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
            }
            render retMap as JSON
        } else if (request.method == "POST") {
            Map<String, Object> retMap = new HashMap<String, Object>()

            JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser()
            reqBodyParser.addParameter("object_id", String.class, true)
            reqBodyParser.addParameter("name", String.class, false)
            reqBodyParser.addParameter("type", String.class, false)
            reqBodyParser.addParameter("latitude", Double.class, false)
            reqBodyParser.addParameter("longitude", Double.class, false)
            reqBodyParser.addParameter("bearing", Double.class, true)
            reqBodyParser.addParameter("user_id", String.class, true)
            reqBodyParser.addParameter("description", String.class, true)
            reqBodyParser.addParameter("focal_length", Double.class, true)

            if (reqBodyParser.parseJSON(request.JSON as String)) {

                String object_id = (String) reqBodyParser.getParsedValue("object_id")
                String name = (String) reqBodyParser.getParsedValue("name")
                String type = (String) reqBodyParser.getParsedValue("type")
                Double latitude = (Double) reqBodyParser.getParsedValue("latitude")
                Double longitude = (Double) reqBodyParser.getParsedValue("longitude")
                Double bearing = (Double) reqBodyParser.getParsedValue("bearing")
                String user_id = (String) reqBodyParser.getParsedValue("user_id")
                String description = (String) reqBodyParser.getParsedValue("description")
                Double focal_length = (Double) reqBodyParser.getParsedValue("focal_length")

                try {
                    boolean updateSuccessful = objectDao.updatePointOfInterest(id, object_id, name, type, latitude, longitude, bearing, user_id, description, focal_length)
                    retMap.put("updated", updateSuccessful)
                } catch (Exception ex) {
                    log.error("Error updating point of interest " + id, ex)
                    retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
                }
            } else {
                retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","))
            }

            render retMap as JSON
        } else if (request.method == "GET") {
            Map<String, Object> retMap = new HashMap<String, Object>()
            try {
                return objectDao.getPointOfInterestDetails(id)
            } catch (IllegalArgumentException ex) {
                log.trace(ex.getMessage(), ex)
                retMap.put("error", "Invalid point of interest id " + id)
            } catch (Exception ex) {
                log.trace(ex.getMessage(), ex)
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
            }
            render retMap as JSON
        }
    }

    private String fixWkt(String wkt) {
        // only attempt to fix POLYGON and MULTIPOLYGON
        if (wkt.startsWith("POLYGON") || wkt.startsWith("MULTIPOLYGON")) {
            try {
                WKTReader wktReader = new WKTReader()
                Geometry geom = wktReader.read(wkt.toString())

                // Use CCW for exterior rings. Normalizing will use the JTS default (CW). Reverse makes it CCW.
                Geometry validGeom = GeomMakeValid.makeValid(geom)
                validGeom.normalize()
                wkt = validGeom.reverse().toText()
            } catch (Exception e) {
                throw new ParseException(e.getMessage())
            }
        }
        return wkt
    }

    private boolean isWKTValid(String wkt) {
        // only validate POLYGON and MULTIPOLYGON
        if (wkt.startsWith("POLYGON") || wkt.startsWith("MULTIPOLYGON")) {
            WKTReader wktReader = new WKTReader()
            Geometry geom = wktReader.read(wkt.toString())
            return geom.isValid()
        }
        return true
    }

    private String makeValidFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\(\\)\\[\\]\\-]", "_")
    }

    private void streamEnvelope(OutputStream os, String envelopeTaskId, String type) {
        String filePrefix = grailsApplication.config.data.dir + "/public/" + envelopeTaskId + "/envelope"

        if (type == 'shp') {
            def file = new File(filePrefix + "-shp.zip")
            if (!file.exists()) {
                ZipUtil.zip(filePrefix + "-shp.zip", (String[]) [filePrefix + ".shp", filePrefix + ".shx", filePrefix + ".dbf", filePrefix + ".fix"])
            }
            InputStream is;
            try {
                is = FileUtils.openInputStream(new File(filePrefix + "-shp.zip"))

                int len;
                byte[] bytes = new byte[1024];
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
                Geometry g = f.getDefaultGeometry()

                if (type == 'wkt') {
                    os.write(g.toText().bytes)
                } else if (type == 'geojson') {
                    GeometryJSON geojson = new GeometryJSON()
                    geojson.write(g, os)
                } else {
                    // kml
                    DefaultFeatureCollection collection = new DefaultFeatureCollection()
                    SimpleFeatureType sft = DataUtilities.createType("location", "geom:Geometry,name:String")
                    collection.add(SimpleFeatureBuilder.build(sft, (List<Object>) [g, "envelope"], null))

                    Encoder encoder = new Encoder(new KMLConfiguration())

                    encoder.encode(collection, KML.kml, os)
                }
            }

            sds.dispose()
        }
    }

}

