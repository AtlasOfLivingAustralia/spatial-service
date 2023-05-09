/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.util

import au.org.ala.spatial.Util
import au.org.ala.spatial.grid.Grid2Shape
import au.org.ala.spatial.intersect.Grid
import com.opencsv.CSVReader

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.geotools.data.DefaultTransaction
import org.geotools.data.FileDataStore
import org.geotools.data.FileDataStoreFinder
import org.geotools.data.Transaction
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.data.simple.SimpleFeatureIterator
import org.geotools.data.simple.SimpleFeatureSource
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.FeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.feature.simple.SimpleFeatureTypeBuilder
import org.geotools.geometry.jts.JTS
import org.geotools.geometry.jts.JTSFactoryFinder
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.kml.v22.KMLConfiguration
import org.geotools.map.FeatureLayer
import org.geotools.map.MapContent
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.renderer.GTRenderer
import org.geotools.renderer.lite.StreamingRenderer
import org.geotools.styling.SLD
import org.geotools.styling.Style
import org.geotools.xsd.Parser
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.referencing.crs.CoordinateReferenceSystem

import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage

@CompileStatic
@Slf4j
class SpatialUtils {

    static int map_zoom = 21
    static int map_offset = 268435456 // half the Earth's circumference at zoom level 21
    static double map_radius = map_offset / Math.PI
    static double meters_per_pixel = 78271.5170 //at zoom level 1
    static int current_zoom = 0

    static int convertLngToPixel(double lng) {
        return (int) Math.round(map_offset + map_radius * lng * Math.PI / 180)
    }

    static double convertPixelToLng(int px) {
        return (px - map_offset) / map_radius * 180 / Math.PI
    }

    static int convertLatToPixel(double lat) {
        return (int) Math.round(map_offset - map_radius * Math.log((1 + Math.sin(lat * Math.PI / 180)) / (1 - Math.sin(lat * Math.PI / 180))) / 2)
    }

    static double convertPixelToLat(int px) {
        return Math.asin((Math.pow(Math.E, ((map_offset - px) / map_radius * 2) as double) - 1) / (1 + Math.pow(Math.E, ((map_offset - px) / map_radius * 2) as double))) * 180 / Math.PI
    }

    static double convertMetersToPixels(double meters, double latitude, int zoom) {
        return meters / ((Math.cos(latitude * Math.PI / 180.0) * 2 * Math.PI * 6378137) / (256 * Math.pow(2, zoom)))
    }

    static double convertPixelsToMeters(int pixels, double latitude, int zoom) {
        return ((Math.cos(latitude * Math.PI / 180.0) * 2 * Math.PI * 6378137) / (256 * Math.pow(2, zoom))) * pixels
    }

    static double convertMetersToLng(double meters) {
        return meters / 20037508.342789244 * 180
    }

    static double convertMetersToLat(double meters) {
        return 180.0 / Math.PI * (2 * Math.atan(Math.exp(meters / 20037508.342789244 * Math.PI)) - Math.PI / 2.0)
    }

