/**
 * ************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia All Rights Reserved.
 * <p/>
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * <p/>
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * *************************************************************************
 */
package au.org.ala.spatial.layers

import au.org.ala.spatial.util.Records

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/**
 * DEPRECATED AND LEFT HERE FOR POSTERITY. SEE CalculatedLayerGenerator and subclasses. CF 12/2013
 * <p/>
 * Produce an occurrence density layer with a moving average.
 * <p/>
 * Output is diva grid and/or ascii grid.
 * <p/>
 * Construct then run .write
 *
 * @author Adam
 */
//@CompileStatic
class OccurrenceDensity {

    /**
     * all occurrence records for this occurrence density grid.
     */
    Records records
    /**
     * gridSize is (moving average-1)/2.
     */
    int gridSize
    /**
     * output grid resolution as decimal degrees.
     */
    double resolution
    /**
     * output grid bounds as xmin,ymin,xmax,ymax.
     */
    double[] bbox
    /**
     * output grid dimensions.
     */
    int width, height

    /**
     * @param gridSize this is (moving average - 1) / 2 as int.
     * @param resolution output grid resolution as double in decimal degrees.
     * @param bbox output grid bounds xmin,ymin,xmax,ymax as double [].
     */
    OccurrenceDensity(int gridSize, double resolution, double[] bbox) {
        this.gridSize = gridSize
        this.resolution = resolution
        this.bbox = bbox

        width = (int) ((bbox[2] - bbox[0]) / resolution)
        height = (int) ((bbox[3] - bbox[1]) / resolution)
    }

    /**
     * @param gridSize
     */
    void setGridSize(int gridSize) {
        this.gridSize = gridSize
    }

    /**
     * @param resolution
     */
    void setResolution(double resolution) {
        this.resolution = resolution
        width = (int) ((bbox[2] - bbox[0]) / resolution)
        height = (int) ((bbox[3] - bbox[1]) / resolution)
    }

    /**
     * @param bbox
     */
    void setBBox(double[] bbox) {
        this.bbox = bbox
        width = (int) ((bbox[2] - bbox[0]) / resolution)
        height = (int) ((bbox[3] - bbox[1]) / resolution)
    }

