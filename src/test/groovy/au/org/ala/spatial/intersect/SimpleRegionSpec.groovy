package au.org.ala.spatial.intersect

import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

class SimpleRegionSpec extends Specification {

    static final int UNDEFINED = 0
    static final int BOUNDING_BOX = 1
    static final int CIRCLE = 2
    static final int POLYGON = 3

    void "an undefined region contains everything"() {
        given:
        SimpleRegion region = new SimpleRegion()

        expect:
        region.type == UNDEFINED
        region.isWithin(0, 0)
        region.isWithin(-179, 89)
    }

    void "parseSimpleRegion('none') is null"() {
        expect:
        SimpleRegion.parseSimpleRegion('none') == null
        SimpleRegion.parseSimpleRegion('NONE') == null
    }

    @PendingFeature(reason = "setBox stores the bounding box as [[minx, maxx], [miny, maxy]]")
    void "parseSimpleRegion recognises a four corner box"() {
        when:
        SimpleRegion region = SimpleRegion.parseSimpleRegion('1 2,1 4,3 4,3 2')

        then:
        region.type == BOUNDING_BOX
        // same layout as polygons: [[min lng, min lat], [max lng, max lat]]
        region.boundingBox == [[1d, 2d], [3d, 4d]] as double[][]
        region.width == 2d
        region.height == 2d
    }

    void "parseSimpleRegion reads any other shape as a polygon"() {
        when:
        SimpleRegion region = SimpleRegion.parseSimpleRegion('0 0,4 0,0 4,0 0')

        then:
        region.type == POLYGON
        region.numberOfPoints == 4
        region.isWithin(1, 1)
        !region.isWithin(3, 3)
    }

    @Unroll
    void "box (1,2)-(3,4) contains (#lng, #lat): #expected"() {
        given:
        SimpleRegion region = new SimpleRegion()
        region.setBox(3, 4, 1, 2) // corners in any order

        expect:
        region.isWithin(lng, lat) == expected

        where:
        lng | lat | expected
        2   | 3   | true
        1   | 2   | true // on the edge
        3   | 4   | true
        0   | 3   | false
        2   | 5   | false
    }

    @PendingFeature(reason = "setBox stores the bounding box as [[minx, maxx], [miny, maxy]]")
    void "a box is clamped to the world"() {
        given:
        SimpleRegion region = new SimpleRegion()

        when:
        region.setBox(-200, -100, 200, 100)

        then:
        region.boundingBox == [[-180d, -90d], [180d, 90d]] as double[][]
        region.width == 360d
        region.height == 180d
    }

    void "a circle contains points within its radius"() {
        given:
        SimpleRegion region = new SimpleRegion()

        when:
        region.setCircle(10, 10, 1)

        then:
        region.type == CIRCLE
        region.isWithin(10.5, 10.5)
        region.isWithin(11, 10)
        !region.isWithin(11, 11)
    }

    @Unroll
    void "L shaped polygon contains (#lng, #lat): #expected"() {
        given:
        SimpleRegion region = new SimpleRegion()
        region.setPolygon([0, 0, 4, 0, 4, 2, 2, 2, 2, 4, 0, 4, 0, 0] as double[])

        expect:
        region.isWithin(lng, lat) == expected

        where:
        lng | lat | expected
        1   | 1   | true
        3   | 1   | true
        1   | 3   | true
        3   | 3   | false // the missing corner
        5   | 1   | false
        -1  | 1   | false
    }

    void "a polygon has a bounding box"() {
        given:
        SimpleRegion region = new SimpleRegion()

        when:
        region.setPolygon([0, 0, 4, 0, 4, 2, 2, 2, 2, 4, 0, 4, 0, 0] as double[])

        then:
        region.boundingBox == [[0d, 0d], [4d, 4d]] as double[][]
    }
}
