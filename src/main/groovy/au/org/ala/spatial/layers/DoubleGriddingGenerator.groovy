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
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.intersect.SimpleRegion
import com.opencsv.CSVReader

/**
 * Generate a sites by species table.
 *
 * @author Adam
 */
//@CompileStatic
class DoubleGriddingGenerator {

    /**
     * all occurrence records for this occurrence density grid.
     */
    Records records
    /**
     * output grid resolution as decimal degrees.
     */
    double resolution
    /**
     * 2nd, smaller, output grid resolution as decimal degrees.
     */
    double resolutionInner
    /**
     * output grid bounds as xmin,ymin,xmax,ymax.
     */
    double[] bbox
    /**
     * output grid dimensions.
     */
    int width, height

    /**
     * year parameters
     */
    int minYear, maxYear, yearStep

    DoubleGriddingGenerator(double resolution, double resolutionInner, double[] bbox, int minYear, int maxYear, int yearStep) {
        this.resolution = resolution
        this.resolutionInner = resolutionInner
        this.bbox = bbox
        this.minYear = minYear
        this.maxYear = maxYear
        this.yearStep = yearStep

        width = (int) Math.ceil((bbox[2] - bbox[0]) / resolution)
        height = (int) Math.ceil((bbox[3] - bbox[1]) / resolution)
    }

    /**
     * @param resolution
     */
    void setResolution(double resolution) {
        this.resolution = resolution
        width = (int) Math.ceil((bbox[2] - bbox[0]) / resolution)
        height = (int) Math.ceil((bbox[3] - bbox[1]) / resolution)
    }

    /**
     * @param bbox
     */
    void setBBox(double[] bbox) {
        this.bbox = bbox
        width = (int) Math.ceil((bbox[2] - bbox[0]) / resolution)
        height = (int) Math.ceil((bbox[3] - bbox[1]) / resolution)
    }

