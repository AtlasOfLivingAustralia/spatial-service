package au.org.ala.spatial.process

import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.util.AnalysisLayerUtil
import au.org.ala.layers.util.Bil2diva
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils
import org.geotools.data.DataUtilities
import org.geotools.data.DefaultTransaction
import org.geotools.data.FileDataStore
import org.geotools.data.FileDataStoreFinder
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.data.store.ContentFeatureSource
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.FeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType

@Commons
class StandardizeLayers extends SlaveProcess {

    void start() {
        double[] shpResolutions = task.input.shpResolutions as double[]
        double[] grdResolutions = task.input.grdResolutions as double[]

        //optional fieldId
        String fieldId = task.input.fieldId

        task.message = 'running: getting fields'
        List fields = getFields()

        int shpCount = 0
        int grdCount = 0

        fields.each { Map f ->
            try {
                if (f.analysis && (fieldId == null || f.id.equals(fieldId))) {

                    Map l = getLayer(f.spid)


                    if ('c'.equals(f.type) && slaveService.peekFile('/layer/' + l.name + '.shp')[0].exists) {

                        File shpFile = File.createTempFile(f.id.toString(), '')

                        task.message = 'running: getting field ' + f.id
                        boolean hasTxt = fieldToShapeFile(f.id.toString(), shpFile.getPath())

                        shpResolutions.each { res ->
                            String path = '/standard_layer/' + res + '/' + f.id + '.grd'
                            if (true || !slaveService.peekFile(path)[0].exists) {
                                task.message = 'running: making for field ' + f.id + ' and resolution ' + res

                                // standardized file is missing, make for this shapefile
                                if (hasTxt && shp2Analysis(shpFile.getPath(),
                                        grailsApplication.config.data.dir.toString() + '/standard_layer/' + res + '/' + f.id,
                                        new Double(res),
                                        grailsApplication.config.gdal.dir.toString())) {

                                    addOutput('file', '/standard_layer/' + res + '/' + f.id + '.grd')
                                    addOutput('file', '/standard_layer/' + res + '/' + f.id + '.gri')
                                    addOutput('file', '/standard_layer/' + res + '/' + f.id + '.txt')

                                    log.debug 'finished standardizing for field: ' + f.id + ' @ ' + res

                                    shpCount++
                                } else {
                                    log.error 'failed standardizing for field: ' + f.id + ' @ ' + res
                                }
                            }
                        }

                        [shpFile.getPath() + '.shp', shpFile.getPath() + '.shx', shpFile.getPath() + '.prj',
                         shpFile.getPath() + '.dbf', shpFile.getPath() + '.txt', shpFile.getPath() + '.fix',
                         shpFile.getPath()].each {
                            def fd = new File(it)
                            if (fd.exists()) {
                                fd.delete()
                            }
                        }
                    } else if (('e'.equals(f.type) || 'a'.equals(f.type) || 'b'.equals(f.type)) &&
                            slaveService.peekFile('/layer/' + l.name + '.grd')[0].exists) {

                        // standardized file is missing, make for this grid file
                        slaveService.getFile('/layer/' + l.name + '.grd')
                        slaveService.getFile('/layer/' + l.name + '.gri')

                        Grid g = new Grid(grailsApplication.config.data.dir.toString() + '/layer/' + l.name)
                        double minRes = Math.min(g.xres, g.yres)

                        int count = 0
                        double nearestSmallerDRes = -1
                        double nearestSmallerRes = 1
                        grdResolutions.each { Double res ->
                            String path = '/standard_layer/' + res + '/' + f.id + '.grd'
                            if (true || !slaveService.peekFile(path)[0].exists) {
                                // no need to make for this resolution if it is < the actual grid resolution (and not close)
                                double dres = res.doubleValue()
                                if (minRes < dres * 1.2) {
                                    standardizeGrid(f as Map, l as Map, res, dres)
                                    count++
                                    grdCount++
                                } else {
                                    if (nearestSmallerDRes == -1 || nearestSmallerDRes < dres) {
                                        nearestSmallerDRes = dres
                                        nearestSmallerRes = res
                                    }
                                }
                            } else {
                                count++
                            }
                        }
                        //if no standard_layer is produced, use the nearest one found
                        if (count == 0) {
                            standardizeGrid(f as Map, l as Map, nearestSmallerRes, nearestSmallerDRes)
                        }
                    }
                }
            } catch (err) {
                log.error 'error standardizing: ' + f.id, err
            }
        }

        if (grdCount > 0) {
            addOutput("process", "LayerDistancesCreate")
        }
        if (grdCount > 0 || shpCount > 0) {
            addOutput("process", "TabulationCreate")
        }
    }

    void standardizeGrid(Map f, Map l, double res, double dres) {
        if (AnalysisLayerUtil.diva2Analysis(
                String.valueOf(grailsApplication.config.data.dir + '/layer/' + l.name),
                String.valueOf(grailsApplication.config.data.dir + '/standard_layer/' + res + '/' + f.id),
                new Double(dres),
                String.valueOf(grailsApplication.config.gdal.dir),
                false)) {

            addOutput('file', '/standard_layer/' + res + '/' + f.id + '.grd')
            addOutput('file', '/standard_layer/' + res + '/' + f.id + '.gri')

            //copy txt for 'a' and 'b'
            if (slaveService.peekFile('/layer/' + l.name + '.txt')[0].exists) {
                slaveService.getFile('/layer/' + l.name + '.txt')
                FileUtils.copyFile(new File(grailsApplication.config.data.dir.toString() + '/layer/' + l.name + '.txt'),
                        new File(grailsApplication.config.data.dir.toString() + '/standard_layer/' + res + '/' + f.id + '.txt'))
                addOutput('file', '/standard_layer/' + res + '/' + f.id + '.txt')
            }

            log.debug 'finished standardizing for field: ' + f.id + ' @ ' + res
        } else {
            log.error 'failed standardizing for field: ' + f.id + ' @ ' + res
        }

    }

