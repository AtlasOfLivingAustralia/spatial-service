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

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class FieldController {

    FieldService fieldService
    SpatialObjectsService spatialObjectsService

    @Operation(
            method = "GET",
            tags = "fields",
            operationId = "getFields",
            summary = "Get list of fields",
            description = "Get a list of fields",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "restrict to field names that contain this value and include layer information",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "List of fields",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Fields))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("fields")
    @Produces("application/json")
    def index() {
        if (params.containsKey('q')) {
            render fieldService.getFieldsByCriteria(params.q as String) as JSON
        } else {
            render fieldService.getFields(true) as JSON
        }
    }

    /**
     * list fields table with db only records
     * @return
     */
    @Operation(
            method = "GET",
            tags = "fields",
            operationId = "getFieldsForIndex",
            summary = "Get list of fields for indexing",
            description = "Get a list of fields for indexing",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of fields for indexing",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Fields))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("fieldsdb")
    @Produces("application/json")
    def db() {
        render fieldService.getFieldsByDB() as JSON
    }

    /**
     * This method returns a single field by field id
     *
     * Paging is available for object lists because some fields have many objects
     *
     * @param id
     * @return
     */
    @Operation(
            method = "GET",
            tags = "fields",
            operationId = "getFieldById",
            summary = "Get a field by Id",
            description = "Get a field by Id. Includes all objects associated with the field.",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "Id of the field",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            description = "starting index for associated objects",
                            schema = @Schema(implementation = Long),
                            required = false
                    ),
                    @Parameter(
                            name = "pageSize",
                            in = QUERY,
                            description = "number of associated objects to return",
                            schema = @Schema(implementation = Long),
                            required = false
                    ),
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "restrict to associated object names that contain this value",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "field",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Fields)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("field/{id}")
    @Produces("application/json")
    def show() {
        String id = params.id
        Integer start = params.containsKey('start') ? Integer.parseInt(params.start.toString()) : 0
        Integer pageSize = params.containsKey('pageSize') ? Integer.parseInt(params.pageSize.toString()) : -1
        String q = params.containsKey('q') ? params.q : null

        Fields field = fieldService.get(id, q, start, pageSize)
        if (!field) {
            render status:404
        } else {
            render field as JSON
        }

    }

    @Operation(
            method = "GET",
            tags = "fields",
            operationId = "searchFields",
            summary = "search for fields",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "restrict to field names that contain this value",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "List of fields",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Fields)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Deprecated
    @Path("fields/search")
    @Produces("application/json")
    def search() {
        render fieldService.getFieldsByCriteria(params.q as String) as JSON
    }

    @Operation(
            method = "GET",
            tags = "fields",
            operationId = "searchLayers",
            summary = "search for layers",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "restrict to layer names that contain this value",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "List of layers",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Layers)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("fields/layers/search")
    @Produces("application/json")
    def searchLayers() {
        render fieldService.getLayersByCriteria(params.q as String) as JSON
    }
}
