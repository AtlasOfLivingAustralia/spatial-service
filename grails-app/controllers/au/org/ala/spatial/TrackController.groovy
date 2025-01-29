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


import au.org.ala.spatial.dto.MapDTO
import au.org.ala.plugins.openapi.Path
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

class TrackController {
    DistributionsService distributionsService
    AttributionService attributionService

    @Operation(
            method = "GET",
            tags = "tracks",
            operationId = "getTracks",
            summary = "Get list of tracks",
            description = "Get a list of tracks",
            parameters = [
                    @Parameter(
                            name = "lsids",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "LSID to filter",
                            required = false
                    ),
                    @Parameter(
                            name = "geom_idx",
                            in = QUERY,
                            schema = @Schema(implementation = Long),
                            description = "Geometry index to filter",
                            required = false
                    ),
                    @Parameter(
                            name = "data_resource_uids",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Data resource UIDs to filter",
                            required = false
                    ),
                    @Parameter(
                            name = "wkt",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "WKT or Object ID to filter",
                            required = false
                    ),
                    @Parameter(
                            name = "pid",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Object ID to filter",
                            required = false
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Starting index for pagination",
                            required = false
                    ),
                    @Parameter(
                            name = "pageSize",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Page size for pagination",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "List of tracks",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Distributions))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("tracks")
    @Produces("application/json")
    def index() {
        List distributions = distributionsService.queryDistributions(params, true, Distributions.TRACK)

        render distributions as JSON
    }

    @Operation(
            method = "GET",
            tags = "tracks",
            operationId = "getLsids",
            summary = "Get list of species level LSIDs that have a track",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of LSIDs",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = String))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("tracks/lsids")
    @Produces("application/json")
    def listLsids() {
        def lsids = Distributions.createCriteria().list {
            projections {
                distinct('lsid')
            }
        }

        render lsids as JSON
    }

    /**
     * index family count
     * @return
     */
    @Deprecated
    def count() {
        def distributions = distributionsService.count(params, 't')

        render distributions as JSON
    }

    @Deprecated
    def pointRadius() {

        def distributions = distributionsService.pointRadius(params, 't')

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    def pointRadiusCount() {
        def distributions = distributionsService.pointRadiusCount(params, 't')

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    @Operation(
            method = "GET",
            tags = "tracks",
            operationId = "getTrackById",
            summary = "Get a track by Id",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "Id of the track",
                            schema = @Schema(implementation = Long),
                            required = true
                    ),
                    @Parameter(
                            name = "nowkt",
                            in = QUERY,
                            description = "true to exclude WKT from the response",
                            schema = @Schema(implementation = Boolean),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "track",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Distributions)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("track/{id}")
    @Produces("application/json")
    def show() {
        Long id = Long.parseLong(params.id)
        if (id == null) {
            render status: 400
            return
        }

        def distribution = distributionsService.show(id, params, Distributions.TRACK)

        if (distribution) {
            render distribution as JSON
        } else {
            response.status = 404
        }
    }

    @Deprecated
    def lsidFirst(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.TRACK)

        if (distributions) {
            render distributions.get(0) as JSON
        } else {
            render(status: 404)
        }
    }

    @Deprecated
    def lsid(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.TRACK)

        if (distributions) {
            render distributions.get(0) as JSON
        } else {
            render(status: 404)
        }
    }

    @Operation(
            method = "GET",
            tags = "tracks",
            operationId = "getTracksByLsid",
            summary = "Get tracks by LSID",
            parameters = [
                    @Parameter(
                            name = "lsid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "LSID of the tracks",
                            required = true
                    ),
                    @Parameter(
                            name = "nowkt",
                            in = QUERY,
                            schema = @Schema(implementation = Boolean),
                            description = "true to exclude WKT from the response",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "List of tracks",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Distributions))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("tracks/lsids/{lsid}")
    @Produces("application/json")
    def lsids() {
        String lsid = params.lsid
        Boolean noWkt = params.containsKey('nowkt') ? params.notwkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.TRACK)

        if (distributions) {
            render distributions as JSON
        } else {
            render(status: 404)
        }
    }

    /*
     * get one distribution map by lsid
     */

    @Deprecated
    def lsidMapFirst(String lsid) {
        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], true, Distributions.TRACK)

        if (distributions) {
            Distributions distribution = distributions.get(0)

            MapDTO m = attributionService.getMapDTO(distribution)

            render m as JSON
        } else {
            render status: 404
        }
    }

    @Deprecated
    def lsidMaps(String lsid) {

        List found = []

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], true, Distributions.TRACK)

        if (distributions != null) {
            distributions.each { Distributions distribution ->
                found.add(attributionService.getMapDTO(distribution))
            }
        }

        render found as JSON
    }

    @Deprecated
    def map(String geomIdx) {
        InputStream input = distributionsService.mapCache().getCachedMap(geomIdx)
        try {
            response.contentType = 'image/png'
            response.outputStream << input
            response.outputStream.flush()
        } finally {
            input.close()
        }
    }

    @Operation(
            method = "GET",
            tags = "tracks",
            operationId = "getTrackImageById",
            summary = "Get image of track by id",
            parameters = [
                    @Parameter(
                            name = "imageId",
                            in = PATH,
                            schema = @Schema(oneOf = Integer),
                            description = "LSID of the track",
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Overview image of the track in the Australian region",
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
    @Path("track/map/png/{imageId}")
    @Produces("image/png")
    @Deprecated
    def overviewMapPng() {
        String imageId = params.imageId
        map(imageId)
    }

    @Deprecated
    def overviewMapPngLsid(String lsid) {
        image(response, lsid, null, null)
    }

    @Deprecated
    def overviewMapPngSpcode(Long spcode) {
        if (spcode == null) {
            render status: 400
            return
        }
        image(response, null, spcode, null)
    }

    @Deprecated
    def overviewMapPngName(String name) {
        image(response, null, null, name)
    }

    /**
     * returns writes one image to the HttpServletResponse for lsid, spcode or scientificName match
     * *
     *
     * @param response
     * @param lsid
     * @param spcode
     * @param scientificName
     * @throws Exception
     */
    @Deprecated
    // users can directly use geoserver
    private def image(response, String lsid, Long spcode, String scientificName) {
        def geomIdxs = Distributions.createCriteria().list {
            projections {
                distinct('geom_idx')
            }
            and {
                or {
                    eq('spcode', spcode)
                    eq('lsid', lsid?.replace("https:/", "https://"))
                    eq('scientific', scientificName)
                }
                eq('type', Distributions.TRACK)
            }
        }

        if (geomIdxs?.size() > 0) {
            map(String.valueOf(geomIdxs.get(0)))
        } else {
            response.status = 404
        }
    }
}