    /**
     * Generate and write the occurrence density grid.
     *
     * @param records all occurrence records for this density grid as Records.
     * @param outputDirectory path to output directory as String.
     * @param filename output filename as String.  No file extentions.
     * @param threadCount number of threads to use during calculations as int.
     * @param outputDivaGrid true to write a diva grid, as boolean.
     * @param outputASC true to write an ascii grid, as boolean.
     * @throws IOException
     */
    void write(Records records, String outputDirectory, String filename, int threadCount, boolean outputDivaGrid, boolean outputASC) throws IOException {
        if (filename == null) {
            filename = "_occurrence_density_av_" + gridSize + "x" + gridSize + "_" + String.valueOf(resolution).replace(".", "")
        }

        BufferedWriter bw = null
        if (outputASC) {
            bw = new BufferedWriter(new FileWriter(outputDirectory + filename + ".asc"))
        }
        BufferedOutputStream bos = null
        if (outputDivaGrid) {
            bos = new BufferedOutputStream(new FileOutputStream(outputDirectory + filename + ".gri"))
        }

        //write data
        byte[] bytes = null
        ByteBuffer bb = null
        if (outputDivaGrid) {
            bytes = new byte[4 * width]
            bb = ByteBuffer.wrap(bytes)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.mark()
        }

        if (bw != null) {
            bw.append("ncols " + width + "\n"
                    + "nrows " + height + "\n"
                    + "xllcorner " + bbox[0] + "\n"
                    + "yllcorner " + bbox[1] + "\n"
                    + "cellsize " + resolution + "\n"
                    + "NODATA_value -9999\n")
        }

        int[][] cRows = new int[gridSize][]
        double max = 0
        boolean worldwrap = (bbox[2] - bbox[0]) == 360
        float[] values = new float[width]

        int partCount = threadCount * 5
        int partSize = (int) Math.ceil(width / (double) partCount)
        GetValuesOccurrencesThread[] getValues = new GetValuesOccurrencesThread[threadCount]
        LinkedBlockingQueue<Integer> lbqGetValues = new LinkedBlockingQueue<Integer>()

        int[] rowStarts = records.sortedRowStarts(bbox[1], height, resolution)

        for (int row = 0; row < height; row++) {
            long start = System.currentTimeMillis()
            //get rows
            int[] oldRow = cRows[0]
            if (row == 0) {
                for (int i = 0; i < gridSize; i++) {
                    cRows[i] = getNextCountsRow(records, rowStarts, row + i, null)
                }
            } else {
                for (int i = 0; i < gridSize && row + i < height; i++) {
                    if (i + 1 < cRows.length) {
                        cRows[i] = cRows[i + 1]
                    } else {
                        cRows[i] = getNextCountsRow(records, rowStarts, row + i, oldRow)
                    }
                }
            }
            long t1 = System.currentTimeMillis()

            //operate on current row
            int startRow = (row == 0) ? 0 : row + gridSize / 2 //gridSize is odd
            int endRow = (row == height - 1) ? height - 1 : row + gridSize / 2 //gridSize is odd

            for (int currentRow = startRow; currentRow <= endRow; currentRow++) {
                if (bb != null) {
                    bb.reset()
                }

                //calculate moving average
                int offset = (int)(gridSize / 2)
                CountDownLatch cdl = new CountDownLatch(partCount)
                for (int i = 0; i < threadCount; i++) {
                    if (getValues[i] == null) {
                        getValues[i] = new GetValuesOccurrencesThread(lbqGetValues)
                        getValues[i].start()
                        getValues[i].setPriority(Thread.MIN_PRIORITY)
                    }
                    getValues[i].set(cdl, partSize, cRows, values, worldwrap, height, width, offset, currentRow, row)
                }
                try {
                    for (int i = 0; i < partCount; i++) {
                        lbqGetValues.put(i)
                    }
                    cdl.await()
                } catch (InterruptedException ignored) {
                }

                for (int i = 0; i < width; i++) {
                    float value = values[i]

                    if (bb != null) {
                        if (max < value) {
                            max = value
                        }
                        bb.putFloat(value)
                    }

                    if (bw != null) {
                        if (i > 0) {
                            bw.append(" ")
                        }
                        if (value == 0) {
                            bw.append("0")
                        } else {
                            bw.append(String.valueOf(value))
                        }
                    }
                }
                if (bos != null) {
                    bos.write(bytes)
                }
                if (bw != null) {
                    bw.append("\n")
                }
            }
            long end = System.currentTimeMillis()
        }

        for (int i = 0; i < threadCount; i++) {
            getValues[i].interrupt()
        }
        if (bos != null) {
            bos.close()
            DensityLayers.writeHeader(outputDirectory + filename + ".grd", resolution, height, width, bbox[0], bbox[1], bbox[2], bbox[3], 0, max, -1)
        }
        if (bw != null) {
            bw.close()
        }
    }

    /**
     * Get grid cell counts for the next row.
     *
     * @param records
     * @param rowStarts
     * @param row
     * @param counts
     * @return
     */
    int[] getNextCountsRow(Records records, int[] rowStarts, int row, int[] counts) {
        //get count for each grid cell
        if (counts == null) {
            counts = new int[width]
        } else {
            for (int i = 0; i < counts.length; i++) {
                counts[i] = 0
            }
        }

        int len = (row + 1 < rowStarts.length) ? rowStarts[row + 1] : records.getRecordsSize()
        for (int i = rowStarts[row]; i < len; i++) {
            int y = height - 1 - (int) ((records.getSortedLatitude(i) - bbox[1]) / resolution)

            if (y == row) {
                int x = (int) ((records.getSortedLongitude(i) - bbox[0]) / resolution)

                if (x >= 0 && x < width) {
                    counts[x]++
                }
            }
        }

        return counts
    }
}
