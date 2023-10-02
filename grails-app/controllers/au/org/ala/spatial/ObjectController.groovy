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
import au.org.ala.spatial.dto.LayerFilter
import au.org.ala.spatial.util.SpatialConversionUtils
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class ObjectController {

    FieldService fieldService
    SpatialObjectsService spatialObjectsService

    @Operation(
            method = "GET",
            tags = "objects",
            operationId = "getSpatialObject",
            summary = "Get a spatial object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Id of the checklist item",
                            schema = @Schema(implementation = Long),
                            required = true
                    )],
            responses = [
                    @ApiResponse(
                            description = "Spatial Object",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = SpatialObjects)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('object/{pid}')
    @Produces("application/json")
    def show() {
        String pid = params.pid
        def obj
        if (pid.startsWith("ENVELOPE")) {
            obj = getEnvelope(pid.replace("ENVELOPE", ""))
        } else {
            obj = spatialObjectsService.getObjectByPid(pid)
        }
        if (obj != null) render obj as JSON
        else render(status: 404)
    }

    private static def getEnvelope(String envelopeTaskId) {
        def task = Task.get(envelopeTaskId)

        for (def output : task.output) {
            if ("area" == output.name) {
                return JSON.parse(output.file)
            }
        }
        return null
    }

    @Operation(
            method = "GET",
            tags = "objects",
            operationId = "nearestObjects",
            summary = "Get a list of objects nearest to a point",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            description = "Field ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "lat",
                            in = PATH,
                            description = "latitude",
                            schema = @Schema(implementation = Double),
                            required = true
                    ),
                    @Parameter(
                            name = "lng",
                            in = PATH,
                            description = "longitude",
                            schema = @Schema(implementation = Double),
                            required = true
                    ),
                    @Parameter(
                            name = "limit",
                            in = QUERY,
                            description = "maximum number of items to return",
                            schema = @Schema(implementation = Integer),
                            required = false
                    )],
            responses = [
                    @ApiResponse(
                            description = "Spatial Object",
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
    @Path('objects/{fid}/{lat}/{lng}')
    @Produces("application/json")
    def listByLocation() {
        String fid = params.fid
        Double lat = Double.parseDouble(params.lat)
        Double lng = Double.parseDouble(params.lng)

        if (lat == null) {
            render status: 400, text: "Path parameter `lat` is not a number."
            return
        }
        if (lng == null) {
            render status: 400, text: "Path parameter `lng` is not a number."
            return
        }

        Integer limit = params.containsKey('limit') ? params.limit as Integer : 40

        Fields field = fieldService.getFieldById(fid, false)

        if (field == null) {
            render(status: 404, text: 'Invalid field id')
        } else {
            def objects = spatialObjectsService.getNearestObjectByIdAndLocation(fid, limit, lng, lat)

            render objects as JSON
        }
    }

    @Deprecated
    def listByWkt(String id) {
        Integer limit = params.containsKey('limit') ? params.limit as Integer : 40
        String wkt = params?.wkt

        Fields field = fieldService.getFieldById(id)

        if (field == null) {
            render(status: 404, text: 'Invalid field id')
        } else {
            if (wkt.startsWith("ENVELOPE(")) {

                //get results of each filter term
                def filters = LayerFilter.parseLayerFilters(wkt.toString())
                def all = []
                filters.each { LayerFilter it ->
                    all.add(spatialObjectsService.getObjectsByIdAndIntersection(id, limit, it))
                }

                //merge common entries only
                Map<String, Integer> objectCounts = [:]
                def list = all[0]
                list.each { SpatialObjects it ->
                    objectCounts.put(it.getPid(), 1)
                }
                all.subList(1, all.size()).each {
                    it.each { SpatialObjects t ->
                        Integer v = objectCounts.get(t.getPid())
                        if (v != null) {
                            objectCounts.put(t.getPid(), v + 1)
                        }
                    }
                }
                def inAllGroups = []
                list.each { SpatialObjects it ->
                    if (objectCounts.get(it.getPid()) == all.size()) {
                        inAllGroups.add(it)
                    }
                }

                render inAllGroups as JSON
            } else if (wkt.startsWith("OBJECT(")) {

                def pid = wkt.substring("OBJECT(".length(), wkt.length() - 1)
                def objects = spatialObjectsService.getObjectsByIdAndIntersection(id, limit, pid.toString())

                render objects as JSON

            } else if (wkt.startsWith("GEOMETRYCOLLECTION")) {
                def collectionParts = SpatialConversionUtils.getGeometryCollectionParts(wkt.toString())

                def objectsSet = [] as Set

                collectionParts.each {
                    objectsSet.addAll(spatialObjectsService.getObjectsByIdAndArea(id, limit, it))
                }

                render objectsSet as JSON
            } else {
                render spatialObjectsService.getObjectsByIdAndArea(id, limit, wkt.toString()) as JSON
            }
        }
    }

    @Operation(
            method = "GET",
            tags = "objects",
            operationId = "objectsInField",
            summary = "Get a list of objects in a field",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            description = "Field ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            description = "Paging start index",
                            schema = @Schema(implementation = Integer),
                            required = false
                    ),
                    @Parameter(
                            name = "pageSize",
                            in = QUERY,
                            description = "Paging page size",
                            schema = @Schema(implementation = Integer),
                            required = false
                    )],
            responses = [
                    @ApiResponse(
                            description = "Spatial Object",
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
    @Deprecated
    @Path('objects/{fid}')
    @Produces("application/json")
    def fieldObjects() {
        String fid = params.fid
        Integer start = params.containsKey('start') ? params.start as Integer : 0
        Integer pageSize = params.containsKey('pageSize') ? params.pageSize as Integer : -1

        render spatialObjectsService.getObjectsById(fid, start, pageSize, null) as JSON
    }

    @Operation(
            method = "GET",
            tags = "objects",
            operationId = "nearestObjects",
            summary = "Test if a point intersects a single object",
            parameters = [
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "lat",
                            in = PATH,
                            description = "latitude",
                            schema = @Schema(implementation = Double),
                            required = true
                    ),
                    @Parameter(
                            name = "lng",
                            in = PATH,
                            description = "longitude",
                            schema = @Schema(implementation = Double),
                            required = true
                    )],
            responses = [
                    @ApiResponse(
                            description = "Spatial Object",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = SpatialObjects)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path('object/intersect/{pid}/{lat}/{lng}')
    @Produces("application/json")
    def intersectObject() {
        String pid = params.pid
        Double lat = Double.parseDouble(params.lat)
        Double lng = Double.parseDouble(params.lng)
        if (lat == null) {
            render status: 400, text: "Path parameter `lat` is not a number."
            return
        }
        if (lng == null) {
            render status: 400, text: "Path parameter `lng` is not a number."
            return
        }
        def obj = spatialObjectsService.intersectObject(pid, lat, lng) ?: [:]
        render obj as JSON
    }

    @Operation(
            method = "POST",
            tags = "objects",
            operationId = "objectsInWkt",
            summary = "Get a list of a field's objects that intersect with provided WKT",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            description = "Field ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "wkt",
                            in = QUERY,
                            description = "WKT. pid or wkt is required",
                            schema = @Schema(implementation = String),
                            required = false
                    ), @Parameter(
                            name = "limit",
                            in = QUERY,
                            description = "Maximum number of objects to return",
                            schema = @Schema(implementation = String),
                            required = true
                    ), @Parameter(
                            name = "pid",
                            in = QUERY,
                            description = "Object ID. pid or wkt is required",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Spatial Object",
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
    @Path('objects/inarea/{fid}')
    @Produces("application/json")
    def objectsInArea() {
        String fid = params.fid
        Integer limit = params.containsKey('limit') ? params.limit as Integer : 40

        String wkt = params.wkt ?: "OBJECT(${params.pid})"

        if (wkt.startsWith("ENVELOPE(")) {
            //get results of each filter
            LayerFilter[] filters = LayerFilter.parseLayerFilters(wkt)
            List<List<SpatialObjects>> all = new ArrayList<List<SpatialObjects>>()
            for (int i = 0; i < filters.length; i++) {
                all.add(spatialObjectsService.getObjectsByIdAndIntersection(fid, limit, filters[i]))
            }
            //merge common entries only
            HashMap<String, Integer> objectCounts = new HashMap<String, Integer>()
            List<SpatialObjects> list = all.get(0)
            for (int j = 0; j < list.size(); j++) {
                objectCounts.put(list.get(j).getPid(), 1)
            }
            for (int i = 1; i < all.size(); i++) {
                List<SpatialObjects> t = all.get(i)
                for (int j = 0; j < t.size(); j++) {
                    Integer v = objectCounts.get(t.get(j).getPid())
                    if (v != null) {
                        objectCounts.put(t.get(j).getPid(), v + 1)
                    }
                }
            }
            List<SpatialObjects> inAllGroups = new ArrayList<SpatialObjects>(list.size())
            for (int j = 0; j < list.size(); j++) {
                if (objectCounts.get(list.get(j).getPid()) == all.size()) {
                    inAllGroups.add(list.get(j))
                }
            }

            render inAllGroups as JSON
        } else if (wkt.startsWith("OBJECT(")) {
            String pid = wkt.substring("OBJECT(".length(), wkt.length() - 1)
            render spatialObjectsService.getObjectsByIdAndIntersection(fid, limit, LayerFilter.parseLayerFilter(pid)) as JSON
        } else if (wkt.startsWith("GEOMETRYCOLLECTION")) {
            List<String> collectionParts = SpatialConversionUtils.getGeometryCollectionParts(wkt)

            Set<SpatialObjects> objectsSet = new HashSet<SpatialObjects>()

            for (String part : collectionParts) {
                objectsSet.addAll(spatialObjectsService.getObjectsByIdAndArea(fid, limit, part))
            }

            render new ArrayList<SpatialObjects>(objectsSet) as JSON
        } else {
            render spatialObjectsService.getObjectsByIdAndArea(fid, limit, wkt) as JSON
        }
    }

}
