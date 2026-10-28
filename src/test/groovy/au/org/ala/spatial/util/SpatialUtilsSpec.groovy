package au.org.ala.spatial.util

import org.locationtech.jts.io.WKTReader
import spock.lang.Specification
import spock.lang.Unroll

class SpatialUtilsSpec extends Specification {

    // Spherical area of a 1x1 degree cell on the equator: R^2 * dLng * sin(1 degree), in sq km
    static final double EQUATOR_CELL_SQ_KM = 6378137d * 6378137d * Math.toRadians(1) * Math.sin(Math.toRadians(1)) / 1e6

    @Unroll
    void "longitude #lng survives a round trip through pixels"() {
        expect:
        Math.abs(SpatialUtils.convertPixelToLng(SpatialUtils.convertLngToPixel(lng)) - lng) < 1e-5

        where:
        lng << [-180d, -3.7d, 0d, 151.2d, 179.9d]
    }

    @Unroll
    void "latitude #lat survives a round trip through pixels"() {
        expect:
        Math.abs(SpatialUtils.convertPixelToLat(SpatialUtils.convertLatToPixel(lat)) - lat) < 1e-5

        where:
        lat << [-85d, -35.3d, 0d, 40.4d, 85d]
    }

    void "the origin maps to the centre of the pixel space"() {
        expect:
        SpatialUtils.convertLngToPixel(0) == 268435456
        SpatialUtils.convertLatToPixel(0) == 268435456
    }

    @Unroll
    void "#meters metres survive a round trip through web mercator"() {
        expect:
        Math.abs(SpatialUtils.convertLngToMeters(SpatialUtils.convertMetersToLng(meters)) - meters) < 1e-6
        Math.abs(SpatialUtils.convertLatToMeters(SpatialUtils.convertMetersToLat(meters)) - meters) < 1e-3

        where:
        meters << [-1000000d, 0d, 12345.6d, 5000000d]
    }

    void "the edge of the web mercator world is longitude 180"() {
        expect:
        Math.abs(SpatialUtils.convertMetersToLng(20037508.342789244) - 180) < 1e-9
    }

    void "metres and pixels conversions are inverse"() {
        when:
        double pixels = SpatialUtils.convertMetersToPixels(1000, 40, 10)

        then:
        Math.abs(SpatialUtils.convertPixelsToMeters(1, 40, 10) * pixels - 1000) < 1e-6
    }

    @Unroll
    void "planeDistance of one degree on the equator at zoom #zoom is #expected pixels"() {
        expect:
        SpatialUtils.planeDistance(0, 0, 0, 1, zoom) == expected

        where:
        zoom | expected
        21   | 1491308
        10   | 728
        0    | 0
    }

    void "convertGeoToPoints turns polygon WKT into colon separated pairs"() {
        expect:
        SpatialUtils.convertGeoToPoints('POLYGON((1 2,3 4,5 6,1 2))') == '1:2,3:4,5:6,1:2'
        SpatialUtils.convertGeoToPoints('MULTIPOLYGON(((1 2,3 4,5 6,1 2)),((7 8,9 10,11 12,7 8)))') ==
                '1:2,3:4,5:6,1:2S7:8,9:10,11:12,7:8'
        SpatialUtils.convertGeoToPoints(null) == ''
    }

    void "calculateArea of a coordinate array returns sq km"() {
        given:
        double[][] cell = [[0, 0], [0, 1], [1, 1], [1, 0], [0, 0]] as double[][]

        expect:
        Math.abs(SpatialUtils.calculateArea(cell) - EQUATOR_CELL_SQ_KM) / EQUATOR_CELL_SQ_KM < 0.001
    }

    void "calculateArea of WKT returns sq m and does not depend on ring direction"() {
        given:
        double expected = EQUATOR_CELL_SQ_KM * 1e6

        expect:
        Math.abs(SpatialUtils.calculateArea('POLYGON((0 0,0 1,1 1,1 0,0 0))') - expected) / expected < 0.001
        Math.abs(SpatialUtils.calculateArea('POLYGON((0 0,1 0,1 1,0 1,0 0))') - expected) / expected < 0.001
    }

    void "calculateArea sums the polygons of a MULTIPOLYGON"() {
        given:
        double single = SpatialUtils.calculateArea('POLYGON((0 0,0 1,1 1,1 0,0 0))')

        when:
        double multi = SpatialUtils.calculateArea('MULTIPOLYGON(((0 0,0 1,1 1,1 0,0 0)),((0 -1,0 0,1 0,1 -1,0 -1)))')

        then:
        Math.abs(multi - 2 * single) / single < 0.001
    }

    // JTS writes the parts of a MULTIPOLYGON separated by ", " (Geometry.toText()), and that is the
    // form AooEoo, FieldCreation, GridClassBuilder and the area report pass to calculateArea.
    void "calculateArea sums the polygons of a MULTIPOLYGON written by JTS"() {
        given:
        double single = SpatialUtils.calculateArea('POLYGON((0 0,0 1,1 1,1 0,0 0))')
        String jts = new WKTReader()
                .read('MULTIPOLYGON(((0 0,0 1,1 1,1 0,0 0)),((0 -1,0 0,1 0,1 -1,0 -1)))')
                .toText()

        expect:
        jts.contains(')), ((')

        and:
        Math.abs(SpatialUtils.calculateArea(jts) - 2 * single) / single < 0.001
    }

