/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License") you may not use this file
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
import com.opencsv.CSVWriter
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.ArrayUtils

import javax.ws.rs.Produces
import java.text.SimpleDateFormat

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class LayerController {

    LayerService layerService
    FieldService fieldService
    SpatialObjectsService spatialObjectsService
    FileService fileService
    SpatialConfig spatialConfig

    // HTML Page
    def list() {
        def fields = fieldService.getFieldsByCriteria('')
        def layers = layerService.getLayers().collect { layer ->
            def map = layer as Map
            fields.each { field ->
                if (field.spid == layer.id.toString()) {
                    if (map?.last_update) {
                        map.put('last_update', field?.last_update?.getTime() < ((Date) map?.last_update)?.getTime() ? field.last_update : map.last_update)
                    } else {
                        map.put('last_update', field.last_update)
                    }
                }
            }
            map
        }

        render(view: "index.gsp", model: [layers: layers])
    }

    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "getLayerImage",
            summary = "Get thumbnail image of layer",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Layer ID",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Image",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "image/jpg"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layer/img")
    @Produces("image/jpg")
    def img(String id) {
        if (layerService.getLayerByName(id)) {
            File f = new File(spatialConfig.data.dir + '/public/thumbnail/' + id + '.jpg')
            render(file: f, fileName: "${id}.jpg")
        } else {
            response.sendError(404, "$id not found")
        }
    }

    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "getLayersCSV",
            summary = "Get CSV list of layers",
            parameters = [
                    @Parameter(
                            name = "usage",
                            in = QUERY,
                            schema = @Schema(implementation = Boolean),
                            description = "Set as True to include layer usage information",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "CSV of layers",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/csv"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layers/layers.csv")
    @Produces("application/csv")
    def csvlist() {
        List layers = layerService.getLayers()

        String[] header = ["ID",
                           "Short name",
                           "Name",
                           "Description",
                           "Data provider",
                           "Provider website",
                           "Provider role",
                           "Metadata date",
                           "Reference date",
                           "Licence level",
                           "Licence info",
                           "Licence notes",
                           "Type",
                           "Classification 1",
                           "Classification 2",
                           "Units",
                           "Notes",
                           "More information",
                           "Keywords", "Date Added"]

        Map<String, Map> usage = null
        if ("true".equalsIgnoreCase(params.usage as String)) {
            header = ArrayUtils.addAll(header, "Usage")
            usage = layerUsage(params.months as Integer).get("Total")
        }

        response.setContentType("text/csv charset=UTF-8")
        response.setHeader("Content-Disposition", "inlinefilename=ALA_Spatial_Layers.csv")
        CSVWriter cw = null

        try {
            cw = new CSVWriter(response.getWriter())
            cw.writeNext("Please provide feedback on the 'keywords' columns to data_management@ala.org.au".split("\n"))
            cw.writeNext(header)

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd")
            for (Layers lyr : layers) {
                String[] row = [
                        String.valueOf(lyr.id),
                        String.valueOf(lyr.name),
                        String.valueOf(lyr.displayname),
                        String.valueOf(lyr.description?.replaceAll("\n", " ")),
                        String.valueOf(lyr.source),
                        String.valueOf(lyr.source_link),
                        String.valueOf(lyr.respparty_role),
                        String.valueOf(lyr.mddatest),
                        String.valueOf(lyr.citation_date),
                        String.valueOf(lyr.licence_level),
                        String.valueOf(lyr.licence_link),
                        String.valueOf(lyr.licence_notes?.replaceAll("\n", " ")),
                        String.valueOf(lyr.type),
                        String.valueOf(lyr.classification1),
                        String.valueOf(lyr.classification2),
                        String.valueOf(lyr.environmentalvalueunits),
                        String.valueOf(lyr.notes?.replaceAll("\n", " ")),
                        String.valueOf(lyr.metadatapath),
                        String.valueOf(lyr.keywords),
                        String.valueOf(lyr.getDt_added() == null ? '' : sdf.format(lyr.getDt_added()))]

                if ("true".equalsIgnoreCase(params.usage as String)) {
                    row = ArrayUtils.add(row, (String) usage.get("Totals").getOrDefault(lyr.id , 0) as String)
                }

                cw.writeNext(row)
            }
            cw.flush()
        } catch (err) {
            log.trace(err.getMessage(), err)
        } finally {
            if (cw != null) {
                try {
                    cw.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }

    }

    /**
     * Get layer usage count by usage type. Includes 'Total' for all usage.
     *
     * Layer usage is determined from specific Log and Task records created by spatial-hub.
     *
     * spatial-hub use of a layer
     *
     *         Add to Map | Layer
     *         log search
     *         category2=ToolAddLayerService
     *         data.0 -> array of fieldIds
     *
     *         Add to Map | Area | Environmental envelope
     *         task search
     *         name=Envelope
     *         input[name=envelope].value -> array of string -> split(':')[0] for fieldIds
     *
     *         Tools | Scatterplot - single
     *         task search
     *         name=ScatterplotCreate
     *         input[name=layer].value -> array of fieldIds
     *
     *         Tools | Scatterplot - multiple
     *         task search
     *         name=ScatterplotList
     *         input[name=layer].value -> array of fieldIds
     *
     *         Tools | Tabulate
     *         task search
     *         category2=Tabulation
     *         data.layer1 and data.layer2
     *
     *         Tools | Predict
     *         task search
     *         name=Maxent
     *         input[name=layer].value -> array of fieldIds
     *
     *         Tools | Classify
     *         task search
     *         name=Classification
     *         input[name=layer].value -> array of fieldIds
     *
     *         Tools | Classify
     *         task search
     *         name=SpeciesByLayer
     *         input[name=layer].value -> array of fieldIds
     *
     *  spatial-hub use of an area
     *
     *         Add to Map | Area | Select area from polygonal layer
     *         Add to Map | Area | Gazetteer polygon
     *         log search
     *         category2=Area
     *         data.pid -> select fid from objects where pid=data.pid
     *
     * @return
     */
    private Map<String, Map> layerUsage(Integer months) {
        Map<String, Map> layerUsage = [:]

        def c = Calendar.getInstance()
        c.add(Calendar.MONTH, params.ageInMonths as Integer ?: -1 * months)

        // Use as layer
        def fields = fieldService.getFields(false)

        def layers = [:]
        Log.executeQuery("SELECT data FROM Log WHERE created is not null and created >= ${c.time} " +
                "and category2='ToolAddLayerService'").each { it ->
            try {
                if (it) {
                    def layer = JSON.parse(it)
                    String layerId = layer['0'][0]
                    layers.put(layerId, layers.getOrDefault(layerId, 0) + 1)
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: add layer")
            }
        }
        layerUsage.put("Add to Map | Layer", layers)

        layers = [:]
        Log.executeQuery("SELECT data FROM Log WHERE created is not null and created >= ${c.time} and category2='Tabulation'").each {
            try {
                def layer1 = JSON.parse(it)["layer1"]
                layers.put(layer1, layers.getOrDefault(layer1, 0) + 1)
                def layer2 = JSON.parse(it)["layer2"]
                layers.put(layer2, layers.getOrDefault(layer2, 0) + 1)
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: tabulation")
            }
        }
        layerUsage.put("Tools | Tabulate", layers)

        layers = [:]
        Log.executeQuery("SELECT data FROM Log WHERE data is not null and created is not null and created >= ${c.time} and category2='Area'").each {
            try {
                String pid = JSON.parse(it)["pid"]
                if (pid != null) {
                    for (String p : pid.split("~")) {
                        def obj = spatialObjectsService.getObjectByPid(p)
                        if (obj) {
                            if (obj.fid != spatialConfig.userObjectsField) {
                                layers.put(obj.fid, layers.getOrDefault(obj.fid, 0) + 1)
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of areas")
            }
        }
        layerUsage.put("Add to map | Area | Gaz or Area from polygonal layer", layers)

        layers = [:]
        Task.executeQuery("SELECT input FROM Task t where t.created is not null and t.created >= ${c.time} and t.status = 4 and t.name = 'Envelope'").each {
            try {
                if (it.name == 'envelope') {
                    JSON.parse(it.value).each {
                        def layer = it.split(':')[0]
                        layers.put(layer, layers.getOrDefault(layer, 0) + 1)
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of envelopes")
            }

        }
        layerUsage.put("Add to Map | Area | Environmental envelope", layers)

        layers = [:]
        Task.executeQuery("SELECT input FROM Task t where t.created is not null and t.created >= ${c.time} and t.status = 4 and t.name = 'ScatterplotCreate'").each {
            try {
                if (it.name == 'layer') {
                    JSON.parse(it.value).each {
                        layers.put(it, layers.getOrDefault(it, 0) + 1)
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: single scatterplot ")
            }
        }
        layerUsage.put("Tools | Scatterplot - single", layers)

        layers = [:]
        Task.executeQuery("SELECT input FROM Task t where t.created is not null and t.created >= ${c.time} and t.status = 4 and t.name = 'ScatterplotList'").each {
            try {
                if (it.name == 'layer') {
                    JSON.parse(it.value).each {
                        layers.put(it, layers.getOrDefault(it, 0) + 1)
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: multiple scatterplot")
            }
        }
        layerUsage.put("Tools | Scatterplot - multiple", layers)

        layers = [:]
        Task.executeQuery("SELECT input FROM Task t where t.created is not null and t.created >= ${c.time} and t.status = 4 and t.name = 'Maxent'").each {
            try {
                if (it.name == 'layer') {
                    JSON.parse(it.value).each {
                        layers.put(it, layers.getOrDefault(it, 0) + 1)
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: predict")
            }
        }
        layerUsage.put("Tools | Predict", layers)

        layers = [:]
        Task.executeQuery("SELECT input FROM Task t where t.created is not null and t.created >= ${c.time} and t.status = 4 and t.name = 'Classification'").each {
            try {
                if (it.name == 'layer') {
                    JSON.parse(it.value).each {
                        layers.put(it, layers.getOrDefault(it, 0) + 1)
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: classify")
            }
        }
        layerUsage.put("Tools | Classify", layers)

        layers = [:]
        Task.executeQuery("SELECT input FROM Task t where t.created is not null and t.created >= ${c.time} and t.status = 4 and t.name = 'SpeciesByLayer'").each {
            try {
                if (it.name == 'layer') {
                    JSON.parse(it.value).each {
                        layers.put(it, layers.getOrDefault(it, 0) + 1)
                    }
                }
            } catch (Exception e) {
                log.error("Error in calculating usage of tool: speccies by layer")
            }
        }
        layerUsage.put("Tools | Species By Layer", layers)

        // convert to fieldIds to layerIds and aggregate.
        layers = [:]

        layerUsage.each { k1, v1 ->
            v1.each { k2, v2 ->
                for (Fields f : fields) {
                    if (f.id == k2) {
                        layers.put(f.spid, layers.getOrDefault(f.spid, 0) + v2)
                    }
                }
            }
        }
        layerUsage.put("Total", layers)

        layerUsage

    }

    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "getLayerFile",
            summary = "Get zipped copy of the layer if permitted",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = QUERY,
                            schema = @Schema(implementation = String),
                            description = "Layer ID",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "zipped layer file",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/zip"
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layer/download")
    @Produces("application/zip")
    @RequirePermission
    def download(String id) {
        Layers layer = layerService.getLayerByDisplayName(id)
        if (layer) {
            if (downloadAllowed(layer)) {
                OutputStream outputStream = null
                try {
                    outputStream = response.outputStream as OutputStream
                    //write resource
                    response.setContentType("application/octet-stream")
                    response.setHeader("Content-disposition", "attachment;filename=${id}.zip")

                    // When a geotiff exists, only download the geotiff
                    def path = "/layer/${layer.name}"
                    def geotiff = new File(spatialConfig.data.dir + path + ".tif")
                    if (geotiff.exists()) {
                        path += ".tif"
                    }

                    fileService.write(outputStream, path)
                    outputStream.flush()
                } catch (err) {
                    log.error(err.getMessage(), err)
                } finally {
                    if (outputStream != null) {
                        try {
                            outputStream.close()
                        } catch (err) {
                            log.error(err.getMessage(), err)
                        }
                    }
                }
            } else {
                response.status = 403
            }
        } else {
            response.status = 404
        }
    }

    private downloadAllowed(layer) {
        return spatialConfig.download.layer.licence_levels.contains(layer.licence_level)
    }

    // HTML Page
    def more(String id) {
        Layers l = layerService.getLayerByName(id)

        if (l == null) {

            try {
                l = layerService.getLayerById(Integer.parseInt(id))
            } catch (err) {
                log.error 'failed to get layer: ' + id, err
            }
        }

        render(view: "show.gsp", model: [layer: l as Map, downloadAllowed: downloadAllowed(l)])
    }

    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "listLayers",
            summary = "Get a list of all layers",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of layers",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Layers))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layers")
    @Produces("application/json")
    def index(String id) {
        if (id == null)
            if (params?.all as Boolean) {
                render layerService.getLayersForAdmin() as JSON
            } else {
                render layerService.getLayers() as JSON
            }
        else
            show(id)
    }

    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "getLayerById",
            summary = "Get a layer by layer ID",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            schema = @Schema(implementation = String),
                            description = "Layer ID",
                            required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Layer",
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
    @Path("layer")
    @Produces("application/json")
    def show(String id) {
        if (id == null) {
            render layerService.getLayers() as JSON
        } else {
            Layers l = null
            try {
                l = layerService.getLayerById(Integer.parseInt(id), false)
            } catch (err) {
                log.error 'failed to get layer: ' + id, err
            }

            if (l == null) {
                l = layerService.getLayerByName(id, false)
            }

            if (l == null) {
                Fields f = fieldService.getFieldById(id, false)
                if (f != null) {
                    l = layerService.getLayerById(Integer.parseInt(f.spid), false)
                }
            }

            if (l) {
                render l as JSON
            } else {
                response.status = 404
            }
        }
    }

    @Operation(
            method = "GET",
            tags = "layers",
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
    @Path("layers/search")
    @Produces("application/json")
    def search() {
        render layerService.getLayersByCriteria(params.q as String) as JSON
    }

    @Deprecated
    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "listEnvironmentalLayers",
            summary = "Get a list of all environmental (raster) layers",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of layers",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Layers))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layers")
    @Produces("application/json")
    def grids() {
        render layerService.getLayersByEnvironment() as JSON
    }

    @Deprecated
    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "listContextualLayers",
            summary = "Get a list of all contextual (vector) layers",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of layers",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            array = @ArraySchema(schema = @Schema(implementation = Layers))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layers")
    @Produces("application/json")
    def shapes() {
        render layerService.getLayersByContextual() as JSON
    }

    @Deprecated
    @Operation(
            method = "GET",
            tags = "layer",
            operationId = "listLayersRIFCS",
            summary = "Get a list of all layers in RIF-CS format",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "List of layers",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "text/xml",
                                            array = @ArraySchema(schema = @Schema(implementation = Layers))
                                    )
                            ]
                    )
            ],
            security = []
    )
    @Path("layers")
    @Produces("application/json")
    def "rif-cs"() {
        // Build XML by hand here because JSP processing seems to omit CDATA sections from the output
        StringBuilder sb = new StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>")
        sb.append("<registryObjects xmlns=\"https://ands.org.au/standards/rif-cs/registryObjects\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://ands.org.au/standards/rif-cs/registryObjects http://services.ands.org.au/documentation/rifcs/schema/registryObjects.xsd\">")
        for (Layers layer : layerService.getLayers()) {
            sb.append("<registryObject group=\"Atlas of Living Australia\">")
            sb.append("<key>ala.org.au/uid_" + layer.getUid() + "</key>")
            sb.append("<originatingSource><![CDATA[" + layer.getMetadatapath() + "]]></originatingSource>")
            sb.append("<collection type=\"dataset\">")
            sb.append("<identifier type=\"local\">ala.org.au/uid_" + layer.getUid() + "</identifier>")
            sb.append("<name type=\"abbreviated\">")
            sb.append("<namePart>" + layer.getName() + "</namePart>")
            sb.append("</name>")
            sb.append("<name type=\"alternative\">")
            sb.append("<namePart><![CDATA[" + layer.getDescription() + "]]></namePart>")
            sb.append("</name>")
            sb.append("<name type=\"primary\">")
            sb.append("<namePart><![CDATA[" + layer.getDescription() + "]]></namePart>")
            sb.append("</name>")
            sb.append("<location>")
            sb.append("<address>")
            sb.append("<electronic type=\"url\">")
            sb.append("<value>https://spatial.ala.org.au/layers</value>")
            sb.append("</electronic>")
            sb.append("</address>")
            sb.append("</location>")
            sb.append("<relatedObject>")
            sb.append("<key>Contributor:Atlas of Living Australia</key>")
            sb.append("<relation type=\"hasCollector\" />")
            sb.append("</relatedObject>")
            sb.append("<subject type=\"anzsrc-for\">0502</subject>")
            sb.append("<subject type=\"local\">" + layer.getClassification1() + "</subject>")
            if (!StringUtils.isEmpty(layer.getClassification2())) {
                sb.append("<subject type=\"local\">" + layer.getClassification2() + "</subject>")
            }
            sb.append("<description type=\"full\"><![CDATA[" + layer.getNotes() + "]]></description>")
            sb.append("<relatedInfo type=\"website\">")
            sb.append("<identifier type=\"uri\"><![CDATA[" + layer.getMetadatapath() + "]]></identifier>")
            sb.append("<title>Further metadata</title>")
            sb.append("</relatedInfo>")
            sb.append("<relatedInfo type=\"website\">")
            sb.append("<identifier type=\"uri\"><![CDATA[" + layer.getSource_link() + "]]></identifier>")
            sb.append("<title>Original source of this data</title>")
            sb.append("</relatedInfo>")
            sb.append("<rights>")
            sb.append("<licence ")
            if (!StringUtils.isEmpty(layer.getLicence_link())) {
                sb.append("rightsUri=\"" + StringEscapeUtils.escapeXml(layer.getLicence_link()) + "\">")
            } else {
                sb.append(">")
            }
            sb.append("<![CDATA[" + layer.getLicence_notes() + "]]></licence>")
            sb.append("</rights>")
            sb.append("<coverage>")
            sb.append("<spatial type=\"iso19139dcmiBox\">northlimit=" + layer.getMaxlatitude() + " southlimit=" + layer.getMinlatitude() + " westlimit=" + layer.getMinlongitude() + " eastLimit=" + layer.getMaxlongitude() + " projection=WGS84</spatial>")
            sb.append("</coverage>")
            sb.append("</collection>")
            sb.append("</registryObject>")
        }
        sb.append("</registryObjects>")

        render(contentType: "text/xml", text: sb.toString())
    }
}
