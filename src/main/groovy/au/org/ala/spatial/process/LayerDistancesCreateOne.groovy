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

import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.tabulation.TabulationGenerator
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils

@Commons
class LayerDistancesCreateOne extends SlaveProcess {

    void start() {
        List fieldIds = []
        for (int i = 0; taskWrapper.input.containsKey('fieldId' + (i + 1)); i++) {
            fieldIds.add(taskWrapper.input["fieldId" + (i + 1)])
        }

        String[] grdResolutions = taskWrapper.input.grdResolutions

        taskWrapper.message = 'getting layerDistances.properties'
        slaveService.getFile('/public/layerDistances.properties')

        File f = new File(grailsApplication.config.data.dir.toString() + '/public/layerDistances.properties')
        if (!f.exists()) FileUtils.writeStringToFile(f, '')
        Map distances = [:]
        FileReader fr = new FileReader(f)
        for (String line : fr.readLines()) {
            String[] split = line.split('=')
            if (split.length == 2) {
                distances.put(split[0], split[1])
            }
        }

        List files = []

        //get highest resolution standardized layer files
        taskWrapper.message = 'get standardized layer files'
        int found = 0
        for (def i = grdResolutions.size() - 1; i >= 0; i--) {
            for (def j = 0; j < fieldIds.size(); j++) {
                if (files[j] == null) {
                    String path = '/standard_layer/' + grdResolutions[i] + '/' + fieldIds[j] + '.grd'
                    taskWrapper.message = 'checking file ' + path
                    if (slaveService.peekFile(path)[0].exists) {
                        taskWrapper.message = 'getting file ' + path
                        slaveService.getFile(path)
                        files[j] = new File(path)
                        found++
                    }
                }
            }
        }

        //cache
        List<Grid> grids = []
        List<float[]> values = []
        List<Double> ranges = []

        for (int i = 0; i < files.size(); i++) {
            String f1 = grailsApplication.config.data.dir + files[i].getPath()
            f1 = f1.substring(0, f1.length() - 4)

            Grid g1 = Grid.getGrid(f1)
            grids.add(g1)

            ranges[i] = g1.maxval - g1.minval

            long maxLength = 4000 * 4000

            if (((long) g1.nrows) * ((long) g1.ncols) < maxLength) {
                taskWrapper.message = 'loading grid file ' + f1
                float[] d1 = g1.getGrid()
                values.add(d1)
            } else {
                taskWrapper.message = 'not loading grid file ' + f1
                values.add(null)
            }
        }

        if (found >= 2) {
            taskWrapper.message = 'init batch objects'
            int batchSize = 1000000
            double[][] points = new double[batchSize][2]

            float[] v1 = new double[batchSize]
            float[] v2 = new double[batchSize]

            String distString = ''
            for (int i = 0; i < files.size() - 1; i++) {
                for (int j = i + 1; j < files.size(); j++) {
                    if (!distances.containsKey(fieldIds[i] + ' ' + fieldIds[j])) {
                        try {
                            String domain1 = getLayer(getField(fieldIds[i]).spid).domain
                            String domain2 = getLayer(getField(fieldIds[j]).spid).domain

                            if (TabulationGenerator.isSameDomain(TabulationGenerator.parseDomain(domain1),
                                    TabulationGenerator.parseDomain(domain2))) {

                                taskWrapper.message = 'calculating distance for ' + fieldIds[i] + ' and ' + fieldIds[j]

                                double distance = calculateDistanceBatch(points, v1, v2, values[i], values[j],
                                        grids[i], grids[j], ranges[i], ranges[j])

                                //append distance
                                if (!Double.isNaN(distance)) {
                                    if (distString.length() > 0) distString += '\n'
                                    distString += fieldIds[i] + ' ' + fieldIds[j] + '=' + distance
                                }
                            }
                        } catch (err) {
                            //error
                            log.error 'failed to calc distance for fields ' + fieldIds[i] + ' and ' + fieldIds[j], err
                        }
                    }
                }
            }
            if (distString.length() > 0) {
                addOutput('append', '/public/layerDistances.properties?' + distString)
            }
        } else {
            //error
        }
    }

    private double calculateDistanceBatch(double[][] points, float[] v1, float[] v2, float[] d1, float[] d2,
                                          Grid g1, Grid g2, double range1, double range2) {

        int count = 0
        double sum = 0

        int batchSize = points.length
        for (int i = 0; i < points.length; i++) {
            points[i][0] = 0
            points[i][1] = 0
        }

        int size = 0

        double minx = Math.max(g1.xmin, g2.xmin)
        double maxx = Math.min(g1.xmax, g2.xmax)
        double miny = Math.max(g1.ymin, g2.ymin)
        double maxy = Math.min(g1.ymax, g2.ymax)

        Grid g = g1.xres < g2.xres ? g1 : g2

        for (double y = maxy; y >= miny; y -= g.yres) {
            for (double x = minx; x < maxx; x += g.xres) {
                if (size < batchSize) {
                    v1[size] = d1 != null ? d1[(int) g1.getcellnumber(x, y)] : 0.0f
                    v2[size] = d2 != null ? d2[(int) g2.getcellnumber(x, y)] : 0.0f
                    if (d1 == null || d2 == null) {
                        points[size][0] = x
                        points[size][1] = y
                    }
                    size++
                }

                if (size == batchSize) {
                    if (d1 == null) v1 = g1.getValues3(points, 1024 * 1024)
                    if (d2 == null) v2 = g2.getValues3(points, 1024 * 1024)

                    for (int i = 0; i < size; i++) {
                        if (!Double.isNaN(v1[i]) && !Double.isNaN(v2[i])) {
                            count++

                            float s1 = (v1[i] - g1.minval) / range1
                            float s2 = (v2[i] - g2.minval) / range2
                            sum += Math.abs(s1 - s2)
                        }
                    }
                    size = 0
                }
            }
        }

        if (size > 0) {
            if (size == batchSize) {
                if (d1 == null) v1 = g1.getValues3(points, 1024 * 1024)
                if (d2 == null) v2 = g1.getValues3(points, 1024 * 1024)

                for (int i = 0; i < size; i++) {
                    if (!Double.isNaN(v1[i]) && !Double.isNaN(v2[i])) {
                        float s1 = (v1[i] - g1.minval) / range1
                        float s2 = (v2[i] - g2.minval) / range2
                        sum += Math.abs(s1 - s2)

                        count++
                    }
                }
            }
        }

        return sum / count
    }
}
