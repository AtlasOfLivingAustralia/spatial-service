package au.org.ala.spatial.util

import spock.lang.PendingFeature
import spock.lang.Specification

class JSONRequestBodyParserSpec extends Specification {

    JSONRequestBodyParser parser = new JSONRequestBodyParser()

    def setup() {
        parser.addParameter('wkt', String, false)
        parser.addParameter('name', String, false)
        parser.addParameter('description', String, true)
    }

    void "a request with all the parameters is parsed"() {
        when:
        boolean ok = parser.parseJSON('{"wkt":"POINT(1 2)","name":"n","description":"d"}')

        then:
        ok
        parser.errorMessages.empty
        parser.getParsedValue('wkt') == 'POINT(1 2)'
        parser.getParsedValue('name') == 'n'
        parser.getParsedValue('description') == 'd'
    }

    void "optional parameters may be missing"() {
        expect:
        parser.parseJSON('{"wkt":"POINT(1 2)","name":"n"}')
        parser.getParsedValue('description') == null
    }

    void "a missing compulsory parameter fails"() {
        expect:
        !parser.parseJSON('{"wkt":"POINT(1 2)"}')
        parser.errorMessages == ['Missing compulsory parameter name']
    }

    @PendingFeature(reason = "parseJSON returns true when a value has the wrong type")
    void "a value of the wrong type fails"() {
        expect:
        !parser.parseJSON('{"wkt":"POINT(1 2)","name":42}')
        parser.errorMessages == ['Invalid value for name expecting a value of type String']
        parser.getParsedValue('name') == null
    }

    @PendingFeature(reason = "parseJSON returns true when the JSON is malformed")
    void "malformed JSON fails"() {
        expect:
        !parser.parseJSON('{"wkt":')
        parser.errorMessages == ['Malformed JSON map']
    }

    void "a parameter cannot be defined twice"() {
        when:
        parser.addParameter('wkt', String, true)

        then:
        thrown(IllegalArgumentException)
    }
}
