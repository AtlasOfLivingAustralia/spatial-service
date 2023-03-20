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

import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.scatterplot.Scatterplot
import au.org.ala.spatial.scatterplot.ScatterplotDTO
import au.org.ala.spatial.scatterplot.ScatterplotStyleDTO
import grails.converters.JSON
import groovy.util.logging.Slf4j

//@CompileStatic
@Slf4j
class ScatterplotCreate extends SlaveProcess {

    void start() {

        Boolean grid = getInput('grid').toString().toBoolean()

        //area to restrict (only interested in area.q part)
        List<AreaInput> areas = JSON.parse(getInput('area').toString()) as List<AreaInput>

        String layersServiceUrl = getInput('layersServiceUrl')

        SpeciesInput species1 = JSON.parse(getInput('species1').toString()) as SpeciesInput
        SpeciesInput species2 = JSON.parse(getInput('species2').toString()) as SpeciesInput
        List<String> layerList = JSON.parse(getInput('layer').toString()) as List<String>

        SpeciesInput speciesArea1 = getSpeciesArea(species1, areas)
        SpeciesInput speciesArea2 = species2?.q ? getSpeciesArea(species2, areas) : null

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
            log.info("Creating scatter plot.")
            Scatterplot scatterplot = new Scatterplot(desc, style, null, getTaskPath(), getInput('resolution').toString(), getInput('layersServiceUrl') as String)

            if (layers.length <= 2) {
                taskLog("Generate plot style")
                scatterplot.reStyle(style, false, false, false, false, false, false, false)

                File file = new File(getTaskPath() + "data.xml")
                scatterplot.save(file)

                File csvFile = new File(getTaskPath() + "data.csv")
                scatterplot.saveCsv(csvFile)

                species1["scatterplotId"] = taskWrapper.id
                def imgFile = new File(scatterplot.getImagePath())
                species1["scatterplotUrl"] = imgFile.path.replace(spatialConfig.data.dir + '/public/', layersServiceUrl + '/tasks/output/')
                        .replace(imgFile.name, "Scatterplot%20(" + taskWrapper.id + ").png?filename=" + imgFile.name)

                //style
                species1['red'] = style.red
                species1['green'] = style.green
                species1['blue'] = style.blue
                species1['size'] = style.size
                species1['opacity'] = style.opacity
                species1['highlightWkt'] = style.highlightWkt

                //annotation
                species1['q'] = scatterplot.getScatterplotDTO().getForegroundOccurrencesQs()
                species1['bs'] = scatterplot.getScatterplotDTO().getForegroundOccurrencesBs()
                species1['name'] = scatterplot.getScatterplotDTO().getForegroundName()

                species1['scatterplotExtents'] = scatterplot.getScatterplotDataDTO().layerExtents()
                species1['scatterplotSelectionExtents'] = scatterplot.getScatterplotStyleDTO().getSelection()
                species1['scatterplotLayers'] = scatterplot.getScatterplotDTO().getLayers()
                species1['scatterplotSelectionMissingCount'] = scatterplot.getScatterplotDataDTO().getMissingCount()

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
