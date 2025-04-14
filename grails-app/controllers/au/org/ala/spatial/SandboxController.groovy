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
import au.org.ala.spatial.dto.SandboxIngress
import au.org.ala.web.AuthService
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.web.multipart.MultipartFile

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class SandboxController {

    SpatialObjectsService spatialObjectsService

    SpatialConfig spatialConfig
    def dataSource
    def sandboxService
    AuthService authService

    @Operation(
            method = "POST",
            tags = "uploads",
            operationId = "uploadCSV",
            summary = "Upload a CSV or zipped CSV file containing occurrence points",
            parameters = [
                    @Parameter(
                            name = "name",
                            in = QUERY,
                            description = "datasetName",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            requestBody = @RequestBody(
                    description = "Uploaded CSV or zipped CSV file",
                    content = @Content(
                            mediaType = 'application/zip',
                            schema = @Schema(
                                    type = "string",
                                    format = "binary"
                            )
                    )
            ),
            responses = [
                    @ApiResponse(
                            description = "Recognised header and a dataResourceUid",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = [@SecurityRequirement(name = 'openIdConnect')]
    )
    @Path("/sandbox/upload")
    @Produces("application/json")
    @RequireApiKey
    def upload() {
        if (!spatialConfig.sandboxEnabled) {
            return [error: "Sandbox is disabled"] as JSON
        }

        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>()

        // Parse the request
        Map<String, MultipartFile> items = request.getFileMap()

        if (items.size() == 1) {
            MultipartFile fileItem = items.values()[0]

            SandboxIngress info = sandboxService.upload(fileItem, (String) params.name, authService.getUserId())
            if (info) {
                render info as JSON
                return
            } else {
                retMap.put("error", "Failed to upload file")
            }
        } else {
            retMap.put("error", items.size() + " files sent in request. A single zipped CSV file should be supplied.")
        }

        render retMap as JSON
    }

    // delete service
    @Operation(
            method = "DELETE",
            tags = "uploads",
            operationId = "deleteCSV",
            summary = "Delete a CSV or zipped CSV file containing occurrence points",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = QUERY,
                            description = "datasetId",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Delete the file",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = [@SecurityRequirement(name = 'openIdConnect')]
    )
    @Path("/sandbox/delete")
    @Produces("application/json")
    @RequireApiKey
    def delete() {
        if (!spatialConfig.sandboxEnabled) {
            return [error: "Sandbox is disabled"] as JSON
        }

        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>()

        // Parse the request
        String id = params.id

        if (id) {
            boolean successful = sandboxService.delete(id, authService.getUserId(), authService.userInRole("ROLE_ADMIN"))
            if (successful) {
                retMap.put("message", "File deleted")
                render retMap as JSON
                return
            }
        } else {
            retMap.put("error", "No file id supplied")
        }

        render retMap as JSON, status: 500
    }

    // status service
    @Operation(
            method = "GET",
            tags = "uploads",
            operationId = "statusCSV",
            summary = "Get the status of a CSV or zipped CSV file containing occurrence points",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = QUERY,
                            description = "datasetId",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Status of the file",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ]
                    )
            ],
            security = [@SecurityRequirement(name = 'openIdConnect')]
    )
    @Path("/sandbox/status")
    @Produces("application/json")
    def status() {
        if (!spatialConfig.sandboxEnabled) {
            return [error: "Sandbox is disabled"] as JSON
        }

        // Use linked hash map to maintain key ordering
        Map<Object, Object> retMap = new LinkedHashMap<Object, Object>()

        // Parse the request
        String id = params.id

        if (id) {
            SandboxIngress info = sandboxService.getStatus(id)
            if (info) {
                render info as JSON
                return
            } else {
                retMap.put("error", "Failed to get status")
            }
        } else {
            retMap.put("error", "No file id supplied")
        }

        render retMap as JSON, status: 500
    }
}

