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
import groovy.util.logging.Slf4j
import sun.reflect.annotation.ExceptionProxy

@Slf4j
class ScatterplotCreate extends SlaveProcess {

    void start() {

        Boolean grid = task.input.grid.toString().toBoolean()

        //area to restrict (only interested in area.q part)
        def area = JSON.parse(task.input.area.toString())

        String layersServiceUrl = task.input.layersServiceUrl

        def species1 = JSON.parse(task.input.species1.toString())
        def species2 = JSON.parse(task.input.species2.toString())
        def layerList = JSON.parse(task.input.layer.toString())

        def speciesArea1 = getSpeciesArea(species1, area)
        def speciesArea2 = species2?.q ? getSpeciesArea(species2, area) : null

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

        String fqs = speciesArea1.q
        String fbs = speciesArea1.bs
        String fname = speciesArea1.name

        String bqs = speciesArea2?.q
        String bbs = speciesArea2?.bs
        String bname = speciesArea2?.name

        ScatterplotDTO desc = new ScatterplotDTO(fqs, fbs, fname, bqs, bbs, bname, '', null, null, null, null, grid ? 20 : -1, null, null, null)

        desc.setLayers(layers)
        desc.setLayerunits(layerUnits)
        desc.setLayernames(layerNames)

        try {
            ScatterplotStyleDTO style = new ScatterplotStyleDTO()
            taskLog("Creating scatter plot.....")
            Scatterplot scatterplot = new Scatterplot(desc, style, null, getTaskPath(), task.input.resolution.toString(), task.input.layersServiceUrl)

            if (layers.length <= 2) {
                taskLog("Generate plot style")
                scatterplot.reStyle(style, false, false, false, false, false, false, false)

                File file = new File(getTaskPath() + "data.xml")
                scatterplot.save(file)

                File csvFile = new File(getTaskPath() + "data.csv")
                scatterplot.saveCsv(csvFile)

                species1.putAt("scatterplotId", task.id)
                def imgFile = new File(scatterplot.getImagePath())
                species1.putAt("scatterplotUrl",
                        imgFile.path.replace(grailsApplication.config.data.dir + '/public/', layersServiceUrl + '/tasks/output/')
                                .replace(imgFile.name, "Scatterplot%20(" + task.id + ").png?filename=" + imgFile.name))

                //style
                species1.putAt('red', style.red)
                species1.putAt('green', style.green)
                species1.putAt('blue', style.blue)
                species1.putAt('size', style.size)
                species1.putAt('opacity', style.opacity)
                species1.putAt('highlightWkt', style.highlightWkt)

                //annotation
                species1.putAt('q', scatterplot.getScatterplotDTO().getForegroundOccurrencesQs())
                species1.putAt('bs', scatterplot.getScatterplotDTO().getForegroundOccurrencesBs())
                species1.putAt('name', scatterplot.getScatterplotDTO().getForegroundName())

                species1.putAt('scatterplotExtents', scatterplot.getScatterplotDataDTO().layerExtents())
                species1.putAt('scatterplotSelectionExtents', scatterplot.getScatterplotStyleDTO().getSelection())
                species1.putAt('scatterplotLayers', scatterplot.getScatterplotDTO().getLayers())
                species1.putAt('scatterplotSelectionMissingCount', scatterplot.getScatterplotDataDTO().getMissingCount())

                addOutput("species", (species1 as JSON).toString())
                addOutput("download", csvFile.name)
            }
        } catch (Exception e) {
            taskLog("Failed to generate the scatter plot!")
            log.error(e.message)
            throw new Exception(e.message)
        }
    }
}
