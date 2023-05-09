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
import io.swagger.v3.oas.annotations.responses.ApiResponse

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

//@CompileStatic
class JournalMapController {

    JournalMapService journalMapService
    SpatialObjectsService spatialObjectsService

    @Operation(
            method = "GET",
            tags = "JournalMap",
            operationId = "searchJournalMap",
            summary = "Get list of Journal Map records within an area",
            parameters = [
                    @Parameter(
                            name = "wkt",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "WKT to filter. Required if pid is not provided.",
                            required = false
                    ),
                    @Parameter(
                            name = "pid",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Object ID to filter. Required if wkt is not provided.",
                            required = false
                    ),
                    @Parameter(
                            name = "offset",
                            in = QUERY,
                            description = "starting index for associated objects",
                            schema = @Schema(implementation = Long),
                            required = false
                    ),
                    @Parameter(
                            name = "max",
                            in = QUERY,
                            description = "number of associated objects to return",
                            schema = @Schema(implementation = Long),
                            required = false
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "List of JournalMap items",
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
    @Path("journalMap/search")
    @Produces("application/json")
    def search() {
        String wkt = params?.pid ? spatialObjectsService.getObjectsGeometryById(params.pid.toString(), "wkt") : params?.wkt
        render journalMapService.search(wkt, params.max as Integer ?: 10, params.offset as Integer ?: 0) as JSON
    }

    @Operation(
            method = "GET",
            tags = "JournalMap",
            operationId = "countJournalMap",
            summary = "Get count of Journal Map records within an area",
            parameters = [
                    @Parameter(
                            name = "wkt",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "WKT to filter",
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
                            description = "Count",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Map)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("journalMap/count")
    @Produces("application/json")
    def count() {
        String wkt = params?.pid ? spatialObjectsService.getObjectsGeometryById(params.pid.toString(), "wkt") : params?.wkt
        def map = [count: journalMapService.count(wkt)]
        render map as JSON
    }
}
