package au.org.ala.spatial.legend

import spock.lang.Specification
import spock.lang.Unroll

class LegendSpec extends Specification {

    static final int OPAQUE = argb(0xFF000000)
    static final int WHITE = argb(0xFFFFFFFF)

    // hex literals above 0x7FFFFFFF are longs in Groovy
    static int argb(long colour) {
        (int) colour
    }

    static float[] oneToHundred() {
        float[] d = new float[100]
        for (int i = 0; i < 100; i++) {
            d[i] = i + 1
        }
        d
    }

    @Unroll
    void "#legend.typeName cutoffs are sorted and end at the maximum"() {
        given:
        float[] d = oneToHundred()

        when:
        legend.generate(d, 10)
        legend.determineGroupSizes(d)

        then:
        legend.min == 1f
        legend.max == 100f
        legend.numberOfRecords == 100
        legend.numberOfUniqueValues == 100
        legend.cutoffs.length == 10
        (0..<9).every { legend.cutoffs[it] <= legend.cutoffs[it + 1] }
        legend.cutoffs[9] == 100f
        legend.groupSizes.sum() == 100
        legend.countOfNaN == 0

        where:
        legend << [new LegendEvenInterval(), new LegendEqualSize(), new LegendEqualArea(),
                   new LegendEvenIntervalLog(), new LegendEvenIntervalLog10()]
    }

    void "even interval cutoffs are equally spaced"() {
        given:
        Legend legend = new LegendEvenInterval()

        when:
        legend.generate(oneToHundred(), 10)

        then:
        legend.cutoffs[0] == 10.9f
        legend.cutoffs[4] == 50.5f
    }

    void "equal size puts the same number of values in every group"() {
        given:
        float[] d = oneToHundred()
        Legend legend = new LegendEqualSize()

        when:
        legend.generate(d, 10)
        legend.determineGroupSizes(d)

        then:
        legend.groupSizes as List == [10] * 10
    }

    void "NaN values are counted apart from the records"() {
        given:
        float[] d = [1f, 2f, 3f, 4f, Float.NaN, Float.NaN] as float[]
        Legend legend = new LegendEvenInterval()

        when:
        legend.generate(d, 2)
        legend.determineGroupSizes(d)

        then:
        legend.numberOfRecords == 4
        legend.max == 4f
        legend.countOfNaN == 2
        legend.groupSizes.sum() == 4
    }

    void "only NaN values produce no cutoffs"() {
        given:
        Legend legend = new LegendEvenInterval()

        when:
        legend.generate([Float.NaN, Float.NaN] as float[], 10)

        then:
        Float.isNaN(legend.max)
        legend.cutoffs == null
        Double.isNaN(legend.evaluateStdDev([Float.NaN] as float[]))
    }

    void "getColour maps the range onto the colour ramp"() {
        given:
        Legend legend = new LegendEvenInterval()
        legend.generate(oneToHundred(), 10)

        expect:
        legend.getColour(1f) == (Legend.colours[0] | OPAQUE)
        legend.getColour(100f) == (Legend.colours[10] | OPAQUE)
        legend.getColour(Float.NaN) == WHITE
        legend.getColour(101f) == WHITE
    }

    void "getLinearColour interpolates each channel"() {
        expect:
        Legend.getLinearColour(0, 0, 10, 0x000000, 0xFF00FF) == argb(0xFF000000)
        Legend.getLinearColour(10, 0, 10, 0x000000, 0xFF00FF) == argb(0xFFFF00FF)
        Legend.getLinearColour(5, 0, 10, 0x000000, 0xFF00FF) == argb(0xFF7F007F)
    }

    // The static getColour computed (int) a on a value between 0 and 1, which is 0 for anything under 1, so every
    // value was interpolated inside the first step of the ramp and extrapolated beyond it.
    void "the static getColour walks the ramp as the value grows"() {
        expect:
        Legend.getColour(0, 0, 100) == (Legend.colours[0] | OPAQUE)
        Legend.getColour(50, 0, 100) == (Legend.colours[5] | OPAQUE)
        Legend.getColour(100, 0, 100) == (Legend.colours[10] | OPAQUE)
        Legend.getColour(Float.NaN as double, 0, 100) == WHITE
    }

    // The log legends shift the values up by an offset to keep them positive, and subtracted it on a line of its
    // own, below the assignment: the cutoffs stayed in the shifted range.
    @Unroll
    void "#legend.typeName cutoffs stay inside the range of the values"() {
        given:
        float[] d = new float[11]
        (0..10).each { d[it] = (it - 5) as float }

        when:
        legend.generate(d, 10)

        then:
        legend.min == -5f
        legend.max == 5f
        legend.cutoffs.every { it >= -5f && it <= 5f }

        where:
        legend << [new LegendEvenIntervalLog(), new LegendEvenIntervalLog10()]
    }
}
