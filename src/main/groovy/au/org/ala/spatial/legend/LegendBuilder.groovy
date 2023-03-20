/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.legend

/**
 * @author Adam
 */
//@CompileStatic
class LegendBuilder {

    /**
     * Build a legend from a QueryField
     *
     * @param qf
     * @return
     */
    static LegendObject build(QueryField qf) {
        switch (qf.getFieldType()) {
            case QueryField.FieldType.INT:
                return intLegend(qf)
            case QueryField.FieldType.LONG:
                return longLegend(qf)
            case QueryField.FieldType.FLOAT:
                return floatLegend(qf)
            case QueryField.FieldType.DOUBLE:
                return doubleLegend(qf)
            default:
                return stringLegend(qf)
        }
    }

    private static LegendObject intLegend(QueryField qf) {
        int[] raw = qf.intData
        double[] d = new double[raw.length]
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == Integer.MIN_VALUE) {
                d[i] = Double.NaN
            } else {
                d[i] = raw[i]
            }
        }
        return legendFromDoubles(d, qf)
    }

    static LegendObject legendFromDoubles(double[] d, QueryField qf) {
        Arrays.sort(d)
        //to floats
        float[] f = new float[d.length]
        for (int i = 0; i < d.length; i++) {
            f[i] = (float) d[i]
        }
        Legend legend = new LegendEqualArea()
        legend.generate(f)

        legend.determineGroupSizes(f)

        return new LegendObject(legend, qf.getFieldType())
    }

    static LegendObject legendForDecades(double[] d, QueryField qf) {
        Legend legend = new LegendDecade()

        Arrays.sort(d)

        //to floats
        float[] f = new float[d.length]
        for (int i = 0; i < d.length; i++) {
            f[i] = (float) d[i]
        }
        legend.generate(f)

        return new LegendObject(legend, qf.getFieldType())
    }

    private static LegendObject longLegend(QueryField qf) {
        long[] raw = qf.longData
        double[] d = new double[raw.length]
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == Long.MIN_VALUE) {
                d[i] = Double.NaN
            } else {
                d[i] = raw[i]
            }
        }
        return legendFromDoubles(d, qf)
    }

    private static LegendObject floatLegend(QueryField qf) {
        float[] raw = qf.floatData
        double[] d = new double[raw.length]
        for (int i = 0; i < raw.length; i++) {
            d[i] = raw[i]
        }
        return legendFromDoubles(d, qf)
    }

    private static LegendObject doubleLegend(QueryField qf) {
        double[] raw = qf.doubleData
        double[] d = new double[raw.length]
        System.arraycopy(raw, 0, d, 0, raw.length)
        return legendFromDoubles(d, qf)
    }

    private static LegendObject stringLegend(QueryField qf) {
        return new LegendObject(qf.stringData, qf.stringCounts)
    }
}
