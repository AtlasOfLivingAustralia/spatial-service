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

import au.org.ala.layers.intersect.IntersectConfig
import au.org.ala.layers.util.SpatialConversionUtils
import au.org.ala.spatial.slave.SpatialUtils
import au.org.ala.spatial.util.JSONRequestBodyParser
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.ParseException
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.jackson.map.ObjectMapper
import org.geotools.geojson.geom.GeometryJSON
import org.springframework.dao.DataAccessException
import org.springframework.web.multipart.MultipartFile

import java.text.MessageFormat

class ShapesController {

    def objectDao

    def wkt(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = null
        try {
            os = response.getOutputStream()
            response.setContentType("application/wkt")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".wkt\"");
            objectDao.streamObjectsGeometryById(os, cleanObjectId(id), 'wkt')
            os.flush()
        } catch (err) {
            log.error 'failed to get wkt for object: ' + id, err
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
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
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".kml\"");
            objectDao.streamObjectsGeometryById(os, cleanObjectId(id), 'kml')
            os.flush()
        } catch (err) {
            log.error 'failed to get kml for object: ' + id, err
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
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
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".geojson\"");
            objectDao.streamObjectsGeometryById(os, cleanObjectId(id), 'geojson')
            os.flush()
        } catch (err) {
            log.error 'failed to get geojson for object: ' + id, err
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
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
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".zip\"");
            objectDao.streamObjectsGeometryById(os, cleanObjectId(id), 'shp')
            os.flush()
        } catch (err) {
            log.error 'failed to get shapefile zip for object: ' + id, err
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                }
            }
        }
    }

    def cleanObjectId(id) {
        id.replaceAll("[^a-zA-Z0-9]:", "")
    }

    private Map<String, Object> processGeoJSONRequest(json, Integer pid) {
        Map<String, Object> retMap = new HashMap<String, Object>();

        String wkt = null;
        Map<String, Object> geojsonAsMap = (Map<String, Object>) json.geojson;
        try {
            String geojsonString = new ObjectMapper().writeValueAsString(geojsonAsMap);
            GeometryJSON gJson = new GeometryJSON();
            Geometry geometry = gJson.read(new StringReader(geojsonString));

            if (!geometry.isValid()) {
                retMap.put("error", "Invalid geometry");
            }

            wkt = geometry.toText();
        } catch (Exception ex) {
            logger.error("Malformed GeoJSON geometry. Note that only GeoJSON geometries can be supplied here. Features and FeatureCollections cannot.", ex);
            retMap.put("error", "Malformed GeoJSON geometry. Note that only GeoJSON geometries can be supplied here. Features and FeatureCollections cannot.");
            return retMap;
        }

        String name = json.name
        String description = json.description
        String user_id = json.user_id
        String api_key = json.api_key

        if (false && !checkAPIKey(api_key, null)) {
            retMap.put("error", "Invalid user ID or API key");
            return retMap;
        }

        try {
            if (pid != null) {
                objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id);
                objectDao.updateObjectNames();
                retMap.put("updated", true);
            } else {
                String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id);
                objectDao.updateObjectNames();
                retMap.put("id", Integer.parseInt(generatedPid));
            }

        } catch (Exception ex) {
            logger.error("Error uploading geojson", ex);
            retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
        }

        return retMap;
    }

    // Create from geoJSON
    def uploadGeojson(String id) {
        //id can be null
        processGeoJSONRequest(request.getJSON(), id)
    }

    private Map<String, Object> processWKTRequest(json, Integer pid, boolean namesearch) {
        Map<String, Object> retMap = new HashMap<String, Object>();

        String wkt = json.wkt
        String name = json.name
        String description = json.description
        String user_id = json.user_id
        String api_key = json.api_key

        if (false && !checkAPIKey(api_key, null)) {
            retMap.put("error", "Invalid user ID or API key");
            return retMap;
        }

        if (!isWKTValid(wkt)) {
            retMap.put("error", "Invalid WKT");
        }

        try {
            if (pid != null) {
                objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id);
                objectDao.updateObjectNames();
                retMap.put("updated", true);
            } else {
                String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id, namesearch);
                objectDao.updateObjectNames();
                retMap.put("id", Integer.parseInt(generatedPid));
            }

        } catch (DataAccessException ex) {
            log.error("Malformed WKT.", ex);
            retMap.put("error", "Malformed WKT.");
        } catch (Exception ex) {
            log.error("Error uploading WKT", ex);
            retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
        }

        return retMap;
    }

    def uploadWkt(String id) {
        def namesearch = params.containsKey('namesearch') ? params.namesearch : false

        //id can be null
        render processWKTRequest(request.JSON, id, namesearch) as JSON
    }

    def uploadGeoJSON() throws Exception {
        render processGeoJSONRequest(request.JSON, null) as JSON
    }

    def updateWithGeojson(Integer pid) {
        render processGeoJSONRequest(request.JSON, pid) as JSON
    }

    def updateWithWKT(Integer pid) {
        render processWKTRequest(request.JSON, pid, params.containsKey('namesearch') ? params.namesearch : "true") as JSON
    }

    def uploadShapeFile() {
        String userId = params.containsKey("user_id") ? params.user_id : null;
        String apiKey = params.containsKey("api_key") ? params.api_key : null;

        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>();

        File tmpZipFile = File.createTempFile("shpUpload", ".zip");

        if (!ServletFileUpload.isMultipartContent(request)) {
            String jsonRequestBody = IOUtils.toString(request.reader);

            JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser();
            reqBodyParser.addParameter("user_id", String.class, false);
            reqBodyParser.addParameter("shp_file_url", String.class, false);
            reqBodyParser.addParameter("api_key", String.class, false);

            if (reqBodyParser.parseJSON(jsonRequestBody)) {

                String shpFileUrl = (String) reqBodyParser.getParsedValue("shp_file_url");
                userId = (String) reqBodyParser.getParsedValue("user_id");
                apiKey = (String) reqBodyParser.getParsedValue("api_key");

                if (!checkAPIKey(apiKey, userId)) {
                    retMap.put("error", "Invalid user ID or API key");
                    return retMap;
                }

                // Use shape file url from json body
                IOUtils.copy(new URL(shpFileUrl).openStream(), new FileOutputStream(tmpZipFile));
                retMap.putAll(handleZippedShapeFile(tmpZipFile));
            } else {
                retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","));
            }

        } else {
            if (false && !checkAPIKey(apiKey, userId)) {
                retMap.put("error", "Invalid user ID or API key");
                return retMap;
            }

            // Parse the request
            Map<String, MultipartFile> items = request.getFileMap()

            if (items.size() == 1) {
                MultipartFile fileItem = items.values().getAt(0);
                IOUtils.copy(fileItem.getInputStream(), new FileOutputStream(tmpZipFile));
                retMap.putAll(handleZippedShapeFile(tmpZipFile));
            } else {
                retMap.put("error", "Multiple files sent in request. A single zipped shape file should be supplied.");
            }
        }

        render retMap as JSON
    }

    def uploadKMLFile() {
        String userId = params.containsKey("user_id") ? params.user_id : null;
        String apiKey = params.containsKey("api_key") ? params.api_key : null;
        String name = params.containsKey("name") ? params.name : null;
        String description = params.containsKey("description") ? params.api_key : null;

        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>();

        if (false && !checkAPIKey(apiKey, userId)) {
            retMap.put("error", "Invalid user ID or API key");
            return retMap;
        }

        // Parse the request
        Map<String, MultipartFile> items = request.getFileMap()

        if (items.size() == 1) {
            MultipartFile fileItem = items.values().getAt(0);

            String kml = IOUtils.toString(fileItem.getInputStream())
            String wkt = SpatialUtils.getKMLPolygonAsWKT(kml)

            if (!isWKTValid(wkt)) {
                retMap.put("error", "Invalid geometry");
            } else {
                String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, userId);
                retMap.put("id", Integer.parseInt(generatedPid));
            }
        } else {
            retMap.put("error", "Multiple files sent in request. A single unzipped kml file should be supplied.");
        }

        render retMap as JSON
    }

    private Map<Object, Object> handleZippedShapeFile(File zippedShp) throws IOException {
        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>();

        Pair<String, File> idFilePair = SpatialConversionUtils.extractZippedShapeFile(zippedShp);
        String uploadedShpId = idFilePair.getLeft();
        File shpFile = idFilePair.getRight();

        retMap.put("shp_id", uploadedShpId);

        List<List<Pair<String, Object>>> manifestData = SpatialConversionUtils.getShapeFileManifest(shpFile);

        int featureIndex = 0;
        for (List<Pair<String, Object>> featureData : manifestData) {
            // Use linked hash map to maintain key ordering
            Map<String, Object> featureDataMap = new LinkedHashMap<String, Object>();

            for (Pair<String, Object> fieldData : featureData) {
                featureDataMap.put(fieldData.getLeft(), fieldData.getRight());
            }

            retMap.put(featureIndex, featureDataMap);

            featureIndex++;
        }

        return retMap;
    }

    private Map<String, Object> processShapeFileFeatureRequest(String json, Integer pid, String shapeFileId, int featureIndex) {
        Map<String, Object> retMap = new HashMap<String, Object>();

        JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser();
        reqBodyParser.addParameter("name", String.class, false);
        reqBodyParser.addParameter("description", String.class, false);
        reqBodyParser.addParameter("user_id", String.class, false);
        reqBodyParser.addParameter("api_key", String.class, false);

        if (reqBodyParser.parseJSON(json)) {

            String name = (String) reqBodyParser.getParsedValue("name");
            String description = (String) reqBodyParser.getParsedValue("description");
            String user_id = (String) reqBodyParser.getParsedValue("user_id");
            String api_key = (String) reqBodyParser.getParsedValue("api_key");

            if (false && !checkAPIKey(api_key, user_id)) {
                retMap.put("error", "Invalid user ID or API key");
                return retMap;
            }

            try {
                File shpFileDir = new File(System.getProperty("java.io.tmpdir"), shapeFileId);

                String wkt = SpatialConversionUtils.getShapeFileFeatureAsWKT(shpFileDir, featureIndex);

                if (!isWKTValid(wkt)) {
                    retMap.put("error", "Invalid geometry");
                }

                if (pid != null) {
                    objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id);
                    retMap.put("updated", true);
                } else {
                    String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id);
                    retMap.put("id", Integer.parseInt(generatedPid));
                }

            } catch (Exception ex) {
                log.error("Error processsing shapefile feature request", ex);
                retMap.put("error", ex.getMessage());
            }
        } else {
            retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","));
        }

        return retMap;
    }

    def saveFeatureFromShapeFile(String shapeId, Integer featureIndex) {
        render processShapeFileFeatureRequest(request.reader.text, null, shapeId, featureIndex) as JSON
    }

    def updateFromShapeFileFeature(Integer objectPid, String shapeId, Integer featureIndex) throws Exception {
        render processShapeFileFeatureRequest(request.reader.text, objectPid, shapeId, featureIndex) as JSON
    }

    def createPointRadius(Double latitude, Double longitude, Double radius) {
        render processPointRadiusRequest(request.reader.text, null, latitude, longitude, radius) as JSON
    }

    def updateWithPointRadius(Double latitude, Double longitude, Double radius, Integer objectPid) {
        render processPointRadiusRequest(request.reader.text, objectPid, latitude, longitude, radius) as JSON
    }

    private Map<String, Object> processPointRadiusRequest(String json, Integer pid, double latitude, double longitude, double radiusKm) {
        Map<String, Object> retMap = new HashMap<String, Object>();

        JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser();
        reqBodyParser.addParameter("name", String.class, false);
        reqBodyParser.addParameter("description", String.class, false);
        reqBodyParser.addParameter("user_id", String.class, false);
        reqBodyParser.addParameter("api_key", String.class, false);

        if (reqBodyParser.parseJSON(json)) {

            String name = (String) reqBodyParser.getParsedValue("name");
            String description = (String) reqBodyParser.getParsedValue("description");
            String user_id = (String) reqBodyParser.getParsedValue("user_id");
            String api_key = (String) reqBodyParser.getParsedValue("api_key");

            if (!checkAPIKey(api_key, user_id)) {
                retMap.put("error", "Invalid user ID or API key");
                return retMap;
            }

            try {
                String wkt = SpatialConversionUtils.createCircleJs(longitude, latitude, radiusKm * 1000);
                if (pid == null) {
                    String generatedPid = objectDao.createUserUploadedObject(wkt, name, description, user_id);
                    retMap.put("id", Integer.parseInt(generatedPid));
                } else {
                    objectDao.updateUserUploadedObject(pid, wkt, name, description, user_id);
                    retMap.put("updated", true);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","));
        }
        return retMap;
    }

    private boolean checkAPIKey(String apiKey, String userId) {
        if (IntersectConfig.getApiKeyCheckUrlTemplate() == null || IntersectConfig.getApiKeyCheckUrlTemplate().isEmpty()) {
            return true;
        }

        try {
            HttpClient httpClient = new HttpClient();
            GetMethod get = new GetMethod(MessageFormat.format(IntersectConfig.getApiKeyCheckUrlTemplate(), apiKey));

            int returnCode = httpClient.executeMethod(get);
            if (returnCode != 200) {
                throw new RuntimeException("Error occurred checking api key");
            }

            String responseText = get.getResponseBodyAsString();

            ObjectMapper mapper = new ObjectMapper();
            Map parsedJSON = mapper.readValue(responseText, Map.class);

            return (Boolean) parsedJSON.get("valid");
/*
            if (valid) {
                String keyUserId = (String) parsedJSON.get("userId");
                String app = (String) parsedJSON.get("app");

                if (!keyUserId.equals(userId)) {
                    return false;
                }

                if (!app.equals(IntersectConfig.getSpatialPortalAppName())) {
                    return false;
                }

                return true;
            } else {
                return false;
            }
*/
        } catch (Exception ex) {
            throw new RuntimeException("Error checking API key");
        }
    }

    def deleteShape(Integer pid) {
        if (request.method.equals("DELETE")) {
            Map<String, Object> retMap = new HashMap<String, Object>();
            try {
                boolean success = objectDao.deleteUserUploadedObject(pid);
                retMap.put("success", success);
            } catch (Exception ex) {
                logger.error("Error deleting shape " + pid, ex);
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
            }
            render retMap as JSON
        }
    }

    def poi() {
        Map<String, Object> retMap = new HashMap<String, Object>();

        JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser();
        reqBodyParser.addParameter("object_id", String.class, true);
        reqBodyParser.addParameter("name", String.class, false);
        reqBodyParser.addParameter("type", String.class, false);
        reqBodyParser.addParameter("latitude", Double.class, false);
        reqBodyParser.addParameter("longitude", Double.class, false);
        reqBodyParser.addParameter("bearing", Double.class, true);
        reqBodyParser.addParameter("user_id", String.class, false);
        reqBodyParser.addParameter("description", String.class, true);
        reqBodyParser.addParameter("focal_length", Double.class, true);
        reqBodyParser.addParameter("api_key", String.class, false);

        if (reqBodyParser.parseJSON(request.reader.text)) {

            String object_id = (String) reqBodyParser.getParsedValue("object_id");
            String name = (String) reqBodyParser.getParsedValue("name");
            String type = (String) reqBodyParser.getParsedValue("type");
            Double latitude = (Double) reqBodyParser.getParsedValue("latitude");
            Double longitude = (Double) reqBodyParser.getParsedValue("longitude");
            Double bearing = (Double) reqBodyParser.getParsedValue("bearing");
            String user_id = (String) reqBodyParser.getParsedValue("user_id");
            String description = (String) reqBodyParser.getParsedValue("description");
            Double focal_length = (Double) reqBodyParser.getParsedValue("focal_length");
            String api_key = (String) reqBodyParser.getParsedValue("api_key");

            if (!checkAPIKey(api_key, user_id)) {
                retMap.put("error", "Invalid user ID or API key");
                return retMap;
            }
            try {
                int id = objectDao.createPointOfInterest(object_id, name, type, latitude, longitude, bearing, user_id, description, focal_length);
                retMap.put("id", id);
            } catch (Exception ex) {
                logger.error("Error creating point of interest", ex);
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
            }
        } else {
            retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","));
        }

        return retMap;
    }

    def poiRequest(Integer id) {
        if (request.method.equals("DELETE")) {
            String userId = params.containsKey("user_id") ? params.user_id : null;
            String apiKey = params.containsKey("api_key") ? params.api_key : null;

            Map<String, Object> retMap = new HashMap<String, Object>();
            if (!checkAPIKey(apiKey, userId)) {
                retMap.put("error", "Invalid user ID or API key");
                render retMap as JSON
            }

            try {
                boolean success = objectDao.deletePointOfInterest(id);
                retMap.put("deleted", success);
            } catch (Exception ex) {
                logger.error("Error uploading point of interest " + id, ex);
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
            }
            render retMap as JSON
        } else if (request.method.equals("POST")) {
            Map<String, Object> retMap = new HashMap<String, Object>();

            JSONRequestBodyParser reqBodyParser = new JSONRequestBodyParser();
            reqBodyParser.addParameter("object_id", String.class, true);
            reqBodyParser.addParameter("name", String.class, false);
            reqBodyParser.addParameter("type", String.class, false);
            reqBodyParser.addParameter("latitude", Double.class, false);
            reqBodyParser.addParameter("longitude", Double.class, false);
            reqBodyParser.addParameter("bearing", Double.class, true);
            reqBodyParser.addParameter("user_id", String.class, true);
            reqBodyParser.addParameter("description", String.class, true);
            reqBodyParser.addParameter("focal_length", Double.class, true);
            reqBodyParser.addParameter("api_key", String.class, false);

            if (reqBodyParser.parseJSON(json)) {

                String object_id = (String) reqBodyParser.getParsedValue("object_id");
                String name = (String) reqBodyParser.getParsedValue("name");
                String type = (String) reqBodyParser.getParsedValue("type");
                Double latitude = (Double) reqBodyParser.getParsedValue("latitude");
                Double longitude = (Double) reqBodyParser.getParsedValue("longitude");
                Double bearing = (Double) reqBodyParser.getParsedValue("bearing");
                String user_id = (String) reqBodyParser.getParsedValue("user_id");
                String description = (String) reqBodyParser.getParsedValue("description");
                Double focal_length = (Double) reqBodyParser.getParsedValue("focal_length");
                String api_key = (String) reqBodyParser.getParsedValue("api_key");

                if (!checkAPIKey(api_key, user_id)) {
                    retMap.put("error", "Invalid user ID or API key");
                    return retMap;
                }
                try {
                    boolean updateSuccessful = objectDao.updatePointOfInterest(id, object_id, name, type, latitude, longitude, bearing, user_id, description, focal_length);
                    retMap.put("updated", updateSuccessful);
                } catch (Exception ex) {
                    logger.error("Error updating point of interest " + id, ex);
                    retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
                }
            } else {
                retMap.put("error", StringUtils.join(reqBodyParser.getErrorMessages(), ","));
            }

            render retMap as JSON
        } else if (request.method.equals("GET")) {
            Map<String, Object> retMap = new HashMap<String, Object>();
            try {
                return objectDao.getPointOfInterestDetails(id);
            } catch (IllegalArgumentException ex) {
                retMap.put("error", "Invalid point of interest id " + id);
            } catch (Exception ex) {
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.");
            }
            render retMap as JSON
        }
    }

    private boolean isWKTValid(String wkt) {
        WKTReader wktReader = new WKTReader();
        try {
            Geometry geom = wktReader.read(wkt.toString());
            return geom.isValid();
        } catch (ParseException ex) {
            return false;
        }
    }

    private String makeValidFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\(\\)\\[\\]\\-]", "_")
    }

}
