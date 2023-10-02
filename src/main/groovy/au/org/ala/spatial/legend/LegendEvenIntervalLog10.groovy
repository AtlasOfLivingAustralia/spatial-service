/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package au.org.ala.spatial.legend

/**
 * generates legend with even interval cutoff's.
 *
 * @author Adam
 */
//@CompileStatic
class LegendEvenIntervalLog10 extends Legend {

    @Override
    void generate(float[] d, int divisions) {
        init(d, divisions)
        if (Float.isNaN(max)) {
            return
        }

        //prevent negative number assignment
        float offset = (float) ((min < 1) ? 1 - min : 0)
        float tmax = (float) (max + offset)
        float tmin = (float) (min + offset)
        double lmin = Math.log10(tmin)
        double lmax = Math.log10(tmax)
        double lrange = lmax - lmin

        cutoffs = new float[divisions]

        for (int i = 0; i < divisions; i++) {
            cutoffs[i] = (float) Math.pow(10, lmin + lrange * ((i + 1) / (double) (divisions)))
            -offset
        }

        //fix max
        cutoffs[divisions - 1] = max
    }

    @Override
    String getTypeName() {
        return "Even Interval Log 10"
    }
}
