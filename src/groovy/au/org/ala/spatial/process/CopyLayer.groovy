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

import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.layers.dto.StoreRequest
import au.org.ala.layers.intersect.IntersectConfig
import au.org.ala.layers.util.LayerStoreFilesUtil
import au.org.ala.layers.util.Util
import au.org.ala.spatial.analysis.layers.LayerDistanceIndex
import au.org.ala.spatial.util.UploadSpatialResource
import grails.converters.JSON
import org.apache.commons.io.FileUtils
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.type.TypeReference
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import org.json.simple.parser.JSONParser

import java.text.MessageFormat

class CopyLayer extends SlaveProcess {

    void start() {
        def layerId = task.input.layerId
        def fieldId = task.input.fieldId
        def sourceUrl = task.input.sourceUrl

        //TODO: fetch default sld from geoserver
        def displayPath = task.input.displayPath

        def field = getField(fieldId)
        def layer = getLayer(layerId)

        //get style
        slaveService.getFile("/layer/${fieldId}.sld", sourceUrl)
        addOutput('sld', '/layer/' + fieldId + ".sld")

        //get layer files
        //TODO: do not download layer files if they are already up to date
        slaveService.getFile("/layer/${layer.name}", sourceUrl)
        addOutputFiles("/layer/${layer.name}", true)

        //get standardized files
        def resolutions
        if (layer.type == 'Contextual') resolutions = grailsApplication.config.shpResolutions
        else resolutions = grailsApplication.config.grdResolutions
        resolutions.each { res ->
            slaveService.getFile("/standard_layer/${res}/${field.id}", sourceUrl)
            addOutputFiles("/standard_layer/${res}/${field.id}")
        }

        //get tabulations
        def tabulations = JSON.parse(au.org.ala.layers.util.Util.readUrl(sourceUrl + "/tabulations.json"))
        File fname = new File(getTaskPath() + 'tabulationImport.sql')
        for (def tab : tabulations) {
            if ((tab.fid1 == field.id && fieldDao.getFieldById(tab.fid2)) ||
                    (tab.fid2 == field.id && fieldDao.getFieldById(tab.fid1))) {
                def data = JSON.parse(au.org.ala.layers.util.Util.readUrl(sourceUrl + "/data/${tab.fid1}/${tab.fid2}/tabulations.json"))

                def ids1 = [:]
                for (def obj : getObjects(tab.fid1)) {
                    ids1.put(obj.name, obj.pid)
                }
                def ids2 = [:]
                for (def obj : getObjects(tab.fid2)) {
                    ids2.put(obj.name, obj.pid)
                }

                //sql to delete existing entry
                FileUtils.writeStringToFile(fname, "DELETE FROM tabulation WHERE " +
                        "(fid1=${tab.fid1} AND fid2=${tab.fid2}) OR (fid1=${tab.fid1} AND fid2=${tab.fid2});", true)

                //sql to add new entries
                for (def row : data) {
                    FileUtils.writeStringToFile(fname, "INSERT INTO tabulation (fid1, fid2, pid1, pid2, species, occurrences, area, speciest1, speciest2) " +
                            "VALUES (${row.fid1},${row.fid2},${ids1.get(row.name1)},${row.ids1.get(row.name2)}," +
                            "${row.species},${row.occurrences},${row.area},${row.speciest1},${row.speciest2});", true)
                }
            }
        }
        if (fname.length() > 0) {
            addOutput('sql', fname)
        }

        //get layerdistances
        slaveService.getFile('/public/layerDistances.properties')
        JSONObject dists = JSON.parse(au.org.ala.layers.util.Util.readUrl(sourceUrl + "/layerdistances.json"))
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
    }
}
