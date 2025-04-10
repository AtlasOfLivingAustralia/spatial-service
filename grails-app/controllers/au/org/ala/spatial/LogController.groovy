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
import au.org.ala.web.AuthService
import com.opencsv.CSVWriter
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Encoding
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY


@RequireApiKey
class LogController {

    LogService logService
    AuthService authService
    SpatialConfig spatialConfig
    SpatialAuthService spatialAuthService

    @Operation(
            method = "POST",
            tags = "logging",
            operationId = "log",
            summary = "Add an entry to the log",
            requestBody = @RequestBody(
                    content = [@Content(
                            encoding = @Encoding(contentType = 'application/json'),
                            schema = @Schema(oneOf = Log.class)
                    )]),
            responses = [
                    @ApiResponse(
                            description = "Valid request",
                            responseCode = "200"
                    ),
                    @ApiResponse(
                            description = "Invalid request",
                            responseCode = "400"
                    )
            ],
            security = []
    )
    @Path("/log")
    @Transactional
    def index() {
        Map emptyMap = [:]
        try {
            def json = request.JSON
            def lg = new Log()
            lg.userId = authService.getUserId()
            lg.category1 = json.data.category1
            lg.category2 = json.data.category2
            lg.sessionId = json.data.sessionId

            json.data.remove("category1")
            json.data.remove("category2")
            json.data.remove("sessionId")
            json.data.remove("userId")

            lg.data = json.data.toString()
            if (!lg.save()) {
                lg.errors.each {
                    log.error(it)
                }
            }
            response.status = 200
            render emptyMap as JSON
        } catch (Exception e) {
            log.warn("log info is broken, ignored! " + e.getMessage())
            response.status = 400
            render emptyMap as JSON
        }
    }

    /**
     * login required
     *
     *
     * @return
     */
    @Operation(
            method = "GET",
            tags = "logging",
            operationId = "logSearch",
            summary = "Search the log",
            parameters = [
                    @Parameter(
                            name = "accept",
                            in = QUERY,
                            description = "Format of response, application/csv or application/json. The default is application/json.",
                            required = false,
                            example = 'application/json'
                    ),
                    @Parameter(
                            name = "max",
                            in = QUERY,
                            description = "Maximum number of rows to return",
                            required = false,
                            example = '10'
                    ),
                    @Parameter(
                            name = "offset",
                            in = QUERY,
                            description = "Offset value for paging",
                            required = false,
                            example = '0'
                    ),
                    @Parameter(
                            name = "groupBy",
                            in = QUERY,
                            description = "Grouping for results. A comma delimited list of: \"category1\", \"category2\", \"data\", \"sessionId\", \"userId\", \"year\", \"month\".",
                            required = false,
                            example = 'year,category1,category2'
                    ),
                    @Parameter(
                            name = "countBy",
                            in = QUERY,
                            description = "Type of counts. This is number of records or the number of unique users, sessions, etc. A comma delimited list of: \"category1\", \"category2\", \"record\", \"session\", \"user\"",
                            required = false,
                            example = 'record,session,user'
                    ),
                    @Parameter(
                            name = "admin",
                            in = QUERY,
                            description = "When true, return results for all users if request has an admin role.",
                            required = false,
                            example = "true"
                    ),
                    @Parameter(
                            name = "category1",
                            in = QUERY,
                            description = "Limit results to a single category1 value.",
                            required = false
                    ),
                    @Parameter(
                            name = "category2",
                            in = QUERY,
                            description = "Limit results to a single category2 value.",
                            required = false
                    ),
                    @Parameter(
                            name = "sessionId",
                            in = QUERY,
                            description = "Limit results to a single sessionId value.",
                            required = false
                    ),
                    @Parameter(
                            name = "startDate",
                            in = QUERY,
                            description = "Limit results between a startDate and endDate.",
                            required = false,
                            example = "2000-01-24"
                    ),
                    @Parameter(
                            name = "endDate",
                            in = QUERY,
                            description = "Limit results between a startDate and endDate.",
                            required = false,
                            example = "2025-01-24"
                    ),
                    @Parameter(
                            name = "excludeRoles",
                            in = QUERY,
                            description = "Exclude users having specific roles in their profile. Used with admin=true requests",
                            required = false,
                            example = "ROLE_ADMIN, ROLE_EDITOR"
                    )

            ],
            responses = [
                    @ApiResponse(
                            description = "Valid request",
                            responseCode = "200"
                    ),
                    @ApiResponse(
                            description = "Invalid request",
                            responseCode = "400"
                    )
            ],
            security = []
    )
    @Path("/log/search")
    @Produces("application/json")
    def search() {
        def searchResult = logService.search(params, authService.getUserId(), spatialAuthService.userInRole(spatialConfig.auth.admin_role))
        def totalCount = logService.searchCount(params, authService.getUserId(), spatialAuthService.userInRole(spatialConfig.auth.admin_role))
        log.debug("Logs: " + totalCount)
        log.debug("Return as " + request.getHeader("accept"))
        if ("application/csv" == request.getHeader("accept") || "application/csv" == params['accept']) {
            response.contentType = 'application/csv'
            response.setHeader("Content-disposition", "filename=\"search.csv\"")

            CSVWriter writer = new CSVWriter(new OutputStreamWriter(response.outputStream))
            def firstRow = true
            searchResult.each { Map<Object, Object> row ->
                if (firstRow) {
                    writer.writeNext(row.keySet() as String[])
                    firstRow = false
                }
                writer.writeNext(row.values() as String[])
            }
            writer.flush()
            writer.close()
        } else {
            def map = [records: searchResult, totalCount: totalCount]
            render map as JSON
        }
    }
}
