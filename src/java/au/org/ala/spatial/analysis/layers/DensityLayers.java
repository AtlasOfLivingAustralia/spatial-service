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
package au.org.ala.spatial.analysis.layers;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generate occurrence density and species richness layers.
 * <p/>
 * Outputs are diva grids.
 *
 * @author Adam
 */
public class DensityLayers {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
//        args = new String[] {
//            //"http://localhost:8083/biocache-service",
//            "http://biocache.ala.org.au/ws",
//            "e:\\records\\e_\\grid_ala2",
//            "9",
//            "-180,-90,180,90",
//            //"112,-42,155,-9",
//            "0.01",
//            "6"
//        };

        if (args.length < 3) {
            printUsage();
            return;
        }

        try {
            int gridSize = Integer.parseInt(args[2]);
            double resolution = Double.parseDouble(args[4]);
            int maxThreads = Integer.parseInt(args[5]);

            String[] sbbox = args[3].split(",");
            double[] bbox = new double[4];
            bbox[0] = Double.parseDouble(sbbox[0]);
            bbox[1] = Double.parseDouble(sbbox[1]);
            bbox[2] = Double.parseDouble(sbbox[2]);
            bbox[3] = Double.parseDouble(sbbox[3]);

            // load up the reference existing records file, or generate a new 
            // one if it does not exist.
            String recordsFile = args[1] + "_records.csv";
            Records records = null;
            if (new File(recordsFile).exists()) {
                records = new Records(recordsFile);
            } else {
                try {
                    records = new Records(args[0], "*:*", bbox, recordsFile, null);
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        if (args.length > 1
                                && new File(args[1] + "_records.csv").exists()) {
                            new File(args[1] + "_records.csv").delete();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                records = null;
                records = new Records(recordsFile);
            }

            //do not need species identifier
            records.removeSpeciesNames();
            System.gc();
            OccurrenceDensity occurrenceDensity = new OccurrenceDensity(gridSize, resolution, bbox);
            occurrenceDensity.write(records, args[1], null, maxThreads, true, true);

            System.gc();
            SpeciesDensity speciesDensity = new SpeciesDensity(gridSize, resolution, bbox);
            speciesDensity.write(records, args[1], null, maxThreads, true, true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void printUsage() {
        System.out.println("args[0] = biocache-service url, "
                + "args[1] = output prefix, "
                + "args[2] = number of grids for average.  e.g. 9 for 9x9), "
                + "args[3] = extents, e.g. -180,-90,180,90"
                + "args[4] = grid resolution, e.g. 0.5"
                + "args[5] = max threads, e.g. 1");
    }

    /**
     * Write the header for a diva grid file.
     *
     * @param filename
     * @param resolution
     * @param nrows
     * @param ncols
     * @param minx
     * @param miny
     * @param maxx
     * @param maxy
     * @param minvalue
     * @param maxvalue
     * @param nodatavalue
     */
    static void writeHeader(String filename, double resolution, int nrows, int ncols, double minx, double miny, double maxx, double maxy, double minvalue, double maxvalue, double nodatavalue) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            File file = new File(filename);
            FileWriter fw = new FileWriter(filename);
            fw.append("[General]\nCreator=alaspatial\nCreated=" + sdf.format(new Date()) + "\nTitle=" + file.getName() + "\n\n");

            fw.append("[GeoReference]\nProjection=\nDatum=\nMapunits=\nColumns=" + ncols
                    + "\nRows=" + nrows + "\nMinX=" + minx + "\nMaxX=" + maxx
                    + "\nMinY=" + miny + "\nMaxY=" + maxy + "\nResolutionX=" + resolution
                    + "\nResolutionY=" + resolution + "\n\n");

            fw.append("[Data]\nDataType=FLT4S\nMinValue=" + minvalue
                    + "\nMaxValue=" + maxvalue + "\nNoDataValue=" + nodatavalue
                    + "\nTransparent=0\nUnits=\n");

            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
