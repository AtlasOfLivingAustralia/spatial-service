package au.org.ala.spatial

import org.geotools.data.DataUtilities
import org.geotools.data.DefaultTransaction
import org.geotools.data.collection.ListFeatureCollection
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.locationtech.jts.io.WKTReader
import org.opengis.feature.simple.SimpleFeatureType
import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipInputStream

/**
 * Downloads of environmental envelopes: /shape/{wkt|kml|geojson|shp}/ENVELOPE{taskId}
 */
class SpatialObjectsServiceEnvelopeSpec extends Specification {

    @TempDir
    File dataDir

    SpatialObjectsService service

    def setup() {
        service = new SpatialObjectsService()
        service.spatialConfig = new SpatialConfig(data: new SpatialConfig.DotDir(dir: dataDir.path))
    }

    /** Writes public/{taskId}/envelope.shp (+ shx, dbf, fix) as the Envelope process does. */
    private File writeEnvelope(int taskId, String wkt) {
        File dir = new File(dataDir, "public/$taskId")
        dir.mkdirs()
        File shp = new File(dir, 'envelope.shp')

        SimpleFeatureType type = DataUtilities.createType('envelope', 'the_geom:MultiPolygon:srid=4326')
        ShapefileDataStore store = new ShapefileDataStoreFactory().createNewDataStore([url: shp.toURI().toURL()]) as ShapefileDataStore
        store.createSchema(type)
        def feature = SimpleFeatureBuilder.build(type, [new WKTReader().read(wkt)] as Object[], null)
        def tx = new DefaultTransaction('create')
        SimpleFeatureStore fs = store.getFeatureSource(store.typeNames[0]) as SimpleFeatureStore
        fs.transaction = tx
        fs.addFeatures(new ListFeatureCollection(type, [feature]))
        tx.commit()
        tx.close()
        store.dispose()
        shp
    }

    private static List<String> zipEntryNames(byte[] bytes) {
        List<String> names = []
        new ZipInputStream(new ByteArrayInputStream(bytes)).withCloseable { zis ->
            def e
            while ((e = zis.nextEntry) != null) names << e.name
        }
        names
    }

    def "an envelope can be downloaded as WKT"() {
        given:
        writeEnvelope(123, 'MULTIPOLYGON(((1 1,1 2,2 2,2 1,1 1)))')
        def os = new ByteArrayOutputStream()

        when:
        service.wkt('ENVELOPE123', os)

        then:
        new WKTReader().read(os.toString()).equalsTopo(new WKTReader().read('MULTIPOLYGON(((1 1,1 2,2 2,2 1,1 1)))'))
    }

    def "an envelope task id must be a number"() {
        when:
        service.wkt('ENVELOPE../../etc', new ByteArrayOutputStream())

        then:
        thrown(NumberFormatException)
    }

    def "an envelope can be downloaded as a zipped shapefile"() {
        given:
        writeEnvelope(124, 'MULTIPOLYGON(((1 1,1 2,2 2,2 1,1 1)))')
        File fix = new File(dataDir, 'public/124/envelope.fix') // feature id index written by the Envelope process
        if (!fix.exists()) fix.bytes = [0] as byte[]
        def os = new ByteArrayOutputStream()

        when:
        service.streamEnvelope(os, '124', 'shp')
        List<String> names = zipEntryNames(os.toByteArray())

        then:
        names.sort() == ['envelope.dbf', 'envelope.fix', 'envelope.shp', 'envelope.shx']
    }
}
