package au.org.ala.spatial.dto

import spock.lang.Specification
import spock.lang.Unroll

class LayerFilterSpec extends Specification {

    void "parseLayerFilters reads environmental and contextual terms inside ENVELOPE()"() {
        when:
        def filters = LayerFilter.parseLayerFilters('ENVELOPE(el600,1,4:cl22,pid1,pid2)')

        then:
        filters.length == 2

        filters[0].layername == 'el600'
        !filters[0].contextual
        filters[0].minimum_value == 1d
        filters[0].maximum_value == 4d
        filters[0].ids == null

        filters[1].layername == 'cl22'
        filters[1].contextual
        filters[1].ids as List == ['pid1', 'pid2']
    }

    void "parseLayerFilters accepts terms without the ENVELOPE() wrapper"() {
        when:
        def filters = LayerFilter.parseLayerFilters('el600,1,4:el801,9.6,20.3')

        then:
        filters*.layername == ['el600', 'el801']
        filters[1].minimum_value == 9.6d
        filters[1].maximum_value == 20.3d
    }

    void "parseLayerFilter unwraps a single ENVELOPE() term, case insensitive"() {
        when:
        def filter = LayerFilter.parseLayerFilter('envelope(el600,-2.5,3)')

        then:
        filter.layername == 'el600'
        filter.minimum_value == -2.5d
        filter.maximum_value == 3d
    }

    void "a contextual term without pids has no ids"() {
        expect:
        LayerFilter.parseLayerFilter('cl22').ids.length == 0
    }

    @Unroll
    void "isValid(#value) in [1, 4] is #expected"() {
        expect:
        new LayerFilter('el600', 1, 4).isValid(value) == expected

        where:
        value        | expected
        1d           | true
        2.5d         | true
        4d           | true
        4.00000001d  | true  // within the data type correction
        0.99999999d  | true
        4.1d         | false
        0.9d         | false
        Double.NaN   | false
    }
}
