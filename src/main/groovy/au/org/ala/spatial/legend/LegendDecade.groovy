package au.org.ala.spatial.legend

/**
 * Generates a legend based on decades
 *
 * @author Natasha Quimby (natsha.quimby@csiro.au)
 */
//@CompileStatic
class LegendDecade extends Legend {

    @Override
    void generate(float[] d, int divisions) {
        //all the values for the decades MUST be included
        if (Float.isNaN(max)) {
            return
        }
        cutoffMins = removeNaN(d)
        cutoffs = new float[cutoffMins.length]
        for (int i = 0; i < cutoffs.length; i++) {
            cutoffs[i] = (float) (cutoffMins[i] + 9)
        }

    }

    private static float[] removeNaN(float[] ds) {
        int nanCount = 0
        for (float d : ds) {
            if (Float.isNaN(d)) {
                nanCount++
            }
        }
        float[] clean = new float[ds.length - nanCount]
        int i = 0
        for (float d : ds) {
            if (!Double.isNaN(d)) {
                clean[i++] = d
            }
        }
        return clean
    }

    @Override
    String getTypeName() {

        return "Decade Legend"
    }

}
