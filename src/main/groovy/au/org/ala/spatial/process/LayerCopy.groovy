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

package au.org.ala.spatial.process

import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.json.simple.JSONObject

@Slf4j
class LayerCopy extends SlaveProcess {

    void start() {
        def layerId = task.input.layerId
        def fieldId = task.input.fieldId
        def sourceUrl = task.input.sourceUrl

        //TODO: fetch default sld from geoserver
        def displayPath = task.input.displayPath

        def field = getField(fieldId)
        def layer = getLayer(layerId)

        //get style
        if (fieldId.toString().startsWith("cl")) {
            slaveService.getFile("/layer/${fieldId}.sld", sourceUrl)
            addOutput('sld', '/layer/' + fieldId + ".sld")
        } else {
            slaveService.getFile("/layer/${layer.name}.sld", sourceUrl)
            addOutput('sld', '/layer/' + layer.name + ".sld")
        }

        //get layer files
        //TODO: do not download layer files if they are already up to date
        slaveService.getFile("/layer/${layer.name}", sourceUrl)
        addOutputFiles("/layer/${layer.name}", true)

        //get standardized files
        taskLog("get standardized files")
        def resolutions
        if (layer.type == 'Contextual') resolutions = grailsApplication.config.shpResolutions
        else resolutions = grailsApplication.config.grdResolutions
        if (!(resolutions instanceof List)) {
            // comma separated or JSON list
            if (resolutions.toString().startsWith("[")) {
                resolutions = new org.json.simple.parser.JSONParser().parse(resolutions.toString())
            } else {
                resolutions = Arrays.asList(resolutions.toString().split(","))
            }
        }

        resolutions.each { res ->
            slaveService.getFile("/standard_layer/${res}/${field.id}", sourceUrl)
            addOutputFiles("/standard_layer/${res}/${field.id}")
        }

        //get layerdistances
        taskLog("get layer distances")
        slaveService.getFile('/public/layerDistances.properties')
        JSONObject dists = JSON.parse(Util.getUrl(sourceUrl + "/layerDistances/layerdistancesJSON.json"))
        def distString = ''
        for (def f : getFields()) {
            if ("e".equalsIgnoreCase(f.type) && !f.id.equals(field.id)) {
                String c = (f.id.compareTo(field.id) < 0 ? f.id + " " + field.id : field.id + " " + f.id)

                if (dists.containsKey(c)) {
                    if (distString.length() > 0) distString += '\n'
                    distString += f.id + ' ' + field.id + '=' + dists.get(c).toString()
                }
            }
        }
        if (distString.length() > 0) {
            addOutput('append', '/public/layerDistances.properties?' + distString)
        }

        def m = [fieldId: String.valueOf(field.id), uploadId: layer.id, skipSLDCreation: true]
        addOutput("process", "FieldCreation " + (m as JSON))
        addOutput("process", "Thumbnails " + ([] as JSON))
    }
}