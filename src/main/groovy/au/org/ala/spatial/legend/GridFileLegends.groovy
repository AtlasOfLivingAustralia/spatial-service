/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.legend

import au.org.ala.spatial.intersect.Grid

/**
 * Produces legend cutoff values for environmental layers
 *
 * @author Adam
 */

import groovy.util.logging.Slf4j

@Slf4j
//@CompileStatic
class GridFileLegends {

    //private static final Logger logger = log.getLogger(GridFileLegends.class);

    /**
     * @param filename grid file name.  must reside in
     *                    tabulation settings <environmental_data_path> as String
     * @param output_name Base output file path and name as String
     */
    GridFileLegends(String filename, String output_name, boolean useAreaEvaluation, String[] legendNames, FileWriter cutpointFile) {
        Grid g = Grid.getGrid(filename)
        //don't bother reading the whole file
        float[] d = g.getGrid(g.ncols * g.nrows < 128000 ? 1 : 128)

        if (legendNames != null) {
            Arrays.sort(legendNames)
        }

        float[] dsorted = d.clone()
        Arrays.sort(dsorted)

        //min/max correction
        dsorted[0] = (float) g.minval
        dsorted[dsorted.length - 1] = (float) g.maxval

        Legend[] legends = new Legend[5]
        legends[4] = new LegendEqualArea()
        legends[3] = new LegendEqualSize()
        legends[1] = new LegendEvenInterval()
        legends[2] = new LegendEvenIntervalLog()
        legends[0] = new LegendEvenIntervalLog10()

        int minI = 0
        double minE = 0
        boolean firstTime = true
        for (int i = 0; i < legends.length; i++) {
            if (legendNames == null || Arrays.binarySearch(legendNames, legends[i].getTypeName()) < 0) {
                continue
            }
            legends[i].generate(dsorted)
            legends[i].determineGroupSizes(dsorted)
            double e2 = 0
            if (useAreaEvaluation) {
                e2 = legends[i].evaluateStdDevArea(dsorted)
            } else {
                e2 = legends[i].evaluateStdDev(dsorted)
            }
            try {
                (new File(output_name + "_" + legends[i].getTypeName() + ".jpg")).delete()
            } finally {
            }
            legends[i].exportImage(d, g.ncols, output_name + "_" + legends[i].getTypeName() + ".jpg", Math.max(8, g.ncols / 800 as double) as int, false)
            legends[i].exportLegend(output_name + "_" + legends[i].getTypeName() + "_legend.txt")

            log.debug(output_name + "," + legends[i].getTypeName() + ": " + String.valueOf(e2))
            if (firstTime || e2 <= minE) {
                minE = e2
                minI = i
                firstTime = false
            }
        }

        try {
            if (cutpointFile != null) {
                cutpointFile.append(filename).append(",").append(legends[minI].getTypeName())
                float[] minmax = legends[minI].getMinMax()
                float[] f = legends[minI].getCutoffFloats()

                cutpointFile.append(",min,").append(String.valueOf(minmax[0]))

                cutpointFile.append(",#cutpoints,").append(String.valueOf(f.length))

                cutpointFile.append(",cutpoints")
                for (int i = 0; i < f.length; i++) {
                    cutpointFile.append(",").append(String.valueOf(f[i]))
                }

                cutpointFile.append(",distribution")
                int[] a
                if (useAreaEvaluation) {
                    a = legends[minI].groupSizesArea
                } else {
                    a = legends[minI].groupSizes
                }
                for (int i = 0; i < a.length; i++) {
                    cutpointFile.append(",").append(String.valueOf(a[i]))
                }

                cutpointFile.append("\n")
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        log.debug(output_name + ",best=" + legends[minI].getTypeName())
    }
}
