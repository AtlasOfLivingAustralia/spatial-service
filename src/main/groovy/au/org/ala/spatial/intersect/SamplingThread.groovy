/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial.intersect

import au.org.ala.spatial.dto.GridClass
import au.org.ala.spatial.dto.IntersectionFile
import groovy.util.logging.Slf4j
import org.apache.commons.lang.StringUtils

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Adam
 */
@Slf4j
//@CompileStatic
class SamplingThread extends Thread {

    /**
     * log4j logger
     */
    //private static final Logger logger = log.getLogger(SamplingThread.class);
    LinkedBlockingQueue<Integer> lbq
    CountDownLatch cdl
    double[][] points
    IntersectionFile[] intersectionFiles
    ArrayList<String> output
    int threadCount
    SimpleShapeFileCache simpleShapeFileCache
    int gridBufferSize
    IntersectCallback callback

    SamplingThread(LinkedBlockingQueue<Integer> lbq, CountDownLatch cdl, IntersectionFile[] intersectionFiles,
                   double[][] points, ArrayList<String> output, int threadCount,
                   SimpleShapeFileCache simpleShapeFileCache, int gridBufferSize, IntersectCallback callback) {
        this.lbq = lbq
        this.cdl = cdl
        this.points = points
        this.intersectionFiles = intersectionFiles
        this.output = output
        this.threadCount = threadCount
        this.simpleShapeFileCache = simpleShapeFileCache
        this.gridBufferSize = gridBufferSize
        this.callback = callback
        setPriority(MIN_PRIORITY)
    }

    void run() {
        try {
            while (true) {
                int pos = lbq.take()

                this.callback.setCurrentLayerIdx(pos)

                try {
                    StringBuilder sb = new StringBuilder()
                    sample(points, intersectionFiles[pos], sb)
                    output.set(pos, sb.toString())
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }

                this.callback.setCurrentLayer(intersectionFiles[pos])
                cdl.countDown()
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            log.trace(e.getMessage(), e)
        }
    }

    void sample(double[][] points, IntersectionFile intersectionFile, StringBuilder sb) {
        if (intersectionFile == null) {
            return
        }
        HashMap<Integer, GridClass> classes = intersectionFile.getClasses()
        String shapeFieldName = intersectionFile.getShapeFields()
        String fileName = intersectionFile.getFilePath()
        String name = intersectionFile.getFieldId()
        long start = System.currentTimeMillis()
        log.debug("Starting sampling " + points.length + " points in " + name + ':' + fileName + (shapeFieldName == null ? "" : " field: " + shapeFieldName))
        callback.progressMessage("Started sampling layer:" + intersectionFile.getLayerName())
        if (StringUtils.isNotEmpty(shapeFieldName)) {
            intersectShape(fileName, shapeFieldName, points, sb)
        } else if (classes != null) {
            intersectGridAsContextual(fileName, classes, points, sb)
        } else {
            intersectGrid(fileName, points, sb)
        }

        log.debug("Finished sampling " + points.length + " points in " + name + ':' + fileName + " in " + (System.currentTimeMillis() - start) + "ms")

        callback.progressMessage("Finished sampling layer: " + intersectionFile.getLayerName() + ". Points processed: " + points.length / 2)
    }

    void intersectGrid(String filename, double[][] points, StringBuilder sb) {
        try {
            Grid grid = new Grid(filename, true)
            float[] values = grid.getValues3(points, gridBufferSize)

            if (values != null) {
                for (int i = 0; i < points.length; i++) {
                    if (i > 0) {
                        sb.append("\n")
                    }
                    if (!Float.isNaN(values[i])) {
                        sb.append(values[i])
                    }
                }
            } else {
                for (int i = 1; i < points.length; i++) {
                    sb.append("\n")
                }
            }
        } catch (Exception e) {
            log.error("Error with grid: " + filename, e)
        }
    }

    void intersectGridAsContextual(String filename, HashMap<Integer, GridClass> classes, double[][] points, StringBuilder sb) {
        try {
            Grid grid = new Grid(filename)
            GridClass gc
            float[] values = grid.getValues3(points, gridBufferSize)

            if (values != null) {
                for (int i = 0; i < points.length; i++) {
                    if (i > 0) {
                        sb.append("\n")
                    }
                    gc = classes.get((int) values[i])
                    if (gc != null) {
                        sb.append(gc.getName())
                    }
                }
            } else {
                for (int i = 1; i < points.length; i++) {
                    sb.append("\n")
                }
            }
        } catch (Exception e) {
            log.error("Error with grid: " + filename, e)
        }
    }

    void intersectShape(String filename, String fieldName, double[][] points, StringBuilder sb) {
        try {
            SimpleShapeFile ssf = null

            if (simpleShapeFileCache != null) {
                ssf = simpleShapeFileCache.get(filename)
            }

            if (ssf == null) {
                log.debug("shape file not in cache: " + filename)
                ssf = new SimpleShapeFile(filename, fieldName)
            }

            int column_idx = ssf.getColumnIdx(fieldName)
            String[] categories = ssf.getColumnLookup(column_idx)

            int[] values = ssf.intersect(points, categories, column_idx, threadCount)

            if (values != null) {
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        sb.append("\n")
                    }
                    if (values[i] >= 0) {
                        sb.append(categories[values[i]])
                    }
                }
            } else {
                for (int i = 1; i < points.length; i++) {
                    sb.append("\n")
                }
            }
        } catch (Exception e) {
            log.error("Error with shapefile: " + filename, e)
        }
    }
}