    static int planeDistance(double lat1, double lng1, double lat2, double lng2, int zoom) {
        // Given a pair of lat/long coordinates and a map zoom level, returns
        // the distance between the two points in pixels

        int x1 = convertLngToPixel(lng1)
        int y1 = convertLatToPixel(lat1)

        int x2 = convertLngToPixel(lng2)
        int y2 = convertLatToPixel(lat2)

        int distance = (int) Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2))

        return distance >> (map_zoom - zoom)
    }

    static String convertGeoToPoints(String geometry) {
        if (geometry == null) {
            return ""
        }
        geometry = geometry.replace(" ", ':')
        geometry = geometry.replace("MULTIPOLYGON(((", "")
        geometry = geometry.replace("POLYGON((", "")
        while (geometry.contains(")")) {
            geometry = geometry.replace(")", "")
        }

        //for case of more than one polygon
        while (geometry.contains(",((")) {
            geometry = geometry.replace(",((", "S")
        }
        while (geometry.contains(",(")) {
            geometry = geometry.replace(",(", "S")
        }
        return geometry
    }

    static double convertLngToMeters(double lng) {
        return lng * 20037508.342789244 / 180
    }

    static double convertLatToMeters(double lat) {
        return Math.log(Math.tan(((lat / 180.0 * Math.PI) + Math.PI / 2.0) / 2.0)) * 20037508.342789244 / Math.PI
    }


    static void grid2shp(String grdPath, List shpValues) {
        File shpFile = new File(grdPath + '.shp')
        Grid grid = new Grid(grdPath)
        if (grid != null) {
            //TODO: review 2GB limit

            //get list of unique grid values
            def data = grid.getGrid()
            def values = [] as Set
            if (shpValues) {
                values.addAll(shpValues)
            } else {
                data.each { values.add(it) }
            }

            def means = new File(shpFile.getParent() + File.separator + "classification_means.csv")
            List<String[]> classificationMeans = means.exists() ? new CSVReader(new FileReader(means)).readAll() : [[]] as List<String[]>

            try {
                final SimpleFeatureType type = createFeatureType(means.exists() ? classificationMeans.get(0) : null)

                List<SimpleFeature> features = new ArrayList<SimpleFeature>()
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type)

                WKTReader wkt = new WKTReader()

                values.each { value ->
                    String w = Grid2Shape.grid2Wkt(data, value as double, value as double, grid.nrows, grid.ncols, grid.xmin, grid.ymin, grid.xres, grid.yres)
                    if (!w.contains("()")) {
                        Geometry geom = wkt.read(w)
                        featureBuilder.add(geom)
                        SimpleFeature feature = featureBuilder.buildFeature(null)
                        feature.setAttribute("group", String.valueOf((int) value))

                        if (means.exists()) {
                            classificationMeans.each { cm ->
                                if (cm[0] == String.valueOf((int) value)) {
                                    for (int i = 1; i < cm.length; i++) {
                                        try {
                                            feature.setAttribute(classificationMeans.get(0)[i], Double.parseDouble(cm[i]))
                                        } catch (ignored) {
                                            //failures to parse cm[i] as double are valid (missing values)
                                        }
                                    }
                                }
                            }
                        }

                        features.add(feature)
                    }
                }

                ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory()

                Map<String, Serializable> params = new HashMap<String, Serializable>()
                params.put("url", shpFile.toURI().toURL())
                params.put("create spatial index", Boolean.TRUE)

                ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params)
                newDataStore.createSchema(type)

                newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84)

                Transaction transaction = new DefaultTransaction("create")

                String typeName = newDataStore.getTypeNames()[0]
                SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName)

                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource

                DefaultFeatureCollection collection = new DefaultFeatureCollection()
                collection.addAll(features)
                featureStore.setTransaction(transaction)
                try {
                    featureStore.addFeatures(collection)
                    transaction.commit()
                } catch (Exception problem) {
                    problem.printStackTrace()
                    transaction.rollback()

                } finally {
                    transaction.close()
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
    }

    static SimpleFeatureType createFeatureType(String[] additionalColumns) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder()
        builder.setName("aloc")
        builder.setCRS(DefaultGeographicCRS.WGS84)

        builder.add("the_geom", MultiPolygon.class)
        builder.add("group", String.class)

        for (int i = 1; additionalColumns != null && i < additionalColumns.length; i++) {
            builder.add(additionalColumns[i], Double.class)
        }

        // build the type
        return builder.buildFeatureType()
    }

    static def toGeotiff(String gdalPath, String inputFile) throws Exception {
        String outputFile = inputFile.substring(0, inputFile.lastIndexOf('.')) + ".tif"

        String[] cmd = [gdalPath + '/gdal_translate',
                        "-of", "GTiff",
                        "-co", "COMPRESS=DEFLATE",
                        "-co", "TILED=YES",
                        "-co", "BIGTIFF=IF_SAFER", inputFile, outputFile]
        Util.runCmd(cmd, 36000000)

        cmd = [gdalPath + '/gdaladdo', "-r", "cubic", outputFile, "2", "4", "8", "16", "32", "64"]

        Util.runCmd(cmd, 36000000) // 10hr timeout
    }

    static def save4326prj(String path) {

        StringBuffer sbProjection = new StringBuffer()
        sbProjection.append("GEOGCS[\"WGS 84\", ").append("\n")
        sbProjection.append("    DATUM[\"WGS_1984\", ").append("\n")
        sbProjection.append("        SPHEROID[\"WGS 84\",6378137,298.257223563, ").append("\n")
        sbProjection.append("            AUTHORITY[\"EPSG\",\"7030\"]], ").append("\n")
        sbProjection.append("        AUTHORITY[\"EPSG\",\"6326\"]], ").append("\n")
        sbProjection.append("    PRIMEM[\"Greenwich\",0, ").append("\n")
        sbProjection.append("        AUTHORITY[\"EPSG\",\"8901\"]], ").append("\n")
        sbProjection.append("    UNIT[\"degree\",0.01745329251994328, ").append("\n")
        sbProjection.append("        AUTHORITY[\"EPSG\",\"9122\"]], ").append("\n")
        sbProjection.append("    AUTHORITY[\"EPSG\",\"4326\"]] ").append("\n")

        new File(path).write(sbProjection.toString())
    }

    static def divaToAsc(String path) throws FileNotFoundException, Exception {
        def asc = path + ".asc"
        def grid = new Grid(path)

        BufferedWriter fw
        try {
            fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(asc), "US-ASCII"))
            fw.append("ncols ").append(String.valueOf(grid.ncols)).append("\n")
            fw.append("nrows ").append(String.valueOf(grid.nrows)).append("\n")
            fw.append("xllcorner ").append(String.valueOf(grid.xmin)).append("\n")
            fw.append("yllcorner ").append(String.valueOf(grid.ymin)).append("\n")
            fw.append("cellsize ").append(String.valueOf(grid.xres)).append("\n")

            fw.append("NODATA_value ").append(String.valueOf(-1))

            float[] grid_data = grid.getGrid()

            for (int i = 0; i < grid.nrows; i++) {
                fw.append("\n")
                for (int j = 0; j < grid.ncols; j++) {
                    if (j > 0) {
                        fw.append(" ")
                    }
                    if (Float.isNaN(grid_data[i * grid.ncols + j])) {
                        fw.append("-1")
                    } else {
                        fw.append(String.valueOf(grid_data[i * grid.ncols + j]))
                    }
                }
            }

            fw.append("\n")
            fw.close()

            //projection file
            save4326prj(path + ".prj")
        } catch (Exception e) {
            throw e
        }
    }

    /**
     * parse a KML containing a single placemark, or a placemark in a folder, into WKT.
     *
     * @param kmldata
     * @return WKT if valid, null on error, empty string "" when placemark not matched.
     */
    static String getKMLPolygonAsWKT(String kmldata) throws Exception {
        Parser parser = new Parser(new KMLConfiguration())
        SimpleFeature f = (SimpleFeature) parser.parse(new StringReader(kmldata))
        Collection placemarks = (Collection) f.getAttribute("Feature")

        Geometry g = null
        SimpleFeature sf = null

        //for <Placemark>
        if (!placemarks.isEmpty() && !placemarks.isEmpty()) {
            sf = (SimpleFeature) placemarks.iterator().next()
            g = (Geometry) sf.getAttribute("Geometry")
        }

        //for <Folder><Placemark>
        if (g == null && sf != null) {
            placemarks = (Collection) sf.getAttribute("Feature")
            if (placemarks != null && !placemarks.isEmpty()) {
                g = (Geometry) ((SimpleFeature) placemarks.iterator().next()).getAttribute("Geometry")
            } else {
                placemarks = (Collection) sf.getAttribute("Folder")
                if (placemarks != null && !placemarks.isEmpty()) {
                    g = (Geometry) ((SimpleFeature) placemarks.iterator().next()).getAttribute("Geometry")
                }
            }
        }

        if (g != null) {
            WKTWriter wr = new WKTWriter()
            String wkt = wr.write(g)
            return wkt.replace(" (", "(").replace(", ", ",").replace(") ", ")")
        } else {
            return ""
        }
    }

    static BufferedImage renderCollection(FeatureCollection collection, int width, int height) {
        MapContent map = new MapContent()
        BufferedImage image = null

        try {
            Style style = SLD.createPolygonStyle(Color.BLACK, Color.GRAY, 0.5f)
            map.addLayer(new FeatureLayer(collection, style))

            GTRenderer renderer = new StreamingRenderer()
            renderer.setMapContent(map)

            Rectangle imageBounds
            ReferencedEnvelope mapBounds
            mapBounds = map.getMaxBounds()
            double heightToWidth = mapBounds.getSpan(1) / mapBounds.getSpan(0)
            if (heightToWidth * width > height) {
                imageBounds = new Rectangle(0, 0, (int) Math.round(height / heightToWidth as double), height)
            } else {
                imageBounds = new Rectangle( 0, 0, width, (int) Math.round(width * heightToWidth))
            }

            image = new BufferedImage(imageBounds.width.toInteger(), imageBounds.height.toInteger(), BufferedImage.TYPE_INT_RGB)

            Graphics2D gr = image.createGraphics()
            gr.setPaint(Color.WHITE)
            gr.fill(imageBounds)

            renderer.paint(gr, imageBounds, mapBounds)
        } finally {
            map.dispose()
        }

        image
    }


    static BufferedImage getShapeFileFeaturesAsImage(File shpFileDir, String featureIndexes, int width,int  height) throws IOException {

        if (!shpFileDir.exists() || !shpFileDir.isDirectory()) {
            throw new IllegalArgumentException("Supplied directory does not exist or is not a directory")
        }

        DefaultFeatureCollection collection = new DefaultFeatureCollection()
        FileDataStore store = null
        SimpleFeatureIterator it = null

        try {

            File shpFile = null
            for (File f : shpFileDir.listFiles()) {
                if (f.getName().endsWith(".shp")) {
                    shpFile = f
                    break
                }
            }

            if (shpFile == null) {
                throw new IllegalArgumentException("No .shp file present in directory")
            }

            store = FileDataStoreFinder.getDataStore(shpFile)

            SimpleFeatureSource featureSource = store.getFeatureSource(store.getTypeNames()[0])
            SimpleFeatureCollection featureCollection = featureSource.getFeatures()
            it = featureCollection.features()

            //transform CRS to the same as the shapefile (at least try)
            //default to 4326
            CoordinateReferenceSystem crs = null
            try {
                crs = store.getSchema().getCoordinateReferenceSystem()
                if (crs == null) {
                    //attempt to parse prj
                    try {
                        File prjFile = new File(shpFile.getPath().substring(0, shpFile.getPath().length() - 3) + "prj")
                        if (prjFile.exists()) {
                            String prj = prjFile.text

                            if (prj == "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Mercator_Auxiliary_Sphere\"],PARAMETER[\"False_Easting\",0.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",0.0],PARAMETER[\"Standard_Parallel_1\",0.0],PARAMETER[\"Auxiliary_Sphere_Type\",0.0],UNIT[\"Meter\",1.0]]") {
                                //support for arcgis online default shp exports
                                crs = CRS.decode("EPSG:3857")
                            } else {
                                crs = CRS.parseWKT(prjFile.text)
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    if (crs == null) {
                        crs = DefaultGeographicCRS.WGS84
                    }
                }
            } finally {
            }

            List<SimpleFeature> features = new ArrayList<SimpleFeature>()

            int i = 0
            boolean all = "all".equalsIgnoreCase(featureIndexes)
            def indexes = []
            if (!all) featureIndexes.split(",").each { indexes.push(it.toInteger()) }
            while (it.hasNext()) {
                SimpleFeature feature = (SimpleFeature) it.next()
                if (all || indexes.contains(i)) {
                    features.add(feature)
                }
                i++
            }


            collection.addAll(features)
        } finally {
            if (it != null) {
                it.close()
            }
            if (store != null) {
                store.dispose()
            }
        }

        renderCollection(collection, width, height)
    }


    static String getShapeFileFeaturesAsWkt(File shpFileDir, String featureIndexes) throws IOException {

        if (!shpFileDir.exists() || !shpFileDir.isDirectory()) {
            throw new IllegalArgumentException("Supplied directory does not exist or is not a directory")
        }

        List<Geometry> geometries = new ArrayList<Geometry>()
        FileDataStore store = null
        SimpleFeatureIterator it = null

        try {

            File shpFile = null
            for (File f : shpFileDir.listFiles()) {
                if (f.getName().endsWith(".shp")) {
                    shpFile = f
                    break
                }
            }

            if (shpFile == null) {
                throw new IllegalArgumentException("No .shp file present in directory")
            }

            store = FileDataStoreFinder.getDataStore(shpFile)

            SimpleFeatureSource featureSource = store.getFeatureSource(store.getTypeNames()[0])
            SimpleFeatureCollection featureCollection = featureSource.getFeatures()
            it = featureCollection.features()

            //transform CRS to the same as the shapefile (at least try)
            //default to 4326
            CoordinateReferenceSystem crs = null
            try {
                crs = store.getSchema().getCoordinateReferenceSystem()
                if (crs == null) {
                    //attempt to parse prj
                    try {
                        File prjFile = new File(shpFile.getPath().substring(0, shpFile.getPath().length() - 3) + "prj")
                        if (prjFile.exists()) {
                            String prj = prjFile.text

                            if (prj == "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Mercator_Auxiliary_Sphere\"],PARAMETER[\"False_Easting\",0.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",0.0],PARAMETER[\"Standard_Parallel_1\",0.0],PARAMETER[\"Auxiliary_Sphere_Type\",0.0],UNIT[\"Meter\",1.0]]") {
                                //support for arcgis online default shp exports
                                crs = CRS.decode("EPSG:3857")
                            } else {
                                crs = CRS.parseWKT(prjFile.text)
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    if (crs == null) {
                        crs = DefaultGeographicCRS.WGS84
                    }
                }
            } catch (Exception ignored) {
            }

            int i = 0
            boolean all = "all".equalsIgnoreCase(featureIndexes)
            def indexes = []
            if (!all) featureIndexes.split(",").each { indexes.push(it.toInteger()) }
            while (it.hasNext()) {
                SimpleFeature feature = (SimpleFeature) it.next()
                if (all || indexes.contains(i)) {
                    geometries.add(feature.getDefaultGeometry() as Geometry)
                }
                i++
            }

            Geometry mergedGeometry

            if (geometries.size() == 1) {
                mergedGeometry = geometries.get(0)
            } else {
                GeometryFactory factory = JTSFactoryFinder.getGeometryFactory(null)
                GeometryCollection geometryCollection = (GeometryCollection) factory.buildGeometry(geometries)

                // note the following geometry collection may be invalid (say with overlapping polygons)
                mergedGeometry = geometryCollection.union()
            }

            try {
                return JTS.transform(mergedGeometry, CRS.findMathTransform(crs, DefaultGeographicCRS.WGS84, true)).toString()
            } catch (Exception ignored) {
                return mergedGeometry.toString()
            }
        } catch (Exception e) {
            throw e
        } finally {
            if (it != null) {
                it.close()
            }
            if (store != null) {
                store.dispose()
            }
        }
    }

    //private static final Logger logger = log.getLogger(SpatialUtils.class);

    static HashMap<Double, double[]> commonGridLatitudeArea = new HashMap<Double, double[]>()
    static private final int map_zoom = 21
    static private final int map_offset = 268435456 // half the Earth's circumference at zoom level 21
    static private final double map_radius = map_offset / Math.PI

    //calculateArea from FilteringResultsWCController
    static double calculateArea(double[][] areaarr) {
        try {
            double totalarea = 0.0
            double[] d = areaarr[0]
            for (int f = 1; f < areaarr.length - 2; ++f) {
                totalarea += Mh(d, areaarr[f], areaarr[f + 1])
            }

            totalarea = Math.abs(totalarea * 6378137 * 6378137)

            //return as sq km
            return totalarea / 1000.0 / 1000.0

        } catch (Exception e) {
            log.error("Error in calculateArea", e)
        }

        return 0
    }

    static private double Mh(double[] a, double[] b, double[] c) {
        return Nh(a, b, c) * hi(a, b, c)
    }

    static private double Nh(double[] a, double[] b, double[] c) {
        double[][] poly = [a, b, c, a]
        double[] area = new double[3]
        int i = 0
        double j = 0.0
        for (i = 0; i < 3; ++i) {
            area[i] = vd(poly[i], poly[i + 1])
            j += area[i]
        }
        j /= 2
        double f = Math.tan(j / 2)
        for (i = 0; i < 3; ++i) {
            f *= Math.tan((j - area[i]) / 2)
        }
        return 4 * Math.atan(Math.sqrt(Math.abs(f)))
    }

    static private double hi(double[] a, double[] b, double[] c) {
        double[][] d = [a, b, c]

        int i = 0
        double[][] bb = new double[3][3]
        for (i = 0; i < 3; ++i) {
            double lng = d[i][0]
            double lat = d[i][1]

            double y = Uc(lat)
            double x = Uc(lng)

            bb[i][0] = Math.cos(y) * Math.cos(x)
            bb[i][1] = Math.cos(y) * Math.sin(x)
            bb[i][2] = Math.sin(y)
        }

        return (bb[0][0] * bb[1][1] * bb[2][2] + bb[1][0] * bb[2][1] * bb[0][2] + bb[2][0] * bb[0][1] * bb[1][2] - bb[0][0] * bb[2][1] * bb[1][2] - bb[1][0] * bb[0][1] * bb[2][2] - bb[2][0] * bb[1][1] * bb[0][2] > 0) ? 1 : -1
    }

    static private double vd(double[] a, double[] b) {
        double lng1 = a[0]
        double lat1 = a[1]

        double lng2 = b[0]
        double lat2 = b[1]

        double c = Uc(lat1)
        double d = Uc(lat2)

        return 2 * Math.asin(Math.sqrt(Math.pow(Math.sin((c - d) / 2), 2) + Math.cos(c) * Math.cos(d) * Math.pow(Math.sin((Uc(lng1) - Uc(lng2)) / 2), 2)))
    }

    static private double Uc(double a) {
        return a * (Math.PI / 180)
    }

    static double calculateArea(String wkt) {


        double sumarea = 0

        //GEOMETRYCOLLECTION
        String areaWorking = wkt
        ArrayList<String> stringsList = new ArrayList<String>()
        if (areaWorking.startsWith("GEOMETRYCOLLECTION")) {
            //split out polygons and multipolygons
            areaWorking = areaWorking.replace(", ", ",")
            areaWorking = areaWorking.replace(") ", ")")
            areaWorking = areaWorking.replace(" )", ")")
            areaWorking = areaWorking.replace(" (", "(")
            areaWorking = areaWorking.replace("( ", "(")

            int posStart, posEnd, p1, p2
            p1 = areaWorking.indexOf("POLYGON")
            p2 = areaWorking.indexOf("MULTIPOLYGON")
            if (p1 < 0) {
                posStart = p2
            } else if (p2 < 0) {
                posStart = p1
            } else {
                posStart = Math.min(p1, p2)
            }
            String endString = null
            if (posStart == p1) {
                endString = "))"
            } else {
                endString = ")))"
            }
            posEnd = areaWorking.indexOf(endString, posStart)
            while (posStart > 0 && posEnd > 0) {
                //split multipolygons
                if (endString.length() == 3) {
                    Collections.addAll(stringsList, areaWorking.substring(posStart, posEnd - 1).split("\\)\\),\\(\\("))
                } else {
                    stringsList.add(areaWorking.substring(posStart, posEnd - 1))
                }

                posStart = posEnd
                p1 = areaWorking.indexOf("POLYGON", posStart)
                p2 = areaWorking.indexOf("MULTIPOLYGON", posStart)
                if (p1 < 0) {
                    posStart = p2
                } else if (p2 < 0) {
                    posStart = p1
                } else {
                    posStart = Math.min(p1, p2)
                }
                if (posStart == p1) {
                    endString = "))"
                } else {
                    endString = ")))"
                }
                posEnd = areaWorking.indexOf(endString, posStart)
            }
            if (posStart >= 0) {
                stringsList.add(areaWorking.substring(posStart))
            }
        } else if (areaWorking.startsWith("MULTIPOLYGON")) {
            //split
            Collections.addAll(stringsList, areaWorking.split("\\)\\),\\(\\("))
        } else if (areaWorking.startsWith("POLYGON")) {
            stringsList.add(areaWorking)
        }

        for (String w : stringsList) {
            if (w.contains("ENVELOPE")) {
                continue
            }

            String[] areas = w.split("\\),\\(")
            double shapearea = 0

            for (String area : areas) {
                area = area.replace("MULTIPOLYGON", "")
                area = area.replace("POLYGON", "")
                area = area.replace(")", "")
                area = area.replace("(", "")

                String[] areaarr = area.split(",")
                // Trim any leading or trailing whitespace off the coordinate pairs.
                for (int i = 0; i < areaarr.length - 1; i++) {
                    areaarr[i] = areaarr[i].trim()
                }

                // check if it's the 'world' bbox
                boolean isWorld = true
                for (int i = 0; i < areaarr.length - 1; i++) {
                    String[] darea = areaarr[i].split(" ")
                    if ((Double.parseDouble(darea[0]) < -174
                            && Double.parseDouble(darea[1]) < -84)
                            || (Double.parseDouble(darea[0]) < -174
                            && Double.parseDouble(darea[1]) > 84)
                            || (Double.parseDouble(darea[0]) > 174
                            && Double.parseDouble(darea[1]) > 84)
                            || (Double.parseDouble(darea[0]) > 174
                            && Double.parseDouble(darea[1]) < -84)) {
                        //return 510000000;
                    } else {
                        isWorld = false
                    }
                }
                //if (isWorld) return (510000000 * 1000 * 1000 * 1L);
                if (isWorld) {
                    return 510000000000000L
                }

                double totalarea = 0.0
                String d = areaarr[0]
                for (int f = 1; f < areaarr.length - 2; ++f) {
                    totalarea += Mh(d, areaarr[f], areaarr[f + 1])
                }

                shapearea += totalarea * 6378137 * 6378137
            }

            sumarea += Math.abs(shapearea)
        }

        return sumarea
    }

    static private double Mh(String a, String b, String c) {
        return Nh(a, b, c) * hi(a, b, c)
    }

    static private double Nh(String a, String b, String c) {
        String[] poly = [a, b, c, a]
        double[] area = new double[3]
        int i = 0
        double j = 0.0
        for (i = 0; i < 3; ++i) {
            area[i] = vd(poly[i], poly[i + 1])
            j += area[i]
        }
        j /= 2
        double f = Math.tan(j / 2)
        for (i = 0; i < 3; ++i) {
            f *= Math.tan((j - area[i]) / 2)
        }
        return 4 * Math.atan(Math.sqrt(Math.abs(f)))
    }

    static private double hi(String a, String b, String c) {
        String[] d = [a, b, c]

        int i = 0
        double[][] bb = new double[3][3]
        for (i = 0; i < 3; ++i) {
            String[] coords = d[i].split(" ")
            double lng = Double.parseDouble(coords[0])
            double lat = Double.parseDouble(coords[1])

            double y = Uc(lat)
            double x = Uc(lng)

            bb[i][0] = Math.cos(y) * Math.cos(x)
            bb[i][1] = Math.cos(y) * Math.sin(x)
            bb[i][2] = Math.sin(y)
        }

        return (bb[0][0] * bb[1][1] * bb[2][2] + bb[1][0] * bb[2][1] * bb[0][2] + bb[2][0] * bb[0][1] * bb[1][2] - bb[0][0] * bb[2][1] * bb[1][2] - bb[1][0] * bb[0][1] * bb[2][2] - bb[2][0] * bb[1][1] * bb[0][2] > 0) ? 1 : -1
    }

    static private double vd(String a, String b) {
        String[] coords1 = a.split(" ")
        double lng1 = Double.parseDouble(coords1[0])
        double lat1 = Double.parseDouble(coords1[1])

        String[] coords2 = b.split(" ")
        double lng2 = Double.parseDouble(coords2[0])
        double lat2 = Double.parseDouble(coords2[1])

        double c = Uc(lat1)
        double d = Uc(lat2)

        return 2 * Math.asin(Math.sqrt(Math.pow(Math.sin((c - d) / 2), 2) + Math.cos(c) * Math.cos(d) * Math.pow(Math.sin((Uc(lng1) - Uc(lng2)) / 2), 2)))
    }

    static double cellArea(double resolution, double latitude) {
        double[] areas = commonGridLatitudeArea.get(resolution)

        if (areas == null) {
            areas = buildCommonGridLatitudeArea(resolution)
            commonGridLatitudeArea.put(resolution, areas)
        }

        return areas[(int) (Math.floor(Math.abs(latitude / resolution as double)) * resolution)]
    }

    static double[] buildCommonGridLatitudeArea(double resolution) {
        int parts = (int) Math.ceil(90 / resolution as double)
        double[] areas = new double[parts]

        for (int i = 0; i < parts; i++) {
            double minx = 0
            double maxx = resolution
            double miny = resolution * i
            double maxy = miny + resolution

            areas[i] = calculateArea([[minx, miny], [minx, maxy], [maxx, maxy], [maxx, miny], [minx, miny]] as double[][])
        }

        return areas
    }
}
