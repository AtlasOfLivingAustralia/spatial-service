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

import au.org.ala.scatterplot.Scatterplot
import au.org.ala.scatterplot.ScatterplotDTO
import au.org.ala.scatterplot.ScatterplotStyleDTO
import grails.converters.JSON
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser

class ScatterplotList extends SlaveProcess {

    void start() {

        //area to restrict (only interested in area.q part)
        JSONParser jp = new JSONParser()
        String area = jp.parse(task.input.area.toString())

        def species1 = JSON.parse(task.input.species1.toString())
        def species2 = JSON.parse(task.input.species2.toString())
        def layerList = (JSONArray) jp.parse(task.input.layer.toString())

        String[] layers = new String[layerList.size()]
        String[] layerNames = new String[layerList.size()]
        String[] layerUnits = new String[layerList.size()]
        layerList.eachWithIndex { field, idx ->
            def f = getField(field)

            def l = getLayer(f.spid)
            layers[idx] = field
            layerNames[idx] = l.displayname
            layerUnits[idx] = l.environmentalvalueunits
        }

        String fqs = species1.q
        String fbs = species1.bs
        String fname = species1.name

        String bqs = species2.q
        String bbs = species2.bs
        String bname = species2.name

        ScatterplotDTO desc = new ScatterplotDTO(fqs, fbs, fname, bqs, bbs, bname, '', null, null, null, null, 20, null, null, null)

        desc.setLayers(layers)
        desc.setLayerunits(layerUnits)
        desc.setLayernames(layerNames)

        ScatterplotStyleDTO style = new ScatterplotStyleDTO();

        new Scatterplot(desc, style, null, getTaskPath(), task.input.resolution.toString());

        File dir = new File(getTaskPath())
        for (File file : dir.listFiles()) {
            if (file.getName().equals("scatterplot.html")) addOutput("metadata", file.getName(), true)
            else addOutput('files', file.getName(), true)
        }
    }
}
