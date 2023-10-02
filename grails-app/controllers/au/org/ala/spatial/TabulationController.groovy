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
import au.org.ala.spatial.dto.Tabulation
import com.opencsv.CSVReader
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

class TabulationController {

    FieldService fieldService
    TabulationService tabulationService
    SpatialObjectsService spatialObjectsService
    SpatialConfig spatialConfig

    def index() {
        def tabulations = tabulationService.listTabulations()

        if (params.format == 'json') {
            render tabulations as JSON
        } else {
            render(view: "index.gsp", model: [tabulations: tabulations])
        }
    }

    @Operation(
            method = "GET",
            tags = "tabulations",
            operationId = "listTabulations",
            summary = "List of field pairs with pre-generated tabulations",
            responses = [
                    @ApiResponse(
                            description = "List of tabulations",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                            //array = @ArraySchema(schema = @Schema(implementation = Tabulation))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tabulation/list")
    @Produces("application/json")
    def list() {
        render tabulationService.listTabulations() as JSON
    }

    @Operation(
            method = "GET",
            tags = "tabulations",
            operationId = "intersectTabulation",
            summary = "Intersect a field with an area and return tabulated intersection information",
            parameters = [
                    @Parameter(
                            name = "fid",
                            in = PATH,
                            description = "Field ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "pid",
                            in = PATH,
                            description = "Object ID",
                            schema = @Schema(implementation = String),
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Intersections as Tabulations",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                            //array = @ArraySchema(schema = @Schema(implementation = Tabulation))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tabulation/single/{fid}/{pid}")
    @Produces("application/json")
    def single() {
        String fid = params.fid
        String pid = params.pid
        if (pid) pid = pid.replace(".json", "")
        if ("single".equalsIgnoreCase(fid)) {
            fid = pid
            pid = params.containsKey('wkt') ? params.wkt : ''
        }
        if (!params.wkt && pid) {
            pid = spatialObjectsService.getObjectsGeometryById(pid, 'wkt')
        }

        // tabulationDao.getTabulationSingle is slow, force a timeout

        def tabulation
        Thread thread = new Thread() {
            @Override
            void run() {
                tabulation = tabulationService.getTabulationSingle(fid, pid)
            }
        }

        thread.start()
        Thread.sleep(1000)

        long start = System.currentTimeMillis()
        while (thread.isAlive() && start + spatialConfig.controller.timeout > System.currentTimeMillis()) {
            Thread.sleep(1000)
        }

        // loop because irregular exception handling
        while (thread.isAlive()) {
            thread.interrupt()
            thread.stop()
            Thread.sleep(1000)
        }

        if (tabulation) {
            render tabulation as JSON
        } else {
            render status: 404
        }
    }

    @Operation(
            method = "GET",
            tags = "tabulations",
            operationId = "showTabulation",
            summary = "Get details of a field pair intersection",
            parameters = [
                    @Parameter(
                            name = "fid1",
                            in = PATH,
                            description = "Field ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "fid2",
                            in = PATH,
                            description = "Field ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "wkt",
                            in = QUERY,
                            description = "WKT to restrict the tabulated area",
                            schema = @Schema(implementation = String),
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Intersections as Tabulations",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json"
                                            //array = @ArraySchema(schema = @Schema(implementation = Tabulation))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("/tabulation/data/{fid1}/{fid2}/tabulation.json")
    @Produces("application/json")
    def show() {
        String func1 = params.func1
        String fid1 = params.fid1
        String fid2 = params.fid2
        String type = params.type
        String wkt = params?.wkt
        if (params?.wkt && params.wkt.toString().isNumber()) {
            wkt = spatialObjectsService.getObjectsGeometryById(params.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        if ("single".equalsIgnoreCase(func1)) {
            func1 = fid1
            fid1 = fid2
            fid2 = null
        }

        if ("data" == func1) {
            render tabulationService.getTabulation(fid1, fid2, wkt) as JSON
        } else {
            String data = tabulationService.generateTabulationCSVHTML(fid1, fid2, wkt, func1, "html" == type ? "csv" : type)

            if ("html" == type) {
                CSVReader reader = new CSVReader(new StringReader(data))
                List<String[]> csv = reader.readAll()
                reader.close()

                String label = 'Tabulation for "' + fieldService.getFieldById(fid1).name + '" and "' +
                        fieldService.getFieldById(fid2).name + '"'
                if ('area' == func1) label += ' - (sq km) for Area (square kilometres)'
                if ('species' == func1) label += ' - Number of species'
                if ('occurrences' == func1) label += ' - Number of occurrences'

                String info = 'Occurrences and species numbers are reported correctly but the area of some intersections may be reported as "0" sq.km. when they are < 50% of the smallest grid cell used for tabulation.'

                render(view: "show.gsp", model: [data: csv, label: label, info: info])
            } else {
                if ("csv" == type) {
                    response.setContentType("text/comma-separated-values")
                } else if ("json" == type) {
                    response.setContentType("application/json")
                }
                OutputStream os = null
                try {
                    os = response.getOutputStream()
                    os.write(data.getBytes("UTF-8"))
                    os.flush()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                } finally {
                    if (os != null) {
                        try {
                            os.close()
                        } catch (err) {
                            log.trace(err.getMessage(), err)
                        }
                    }
                }
            }
        }
    }
}