    // Areas uploaded through /shape/upload/wkt are normalised by JTS first, so the rings of a polygon
    // reach calculateArea separated by "), (" and its parts by ")), ((".
    void "calculateArea separates the rings of a polygon written by JTS"() {
        given:
        double shell = SpatialUtils.calculateArea('POLYGON((0 0,0 2,2 2,2 0,0 0))')
        double hole = SpatialUtils.calculateArea('POLYGON((0.5 0.5,0.5 1.5,1.5 1.5,1.5 0.5,0.5 0.5))')
        String jts = new WKTReader()
                .read('POLYGON((0 0,0 2,2 2,2 0,0 0),(0.5 0.5,1.5 0.5,1.5 1.5,0.5 1.5,0.5 0.5))')
                .toText()

        expect:
        jts.contains('), (')

        and:
        Math.abs(SpatialUtils.calculateArea(jts) - (shell - hole)) / (shell - hole) < 0.001
    }

    @Unroll
    void "calculateArea subtracts a hole drawn #direction the shell"() {
        given:
        double shell = SpatialUtils.calculateArea('POLYGON((0 0,0 2,2 2,2 0,0 0))')
        double hole = SpatialUtils.calculateArea('POLYGON((0.5 0.5,0.5 1.5,1.5 1.5,1.5 0.5,0.5 0.5))')

        expect:
        Math.abs(SpatialUtils.calculateArea(wkt) - (shell - hole)) / (shell - hole) < 0.001

        where:
        direction               | wkt
        // WKT does not mandate a ring orientation, so a hole may run either way
        'the opposite way from' | 'POLYGON((0 0,0 2,2 2,2 0,0 0),(0.5 0.5,1.5 0.5,1.5 1.5,0.5 1.5,0.5 0.5))'
        'the same way as'       | 'POLYGON((0 0,0 2,2 2,2 0,0 0),(0.5 0.5,0.5 1.5,1.5 1.5,1.5 0.5,0.5 0.5))'
    }

    void "calculateArea recognises the whole world"() {
        expect:
        SpatialUtils.calculateArea('POLYGON((-180 -90,-180 90,180 90,180 -90,-180 -90))') == 510000000000000d
    }

    void "cells get smaller towards the poles"() {
        expect:
        SpatialUtils.cellArea(1, 0) > SpatialUtils.cellArea(1, 30)
        SpatialUtils.cellArea(1, 30) > SpatialUtils.cellArea(1, 60)
        Math.abs(SpatialUtils.cellArea(1, 0) - EQUATOR_CELL_SQ_KM) / EQUATOR_CELL_SQ_KM < 0.001
    }

    void "cellArea is symmetric around the equator"() {
        expect:
        SpatialUtils.cellArea(1, 45.5) == SpatialUtils.cellArea(1, -45.5)
    }

    static double areaOfCellAt(double resolution, double latitude) {
        double miny = Math.floor(latitude / resolution) * resolution
        double[][] cell = [[0, miny], [0, miny + resolution], [resolution, miny + resolution], [resolution, miny], [0, miny]] as double[][]
        SpatialUtils.calculateArea(cell)
    }

    void "cellArea with a resolution of 1 matches the area of that cell"() {
        given:
        double expected = areaOfCellAt(1, 40.4)

        expect:
        Math.abs(SpatialUtils.cellArea(1, 40.4) - expected) / expected < 0.001
    }

    @Unroll
    void "cellArea with resolution #resolution at latitude #latitude matches the area of that cell"() {
        given:
        double expected = areaOfCellAt(resolution, latitude)

        expect:
        Math.abs(SpatialUtils.cellArea(resolution, latitude) - expected) / expected < 0.001

        where:
        resolution | latitude
        0.5d       | 60.2d
        0.1d       | 35.05d
        2d         | 10.5d
    }

    static String kml(String body) {
        '<?xml version="1.0" encoding="UTF-8"?>\n<kml xmlns="http://www.opengis.net/kml/2.2">' + body + '</kml>'
    }

    static final String PLACEMARK = '<Placemark><name>a</name><Polygon><outerBoundaryIs><LinearRing><coordinates>' +
            '1,1 1,2 2,2 2,1 1,1</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark>'

    // A KML whose Placemark sits directly inside <kml>, as the ones the hub writes do, came back without geometry.
    @Unroll
    void "getKMLPolygonAsWKT reads a placemark #description"() {
        expect:
        SpatialUtils.getKMLPolygonAsWKT(kml(body)).startsWith('POLYGON')

        where:
        description         | body
        'at the root'       | PLACEMARK
        'inside a document' | '<Document>' + PLACEMARK + '</Document>'
        'inside a folder'   | '<Document><Folder>' + PLACEMARK + '</Folder></Document>'
    }
}
