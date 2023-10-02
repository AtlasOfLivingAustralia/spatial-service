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


import java.text.SimpleDateFormat

/**
 * Generate occurrence density and species richness layers.
 * <p/>
 * Outputs are diva grids.
 *
 * @author Adam
 */
import groovy.transform.CompileStatic
//@CompileStatic
class DensityLayers {


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
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd")
            File file = new File(filename)
            FileWriter fw = new FileWriter(filename)
            fw.append("[General]\nCreator=alaspatial\nCreated=" + sdf.format(new Date()) + "\nTitle=" + file.getName() + "\n\n")

            fw.append("[GeoReference]\nProjection=\nDatum=\nMapunits=\nColumns=" + ncols
                    + "\nRows=" + nrows + "\nMinX=" + minx + "\nMaxX=" + maxx
                    + "\nMinY=" + miny + "\nMaxY=" + maxy + "\nResolutionX=" + resolution
                    + "\nResolutionY=" + resolution + "\n\n")

            fw.append("[Data]\nDataType=FLT4S\nMinValue=" + minvalue
                    + "\nMaxValue=" + maxvalue + "\nNoDataValue=" + nodatavalue
                    + "\nTransparent=0\nUnits=\n")

            fw.close()
        } catch (Exception e) {
            e.printStackTrace()
        }
    }
}
