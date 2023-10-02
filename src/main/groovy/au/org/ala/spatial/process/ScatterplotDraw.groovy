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
import au.org.ala.spatial.scatterplot.Scatterplot
import au.org.ala.spatial.scatterplot.ScatterplotStyleDTO
import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.util.logging.Slf4j

//@CompileStatic
@Slf4j
class ScatterplotDraw extends SlaveProcess {

    void start() {
        //optional area to restrict
        List<AreaInput> areas = JSON.parse(getInput('wkt') as String?: '[]').collect { it as AreaInput } as List<AreaInput>
        def wkt = areas.size() > 0 ? getAreaWkt(areas[0]) : null

        def layersServiceUrl = getInput('layersServiceUrl')

        def colour = getInput('color').toString()
        def colourMode = getInput('colorType').toString()
        def size = getInput('size').toString().toInteger()
        def opacity = getInput('opacity').toString().toDouble()

        def selection = getInput('selection').toString().split(',')

        def taskId = getInput('scatterplotId')

        File dataFile = new File(spatialConfig.data.dir.toString() + '/public/' + taskId + "/data.xml")

        Scatterplot scatterplot = Scatterplot.load(dataFile, gridCutterService, layerIntersectService)

        ScatterplotStyleDTO existingStyle = scatterplot.getScatterplotStyleDTO()

        ScatterplotStyleDTO newStyle = new ScatterplotStyleDTO()
        newStyle.setColourMode(colourMode)
        newStyle.setOpacity(opacity.floatValue())
        newStyle.setSize(size)
        int red = Integer.parseInt(colour.substring(0, 2), 16)
        int green = Integer.parseInt(colour.substring(2, 4), 16)
        int blue = Integer.parseInt(colour.substring(4, 6), 16)
        newStyle.setRed(red)
        newStyle.setGreen(green)
        newStyle.setBlue(blue)
        newStyle.setHighlightWkt(wkt)

        try {
            if (selection && selection.length < 4) {
                scatterplot.scatterplotStyleDTO.colourMode = newStyle.colourMode
                scatterplot.scatterplotStyleDTO.setSelection(null)
                scatterplot.buildScatterplot()
            } else {
                scatterplot.annotatePixelBox(Double.valueOf(selection[0]).intValue(), Double.valueOf(selection[1]).intValue(),
                        Double.valueOf(selection[2]).intValue(), Double.valueOf(selection[3]).intValue())
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        scatterplot.getScatterplotDTO().setId('' + System.currentTimeMillis())
        scatterplot.reStyle(newStyle,
                existingStyle.getColourMode() != newStyle.getColourMode(),
                existingStyle.getRed() != newStyle.getRed(),
                existingStyle.getBlue() != newStyle.getBlue(),
                existingStyle.getGreen() != newStyle.getGreen(),
                existingStyle.getOpacity() != newStyle.getOpacity(),
                existingStyle.getSize() != newStyle.getSize(),
                existingStyle.getHighlightWkt() != newStyle.getHighlightWkt())

        def image = [:]
        image["scatterplotId"] = taskWrapper.task.id
        def imgFile = new File(scatterplot.getImagePath())
        image["scatterplotUrl"] = imgFile.path.replace(spatialConfig.data.dir + '/public/', spatialConfig.grails.serverURL + '/tasks/output/' as CharSequence)
                .replace(imgFile.name, "Scatterplot%20(" + taskWrapper.id + ").png?filename=" + imgFile.name)

        //style
        image['red'] = newStyle.red
        image['green'] = newStyle.green
        image['blue'] = newStyle.blue
        image['size'] = newStyle.size
        image['opacity'] = newStyle.opacity
        image['highlightWkt'] = newStyle.highlightWkt

        //annotation
        image['q'] = scatterplot.getScatterplotDTO().getForegroundOccurrencesQs()
        image['bs'] = scatterplot.getScatterplotDTO().getForegroundOccurrencesBs()
        image['name'] = scatterplot.getScatterplotDTO().getForegroundName()

        image['scatterplotExtents'] = scatterplot.getScatterplotDataDTO().layerExtents()
        image['scatterplotSelectionExtents'] = scatterplot.getScatterplotStyleDTO().getSelection()
        image['scatterplotLayers'] = scatterplot.getScatterplotDTO().getLayers()
        image['scatterplotSelectionMissingCount'] = scatterplot.getScatterplotDataDTO().getMissingCount()

        addOutput("species", (image as JSON).toString())

        // replace original zip of data.csv
        File csvFile = new File(getTaskPathById(taskId) + "data.csv")
        scatterplot.saveCsv(csvFile)
        File downloadZip = new File(getTaskPathById(taskId) + "download.zip")
        downloadZip.delete()
        Util.zip(downloadZip.path, [csvFile.path] as String[], [csvFile.name] as String[])
    }
}
