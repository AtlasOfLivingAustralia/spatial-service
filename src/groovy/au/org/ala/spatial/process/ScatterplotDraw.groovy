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
import au.org.ala.scatterplot.ScatterplotStyleDTO
import grails.converters.JSON
import org.json.simple.parser.JSONParser

class ScatterplotDraw extends SlaveProcess {

    void start() {

        //area to restrict (only interested in area.q part)
        JSONParser jp = new JSONParser()
        String wkt = jp.parse(task.input.wkt.toString())

        def layersServiceUrl = task.input.layersServiceUrl

        def colour = task.input.color.toString()
        def colourMode = task.input.colorType.toString()
        def size = task.input.size.toString().toInteger()
        def opacity = task.input.opacity.toString().toDouble()

        def selection = task.input.selection.toString().split(',')

        def taskId = task.input.scatterplotId

        File dataFile = new File(grailsApplication.config.data.dir.toString() + '/public/' + taskId + "/data.xml")
        slaveService.getFile('/public/' + taskId + '/data.xml')

        Scatterplot scatterplot = Scatterplot.load(dataFile)

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

        scatterplot.annotatePixelBox(selection[0].toInteger(), selection[1].toInteger(), selection[2].toInteger(), selection[3].toInteger())

        scatterplot.getScatterplotDTO().setId('' + System.currentTimeMillis())
        scatterplot.reStyle(newStyle,
                !existingStyle.getColourMode().equals(newStyle.getColourMode()),
                !existingStyle.getRed() != newStyle.getRed(),
                !existingStyle.getBlue() != newStyle.getBlue(),
                !existingStyle.getGreen() != newStyle.getGreen(),
                !existingStyle.getOpacity() != newStyle.getOpacity(),
                !existingStyle.getSize() != newStyle.getSize(),
                !existingStyle.getHighlightWkt() != newStyle.getHighlightWkt())

//        scatterplot.save(dataFile)
//        addOutput("file", "/public/" + taskId + "/data.xml")

        def image = [:]
        image.putAt("scatterplotId", task.id)
        image.putAt("scatterplotUrl", scatterplot.getImagePath().replace(grailsApplication.config.data.dir + '/public/', layersServiceUrl + '/tasks/output/'))

        //style
        image.putAt('red', newStyle.red)
        image.putAt('green', newStyle.green)
        image.putAt('blue', newStyle.blue)
        image.putAt('size', newStyle.size)
        image.putAt('opacity', newStyle.opacity)
        image.putAt('highlightWkt', newStyle.highlightWkt)

        //annotation
        image.putAt('q', scatterplot.getScatterplotDTO().getForegroundOccurrencesQs())
        image.putAt('bs', scatterplot.getScatterplotDTO().getForegroundOccurrencesBs())
        image.putAt('name', scatterplot.getScatterplotDTO().getForegroundName())

        image.putAt('scatterplotSelectionExtents', scatterplot.getScatterplotDataDTO().layerExtents())
        image.putAt('scatterplotLayers', scatterplot.getScatterplotDTO().getLayers())
        image.putAt('scatterplotSelectionMissingCount', scatterplot.getScatterplotDataDTO().getMissingCount())

        addOutput("species", (image as JSON).toString())
    }
}