    /**
     * Generate and write the sites by species list.
     * <p/>
     * Output file is named "SitesBySpecies.csv"
     *
     * @param records all occurrence records for this density grid as Records.
     * @param outputDirectory path to the output directory for the list.
     * @param region area restriction, or null for everywhere the occurrences
     *                        appear.
     * @param envelopeGrid area restriction as an envelope grid, or null for
     *                        everywhere the occurrences appear.
     * @return array as int[] with { grid cells with an occurrence, grid cells
     * in the area }
     * @throws IOException
     */
    int[] write(Records records, String outputDirectory, SimpleRegion region, Grid envelopeGrid) throws IOException {
        FileWriter fw = new FileWriter(outputDirectory + "fullSitesBySpecies.csv")

        for (int j = 0; j < 5; j++) {
            if (j > 0) {
                fw.append("\n")
            }
            if (j == 0) {
                fw.append("LSID,longitudeRange,latitudeRange,yearRange,speciesCount,binCount,occurrenceCount")
            } else if (j == 1) {
                fw.append("Common Name,longitudeRange,latitudeRange,yearRange,speciesCount,binCount,occurrenceCount")
            } else if (j == 2) {
                fw.append("Kingdom,longitudeRange,latitudeRange,yearRange,speciesCount,binCount,occurrenceCount")
            } else if (j == 3) {
                fw.append("Family,longitudeRange,latitudeRange,yearRange,speciesCount,binCount,occurrenceCount")
            } else if (j == 4) {
                fw.append("Species,longitudeRange,latitudeRange,yearRange,speciesCount,binCount,occurrenceCount")
            }
            for (int i = 0; i < records.getSpeciesSize(); i++) {
                fw.append(",\"")
                String[] split = records.getSpeciesN(i).split("\\|")
                if (j == 4) {
                    if (split.length > 0) {
                        fw.append(split[0].replace("\"", "\"\""))
                    }
                } else {
                    if (split.length > j + 1) {
                        fw.append(split[j + 1].replace("\"", "\"\""))
                    }
                }
                fw.append("\"")
            }
        }

        int uniqueSpeciesCount = records.getSpeciesSize()

        int[] totalSpeciesCounts = new int[uniqueSpeciesCount]

        int[][][] bsRows = new int[1][][]
        BitSet[] bins = new BitSet[width]
        for (int i = 0; i < bins.length; i++) {
            bins[i] = new BitSet()
        }
        int[] counts = new int[2]
        boolean[] used = new boolean[records.getRecordsSize()]
        for (int year = minYear; year <= maxYear; year += yearStep)
            for (int row = 0; row < height; row++) {
                bsRows[0] = getNextIntArrayRow(records, row, uniqueSpeciesCount, bsRows[0], bins, used, year, year + yearStep - 1)
                for (int i = 0; i < width; i++) {

                    double longitude = (i + 0.5) * resolution + bbox[0]
                    double latitude = (row + 0.5) * resolution + bbox[1]
                    if ((region == null || region.isWithin_EPSG900913(longitude, latitude))
                            && (envelopeGrid == null || envelopeGrid.getValues2([longitude, latitude] as double[])[0] > 0)) {

                        int sum = 0
                        for (int n = 0; n < uniqueSpeciesCount; n++) {
                            sum += bsRows[0][i][n]
                            totalSpeciesCounts[n] += bsRows[0][i][n]
                        }

                        if (sum > 0) {
                            fw.append("\n\"")
                            fw.append(String.valueOf(i * resolution + bbox[0]))
                            fw.append("_")
                            fw.append(String.valueOf(row * resolution + bbox[1]))
                            fw.append("\",\"")
                            fw.append(String.valueOf(i * resolution + bbox[0])).append(",").append(String.valueOf((i + 1) * resolution + bbox[0]))
                            fw.append("\",\"")
                            fw.append(String.valueOf(row * resolution + bbox[1])).append(",").append(String.valueOf((row + 1) * resolution + bbox[1]))
                            fw.append("\",")
                            fw.append(year + '-' + (year + yearStep - 1))
                            int binCount = 0
                            int speciesCount = 0
                            int occurrenceCount = 0
                            for (int n = 0; n < uniqueSpeciesCount; n++) {
                                if (bsRows[0][i][n] > 0) {
                                    speciesCount++
                                    occurrenceCount += bsRows[0][i][n]
                                }
                            }
                            fw.append(",")
                            fw.append(String.valueOf(speciesCount))
                            fw.append(",")
                            fw.append(String.valueOf(bins[i].cardinality()))
                            fw.append(",")
                            fw.append(String.valueOf(occurrenceCount))
                            for (int n = 0; n < uniqueSpeciesCount; n++) {
                                fw.append(",")
                                fw.append(String.valueOf(bsRows[0][i][n]))
                            }
                            counts[0]++
                        }
                        counts[1]++
                    }
                }
            }
        fw.flush()
        fw.close()

        //remove columns without species
        fw = new FileWriter(outputDirectory + "SitesBySpecies.csv")
        CSVReader r = new CSVReader(new FileReader(outputDirectory + "fullSitesBySpecies.csv"))
        String[] line
        int row = 0
        while ((line = r.readNext()) != null) {
            for (int i = 0; i < line.length; i++) {
                if (i < 7 || totalSpeciesCounts[i - 7] > 0) {
                    if (i > 0) {
                        fw.append(",")
                    }

                    if (row == 0) {
                        fw.append("\"")
                        fw.append(line[i].replace("\"", "\"\""))
                        fw.append("\"")
                    } else {
                        if (i == 0) {
                            fw.append("\n")
                        }
                        if (i < 3) {
                            fw.append("\"")
                            fw.append(line[i].replace("\"", "\"\""))
                            fw.append("\"")
                        } else {
                            fw.append(line[i])
                        }
                    }
                }
            }
            row++
        }
        r.close()
        fw.flush()
        fw.close()

        new File(outputDirectory + "fullSitesBySpecies.csv").delete()

        return counts
    }

    /**
     * get data for the next row.
     *
     * @param records
     * @param row
     * @param uniqueSpeciesCount
     * @param bs
     * @return
     */
    int[][] getNextIntArrayRow(Records records, int row, int uniqueSpeciesCount, int[][] bs, BitSet[] bins, boolean[] used, int minYear, int maxYear) {
        //translate into bitset for each grid cell
        if (bs == null) {
            bs = new int[width][uniqueSpeciesCount]
        } else {
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < uniqueSpeciesCount; j++) {
                    bs[i][j] = 0
                }
            }
        }

        for (int i = 0; i < bins.length; i++) {
            bins[i].clear()
        }

        int innerWidth = (int) Math.ceil(resolution / resolutionInner as double)

        int count = 0
        for (int i = 0; i < records.getRecordsSize(); i++) {
            if (!used[i]) {
                int y = (int) ((records.getLatitude(i) - bbox[1]) / resolution)
                int yInner = (int) ((records.getLatitude(i) - bbox[1]) / resolutionInner)

                if (y == row) {
                    int x = (int) ((records.getLongitude(i) - bbox[0]) / resolution)
                    int xInner = (int) ((records.getLongitude(i) - bbox[0]) / resolutionInner)

                    if (x >= 0 && x < width && records.getYear(i) <= maxYear && records.getYear(i) >= minYear) {
                        used[i] = true
                        bs[x][records.getSpeciesNumber(i)]++
                        count++

                        bins[x].set(xInner + yInner * innerWidth)
                    }
                }
            }
        }

        return bs
    }
}
