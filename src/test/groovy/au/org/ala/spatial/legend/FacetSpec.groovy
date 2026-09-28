package au.org.ala.spatial.legend

import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

class FacetSpec extends Specification {

    @Unroll
    void "parseFacet('#fq') returns null"() {
        expect:
        Facet.parseFacet(fq) == null

        where:
        fq << [null, '', 'ab']
    }

    void "(1) OR of values matches any of them"() {
        when:
        Facet facet = Facet.parseFacet('species:"a" OR species:"b"')

        then:
        facet.type == 2
        facet.fields as List == ['species']
        facet.toString() == 'species:"a" OR species:"b"'
        facet.isValid('a')
        facet.isValid('b')
        !facet.isValid('c')
    }

    void "(2) a negated wildcard matches missing values only"() {
        when:
        Facet facet = Facet.parseFacet('-species:*')

        then:
        facet.type == 1
        facet.field == 'species'
        !facet.includeRange
        facet.toString() == '-species:*'
        facet.isValid('')
        !facet.isValid('a')
    }

    void "(3) negated AND of exclusions matches the listed values and missing values"() {
        when:
        Facet facet = Facet.parseFacet('-(species:* AND -species:"a" AND -species:"b")')

        then:
        facet.isValid('a')
        facet.isValid('b')
        facet.isValid('')
        !facet.isValid('c')
    }

    void "a single value facet"() {
        when:
        Facet facet = Facet.parseFacet('species:"Iris germanica"')

        then:
        facet.type == 1
        facet.field == 'species'
        facet.value == 'Iris germanica'
        facet.includeRange
        facet.toString() == 'species:"Iris germanica"'
        facet.isValid('Iris germanica')
        !facet.isValid('Iris')
    }

    void "a single range facet"() {
        when:
        Facet facet = Facet.parseFacet('el1:[1 TO 4]')

        then:
        facet.type == 0
        facet.field == 'el1'
        facet.min == 1d
        facet.max == 4d
        facet.includeRange
        facet.toString() == 'el1:[1 TO 4]'
    }

    @PendingFeature(reason = "range facets return false from a finally block, so no value ever matches")
    @Unroll
    void "range facet [1 TO 4] includes #value"() {
        given:
        Facet facet = Facet.parseFacet('el1:[1 TO 4]')

        expect:
        facet.isValid(value as double)
        facet.isValid(value.toString())

        where:
        value << [1, 2.5, 4]
    }

    @Unroll
    void "range facet [1 TO 4] excludes #value"() {
        given:
        Facet facet = Facet.parseFacet('el1:[1 TO 4]')

        expect:
        !facet.isValid(value as double)
        !facet.isValid(value.toString())

        where:
        value << [0.5, 5]
    }

    @PendingFeature(reason = "range facets return false from a finally block, so no value ever matches")
    void "a negated range facet matches values outside the range"() {
        given:
        Facet facet = Facet.parseFacet('-el1:[1 TO 4]')

        expect:
        !facet.includeRange
        !facet.isValid(2d)
        facet.isValid(5d)
    }

    void "a range facet does not match text that is not a number"() {
        expect:
        !Facet.parseFacet('el1:[1 TO 4]').isValid('abc')
    }

    @PendingFeature(reason = "range facets return false from a finally block, so no value ever matches")
    void "(4) OR of ranges matches values in any range"() {
        when:
        Facet facet = Facet.parseFacet('el1:[1 TO 4] OR el1:[10 TO 20]')

        then:
        facet.isValid(2d)
        facet.isValid(15d)
        !facet.isValid(7d)
        facet.min == 1d
        facet.max == 20d
    }

    void "(7) AND of ranges on different fields"() {
        when:
        Facet facet = Facet.parseFacet('el1:[1 TO 4] AND el2:[10 TO 20]')

        then:
        facet.type == 2
        (facet.fields as List).sort() == ['el1', 'el2']
        facet.min == 1d
        facet.max == 20d
    }

    void "(10) scatterplot highlight query keeps every field"() {
        when:
        Facet facet = Facet.parseFacet('-(-(el1:[1 TO 4] AND el2:[1 TO 4]) AND el1:[* TO *] AND el2:[* TO *])')

        then:
        facet.type == 2
        (facet.fields as List).sort() == ['el1', 'el2']
        facet.orInAndTerms.length == 2
        facet.andTerms.length == 2
    }

    @PendingFeature(reason = "the last character of the query is dropped, parsing fails")
    void "(13) negated ranges combined with non empty fields"() {
        when:
        Facet facet = Facet.parseFacet('-(el1:[1 TO 4] AND el2:[1 TO 4]) AND el1:[* TO *] AND el2:[* TO *]')

        then:
        facet.type == 2
        (facet.fields as List).sort() == ['el1', 'el2']
    }

    @Unroll
    void "range constructor builds '#expected'"() {
        expect:
        new Facet(field, min as double, max as double, include).toString() == expected

        where:
        field | min                      | max                      | include | expected
        'el1' | 1                        | 4.5                      | true    | 'el1:[1 TO 4.5]'
        'el1' | 1                        | 4                        | false   | '-el1:[1 TO 4]'
        'el1' | Double.NEGATIVE_INFINITY | 3                        | true    | 'el1:[* TO 3]'
        'el1' | 0.25                     | Double.POSITIVE_INFINITY | true    | 'el1:[0.25 TO *]'
    }

    @Unroll
    void "occurrence_year range #min to #max becomes '#expected'"() {
        expect:
        new Facet('occurrence_year', min, max, true).toString() == expected

        where:
        min                    | max                    | expected
        '1990'                 | '2000'                 | 'occurrence_year:[1990-01-01T00:00:00Z TO 2000-12-31T00:00:00Z]'
        '1990-01-01T00:00:00Z' | '2000-12-31T00:00:00Z' | 'occurrence_year:[1990-01-01T00:00:00Z TO 2000-12-31T00:00:00Z]'
        '*'                    | '2000'                 | 'occurrence_year:[* TO 2000-12-31T00:00:00Z]'
        '1990'                 | '*'                    | 'occurrence_year:[1990-01-01T00:00:00Z TO *]'
    }

    @Unroll
    void "decade value '#value' of #field becomes '#expected'"() {
        expect:
        new Facet(field, value, true).toString() == expected

        where:
        field                    | value    | expected
        'decade'                 | '1990'   | 'decade:[1990-01-01T00:00:00Z TO 1999-12-31T00:00:00Z]'
        'occurrence_year_decade' | '"1990"' | 'occurrence_year:[1990-01-01T00:00:00Z TO 1999-12-31T00:00:00Z]'
        'decade'                 | 'before' | 'decade:[* TO 1849-12-31T00:00:00Z]'
    }
}
