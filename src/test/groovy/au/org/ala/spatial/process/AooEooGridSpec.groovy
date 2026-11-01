package au.org.ala.spatial.process

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import spock.lang.Specification
import spock.lang.Unroll

import java.awt.geom.Point2D

/**
 * The grid cells behind the Area Of Occupancy of the AOO/EOO tool.
 */
class AooEooGridSpec extends Specification {

    // float precision of the grid cell corners
    static final double EPS = 1e-4

    /** True if [lng, lat] falls inside the AOO cell that AooEoo assigns to it. */
    static boolean inOwnCell(double lng, double lat, double gridSize) {
        Point2D c = AooEoo.aooProcess(["$lat,$lng".toString()] as String[], gridSize).first()
        c.x - EPS <= lng && lng < c.x + gridSize + EPS && c.y - EPS <= lat && lat < c.y + gridSize + EPS
    }

    static List<List<Double>> randomPoints(double minLng, double maxLng, double minLat, double maxLat, int n) {
        Random r = new Random(42)
        (1..n).collect { [minLng + r.nextDouble() * (maxLng - minLng), minLat + r.nextDouble() * (maxLat - minLat)] }
    }

    @Unroll
    void "round(#d, #by) is the start of the cell that contains #d"() {
        expect:
        AooEoo.round(d, by) == (float) expected

        where:
        d    | by  | expected
        0.5  | 1   | 0
        1.0  | 1   | 1
        2.75 | 0.5 | 2.5
        -0.5 | 1   | -1
        -1.0 | 1   | -1
        -1.5 | 1   | -2
        -2.0 | 1   | -2
    }

    @Unroll
    void "points with positive coordinates fall in their own #gridSize degree cell"() {
        expect:
        randomPoints(0.001, 179, 0.001, 89, 500).findAll { !inOwnCell(it[0], it[1], gridSize) } == []

        where:
        gridSize << [0.02, 0.1, 1]
    }

    // west of Greenwich or south of the equator, which is all of Australia
    @Unroll
    void "points with negative coordinates fall in their own #gridSize degree cell"() {
        expect:
        randomPoints(-179, -0.001, -89, -0.001, 500).findAll { !inOwnCell(it[0], it[1], gridSize) } == []

        where:
        gridSize << [0.02, 0.1, 1]
    }

    void "two points on either side of the equator are two cells"() {
        expect:
        AooEoo.aooProcess(['-0.5,10.5', '0.5,10.5'] as String[], 1).size() == 2
    }

    void "points in the same cell are counted once"() {
        expect:
        AooEoo.aooProcess(['10.1,20.1', '10.9,20.9', '"10.5,20.5"', 'not a point'] as String[], 1).size() == 1
    }

    void "the AOO WKT has one square of gridSize per cell"() {
        given:
        Set<Point2D> cells = AooEoo.aooProcess(['10.5,20.5', '12.5,20.5'] as String[], 1)

        when:
        Geometry g = new WKTReader().read(AooEoo.aooWkt(cells, 1))

        then:
        g.numGeometries == 2
        Math.abs(g.area - 2) < EPS
    }
}
