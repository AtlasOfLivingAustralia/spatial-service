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

package au.org.ala.spatial

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.plugins.openapi.Path
import au.org.ala.spatial.util.GeomMakeValid
import au.org.ala.spatial.util.JSONRequestBodyParser
import au.org.ala.spatial.util.SpatialConversionUtils
import au.org.ala.spatial.util.SpatialUtils
import grails.converters.JSON
import groovy.json.JsonOutput
import groovy.sql.GroovyResultSet
import groovy.sql.Sql
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import org.codehaus.jackson.map.ObjectMapper
import org.geotools.geojson.geom.GeometryJSON
import org.grails.web.json.JSONObject
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader
import org.springframework.web.multipart.MultipartFile

import javax.imageio.ImageIO
import javax.ws.rs.Produces
import java.awt.image.BufferedImage

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class ShapesController {

    SpatialObjectsService spatialObjectsService

    SpatialConfig spatialConfig
    Sql groovySql

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

    @Operation(
            method = "GET",
            tags = "object",
            operationId = "getWKT",
            summary = "Get WKT for an object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "WKT",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "plain/text",
                                            schema = @Schema(implementation = Distributions)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shapes/wkt/{pid}")
    @Produces("plain/text")
    def wkt(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = response.getOutputStream()
        try {
            response.setContentType("application/wkt")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".wkt\"")

            spatialObjectsService.wkt(id, os)

            os.flush()
        } catch (err) {
            log.error 'failed to get wkt for object: ' + id, err
            response.status = 400
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

    @Operation(
            method = "GET",
            tags = "object",
            operationId = "getKML",
            summary = "Get KML for an object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "KML",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "plain/text",
                                            schema = @Schema(implementation = Distributions)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shapes/kml/{pid}")
    @Produces("plain/text")
    def kml(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = response.getOutputStream()
        try {
            response.setContentType("application/vnd.google-earth.kml+xml")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".kml\"")
            if (id.startsWith("ENVELOPE")) {
                spatialObjectsService.streamEnvelope(os, id.replace("ENVELOPE", ""), 'kml')
            } else if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it) }

                os.write(KML_HEADER
                        .replace("<name></name>", "<name><![CDATA[" + filename + "]]></name>")
                        .replace("<description></description>", "<description><![CDATA[" + ids.join(',') + "]]></description>").bytes)

                String query = "select st_askml(st_collect(geom)) as kml from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                groovySql.eachRow(query, { GroovyResultSet row ->
                    os.write(row.getObject('kml').toString().bytes)
                })

                os.write(KML_FOOTER.bytes)
            } else {
                spatialObjectsService.streamObjectsGeometryById(os, cleanObjectId(id), 'kml')
            }
            os.flush()
        } catch (err) {
            log.error 'failed to get kml for object: ' + id, err
            response.status = 400
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

    @Operation(
            method = "GET",
            tags = "object",
            operationId = "getGeoJSON",
            summary = "Get GeoJSON for an object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "GeoJSON",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "plain/text",
                                            schema = @Schema(implementation = Distributions)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shapes/geojson/{pid}")
    @Produces("plain/text")
    def geojson(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = response.getOutputStream()
        try {
            response.setContentType("application/json; subtype=geojson")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".geojson\"")
            if (id.startsWith("ENVELOPE")) {
                spatialObjectsService.streamEnvelope(os, id.replace("ENVELOPE", ""), 'geojson')
            } else if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it) }

                String query = "select st_asgeojson(st_collect(geom)) as geojson from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                groovySql.eachRow(query, { GroovyResultSet row ->
                    os.write(row.getObject('geojson').toString().bytes)
                })
            } else {
                spatialObjectsService.streamObjectsGeometryById(os, cleanObjectId(id), 'geojson')
            }

            os.flush()
        } catch (err) {
            log.error 'failed to get geojson for object: ' + id, err
            response.status = 400
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

    @Operation(
            method = "GET",
            tags = "object",
            operationId = "getShapefile",
            summary = "Get zipped shapefile for an object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Zipped Shapefile",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/zip",
                                            schema = @Schema(implementation = Distributions)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shapes/shp/{pid}")
    @Produces("application/zip")
    def shp(String id) {
        String filename = params?.filename ?: id
        filename = makeValidFilename(filename)
        OutputStream os = response.getOutputStream()
        try {
            response.setContentType("application/zip")
            response.setHeader("Content-Disposition", "filename=\"" + filename + ".zip\"")
            if (id.startsWith("ENVELOPE")) {
                spatialObjectsService.streamEnvelope(os, id.replace("ENVELOPE", ""), 'shp')
            } else if (id.contains('~')) {
                List ids = id.split('~').collect { cleanObjectId(it) }

                String query = "select st_astext(st_collect(geom)) as wkt from (select (st_dump(the_geom)).geom as geom from objects where pid in ('" + ids.join("','") + "')) tmp"
                String wkt = ""
                groovySql.eachRow(query, { GroovyResultSet row ->
                    wkt = row.getObject('wkt')
                })

                File zippedShapeFile = SpatialConversionUtils.buildZippedShapeFile(wkt, 'area', filename, ids.join(','))
                FileUtils.copyFile(zippedShapeFile, os)
            } else {
                spatialObjectsService.streamObjectsGeometryById(os, cleanObjectId(id), 'shp')
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

    private Map<String, Object> processGeoJSONRequest(JSONObject json, Integer pid) {
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
                spatialObjectsService.updateUserUploadedObject(pid, wkt, name, description, user_id)
                retMap.put("updated", true)
            } else {
                String generatedPid = spatialObjectsService.createUserUploadedObject(wkt, name, description, user_id)
                retMap.put("id", Integer.parseInt(generatedPid))
            } 5

        } catch (Exception ex) {
            log.error("Error uploading geojson", ex)
            retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
        }

        return retMap
    }

    @Deprecated
    @RequireApiKey
    def uploadGeojson(Integer id) {
        //id can be null
        processGeoJSONRequest(request.getJSON() as JSONObject, id)
    }

    private Map<String, Object> processWKTRequest(JSONObject json, Integer pid, boolean namesearch) {
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
                spatialObjectsService.updateUserUploadedObject(pid, wkt, name, description, user_id)
                retMap.put("updated", true)
            } else {
                String generatedPid = spatialObjectsService.createUserUploadedObject(wkt, name, description, user_id, namesearch)
                retMap.put("id", Integer.parseInt(generatedPid))
            }

        } catch (ParseException e) {
            log.error("Invalid WKT:" + e.message, e)
            retMap.put("error", "Invalid WKT:" + e.message)
        } catch (Exception e) {
            log.error("Error in processing WKT: " +  e.message, e)
            retMap.put("error", "Unexpected error: " + e.message)
        }

        return retMap
    }

    @Operation(
            method = "POST",
            tags = "object",
            operationId = "uploadWkt",
            summary = "Create an object from WKT",
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = uploadGeoJSON)
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the area id",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/geojson")
    @Produces("application/json")
    @RequireApiKey
    def uploadWkt(Integer id) {
        def namesearch = params.containsKey('namesearch') ? params.namesearch.toString().toBoolean() : false

        //id can be null
        def result = processWKTRequest(request.JSON as JSONObject, id, namesearch) as JSON
        response.contentType = 'application/json'
        render result
    }

    @Operation(
            method = "POST",
            tags = "object",
            operationId = "uploadGeoJSON",
            summary = "Create an object from GeoJSON",
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = uploadGeoJSON)
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the area id",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/geojson")
    @Produces("application/json")
    @RequireApiKey
    def uploadGeoJSON() throws Exception {
        render processGeoJSONRequest(request.JSON as JSONObject, null) as JSON
    }

    @Operation(
            method = "POST",
            tags = "object",
            operationId = "updateGeoJSON",
            summary = "Update an object with new GeoJSON",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )],
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = uploadGeoJSON)
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the area id",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/geojson/{pid}")
    @Produces("application/json")
    @RequireApiKey
    def updateWithGeojson(Integer pid) {
        if (pid == null) {
            render status: 400, text: "Path parameter `pid` is not an integer."
            return
        }
        render processGeoJSONRequest(request.JSON as JSONObject, pid) as JSON
    }

    @Operation(
            method = "POST",
            tags = "object",
            operationId = "updateWKT",
            summary = "Update an object with new WKT",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )],
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = uploadGeoJSON)
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the area id",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/wkt/{pid}")
    @Produces("application/json")
    @RequireApiKey
    def updateWithWKT(Integer pid) {
        if (pid == null) {
            render status: 400, text: "Path parameter `pid` is not an integer."
            return
        }
        def namesearch = params.containsKey('namesearch') ? params.namesearch.toString().toBoolean() : false
        render processWKTRequest(request.JSON as JSONObject, pid, namesearch) as JSON
    }

    @Operation(
            method = "POST",
            tags = "upload",
            operationId = "uploadShapefile",
            summary = "Upload a zipped shapefile and get a shapeId",
            parameters = [
                    @Parameter(
                            name = "name",
                            in = QUERY,
                            description = "searchable name for the area",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "description",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            requestBody = @RequestBody(
                    description = "Uploaded zip file",
                    content = @Content(
                            mediaType = 'application/zip',
                            schema = @Schema(
                                    type = "string",
                                    format = "binary"
                            )
                    )
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the shapeId and a list of all features (areas)",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/shp")
    @Produces("application/json")
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
                String apiKey = (String) reqBodyParser.getParsedValue("api_key")

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

    @Deprecated
    @RequireApiKey
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
                String kml = fileItem.getInputStream().text
                String wkt = SpatialUtils.getKMLPolygonAsWKT(kml)

                wkt = fixWkt(wkt)

                if (!isWKTValid(wkt)) {
                    retMap.put("error", "Invalid geometry")
                    return retMap
                } else {
                    String generatedPid = spatialObjectsService.createUserUploadedObject(wkt, name, description, userId)
                    retMap.put("id", Integer.parseInt(generatedPid))
                }
            } catch (Exception e) {
                response.status = 400
                retMap.put("error", "KML parsing failure: " + e.message)
            }
        } else {
            response.status = 400
            retMap.put("error", "Multiple files sent in request. A single unzipped kml file should be supplied.")
        }

        render retMap as JSON
    }

    private static Map<Object, Object> handleZippedShapeFile(File zippedShp) throws IOException {
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
        processShapeFileFeatureRequest(JsonOutput.toJson(json), pid, shapeFileId, featureIndex)
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
                    spatialObjectsService.updateUserUploadedObject(pid, wkt, name, description, user_id)
                    retMap.put("updated", true)
                } else {
                    String generatedPid = spatialObjectsService.createUserUploadedObject(wkt, name, description, user_id)
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

    @Operation(
            method = "GET",
            tags = "upload",
            operationId = "getImage",
            summary = "Return an image for an uploaded shapefile and a list of features",
            parameters = [
                    @Parameter(
                            name = "shapeId",
                            in = PATH,
                            description = "Uploaded shapefile ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "featureIndexxes",
                            in = PATH,
                            description = "Comma delimited list of feature indexes or the keyword `all` for all features",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Image of features",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "image/png"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/shp/image/{shapeId}/{featureIndexes}")
    @Produces("image/png")
    def shapeImage(String shapeId, String featureIndexes) {
        OutputStream os = response.outputStream
        try {
            File shpFileDir = new File(System.getProperty("java.io.tmpdir"), shapeId)

            BufferedImage bi = SpatialUtils.getShapeFileFeaturesAsImage(shpFileDir, featureIndexes,
                    (params?.width ?: 640) as Integer, (params?.height ?: 480) as Integer)

            response.setContentType("image/png")

            ImageIO.write(bi, "png", os)
            os.flush()

        } catch (Exception ex) {
            log.error("Error processsing shapefile feature request", ex)

            response.status = 404
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
    @Operation(
            method = "POST",
            tags = "object",
            operationId = "uploadShapefileFeature",
            summary = "Create an object from a list of features of an uploaded shapefile",
            parameters = [
                    @Parameter(
                            name = "shapeId",
                            in = PATH,
                            description = "Shapefile ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )],
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UploadFeatures)
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the area id",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/shp/{shapeId}/featureIndex")
    @Produces("application/json")
    @RequireApiKey
    def saveFeatureFromShapeFile(String shapeId, String featureIndex) {
        JSONObject json = request.JSON as JSONObject
        if (!featureIndex) {
            if (json["featureIdx"]) {
                featureIndex = json["featureIdx"]
            } else {
                render status: 400, text: "Feature Index is not provided"
            }
        }
        render processShapeFileFeatureRequest(json, null, shapeId, featureIndex) as JSON
    }

    @Deprecated
    @RequireApiKey
    def updateFromShapeFileFeature(Integer objectPid, String shapeId, String featureIndex) throws Exception {
        if (objectPid == null) {
            render status: 400, text: "Path parameter `objectPid` is not an integer."
            return
        }
        render processShapeFileFeatureRequest(request.JSON as JSONObject, objectPid, shapeId, featureIndex) as JSON
    }

    @Deprecated
    @RequireApiKey
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
        render processPointRadiusRequest(request.JSON as JSONObject, null, latitude, longitude, radius) as JSON
    }

    @Deprecated
    @RequireApiKey
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
        render processPointRadiusRequest(request.JSON as JSONObject, objectPid, latitude, longitude, radius) as JSON
    }

    private Map<String, Object> processPointRadiusRequest(JSONObject json, Integer pid, double latitude, double longitude, double radiusKm) {
        Map<String, Object> retMap = new HashMap<String, Object>()

        String name = (String) json.get("name")
        String description = (String) json.get("description")
        String user_id = (String) json.get("user_id")

        try {
            String wkt = SpatialConversionUtils.createCircleJs(longitude, latitude, radiusKm * 1000)
            if (pid == null) {
                String generatedPid = spatialObjectsService.createUserUploadedObject(wkt, name, description, user_id)
                retMap.put("id", Integer.parseInt(generatedPid))
            } else {
                spatialObjectsService.updateUserUploadedObject(pid, wkt, name, description, user_id)
                retMap.put("updated", true)
            }

        } catch (Exception ex) {
            ex.printStackTrace()
        }

        return retMap
    }

    @Operation(
            method = "DELETE",
            tags = "object",
            operationId = "deleteObject",
            summary = "Delete an object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )],
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UploadFeatures)
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Object with the area id",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/shape/upload/{pid}")
    @RequireApiKey
    def deleteShape(Integer pid) {
        if (pid == null) {
            render status: 400, text: "Path parameter `pid` is not an integer."
            return
        }
        if (request.method == "DELETE") {
            Map<String, Object> retMap = new HashMap<String, Object>()
            try {
                boolean success = spatialObjectsService.deleteUserUploadedObject(pid)
                retMap.put("success", success)
            } catch (Exception ex) {
                log.error("Error deleting shape " + pid, ex)
                retMap.put("error", "Unexpected error. Please notify support@ala.org.au.")
            }
            render retMap as JSON
        }
    }

    private static String fixWkt(String wkt) {
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

    private static boolean isWKTValid(String wkt) {
        // only validate POLYGON and MULTIPOLYGON
        if (wkt.startsWith("POLYGON") || wkt.startsWith("MULTIPOLYGON") || wkt.startsWith("POINT")) {
            WKTReader wktReader = new WKTReader()
            Geometry geom = wktReader.read(wkt.toString())
            return geom.isValid()
        }
        return true
    }

    private static String makeValidFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\(\\)\\[\\]\\-]", "_")
    }

    private static cleanObjectId(String id) {
        String.valueOf(Long.valueOf(id))
    }

    class UploadWkt {
        String wkt
        String name
        String description
        String user_id
    }

    class uploadGeoJSON {
        String name
        String description
        String user_id
        Map geojson
    }

    class UploadFeatures {
        List<String> featureIndex
    }
}

