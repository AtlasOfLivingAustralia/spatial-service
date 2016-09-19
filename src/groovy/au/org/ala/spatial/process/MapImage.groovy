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
import org.apache.commons.io.FileUtils

class MapImage extends SlaveProcess {

    void start() {

        ///area to restrict
        def bboxJSON = JSON.parse(task.input.bbox.toString())
        double[] bbox = new double[4]
        for (int i = 0; i < bboxJSON.size(); i++) {
            bbox[i] = (Double) bboxJSON.get(i)
        }

        def windowSizeJSON = JSON.parse(task.input.windowSize.toString())
        int[] windowSize = new int[2]
        for (int i = 0; i < windowSizeJSON.size(); i++) {
            windowSize[i] = windowSizeJSON.get(i).toString().toInteger()
        }

        def mapLayersJSON = JSON.parse(task.input.mapLayers.toString())
        List<String> mapLayers = new ArrayList<String>()
        for (int i = 0; mapLayersJSON != null && i < mapLayersJSON.size(); i++) {
            mapLayers[i] = mapLayersJSON.get(i).toString()
        }

        String baseMap = task.input.baseMap.toString()
        String comment = task.input.comment.toString()
        String outputType = task.input.outputType.toString()
        Integer resolution = "print".equals(task.input.resolution.toString()) ? 1 : 0

        //test for pid
        def imageBytes = new PrintMapComposer(
                grailsApplication.config.geoserver.url.toString(),
                grailsApplication.config.wkhtmltopdf.path.toString(),
                baseMap,
                mapLayers,
                bbox,
                new double[4],
                windowSize,
                comment,
                outputType,
                resolution).get()

        FileUtils.writeByteArrayToFile(new File(getTaskPath() + task.id + "." + outputType), imageBytes)

        File dir = new File(getTaskPath())

        File pdf = new File(getTaskPath() + 'output.pdf')
        if (dir.listFiles().length == 0) {
            task.history.put(System.currentTimeMillis(), "Failed.")
        } else if (!pdf.exists()) {
            task.history.put(System.currentTimeMillis(), "Failed to make PDF. Exporting html instead.")
        }

        //all for download
        for (File f : dir.listFiles()) {
            if (f.getName().equals("index.html")) {
                addOutput('metadata', 'index.html', !pdf.exists())
            } else if (f.getName().endsWith(".pdf")) {
                addOutput('files', f.getName(), true)
            } else {
                addOutput('files', f.getName(), !pdf.exists())
            }
        }
    }

}
