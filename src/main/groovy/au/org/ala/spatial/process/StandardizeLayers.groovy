package au.org.ala.spatial.process

import au.org.ala.spatial.Fields
import au.org.ala.spatial.Layers
import au.org.ala.spatial.SpatialObjects
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.util.AnalysisLayerUtil
import au.org.ala.spatial.grid.Bil2diva
import groovy.util.logging.Slf4j
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
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType

@Slf4j
//@CompileStatic
class StandardizeLayers extends SlaveProcess {

    void start() {
        double[] shpResolutions = getInput('shpResolutions') as double[]
        double[] grdResolutions = getInput('grdResolutions') as double[]

        //optional fieldId
        String fieldId = getInput('fieldId')

        taskWrapper.task.message = 'running: getting fields'
        List<Fields> fields = getFields()

        int shpCount = 0
        int grdCount = 0

        fields.each { Fields f ->
            try {
                if (f.analysis && (fieldId == null || f.id == fieldId)) {

                    Layers l = getLayer(f.spid)


                    if ('c' == f.type && new File('/layer/' + l.name + '.shp').exists()) {

                        boolean shpFileRetrieved = false
                        boolean hasTxt = false
                        File shpFile

                        shpResolutions.each { String res ->
                            String path = '/standard_layer/' + res + '/' + f.id + '.grd'
                            if (!new File(path).exists()) {
                                taskWrapper.task.message = 'running: making for field ' + f.id + ' and resolution ' + res

                                if (!shpFileRetrieved) {
                                    shpFile = File.createTempFile(f.id.toString(), '')

                                    taskWrapper.task.message = 'running: getting field ' + f.id
                                    hasTxt = fieldToShapeFile(f.id.toString(), shpFile.getPath())
                                    shpFileRetrieved = true
                                }

                                // standardized file is missing, make for this shapefile
                                if (hasTxt && shp2Analysis(shpFile.getPath(),
                                        spatialConfig.data.dir.toString() + '/standard_layer/' + res + '/' + f.id,
                                        new Double(res),
                                        spatialConfig.gdal.dir.toString())) {

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
                    } else if (('e' == f.type || 'a' == f.type || 'b' == f.type) &&
                            new File('/layer/' + l.name + '.grd').exists()) {

                        // standardized file is missing, make for this grid file
                        getFile('/layer/' + l.name + '.grd')
                        getFile('/layer/' + l.name + '.gri')

                        Grid g = new Grid(spatialConfig.data.dir.toString() + '/layer/' + l.name)
                        double minRes = Math.min(g.xres, g.yres)

                        int count = 0
                        double nearestSmallerDRes = -1
                        double nearestSmallerRes = 1
                        grdResolutions.each { Double res ->
                            String path = '/standard_layer/' + res + '/' + f.id + '.grd'
                            if (!new File(path).exists()) {
                                taskWrapper.task.message = 'running: making for field ' + f.id + ' and resolution ' + res

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
                String.valueOf(spatialConfig.data.dir + '/layer/' + l.name),
                String.valueOf(spatialConfig.data.dir + '/standard_layer/' + res + '/' + f.id),
                new Double(dres),
                String.valueOf(spatialConfig.gdal.dir),
                false)) {

            addOutput('file', '/standard_layer/' + res + '/' + f.id + '.grd')
            addOutput('file', '/standard_layer/' + res + '/' + f.id + '.gri')

            //copy txt for 'a' and 'b'
            if (new File('/layer/' + l.name + '.txt').exists()) {
                getFile('/layer/' + l.name + '.txt')
                FileUtils.copyFile(new File(spatialConfig.data.dir.toString() + '/layer/' + l.name + '.txt'),
                        new File(spatialConfig.data.dir.toString() + '/standard_layer/' + res + '/' + f.id + '.txt'))
                addOutput('file', '/standard_layer/' + res + '/' + f.id + '.txt')
            }

            log.debug 'finished standardizing for field: ' + f.id + ' @ ' + res
        } else {
            log.error 'failed standardizing for field: ' + f.id + ' @ ' + res
        }

    }

    static boolean shp2Analysis(String shp, String dstFilepath, Double resolution, String gdalPath) {
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
                    (double) ((int) (re.getMaxY() / resolution.doubleValue())) * resolution.doubleValue()

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

                List<SpatialObjects> objects = getObjects(fid)

                WKTReader r = new WKTReader()
                objects.each { SpatialObjects it ->
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
                } catch (ignored) {
                    transaction.rollback()
                } finally {
                    transaction.close()
                }
            } catch (err) {
                log.error 'failed building txt file; shapefile.id=layerdb.objects.id, fid:' + fid + ", path:" + path, err
                ret = false
            } finally {
                if (fw != null) {
                    try {
                        fw.flush()
                        fw.close()
                    } catch (err) {
                        ret = false
                        log.error 'failed closing txt file', err
                    }
                }

            }
        } catch (err) {
            ret = false
            log.error 'failed building txt file; shapefile.id=layerdb.objects.id, fid:' + fid + ", path:" + path, err
        }

        ret
    }
}
