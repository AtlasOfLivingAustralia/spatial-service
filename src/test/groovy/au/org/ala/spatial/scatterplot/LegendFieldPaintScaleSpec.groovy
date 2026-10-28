package au.org.ala.spatial.scatterplot

import au.org.ala.spatial.legend.LegendObject
import spock.lang.Specification

import java.awt.Color

/**
 * LegendFieldPaintScale is the JFreeChart paint scale for the scatterplot legend. Its lower/upper
 * bounds must be the min/max of the legend, and must not index an empty min/max array.
 */
class LegendFieldPaintScaleSpec extends Specification {

    LegendObject legendWith(float[] minMax) {
        Stub(LegendObject) { getMinMax() >> minMax }
    }

    def "a null legend uses the default bounds 0..1"() {
        given:
        def scale = new LegendFieldPaintScale(null, Color.RED)

        expect:
        scale.lowerBound == 0
        scale.upperBound == 1
    }

    def "the bounds are the min and max of the legend"() {
        given:
        def scale = new LegendFieldPaintScale(legendWith([5f, 20f] as float[]), Color.RED)

        expect:
        scale.lowerBound == 5d
        scale.upperBound == 20d
    }

    def "an empty min/max does not throw"() {
        given:
        def scale = new LegendFieldPaintScale(legendWith([] as float[]), Color.RED)

        when:
        scale.lowerBound
        scale.upperBound

        then:
        noExceptionThrown()
    }
}
