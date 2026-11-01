package au.org.ala.spatial.intersect

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The polygon to grid cell rasterisation that GridCutterService (area cutting of environmental
 * layers) and ComplexRegion use.
 *
 * The cells are checked against JTS: a cell has to be returned if the polygon covers part of it,
 * and must not be returned if the polygon does not touch it.
 */
class SimpleRegionGridSpec extends Specification {

    static final long TIMEOUT_MS = 10000

    static final GeometryFactory GF = new GeometryFactory()

    /** Runs the closure in its own thread, so an endless loop fails the test instead of hanging the build. */
    static Map runWithTimeout(Closure c) {
        Map out = [finished: false]
        Thread t = new Thread({
            try {
                out.result = c()
                out.finished = true
            } catch (Throwable e) {
                out.error = e
            }
        })
        t.daemon = true
        t.start()
        t.join(TIMEOUT_MS)
        out
    }

    static Set<List<Integer>> cellsOf(int[][] cells) {
        cells.collect { [it[0], it[1]] } as Set
    }

    /** Cells [x, y] that the polygon covers in part (intersection area > 0) and cells it touches. */
    static Map<String, Set<List<Integer>>> expectedCells(String wkt, double x1, double y1, double x2, double y2, int width, int height) {
        Geometry g = new WKTReader().read(wkt)
        double divx = (x2 - x1) / width
        double divy = (y2 - y1) / height
        Set<List<Integer>> covered = [] as Set
        Set<List<Integer>> touched = [] as Set
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Geometry cell = GF.toGeometry(new Envelope(x1 + x * divx, x1 + (x + 1) * divx, y1 + y * divy, y1 + (y + 1) * divy))
                if (g.intersects(cell)) {
                    touched << [x, y]
                    if (g.intersection(cell).area > 1e-12) {
                        covered << [x, y]
                    }
                }
            }
        }
        [covered: covered, touched: touched]
    }

    static String toWkt(String simpleRegionPoints) {
        "POLYGON((${simpleRegionPoints}))"
    }

    // Cell heights of a whole degree or more work, because the latitude loop advanced by (int) divy.
    @Unroll
    void "getOverlapGridCells for a sloped polygon on a grid of #divy degree cells returns the cells it covers"() {
        given:
        String points = '1.3 1.1,17.9 3.3,9.1 18.7,1.3 1.1'
        SimpleRegion r = SimpleRegion.parseSimpleRegion(points)
        def expected = expectedCells(toWkt(points), 0, 0, 20, 20, width, height)

        when:
        def out = runWithTimeout { r.getOverlapGridCells(0, 0, 20, 20, width, height, null) }

        then:
        out.finished

        and:
        def actual = cellsOf(out.result as int[][])
        actual.containsAll(expected.covered)
        expected.touched.containsAll(actual)

        where:
        width | height | divy
        10    | 10     | 2
        20    | 20     | 1
    }

    /**
     * AtlasOfLivingAustralia/spatial-service#230: with cells smaller than one degree the latitude
     * loop "k += (int) divy" never advanced. AtlasOfLivingAustralia/spatial-service#231 changed the
     * step to max(divy, 1), which avoided the hang but skipped rows, and that change was then lost
     * in 32a100b (the AtlasOfLivingAustralia/spatial-service#256 sandbox work).
     */
    @Unroll
    void "getOverlapGridCells finishes for a sloped polygon on a grid of #divy degree cells (AtlasOfLivingAustralia/spatial-service#230)"() {
        given:
        String points = '0.13 0.11,0.79 0.33,0.41 0.87,0.13 0.11'
        SimpleRegion r = SimpleRegion.parseSimpleRegion(points)

        when:
        def out = runWithTimeout { r.getOverlapGridCells(0, 0, 1, 1, width, height, null) }

        then:
        out.finished

        where:
        width | height | divy
        10    | 10     | 0.1
        100   | 100    | 0.01
    }

    @Unroll
    void "getOverlapGridCells for a sloped polygon on a grid of #divy degree cells returns the cells it covers (AtlasOfLivingAustralia/spatial-service#230)"() {
        given:
        String points = "${0.13 * scale} ${0.11 * scale},${0.79 * scale} ${0.33 * scale},${0.41 * scale} ${0.87 * scale},${0.13 * scale} ${0.11 * scale}"
        SimpleRegion r = SimpleRegion.parseSimpleRegion(points)
        def expected = expectedCells(toWkt(points), 0, 0, scale, scale, 20, 20)

        when:
        def out = runWithTimeout { r.getOverlapGridCells(0, 0, scale, scale, 20, 20, null) }

        then:
        out.finished

        and:
        def actual = cellsOf(out.result as int[][])
        actual.containsAll(expected.covered)
        expected.touched.containsAll(actual)

        where:
        scale | divy
        2     | 0.1
        50    | 2.5
    }

    /**
     * parseGeometryCollection kept the opening bracket of each ring ("(10 10,..."), so the first
     * longitude failed to parse and silently became 0.
     */
    void "parseWKT reads the polygons of a GEOMETRYCOLLECTION"() {
        when:
        def out = runWithTimeout {
            SimpleShapeFile.parseWKT('GEOMETRYCOLLECTION(POLYGON((10 10,10 11,11 11,11 10,10 10)),POLYGON((20 20,20 21,21 21,21 20,20 20)))')
        }

        then:
        out.finished

        and:
        SimpleRegion r = out.result as SimpleRegion
        r.isWithin(10.5, 10.5)
        r.isWithin(20.5, 20.5)
        !r.isWithin(5, 10.5)
        !r.isWithin(15, 15)
    }

    /**
     * parseWKT builds a ComplexRegion for multi part geometries and calls useMask, which rasterises
     * every polygon with getOverlapGridCells, so it hung on the AtlasOfLivingAustralia/spatial-service#230 loop. This is the path used by
     * GeneratePoints, TabulationUtil and TabulationGeneratorService.
     */
    void "parseWKT of a MULTIPOLYGON of triangles finishes (AtlasOfLivingAustralia/spatial-service#230)"() {
        when:
        def out = runWithTimeout {
            SimpleShapeFile.parseWKT('MULTIPOLYGON(((140 -30,141 -30,140.5 -29,140 -30)),((150 -35,151 -35,150.5 -34,150 -35)))')
        }

        then:
        out.finished

        and:
        SimpleRegion r = out.result as SimpleRegion
        r.isWithin(140.5, -29.8)
        r.isWithin(150.5, -34.8)
        !r.isWithin(145, -32)
    }

    /**
     * A closed five point box is a BOUNDING_BOX now, so it is rasterised by
     * getOverlapGridCells_Box, which sized its output from unclamped start offsets: a box that does
     * not reach the grid gave a negative array length when it was outside on one axis, and a
     * positive one full of zeroed cells when it was outside on both.
     */
    @Unroll
    void "getOverlapGridCells for a box outside the grid returns no cells (#points)"() {
        given:
        SimpleRegion r = SimpleRegion.parseSimpleRegion(points)

        expect:
        r.getType() == SimpleRegion.BOUNDING_BOX

        when:
        def out = runWithTimeout { r.getOverlapGridCells(0, 0, 1, 1, 10, 10, null) }

        then:
        out.error == null
        out.finished

        and:
        cellsOf(out.result as int[][]).isEmpty()

        where:
        points << ['2 0.2,2 0.8,3 0.8,3 0.2,2 0.2',
                   '-3 0.2,-3 0.8,-2 0.8,-2 0.2,-3 0.2',
                   '0.2 2,0.2 3,0.8 3,0.8 2,0.2 2',
                   '0.2 -3,0.2 -2,0.8 -2,0.8 -3,0.2 -3',
                   '2 2,2 3,3 3,3 2,2 2']
    }

    /** The same box, with a three state map to fill, must not write outside the grid either. */
    void "getOverlapGridCells for a box outside the grid leaves the three state map untouched"() {
        given:
        SimpleRegion r = SimpleRegion.parseSimpleRegion('2 0.2,2 0.8,3 0.8,3 0.2,2 0.2')
        byte[][] map = new byte[10][10]

        when:
        def out = runWithTimeout { r.getOverlapGridCells(0, 0, 1, 1, 10, 10, map) }

        then:
        out.error == null
        out.finished

        and:
        map.every { row -> row.every { it == SimpleRegion.GI_UNDEFINED } }
    }
}
