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

class DistributionController {

    DistributionsService distributionsService
    AttributionService attributionService

    @Operation(
            method = "GET",
            tags = "distributions",
            operationId = "getDistributions",
            summary = "Get list of expert distributions",
            description = "Get a list of expert distributions",
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
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "List of expert distributions",
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
    @Path("distributions")
    @Produces("application/json")
    def index() {
        List distributions = distributionsService.queryDistributions(params, true, Distributions.EXPERT_DISTRIBUTION)

        render distributions as JSON
    }

    @Operation(
            method = "GET",
            tags = "distributions",
            operationId = "getLsids",
            summary = "Get list of species level LSIDs that have an expert distribution",
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
    @Path("distributions/lsids")
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
        def distributions = distributionsService.count(params, Distributions.EXPERT_DISTRIBUTION)

        render distributions as JSON
    }

    @Deprecated
    def pointRadius() {

        def distributions = distributionsService.pointRadius(params, Distributions.EXPERT_DISTRIBUTION)

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    @Deprecated
    def pointRadiusCount() {
        def distributions = distributionsService.pointRadiusCount(params, Distributions.EXPERT_DISTRIBUTION)

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    @Operation(
            method = "GET",
            tags = "distributions",
            operationId = "getExpertDistributionById",
            summary = "Get an expert distribution by Id",
            description = "Get an expert distribution by Id",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "Id of the expert distribution",
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
                            description = "expert distribution",
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
    @Path("distribution/{id}")
    @Produces("application/json")
    def show(Long id) {
        if (id == null) {
            render status: 400
            return
        }

        def distribution = distributionsService.show(id, params, Distributions.EXPERT_DISTRIBUTION)

        if (distribution) {
            render distribution as JSON
        } else {
            response.status = 404
        }
    }

    @Deprecated
    def lsidFirst(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.EXPERT_DISTRIBUTION)

        if (distributions) {
            render distributions.get(0) as JSON
        } else {
            render(status: 404)
        }
    }

    @Deprecated
    def lsid(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.EXPERT_DISTRIBUTION)

        if (distributions) {
            render distributions.get(0) as JSON
        } else {
            render(status: 404)
        }
    }

    @Operation(
            method = "GET",
            tags = "distributions",
            operationId = "getExpertDistributionsByLsid",
            summary = "Get expert distributions by LSID",
            description = "Get expert distributions by LSID",
            parameters = [
                    @Parameter(
                            name = "lsid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "LSID of the expert distribution",
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
                            description = "List expert distributions",
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
    @Path("distribution/lsids/{lsid}")
    @Produces("application/json")
    def lsids(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.EXPERT_DISTRIBUTION)

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
        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], true, Distributions.EXPERT_DISTRIBUTION)

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

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], true, Distributions.EXPERT_DISTRIBUTION)

        if (distributions != null) {
            distributions.each { Distributions distribution ->
                found.add(attributionService.getMapDTO(distribution))
            }
        }

        render found as JSON
    }

    @Deprecated
    def clearAttributionCache() {
        attributionService.clear()
    }

    @Deprecated
    def cacheMaps() {
        Distributions.createCriteria().list {
            projections {
                distinct('geom_idx')
            }
        }.each { geomIdx ->
            distributionsService.mapCache().getCachedMap(geomIdx as String)
        }

        render(status: 200, text: 'all cached')
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
            tags = "distributions",
            operationId = "getExpertDistributionImageById",
            summary = "Get expert distribution image by id",
            description = "Get expert distribution image by id",
            parameters = [
                    @Parameter(
                            name = "imageId",
                            in = PATH,
                            schema = @Schema(oneOf = Integer),
                            description = "LSID of the expert distribution",
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Overview image of the expert distribution in the Australian region",
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
    @Path("distribution/map/png/{imageId}")
    @Produces("image/png")
    @Deprecated
    def overviewMapPng(String geomIdx) {
        map(geomIdx)
    }

    @Deprecated
    def overviewMapPngLsid(String lsid) {
        image(response, lsid, null, null)
    }

    @Deprecated
    def overviewMapPngSpcode(Long spcode) {
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
                eq('type', Distributions.EXPERT_DISTRIBUTION)
            }
        }

        if (geomIdxs?.size()) {
            map(String.valueOf(geomIdxs.get(0)))
        } else {
            response.status = 404
        }
    }
}
