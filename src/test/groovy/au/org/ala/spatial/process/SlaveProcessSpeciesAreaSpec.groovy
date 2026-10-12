package au.org.ala.spatial.process

import au.org.ala.spatial.Util
import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.dto.SpeciesInput
import spock.lang.Specification

class SlaveProcessSpeciesAreaSpec extends Specification {

    static final String AREA_WKT = 'POLYGON((0 0,0 10,10 10,10 0,0 0))'
    static final String SPECIES_WKT = 'POLYGON((5 5,5 15,15 15,15 5,5 5))'

    def proc = new SlaveProcess() {
        @Override
        String getWkt(objectId) {
            // pids of known areas
            return objectId.toString() == '82281' ? SPECIES_WKT : null
        }
    }

    // wkt that getSpeciesArea registers as a qid
    String registeredWkt

    def setup() {
        def spec = this
        Util.metaClass.static.makeQid = { SpeciesInput query, ws ->
            spec.registeredWkt = query.wkt
            '1'
        }
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Util)
    }

    def "species wkt holding the pid of an area is resolved before intersecting"() {
        given:
        def species = new SpeciesInput(q: ['*:*'], wkt: '82281')
        def area = new AreaInput(wkt: AREA_WKT)

        when:
        proc.getSpeciesArea(species, [area])

        then:
        notThrown(Exception)
        registeredWkt.startsWith('POLYGON')
        // intersection of both squares is (5 5)-(10 10)
        registeredWkt.contains('5 5')
        registeredWkt.contains('10 10')
    }

    def "species wkt that is real wkt is not resolved as a pid"() {
        given:
        def species = new SpeciesInput(q: ['*:*'], wkt: SPECIES_WKT)

        when:
        proc.getSpeciesArea(species, [new AreaInput(wkt: AREA_WKT)])

        then:
        registeredWkt.contains('5 5')
        registeredWkt.contains('10 10')
    }

    def "species without wkt takes the area wkt"() {
        when:
        proc.getSpeciesArea(new SpeciesInput(q: ['*:*']), [new AreaInput(wkt: AREA_WKT)])

        then:
        registeredWkt == AREA_WKT
    }

    def "species wkt holding a pid is resolved when there is no area"() {
        when:
        def result = proc.getSpeciesArea(new SpeciesInput(q: ['*:*'], wkt: '82281'), [])

        then:
        result.wkt == SPECIES_WKT
    }
}
