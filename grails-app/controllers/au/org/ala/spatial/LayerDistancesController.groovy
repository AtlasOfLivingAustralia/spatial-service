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
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse

import javax.ws.rs.Produces

class LayerDistancesController {

    LayerDistancesService layerDistancesService

    @Operation(
            method = "GET",
            tags = "layerDistances",
            operationId = "getLayerDistancesJSON",
            summary = "Get a layer association distances as JSON",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "Layer distances",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                    )
                            ])]
    )
    @Path("layerDistances/layerdistancesJSON")
    @Produces("application/json")
    def layerdistancesJSON() {
        render layerDistancesService.loadDistances() as JSON
    }

    @Operation(
            method = "GET",
            tags = "layerDistances",
            operationId = "getLayerDistancesRawnamesCSV",
            summary = "Get a layer association distances as CSV with internal names",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "Layer distances",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "text/csv"
                                    )
                            ])]
    )
    @Path("layerDistances/csvRawnames")
    @Produces("text/csv")
    def csvRawnames() {
        render text: layerDistancesService.makeCSV("name"), contentType: 'text/csv'
    }

    @Operation(
            method = "GET",
            tags = "layerDistances",
            operationId = "getLayerDistancesCSV",
            summary = "Get a layer association distances as CSV",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "Layer distances",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "text/csv"
                                    )
                            ])]
    )
    @Path("layerDistances/csv")
    @Produces("text/csv")
    def csv() {
        render text: layerDistancesService.makeCSV("displayname"), contentType: 'text/csv'
    }

}
