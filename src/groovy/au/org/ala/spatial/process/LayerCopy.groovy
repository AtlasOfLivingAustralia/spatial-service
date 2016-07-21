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

import au.org.ala.layers.legend.GridLegend
import au.org.ala.layers.util.Bil2diva
import au.org.ala.spatial.slave.SpatialUtils
import au.org.ala.spatial.slave.Utils
import au.org.ala.spatial.util.GeomMakeValid
import grails.converters.JSON
import org.apache.commons.io.FileUtils

class LayerCopy extends SlaveProcess {

    void start() {
        String layerId = task.input.layerId
        String fieldId = task.input.fieldId

        // get layer info
        Map layer = getLayer(layerId)

        if (layer == null) {
            task.err.put(String.valueOf(System.currentTimeMillis()), "layer not found for id: " + layerId)
            return
        }

        //upload shp into layersdb in a table with name layer.id
        String dir = grailsApplication.config.data.dir
        File diva = new File(dir + "/layer/" + layer.name + ".grd")

        if (diva.exists()) {
            addOutput('layers', "/layer/" + layer.name + '.grd')
            addOutput('layers', "/layer/" + layer.name + '.gri')
            addOutput('layers', "/layer/" + layer.name + '.sld')
            addOutput('layers', "/layer/" + layer.name + '.tif')
            addOutput('layers', "/layer/" + layer.name + '.prj')
        }

        if (!diva.exists() && "Contextual".equalsIgnoreCase(layer.type.toString())) {
            String newName = '/layer/' + layer.name
            addOutput('layers', newName)
        } else {
            //TODO: grid as contextual copy
        }

        Map m = [fieldId: fieldId]
        addOutput("process", "FieldCreation " + (m as JSON))
    }
}