    boolean shp2Analysis(String shp, String dstFilepath, Double resolution, String gdalPath) {
        try {

            (new File(dstFilepath)).getParentFile().mkdirs()

            FileDataStore store = FileDataStoreFinder.getDataStore(new File(shp + ".shp"))
            ReferencedEnvelope re = store.getFeatureSource().getBounds()
            double minx = (re.getMinX() == (double) ((int) (re.getMinX() / resolution.doubleValue())) * resolution.doubleValue()) ?
                    re.getMinX() :
                    (double) ((int) (re.getMinX() / resolution.doubleValue())) * resolution.doubleValue() + resolution.doubleValue()
            double maxx = (re.getMaxX() == (double) ((int) (re.getMaxX() / resolution.doubleValue())) * resolution.doubleValue()) ?
                    re.getMaxX() :
                    (double) ((int) (re.getMaxX() / resolution.doubleValue())) * resolution.doubleValue()
            double miny = (re.getMinY() == (double) ((int) (re.getMinY() / resolution.doubleValue())) * resolution.doubleValue()) ?
                    re.getMinY() :
                    (double) ((int) (re.getMinY() / resolution.doubleValue())) * resolution.doubleValue() + resolution.doubleValue()
            double maxy = (re.getMaxY() == (double) ((int) (re.getMaxY() / resolution.doubleValue())) * resolution.doubleValue()) ?
                    re.getMaxY() :
                    (double) ((int) (re.getMaxY() / resolution.doubleValue())) * resolution.doubleValue();

            if (maxx < minx + 2.0 * resolution.doubleValue()) {
                maxx = minx + 2.0 * resolution.doubleValue()
            }

            if (maxy < miny + 2.0 * resolution.doubleValue()) {
                maxy = miny + 2.0 * resolution.doubleValue()
            }

            File tmpBil = File.createTempFile("tmpbil", "")
            if (!AnalysisLayerUtil.gdal_rasterize(gdalPath, shp + ".shp", tmpBil.getPath() + ".bil",
                    resolution.doubleValue(), minx, miny, maxx, maxy)) {
                return false
            }

            if (!Bil2diva.bil2diva(tmpBil.getPath(), dstFilepath, "")) {
                return false
            }

            FileUtils.copyFile(new File(shp + '.txt'), new File(dstFilepath + '.txt'))

            //delete tmpBil files
            [tmpBil.getPath() + '.bil', tmpBil.getPath() + '.hdr'].each {
                def f = new File(it)
                if (f.exists()) {
                    f.delete()
                }
            }

            return true
        } catch (err) {
            log.error 'failed to rasterize ' + shp + '.shp', err
        }

        return false
    }


    boolean fieldToShapeFile(String fid, String path) {
        boolean ret = true

        try {
            SimpleFeatureType e = DataUtilities.createType("tmpshp", "the_geom:MultiPolygon,id:int")
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory()
            Map params = [:]
            params.put("url", (new File(path + ".shp")).toURI().toURL())
            params.put("create spatial index", Boolean.FALSE)
            ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params)
            newDataStore.createSchema(e)
            newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84)
            DefaultTransaction transaction = new DefaultTransaction("create")
            String typeName = newDataStore.getTypeNames()[0]
            ContentFeatureSource featureSource = newDataStore.getFeatureSource(typeName)
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource
            featureStore.setTransaction(transaction)
            ArrayList features = new ArrayList()
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(e)
            FileWriter fw = null

            try {
                fw = new FileWriter(path + ".txt")
                int e1 = 1

                List objects = getObjects(fid)

                WKTReader r = new WKTReader()
                objects.each {
                    Geometry geom = r.read(getWkt(it.pid))
                    featureBuilder.add(geom)
                    featureBuilder.add(Integer.valueOf(e1))
                    SimpleFeature f = featureBuilder.buildFeature(String.valueOf(e1))
                    features.add(f)
                    if (e1 > 1) {
                        fw.write("\n")
                    }

                    fw.write(e1 + "=" + it.id)
                    e1++
                }

                FeatureCollection c = new DefaultFeatureCollection()
                c.addAll(features)
                featureStore.setTransaction(transaction)

                try {
                    featureStore.addFeatures(c)
                    transaction.commit()
                } catch (err) {
                    transaction.rollback()
                } finally {
                    transaction.close()
                }
            } catch (err) {
                log.error 'failed building txt file; shapefile.id=layerdb.objects.id', err
                ret = false
            } finally {
                if (fw != null) {
                    try {
                        fw.close()
                    } catch (err) {
                        ret = false
                        log.error 'failed closing txt file', err
                    }
                }

            }
        } catch (err) {
            ret = false
            log.error 'failed building txt file; shapefile.id=layerdb.objects.id', err
        }

        ret
    }
}
