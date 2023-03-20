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

import au.org.ala.spatial.util.PrintMapComposer
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.grails.web.json.JSONArray

//@CompileStatic
@Slf4j
class MapImage extends SlaveProcess {

    def pdfRenderingService

    void start() {

        ///area to restrict
        JSONArray bboxJSON = JSON.parse(getInput('bbox').toString()) as JSONArray
        double[] bbox = new double[4]
        for (int i = 0; i < bboxJSON.size(); i++) {
            bbox[i] = (Double) bboxJSON.get(i)
        }

        JSONArray windowSizeJSON = JSON.parse(getInput('windowSize').toString()) as JSONArray
        int[] windowSize = new int[2]
        for (int i = 0; i < windowSizeJSON.size(); i++) {
            windowSize[i] = windowSizeJSON.get(i).toString().toInteger()
        }

        JSONArray mapLayersJSON = JSON.parse(getInput('mapLayers').toString()) as JSONArray
        List<String> mapLayers = new ArrayList<String>()
        for (int i = 0; mapLayersJSON != null && i < mapLayersJSON.size(); i++) {
            mapLayers[i] = mapLayersJSON.get(i).toString()
        }

        String baseMap = getInput('baseMap').toString()
        String comment = getInput('comment').toString()
        String outputType = getInput('outputType').toString()
        Integer resolution = "print" == getInput('resolution').toString() ? 1 : 0

        //test for pid
        def imageBytes = new PrintMapComposer(
                spatialConfig.geoserver.url.toString(),
                spatialConfig.openstreetmap.url.toString(),
                baseMap,
                mapLayers,
                bbox,
                new double[4],
                windowSize,
                comment,
                outputType,
                resolution,
                spatialConfig.data.dir, spatialConfig.google.apikey).get()

        if (outputType == 'pdf') {
            FileUtils.writeByteArrayToFile(new File(getTaskPath() + taskWrapper.id + ".jpg"), imageBytes)

            File pdf = new File(getTaskPath() + taskWrapper.id + ".pdf")
            def outputStream = FileUtils.openOutputStream(pdf)

            InputStream stream = new URL(spatialConfig.grails.serverURL + '/slave/exportMap/' + taskWrapper.id).openStream()
            outputStream << stream
            outputStream.flush()
            outputStream.close()

        } else {
            FileUtils.writeByteArrayToFile(new File(getTaskPath() + taskWrapper.id + "." + outputType), imageBytes)
        }

        File dir = new File(getTaskPath())

        File pdf = new File(getTaskPath() + 'output.pdf')
        if (dir.listFiles().length == 0) {
            taskWrapper.task.history.put(System.currentTimeMillis() as String, "Failed.")
        } else if (outputType == 'pdf' && !pdf.exists()) {
            taskWrapper.task.history.put(System.currentTimeMillis() as String, "Failed to make PDF. Exporting html instead.")
        }

        //all for download
        for (File f : dir.listFiles()) {
            if (f.getName() == "index.html") {
                addOutput('metadata', 'index.html', !pdf.exists())
            } else if (f.getName().endsWith(".pdf")) {
                addOutput('files', f.getName(), true)
            } else {
                addOutput('files', f.getName(), !pdf.exists())
            }
        }
    }

}
