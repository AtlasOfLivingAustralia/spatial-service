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
import au.org.ala.layers.dto.Layer
import grails.converters.JSON
import grails.io.IOUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils

import java.text.SimpleDateFormat

class LayerController {

    def layerDao
    def fieldDao
    def fileService
    def authService

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
        File f = new File(grailsApplication.config.data.dir.toString() + '/public/thumbnail/' + id + '.jpg')
        if (f.exists()) {
            OutputStream os = null
            InputStream is = null

            try {
                response.setContentType("image/jpg")
                os = response.outputStream
                is = new BufferedInputStream(new FileInputStream(f))
                IOUtils.copy(is, os)
                os.flush()
            } catch (Exception err) {
                log.debug 'failed to write layer image : ' + id, err
            } finally {
                if (os != null) {
                    try {
                        os.close()
                    } catch (Exception err1) {
                        log.trace(err1.getMessage(), err1)
                    }
                }
                if (is != null) {
                    try {
                        is.close()
                    } catch (Exception err2) {
                        log.trace(err2.getMessage(), err2)
                    }
                }
            }
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
                        String.valueOf(lyr.description.replaceAll("\n", " ")),
                        String.valueOf(lyr.source),
                        String.valueOf(lyr.source_link),
                        String.valueOf(lyr.respparty_role),
                        String.valueOf(lyr.mddatest),
                        String.valueOf(lyr.citation_date),
                        String.valueOf(lyr.licence_level),
                        String.valueOf(lyr.licence_link),
                        String.valueOf(lyr.licence_notes.replaceAll("\n", " ")),
                        String.valueOf(lyr.type),
                        String.valueOf(lyr.classification1),
                        String.valueOf(lyr.classification2),
                        String.valueOf(lyr.environmentalvalueunits),
                        String.valueOf(lyr.notes.replaceAll("\n", " ")),
                        String.valueOf(lyr.metadatapath),
                        String.valueOf(lyr.keywords),
                        String.valueOf(lyr.getDt_added() == null ? '' : sdf.format(lyr.getDt_added()))]

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

    def download(String id) {
        Layer l = layerDao.getLayerByDisplayName(id)
        if (downloadAllowed(l)) {
            OutputStream outputStream = null
            try {
                outputStream = response.outputStream as OutputStream
                //write resource
                response.setContentType("application/octet-stream")
                response.setHeader("Content-disposition", "attachment;filename=${id}.zip")

                // When a geotiff exists, only download the geotiff
                def path = "/layer/${l.name}"
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
            response.sendError(404, "$id not available")
        }
    }

    private downloadAllowed(layer) {
        return grailsApplication.config.download.layer.licence_levels.contains(layer.licence_level) ||
                authService.userInRole(grailsApplication.config.auth.admin_role)
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
            render l as JSON
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
