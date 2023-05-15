package au.org.ala.spatial.tabulation

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.geotools.data.*
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureSource
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.FeatureIterator
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.feature.simple.SimpleFeatureTypeBuilder
import org.geotools.geometry.jts.GeometryBuilder
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.geometry.BoundingBox
import org.springframework.util.StreamUtils

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Created by a on 3/02/15.
 */
import groovy.transform.CompileStatic
@Slf4j
//@CompileStatic
class Intersection {

    private static final LinkedBlockingQueue<String> lbqWriter = new LinkedBlockingQueue<String>()

    static void intersectionZipToShapefile(File intersectionZip, File shapeFile) {
        ZipInputStream zis = null
        ShapefileDataStore newDataStore = null
        try {
            zis = new ZipInputStream(new FileInputStream(intersectionZip))
            ZipEntry ze = zis.getNextEntry()

            InputStreamReader isr = new InputStreamReader(zis)
            BufferedReader br = new BufferedReader(isr)

            final SimpleFeatureType type = createFeatureType()

            List<SimpleFeature> features = new ArrayList<SimpleFeature>()
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type)

            String id1
            String id2
            String wkt
            while ((id1 = br.readLine()) != null) {
                id2 = br.readLine()
                wkt = br.readLine()

                WKTReader wktReader = new WKTReader()
                Geometry geom = wktReader.read(wkt)

                if (geom instanceof Polygon) {
                    geom = new GeometryBuilder().multiPolygon((Polygon) geom)
                }

                featureBuilder.add(geom)

                SimpleFeature feature = featureBuilder.buildFeature(null)
                feature.setAttribute("id1", id1)
                feature.setAttribute("id2", id2)
                feature.setDefaultGeometry(geom)
                features.add(feature)
            }

            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory()

            Map<String, Serializable> params = new HashMap<String, Serializable>()
            params.put("url", shapeFile.toURI().toURL())
            params.put("create spatial index", Boolean.TRUE)

            newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params)
            newDataStore.createSchema(type)

            newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84)

            Transaction transaction = new DefaultTransaction("create")

            String typeName = newDataStore.getTypeNames()[0]
            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName)

            if (featureSource instanceof SimpleFeatureStore) {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource

                DefaultFeatureCollection collection = new DefaultFeatureCollection()
                collection.addAll(features)
                featureStore.setTransaction(transaction)
                try {
                    featureStore.addFeatures(collection)
                    transaction.commit()

                } catch (Exception ignored) {
                    transaction.rollback()

                } finally {
                    transaction.close()
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (zis != null) {
                zis.close()
            }
            if (newDataStore != null) {
                newDataStore.dispose()
            }
        }
    }

    private static SimpleFeatureType createFeatureType() {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder()
        builder.setName("ActiveArea")
        builder.setCRS(DefaultGeographicCRS.WGS84)

        builder.add("id1", String.class)
        builder.add("id2", String.class)
        builder.add("the_geom", MultiPolygon.class)

        // build the type
        return builder.buildFeatureType()
    }

    /**
     * intersects filename1 with all files in List filenames2 to produce an intersection_filename1_* file for
     * each file in filename2.
     */
    static void intersectShapefilesInDir(String shapefileDir, List intersectionFileNames, String outputDir) {
        File f = new File("/data/ala/data/layers/ready/shape/")
        File[] list = f.listFiles()
        Arrays.sort(list, new Comparator<File>() {

            @Override
            int compare(File o1, File o2) {
                return (int) (o1.length() - o2.length())
            }
        })
        for (int i = 0; i < list.length; i++) {
            if (list[i].getPath().endsWith(".shp")) {
                List files = new ArrayList()
                for (int j = i + 1; j < list.length; j++) {
                    if (list[j].getPath().endsWith(".shp")) {
                        File out = new File(outputDir + "/intersection_" + list[i].getName() + "_" + list[j].getName() + ".txt")
                        File outZip = new File(outputDir + "/intersection_" + list[i].getName() + "_" + list[j].getName() + ".zip")
                        File out2 = new File(outputDir + "/intersection_" + list[j].getName() + "_" + list[i].getName() + ".txt")
                        File outZip2 = new File(outputDir + "/intersection_" + list[j].getName() + "_" + list[i].getName() + ".zip")
                        if (!out.exists() && !outZip.exists() &&
                                !out2.exists() && !outZip2.exists()) {
                            files.add(list[j].getPath())
                        }
                    }
                }
                intersectShapefiles(list[i].getPath(), files, outputDir)
            }
        }
    }

    /**
     * intersects filename1 with all files in List filenames2 to produce an intersection_filename1_* file for
     * each file in filename2.
     *
     * @param filename1
     * @param filenames2
     */
    static void intersectShapefiles(String filename1, List filenames2, String outputDir) {
        log.debug("intersectShapefiles START")
        DataStore dataStore1 = null
        DataStore dataStore2 = null
        FeatureIterator iterator1 = null
        FeatureIterator iterator2 = null
        try {

            final LinkedBlockingQueue lbq = new LinkedBlockingQueue()
            final AtomicInteger ai = new AtomicInteger(0)

            FeatureSource source1
            FeatureSource source2

            Map map1 = new HashMap()
            map1.put("url", new File(filename1).toURI().toURL())
            dataStore1 = DataStoreFinder.getDataStore(map1)
            String typeName1 = dataStore1.getTypeNames()[0]
            source1 = dataStore1.getFeatureSource(typeName1)

            iterator1 = source1.getFeatures().features()

            log.debug(filename1 + ": shape count=" + source1.getCount(Query.ALL))

            int count1 = 0

            List geoms1 = new ArrayList()
            List ids1 = new ArrayList()
            while (iterator1.hasNext()) {
                count1++

                SimpleFeature feature1 = (SimpleFeature) iterator1.next()
                Geometry geom1 = (Geometry) feature1.getDefaultGeometry()
                BoundingBox bb = feature1.getBounds()

                //if geom1 is large, split it and iterate
                int len = geom1.toText().length()

                Geometry[] glist = [ geom1 ]
                if (len > 1000000) {
                    glist = split(geom1, 4, 4)
                }
                for (int n = 0; n < glist.length; n++) {
                    try {
                        if (glist[n].getArea() > 0) {
                            geoms1.add(glist[n])
                            ids1.add(feature1.getID())
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    }
                }
            }

            for (int h = 0; h < filenames2.size(); h++) {
                long startTime = System.currentTimeMillis()

                File outf = new File(outputDir + "/intersection_" + new File(filename1).getName() + "_" + new File((String) filenames2.get(h)).getName() + ".txt")
                File outZip = new File(outputDir + "/intersection_" + new File(filename1).getName() + "_" + new File((String) filenames2.get(h)).getName() + ".zip")

                try {
                    Map map2 = new HashMap()
                    map2.put("url", new File((String) filenames2.get(h)).toURI().toURL())
                    dataStore2 = DataStoreFinder.getDataStore(map2)
                    String typeName2 = dataStore2.getTypeNames()[0]
                    source2 = dataStore2.getFeatureSource(typeName2)
                    log.debug((String) filenames2.get(h) + ": shape count=" + source2.getCount(Query.ALL))

                    //fetch all feature 2 bounding boxes
                    iterator2 = source2.getFeatures().features()

                    double[] bb2 = new double[4 * source2.getCount(Query.ALL)]
                    String[] id2 = new String[source2.getCount(Query.ALL)]
                    Geometry[] g2 = new Geometry[source2.getCount(Query.ALL)]

                    int i = 0
                    while (iterator2.hasNext()) {
                        try {
                            SimpleFeature feature2 = (SimpleFeature) iterator2.next()
                            BoundingBox b = feature2.getBounds()
                            bb2[i] = b.getMinX()
                            bb2[i + 1] = b.getMinY()
                            bb2[i + 2] = b.getMaxX()
                            bb2[i + 3] = b.getMaxY()

                            id2[(int)(i / 4)] = feature2.getID()
                            g2[(int)(i / 4)] = (Geometry) feature2.getDefaultGeometry()

                            i += 4
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }


                    log.debug("bounding box compare")
                    //compare to all in (2)
                    for (int n = 0; n < geoms1.size(); n++) {
                        Geometry geom1 = (Geometry) geoms1.get(n)
                        Envelope e = geom1.getEnvelopeInternal()

                        for (int j = 0; j < g2.length; j++) {
                            int jj = j * 4

                            if (bb2[jj] <= e.getMaxX() && bb2[jj + 2] >= e.getMinX() &&
                                    bb2[jj + 1] <= e.getMaxY() && bb2[jj + 3] >= e.getMinY()) {

                                Object[] newtask = new Object[7]
                                newtask[0] = geom1
                                newtask[1] = g2[j]
                                newtask[2] = null
                                newtask[3] = ids1.get(n)
                                newtask[4] = id2[j]

                                lbq.put(newtask)
                            }
                        }
                    }

                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                } finally {
                    if (iterator2 != null) iterator2.close()
                    if (dataStore2 != null) dataStore2.dispose()
                }
                log.debug("comparisons required: " + lbq.size())

                //wait until finished
                final CountDownLatch cdl = new CountDownLatch(lbq.size())
                final LinkedBlockingQueue<BufferedWriter> outputWriterPool = new LinkedBlockingQueue<BufferedWriter>()
                List<BufferedWriter> outputWriters = new ArrayList<BufferedWriter>()
                final Thread[] threads = new Thread[8]
                for (int j = 0; j < threads.length; j++) {
                    outputWriters.add(new BufferedWriter(new FileWriter(new File(outf.getPath() + "." + j))))
                    outputWriterPool.put(outputWriters.get(j))

                    threads[j] = new Thread() {
                        @Override
                        void run() {
                            try {
                                BufferedWriter bw = outputWriterPool.take()
                                while (true) {
                                    Object[] os = (Object[]) lbq.take()
                                    if (lbq.size() % 5000 == 0) {
                                        System.out.print("(" + lbq.size() + ")")
                                    }

                                    Geometry geom1 = (Geometry) ((Geometry) os[0]).copy()
                                    Geometry g2 = (Geometry) ((Geometry) os[1]).copy()

                                    String id1 = (String) os[3]
                                    String id2 = (String) os[4]

                                    try {
                                        if (geom1.intersects(g2)) {
                                            Geometry intersection = geom1.intersection(g2)
                                            if (intersection.getArea() > 0) {
                                                writeIntersection(bw, id1, id2, intersection)
                                                ai.incrementAndGet()
                                            }
                                        }
                                    } catch (Exception e) {
                                        writeIntersection(bw, id1, id2 + ", error: " + e.getMessage(), null)
                                    }
                                    cdl.countDown()
                                }
                            } catch (InterruptedException ignored) {

                            } catch (Exception e) {
                                log.error(e.getMessage(), e)
                                cdl.countDown()
                            }
                        }
                    }

                    threads[j].start()
                }

                cdl.await()

                BufferedOutputStream bos = null

                try {
                    bos = new BufferedOutputStream(new FileOutputStream(outf))

                    for (int j = 0; j < threads.length; j++) {
                        threads[j].interrupt()

                        try {
                            outputWriters.get(j).flush()
                            outputWriters.get(j).close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }

                        File f = new File(outf.getPath() + "." + j)
                        BufferedInputStream bis = null
                        try {
                            bis = new BufferedInputStream(new FileInputStream(f))
                            byte[] bytes = new byte[1024]
                            int len = 0
                            while ((len = bis.read(bytes)) > 0) {
                                bos.write(bytes, 0, len)
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        } finally {
                            if (bis != null) {
                                try {
                                    bis.close()
                                } catch (Exception e) {
                                    log.error(e.getMessage(), e)
                                }
                            }
                        }
                        f.delete()
                    }

                    bos.flush()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                } finally {
                    if (bos != null) {
                        try {
                            bos.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                }

                log.debug("total time: " + outf.getName() + " = " + (System.currentTimeMillis() - startTime) + "ms")
                //zip
                ZipOutputStream zos = null
                BufferedInputStream bis = null
                try {
                    zos = new ZipOutputStream(new FileOutputStream(outZip))
                    bis = new BufferedInputStream(new FileInputStream(outf))
                    zos.putNextEntry(new ZipEntry(outf.getName()))
                    StreamUtils.copy(bis, zos)
                    zos.flush()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                } finally {
                    if (zos != null) {
                        try {
                            zos.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                    if (bis != null) {
                        try {
                            bis.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                    FileUtils.deleteQuietly(outf)
                }
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (iterator1 != null) iterator1.close()
            if (dataStore1 != null) dataStore1.dispose()

        }
    }

    static void writeIntersection(BufferedWriter bw, String id1, String id2, Geometry g) throws Exception {
        if (g != null) {
            bw.write(id1 + "\n" + id2 + "\n" + g.toText() + "\n")
        } else {
            bw.write(id1 + "\n" + id2 + "\n\n")
        }
    }

    static Geometry[] split(Geometry geom, int xsize, int ysize) {
        Envelope bb = geom.getEnvelopeInternal()

        //split into pieces
        double[] xs = new double[xsize + 1]
        xs[0] = bb.getMinX()
        for (int i = 1; i < xsize; i++) {
            xs[i] = bb.getMinX() + (bb.getMaxX() - bb.getMinX()) * i / (double) xsize
        }
        xs[xsize] = bb.getMaxX()

        double[] ys = new double[ysize + 1]
        ys[0] = bb.getMinY()
        for (int i = 1; i < ysize; i++) {
            ys[i] = bb.getMinY() + (bb.getMaxY() - bb.getMinY()) * i / (double) ysize
        }
        ys[ysize] = bb.getMaxY()

        Geometry[] glist = new Geometry[xsize * ysize]
        int gi = 0
        for (int x = 0; x < xsize; x++) {
            for (int y = 0; y < ysize; y++) {
                GeometryBuilder gb = new GeometryBuilder()
                Geometry g = gb.box(xs[x], ys[y], xs[x + 1], ys[y + 1])

                glist[gi] = g.intersection(geom)
                gi++
            }
        }

        return glist
    }
}
