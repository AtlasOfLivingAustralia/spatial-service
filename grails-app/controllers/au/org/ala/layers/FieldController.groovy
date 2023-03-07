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

import au.org.ala.layers.dao.FieldDAO
import au.org.ala.layers.dao.ObjectDAO
import au.org.ala.plugins.openapi.Path
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Objects
import grails.converters.JSON

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class FieldController {

    FieldDAO fieldDao
    ObjectDAO objectDao

    @Operation(
            method = "GET",
            tags = "fields",
            operationId = "getFields",
            summary = "Get list of fields",
            description = "Get a list of fields",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of fields",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Field))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("fields")
    @Produces("application/json")
    def index() {
        if (params?.q) {
            render fieldDao.getFieldsByCriteria(params.q) as JSON
        } else {
            render fieldDao.getFields(true) as JSON
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
                                            array = @ArraySchema(schema = @Schema(implementation = Field))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("fieldsdb")
    @Produces("application/json")
    def db() {
        render fieldDao.getFieldsByDB() as JSON
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
            tags = "field",
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
                                            schema = @Schema(implementation = Field)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("field/{id}")
    @Produces("application/json")
    def show(String id) {
        Integer start = params.containsKey('start') ? Integer.parseInt(params.start.toString()) : 0
        Integer pageSize = params.containsKey('pageSize') ? Integer.parseInt(params.pageSize.toString()) : -1
        String q = params.containsKey('q') ? params.q : null

        //test field id value
        Field field = fieldDao.getFieldById(id, false)

        if (field == null || id == null) {
            render(status: 404, text: 'Invalid field id')
        } else {

            Map map = field.toMap()
            map.put('number_of_objects', field.getNumber_of_objects())

            //include field objects
            log.error('field id: ' + id)
            List objects = objectDao.getObjectsById(id, start, pageSize, q)
            List list = objects.collect { Objects it ->
                [name  : it.name, id: it.id, description: it.description, pid: it.pid,
                 wmsurl: it.wmsurl, area_km: it.area_km, fieldname: it.fieldname,
                 bbox  : it.bbox, fid: it.fid]
            }
            map.put('objects', list)

            render map as JSON
        }
    }

    @Operation(
            method = "GET",
            tags = "field",
            operationId = "searchField",
            summary = "search for fields",
            description = "Search for fields ",
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
                            description = "field",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = Field)
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("fields/search")
    @Produces("application/json")
    def search() {
        def q = params.containsKey('q') ? params.q.toString() : ''

    }
}
