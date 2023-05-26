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

import au.org.ala.plugins.openapi.Path
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class ChecklistController {

    DistributionsService distributionsService

    @Operation(
            method = "GET",
            tags = "checklists",
            operationId = "getChecklists",
            summary = "Get list of checklists",
            description = "Get a list of expert distributions",
            parameters = [
                    @Parameter(
                            name = "lsids",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "LSIDs to filter, comma delimited",
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
                            description = "Data resource UIDs to filter, comma delimited",
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
                            description = "List of checklists",
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
    @Path("checklists")
    @Produces("application/json")
    def index() {
        List checklists = distributionsService.queryDistributions(params, true, Distributions.SPECIES_CHECKLIST)

        render checklists as JSON
    }

    @Operation(
            method = "GET",
            tags = "checklists",
            operationId = "getChecklistItem",
            summary = "Get a checklist item",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "Id of the checklist item",
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
            requestBody = @RequestBody(),
            responses = [
                    @ApiResponse(
                            description = "checklist",
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
    @Path("checklist/{id}")
    @Produces("application/json")
    def show() {
        Long id = Long.parseLong(params.id)
        if (id == null) {
            render status: 400
            return
        }

        def distribution = distributionsService.show(id, params, Distributions.SPECIES_CHECKLIST)

        if (distribution) {
            render distribution as JSON
        } else {
            response.status = 404
        }
    }

    @Deprecated
    def lsidFirst(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.SPECIES_CHECKLIST)

        if (distributions) {
            render distributions.get(0) as JSON
        } else {
            render(status: 404)
        }
    }

    @Deprecated
    def lsid(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.SPECIES_CHECKLIST)

        if (distributions) {
            render distributions.get(0) as JSON
        } else {
            render(status: 404)
        }
    }

    @Operation(
            method = "GET",
            tags = "checklists",
            operationId = "getChecklistItemsByLSID",
            summary = "Get checklist items by LSID",
            parameters = [
                    @Parameter(
                            name = "lsid",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "LSID of the checklist item",
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
                            description = "List checklist items",
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
    @Path("checklist/lsids/{lsid}")
    @Produces("application/json")
    def lsids() {
        String lsid = params.lsid
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt as Boolean : false

        List distributions = distributionsService.queryDistributions([lsids: lsid?.toString()?.replace("https:/", "https://")], noWkt, Distributions.SPECIES_CHECKLIST)

        if (distributions) {
            render distributions as JSON
        } else {
            render(status: 404)
        }
    }
}
