/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License") you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial


import au.org.ala.plugins.openapi.Path
import au.org.ala.spatial.util.BatchConsumer
import au.org.ala.spatial.util.BatchProducer
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.geotools.geojson.geom.GeometryJSON
import org.locationtech.jts.geom.Geometry

import javax.ws.rs.Produces
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class IntersectController {

    LayerIntersectService layerIntersectService
    SpatialObjectsService spatialObjectsService
    SpatialConfig spatialConfig

    @Operation(
            method = "GET",
            tags = "intersect",
            operationId = "intersectPoint",
            summary = "Get intersection of a point and one or more fields",
            parameters = [
                    @Parameter(
                            name = "ids",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "Comma delimited list of field IDs",
                            required = true,
                            example = "cl22"
                    ),
                    @Parameter(
                            name = "lat",
                            in = PATH,
                            schema = @Schema(implementation = Double),
                            description = "Latitude",
                            required = true,
                            example = "-22"
                    ),
                    @Parameter(
                            name = "lng",
                            in = PATH,
                            schema = @Schema(implementation = Double),
                            description = "Longitude",
                            required = true,
                            example = "134"
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Intersection results",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Map)),
                                            examples = [
                                                    @ExampleObject(externalValue = "[{\"field\": \"cl22\",\"description\": \"Northern Territory, Territory\",\"layername\": \"Australian States and Territories\",\"pid\": \"3742608\",\"value\": \"Northern Territory\"},{\"field\": \"cl23\",\"description\": \"Northern Territory, Hanson, Unknown\",\"layername\": \"Local Government Areas 2012 deprecated\",\"pid\": \"3743150\",\"value\": \"Hanson\"}]")
                                            ]
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/$ids/$lat/$lng')
    @Produces("application/json")
    def intersect(String ids, Double lat, Double lng) {
        if (lat == null) {
            render status: 400, text: "Path parameter `lat` is not a number."
            return
        }
        if (lng == null) {
            render status: 400, text: "Path parameter `lng` is not a number."
            return
        }
        render layerIntersectService.samplingFull(ids, lng, lat) as JSON
    }


    @Operation(
            method = "POST",
            tags = "intersect",
            operationId = "intersectBatch",
            summary = "Get intersection for many points and fields",
            parameters = [
                    @Parameter(
                            name = "fids",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Comma delimited list of field IDs",
                            required = true,
                            example = "cl22,cl23"
                    ),
                    @Parameter(
                            name = "points",
                            in = QUERY,
                            schema = @Schema(implementation = Double),
                            description = "Comma delimited list of `latitude,longitude`",
                            required = true,
                            example = "-22,134,-23,130"
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Status of the batch request",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Map)),
                                            examples = [
                                                    @ExampleObject(externalValue = "{\"statusUrl\": \"https://spatial.ala.org.au/ws/intersect/batch/15123\",\"progress\": 0,\"started\": \"09/03/20 12:17:22:353\",\"batchId\": \"15123\",\"fields\": 2,\"points\": 2,\"status\": \"started\"}")
                                            ]
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/batch')
    @Produces("application/json")
    @SkipSecurityCheck
    // Required to because request.reader.text conflicts with serviceAuthService.hasValidApiKey()
    def batch() {
        File dir = new File((spatialConfig.data.dir + '/intersect/batch/') as String)
        dir.mkdirs()
        BatchConsumer.start(layerIntersectService, dir.getPath(), spatialConfig.sampling.threads as Integer)

        //help get params when they don't pick up automatically from a POST
        String fids = params.containsKey('fids') ? params.fids : ''
        String points = params.containsKey('points') ? params.points : ''
        String gridcache = params.containsKey('gridcache') ? params.gridcache : '0'

        try {
            if (request.post && !params.containsKey('fids') && !params.containsKey('points')) {
                String text = request.reader.text
                for (String param : text.split("&")) {
                    if (param.startsWith("fids=")) {
                        fids = param.substring(5)
                    } else if (param.startsWith("points=")) {
                        points = param.substring(7)
                    }
                }
            }
        } catch (err) {
            log.error 'failed to read POST body for batch intersect', err
        }

        if (!points || !fids) {
            def resp = [status: 'fail', error: 'request did not include points or did not include fids']
            render resp as JSON
        } else {
            Map map = new HashMap()
            String batchId
            try {

                // get limits
                int pointsLimit, fieldsLimit

                String[] passwords = spatialConfig.batch_sampling_passwords.toString().split(',')
                pointsLimit = spatialConfig.batch_sampling_points_limit as Integer
                fieldsLimit = spatialConfig.batch_sampling_fields_limit as Integer

                String password = params.containsKey('pw') ? params.pw : null
                for (int i = 0; password != null && i < passwords.length; i++) {
                    if (passwords[i] == password) {
                        pointsLimit = Integer.MAX_VALUE
                        fieldsLimit = Integer.MAX_VALUE
                    }
                }

                // count fields
                int countFields = 1
                int p = 0
                while ((p = fids.indexOf(',', p + 1)) > 0)
                    countFields++

                // count points
                int countPoints = 1
                p = 0
                while ((p = points.indexOf(',', p + 1)) > 0)
                    countPoints++

                if (countPoints / 2 > pointsLimit) {
                    map.put("error", "Too many points.  Maximum is " + pointsLimit)
                } else if (countFields > fieldsLimit) {
                    map.put("error", "Too many fields.  Maximum is " + fieldsLimit)
                } else {
                    batchId = BatchProducer.produceBatch(dir.getPath(), "request address:" + request.getRemoteAddr(), fids, points, gridcache)

                    map.put("batchId", batchId)
                    BatchProducer.addInfoToMap(dir.getPath(), batchId, map)
                    map.put("statusUrl", spatialConfig.grails.serverURL + '/intersect/batch/' + batchId)
                }

                render map as JSON
            } catch (Exception e) {
                log.error(e.getMessage(), e)
                map.put("error", "failed to create new batch")
                render map as JSON
            }
        }
    }

    @Operation(
            method = "GET",
            tags = "intersect",
            operationId = "intersectBatchStatus",
            summary = "Get intersection batch status",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "Batch ID",
                            required = true,
                            example = "15123"
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Status of the batch request",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Map)),
                                            examples = [
                                                    @ExampleObject(externalValue = "{\"waiting\": \"In queue\",\"statusUrl\": \"https://spatial.ala.org.au/ws/intersect/batch/1678306712973\",\"progress\": 0,\"batchId\": \"1678306712973\",\"fields\": 2,\"points\": 2,\"status\": \"waiting\"}")]
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/batch/$id')
    @Produces("application/json")
    def batchStatus(String id) {
        File dir = new File((spatialConfig.data.dir + '/intersect/batch/') as String)
        dir.mkdirs()
        BatchConsumer.start(layerIntersectService, dir.getPath(), spatialConfig.sampling.threads.toInteger())

        Map map = new HashMap()
        try {
            BatchProducer.addInfoToMap(dir.getPath(), id, map)
            if (map.get("finished") != null) {
                map.put("downloadUrl", spatialConfig.grails.serverURL + '/intersect/batch/download/' + id)
            }
        } catch (err) {
            log.error 'failed to get batch status: ' + id, err
        }

        render map as JSON
    }

    @Operation(
            method = "GET",
            tags = "intersect",
            operationId = "intersectBatchDownload",
            summary = "Get intersection batch result",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "Batch ID",
                            required = true,
                            example = "15123"
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Result of the batch request as a zipped CSV",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/zip"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/batch/download/$id')
    @Produces("application/zip")
    def batchDownload(String id) {
        Boolean csv = params.containsKey('csv') ? params.csv.toString().toBoolean() : false

        File dir = new File((spatialConfig.data.dir + '/intersect/batch/') as String)
        dir.mkdirs()
        BatchConsumer.start(layerIntersectService, dir.getPath(), spatialConfig.sampling.threads.toInteger())

        OutputStream os = null
        BufferedInputStream bis = null
        ZipOutputStream zip = null
        try {
            Map map = new HashMap()
            BatchProducer.addInfoToMap(dir.getPath(), String.valueOf(id), map)
            if (map.get("finished") != null) {
                os = response.getOutputStream()

                bis = new BufferedInputStream(new FileInputStream(dir.getPath() + File.separator + id + File.separator + "sample.csv"))

                if (!csv) {
                    zip = new ZipOutputStream(os)
                    zip.putNextEntry(new ZipEntry("sample.csv"))

                    os = zip
                }
                byte[] buffer = new byte[4096]
                int size
                while ((size = bis.read(buffer)) > 0) {
                    os.write(buffer, 0, size)
                }
                os.flush()
            }
        } catch (err) {
            log.error 'failed to download batch', err
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
            if (bis != null) {
                try {
                    bis.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
            if (zip != null) {
                try {
                    zip.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }
    }

    @Deprecated
    @Operation(
            method = "GET",
            tags = "cache",
            operationId = "clearCache",
            summary = "Clear caches",
            parameters = [],
            responses = [
                    @ApiResponse(
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Map))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/reloadconfig')
    @Produces("application/json")
    @RequirePermission
    def reloadConfig() {
        Map map = new HashMap()
        layerIntersectService.reload()
        map.put("layerIntersectDao", "successful")

        render map as JSON
    }

    @Deprecated
    @Operation(
            method = "GET",
            tags = "intersect",
            operationId = "intersectCircle",
            summary = "Get intersection of a circle and one or more fields",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "Comma delimited list of field IDs",
                            required = true,
                            example = "cl22"
                    ),
                    @Parameter(
                            name = "lat",
                            in = PATH,
                            schema = @Schema(implementation = Double),
                            description = "Latitude",
                            required = true,
                            example = "-22"
                    ),
                    @Parameter(
                            name = "lng",
                            in = PATH,
                            schema = @Schema(implementation = Double),
                            description = "Longitude",
                            required = true,
                            example = "134"
                    ),
                    @Parameter(
                            name = "radius",
                            in = PATH,
                            schema = @Schema(implementation = Double),
                            description = "Longitude",
                            required = true,
                            example = "134"
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Intersection results",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = SpatialObjects))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/pointradius/$fid/$lat/$lng/$radius')
    @Produces("application/json")
    def pointRadius(String fid, Double lat, Double lng, Double radius) {
        if (lat == null) {
            render status: 400, text: "Path parameter `lat` is not a number."
            return
        }
        if (lng == null) {
            render status: 400, text: "Path parameter `lng` is not a number."
            return
        }
        if (radius == null) {
            render status: 400, text: "Path parameter `radius` is not a number."
            return
        }
        render spatialObjectsService.getObjectsWithinRadius(fid, lat, lng, radius) as JSON
    }

    @Deprecated
    @Operation(
            method = "POST",
            tags = "intersect",
            operationId = "intersectWKT",
            summary = "Get intersection of WKT and one field",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "field ID",
                            required = true,
                            example = "cl22"
                    )
            ],
            requestBody = @RequestBody(
                    description = "WKT",
                    content = [@Content(schema = @Schema(implementation = String))]
            ),
            responses = [
                    @ApiResponse(
                            description = "Intersection results",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = SpatialObjects))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/wkt/$fid')
    @Produces("application/json")
    @SkipSecurityCheck
    // Required to because request.reader.text conflicts with serviceAuthService.hasValidApiKey()
    def wktGeometryIntersect(String fid) {
        render spatialObjectsService.getObjectsIntersectingWithGeometry(fid, request.reader.text) as JSON
    }

    @Deprecated
    @Operation(
            method = "POST",
            tags = "intersect",
            operationId = "intersectGeoJSON",
            summary = "Get intersection of GeoJSON and one field",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "field ID",
                            required = true,
                            example = "cl22"
                    )
            ],
            requestBody = @RequestBody(
                    description = "GeoJSON",
                    content = [@Content(schema = @Schema(implementation = String))]
            ),
            responses = [
                    @ApiResponse(
                            description = "Intersection results",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = SpatialObjects))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/geojson/$fid')
    @Produces("application/json")
    @SkipSecurityCheck
    // Required to because request.reader.text conflicts with serviceAuthService.hasValidApiKey()
    def geojsonGeometryIntersect(String fid) {
        String wkt = geoJsonToWkt(request.reader.text)
        render spatialObjectsService.getObjectsIntersectingWithGeometry(fid, wkt) as JSON
    }

    private static String geoJsonToWkt(String geoJson) {
        GeometryJSON gJson = new GeometryJSON()
        Geometry geometry = gJson.read(new StringReader(geoJson))

        if (!geometry.isValid()) {
            return null
        }

        String wkt = geometry.toText()
        return wkt
    }

    @Operation(
            method = "GET",
            tags = "intersect",
            operationId = "intersectObject",
            summary = "Get intersection of a spatial object and one field",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "field ID",
                            required = true,
                            example = "cl22"
                    ),
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "Spatial Object ID",
                            required = true,
                            example = "123"
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Intersection results",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = SpatialObjects))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('/intersect/object/$fid/$pid')
    @Produces("application/json")
    def objectIntersect(String fid, String pid) {
        render spatialObjectsService.getObjectsIntersectingWithObject(fid, pid) as JSON
    }

}
