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

package au.org.ala.layers

import au.com.bytecode.opencsv.CSVWriter
import au.org.ala.RequirePermission
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.spatial.service.Log
import au.org.ala.spatial.service.Task
import grails.converters.JSON
import grails.io.IOUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.apache.commons.lang3.ArrayUtils

import java.text.SimpleDateFormat

class LayerController {

    def layerDao
    def fieldDao
    def objectDao
    def fileService

    def list() {
        def fields = fieldDao.getFieldsByCriteria('')
        def layers = layerDao.getLayers().collect { layer ->
            def map = layer.toMap()
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

    def img(String id) {
        if (layerDao.getLayerByName(id)) {
            File f = new File(grailsApplication.config.data.dir.toString() + '/public/thumbnail/' + id + '.jpg')
            render(file: f, fileName: "${id}.jpg")
        } else {
            response.sendError(404, "$id not found")
            return
        }
    }

    def csvlist() {
        List layers = layerDao.getLayers()

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

        def usage
        if ("true".equalsIgnoreCase(params.usage)) {
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

            List<String[]> mylist = new Vector<String[]>()
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd")
            for (Layer lyr : layers) {
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

                if ("true".equalsIgnoreCase(params.usage)) {
                    row = ArrayUtils.add(row, String.valueOf(usage.getOrDefault(String.valueOf(lyr.id), 0)))
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
    private def layerUsage(months) {
        def layerUsage = [:]

        def c = Calendar.getInstance()
        c.add(Calendar.MONTH, params.ageInMonths ?: -1 * months)

        // Use as layer
        def fields = fieldDao.getFields()

        def layers = [:]
        Log.executeQuery("SELECT data FROM Log WHERE created is not null and created >= ${c.time} and category2='ToolAddLayerService'").each {
            try {
                if (it) {
                    def layer = JSON.parse(it)
                    String layerId = layer['0'][0]
                    layers.put(layerId, layers.getOrDefault(layerId, 0) + 1)
                }
            } catch(Exception e) {
                log.error("Error in calculating usage of tool: add layer")
            }
        }
        layerUsage.put("Add to Map | Layer", layers)

        layers = [:]
        Log.executeQuery("SELECT data FROM Log WHERE created is not null and created >= ${c.time} and category2='Tabulation'").each {
            try {
                def layer1 = JSON.parse(it).getAt("layer1")
                layers.put(layer1, layers.getOrDefault(layer1, 0) + 1)
                def layer2 = JSON.parse(it).getAt("layer2")
                layers.put(layer2, layers.getOrDefault(layer2, 0) + 1)
            } catch(Exception e) {
                log.error("Error in calculating usage of tool: tabulation")
            }
        }
        layerUsage.put("Tools | Tabulate", layers)

        layers = [:]
        Log.executeQuery("SELECT data FROM Log WHERE data is not null and created is not null and created >= ${c.time} and category2='Area'").each {
            try {
                def pid = JSON.parse(it).getAt("pid")
                if (pid != null) {
                    for (String p : pid.split("~")) {
                        def obj = objectDao.getObjectByPid(p)
                        if (obj) {
                            if (obj.fid != grailsApplication.config.userObjectsField) {
                                layers.put(obj.fid, layers.getOrDefault(obj.fid, 0) + 1)
                            }
                        }
                    }
                }
            } catch(Exception e) {
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
            }catch (Exception e) {
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
            }catch (Exception e) {
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
            }catch (Exception e) {
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
            }catch (Exception e) {
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
            }catch (Exception e) {
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
            }catch (Exception e) {
                log.error("Error in calculating usage of tool: speccies by layer")
            }
        }
        layerUsage.put("Tools | Species By Layer", layers)

        // convert to fieldIds to layerIds and aggregate.
        layers = [:]

        layerUsage.each { k1, v1 ->
            v1.each { k2, v2 ->
                for (Field f : fields) {
                    if (f.id == k2) {
                        layers.put(f.spid, layers.getOrDefault(f.spid, 0) + v2)
                    }
                }
            }
        }
        layerUsage.put("Total", layers)

        return layerUsage

    }

    @RequirePermission
    def download(String id) {
        Layer layer = layerDao.getLayerByDisplayName(id)
        if (layer) {
            if (downloadAllowed()) {
                OutputStream outputStream = null
                try {
                    outputStream = response.outputStream as OutputStream
                    //write resource
                    response.setContentType("application/octet-stream")
                    response.setHeader("Content-disposition", "attachment;filename=${id}.zip")

                    // When a geotiff exists, only download the geotiff
                    def path = "/layer/${layer.name}"
                    def geotiff = new File(grailsApplication.config.data.dir + path + ".tif")
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
                response.sendError(403, "Downloding $id is prohabited by licence!")
            }
        } else {
            response.sendError(404, "$id not available")
        }
    }

    private downloadAllowed(layer) {
        return grailsApplication.config.getProperty('download.layer.licence_levels', String, '').contains(layer.licence_level)
    }

    def more(String id) {
        Layer l = layerDao.getLayerByName(id)

        if (l == null) {

            try {
                l = layerDao.getLayerById(Integer.parseInt(id))
            } catch (err) {
                log.error 'failed to get layer: ' + id, err
            }
        }

        render(view: "show.gsp", model: [layer: l.toMap(), downloadAllowed: downloadAllowed(l)])
    }

    def index(String id) {
        if (id == null)
            if (params?.all?.toBoolean()) {
                render layerDao.getLayersForAdmin() as JSON
            } else {
                render layerDao.getLayers() as JSON
            }
        else
            show(id)
    }

    /**
     * This method returns a single layer, provided an id
     *
     */
    def show(String id) {
        if (id == null) {
            render layerDao.getLayers() as JSON
        } else {
            Layer l = null
            try {
                l = layerDao.getLayerById(Integer.parseInt(id), false)
            } catch (err) {
                log.error 'failed to get layer: ' + id, err
            }

            if (l == null) {
                l = layerDao.getLayerByName(id, false)
            }

            if (l == null) {
                Field f = fieldDao.getFieldById(id, false)
                if (f != null) {
                    l = layerDao.getLayerById(Integer.parseInt(f.spid), false)
                }
            }

            if (l){
                render l as JSON
            } else {
                response.sendError(404, "Layer not found")
            }
        }
    }

    /**
     * This method returns all layers
     *
     */
    def search() {
        render layerDao.getLayersByCriteria(params.q.toString()) as JSON
    }

    def grids() {
        render layerDao.getLayersByEnvironment() as JSON
    }

    def shapes() {
        render layerDao.getLayersByContextual() as JSON
    }

    /**
     * Return layers list if RIF-CS XML format
     *
     * @param req
     * @param res
     * @throws Exception
     */
    def "rif-cs"() {
        // Build XML by hand here because JSP processing seems to omit CDATA sections from the output
        StringBuilder sb = new StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>")
        sb.append("<registryObjects xmlns=\"http://ands.org.au/standards/rif-cs/registryObjects\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://ands.org.au/standards/rif-cs/registryObjects http://services.ands.org.au/documentation/rifcs/schema/registryObjects.xsd\">")
        for (Layer layer : layerDao.getLayers()) {
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
