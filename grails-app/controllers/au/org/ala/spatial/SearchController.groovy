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
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.apache.commons.lang3.StringUtils
import org.springframework.web.util.UriUtils

import javax.ws.rs.Produces
import java.nio.charset.Charset

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class SearchController {

    SearchService searchService

    @Operation(
            method = "GET",
            tags = "search,objects",
            operationId = "searchObjects",
            summary = "Search all objects by name",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "Search term",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "limit",
                            in = QUERY,
                            description = "Paging page size",
                            schema = @Schema(implementation = Integer),
                            required = false
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            description = "Paging start index",
                            schema = @Schema(implementation = Integer),
                            required = false
                    ),
                    @Parameter(
                            name = "include",
                            in = QUERY,
                            description = "Field IDs to search, comma delimited",
                            schema = @Schema(implementation = String),
                            required = false
                    ),
                    @Parameter(
                            name = "exclude",
                            in = QUERY,
                            description = "Field IDs to exclude from search, comma delimited",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
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
    @Path("/search")
    @Produces("application/json")
    def search() {
        def q = params.get('q', null)
        def limit = params.int('limit', 20)
        def offset = params.int('start', 0)

        def includeFieldIds = params.get('include', '')
        def excludeFieldIds = params.get('exclude', '')

        if (q == null) {
            render status: 404, text: 'No search parameter q.'
        }
        try {
            q = UriUtils.decode(q.toString(), Charset.defaultCharset())
            q = q.trim().toLowerCase()
        } catch (UnsupportedEncodingException ignored) {
            render status: 404
        }

        // Results can differ greatly between the old and new search methods.
        if (StringUtils.isEmpty(includeFieldIds) && StringUtils.isEmpty(excludeFieldIds)) {
            render searchService.findByCriteria(q.toString(), offset, limit) as JSON
        } else {
            List<String> includeIds
            List<String> excludeIds
            if (StringUtils.isNotEmpty(includeFieldIds)) {
                includeIds = Arrays.asList(includeFieldIds.split(","))
            } else {
                includeIds = new ArrayList()
            }
            if (StringUtils.isNotEmpty(excludeFieldIds)) {
                excludeIds = Arrays.asList(excludeFieldIds.split(","))
            } else {
                excludeIds = new ArrayList()
            }
            render searchService.findByCriteria(q.toString(), offset, limit, (List<String>) includeIds, (List<String>) excludeIds) as JSON
        }
    }
}
