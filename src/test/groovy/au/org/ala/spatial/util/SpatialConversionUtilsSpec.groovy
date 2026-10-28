package au.org.ala.spatial.util

import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import spock.lang.Unroll

class SpatialConversionUtilsSpec extends Specification {

    void "getGeometryCollectionParts splits top level geometries only"() {
        when:
        def parts = SpatialConversionUtils.getGeometryCollectionParts(
                'GEOMETRYCOLLECTION(POINT(1 2),POLYGON((0 0,1 0,1 1,0 0)),LINESTRING(0 0,1 1))')

        then:
        parts == ['POINT(1 2)', 'POLYGON((0 0,1 0,1 1,0 0))', 'LINESTRING(0 0,1 1)']
    }

    void "getGeometryCollectionParts returns a single part unchanged"() {
        expect:
        SpatialConversionUtils.getGeometryCollectionParts('GEOMETRYCOLLECTION(POINT(1 2))') == ['POINT(1 2)']
    }

    void "getGeometryCollectionParts rejects other geometry types"() {
        when:
        SpatialConversionUtils.getGeometryCollectionParts('POINT(1 2)')

        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    void "isWKTValid(#wkt) == #expected"() {
        expect:
        SpatialConversionUtils.isWKTValid(wkt) == expected

        where:
        wkt                                        | expected
        'POLYGON((0 0,1 0,1 1,0 1,0 0))'           | true
        'POINT(1 2)'                               | true
        'POLYGON((0 0,1 1,1 0,0 1,0 0))'           | false // self-intersecting bowtie
        'POLYGON((0 0,1 0'                         | false // unparseable
        'not wkt'                                  | false
    }

    void "geoJsonToWkt converts a valid polygon"() {
        given:
        def geoJson = '{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}'

        when:
        def geom = new WKTReader().read(SpatialConversionUtils.geoJsonToWkt(geoJson))

        then:
        geom instanceof Polygon
        geom.area == 1d
    }

    void "geoJsonToWkt returns null for an invalid geometry"() {
        expect:
        SpatialConversionUtils.geoJsonToWkt('{"type":"Polygon","coordinates":[[[0,0],[1,1],[1,0],[0,1],[0,0]]]}') == null
    }

    void "createCircleJs builds a closed 361 point ring around the centre"() {
        when:
        def wkt = SpatialConversionUtils.createCircleJs(-3.7, 40.4, 10000)
        def polygon = (Polygon) new WKTReader().read(wkt)
        def coords = polygon.exteriorRing.coordinates

        then:
        wkt.startsWith('POLYGON((')
        coords.length == 361
        coords.first() == coords.last()
        polygon.valid
        Math.abs(polygon.centroid.x - (-3.7)) < 0.001
        Math.abs(polygon.centroid.y - 40.4) < 0.001
        // 10 km radius is about 0.09 degrees of latitude
        Math.abs(polygon.envelopeInternal.maxY - 40.4 - 0.0898) < 0.001
    }

    void "createCircleJs shifts circles crossing -180 into positive longitudes"() {
        when:
        def polygon = (Polygon) new WKTReader().read(SpatialConversionUtils.createCircleJs(-179.99, 0, 10000))

        then:
        polygon.envelopeInternal.minX > 0
        Math.abs(polygon.centroid.x - 180.01) < 0.001
    }

    @TempDir
    Path tmp

    File zipWith(String... names) {
        File zip = tmp.resolve("shape-${names.length}.zip").toFile()
        new ZipOutputStream(new FileOutputStream(zip)).withCloseable { out ->
            names.each { name ->
                out.putNextEntry(new ZipEntry(name))
                out.write('x'.bytes)
                out.closeEntry()
            }
        }
        zip
    }

    // An archive without the .shx and .dbf still answered with the .shp it had found, so the caller went on to
    // read a shapefile that cannot be opened.
    void "extractZippedShapeFile rejects an archive without the .shx and the .dbf"() {
        expect:
        SpatialConversionUtils.extractZippedShapeFile(zipWith('area.shp')) == null
    }

    void "extractZippedShapeFile returns the .shp of a complete archive"() {
        when:
        def result = SpatialConversionUtils.extractZippedShapeFile(zipWith('area.shp', 'area.shx', 'area.dbf'))

        then:
        result != null
        result.right.name == 'area.shp'
    }
}
