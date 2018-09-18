/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.slave

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.layers.grid.Grid2Shape
import au.org.ala.layers.intersect.Grid
import au.org.ala.spatial.Util
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.GeometryCollection
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.geom.MultiPolygon
import com.vividsolutions.jts.io.WKTReader
import com.vividsolutions.jts.io.WKTWriter
import org.apache.commons.io.FileUtils
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
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.feature.simple.SimpleFeatureTypeBuilder
import org.geotools.geometry.jts.FactoryFinder
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.map.FeatureLayer
import org.geotools.map.MapContent
import org.geotools.referencing.CRS
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.renderer.GTRenderer
import org.geotools.renderer.lite.StreamingRenderer
import org.geotools.styling.SLD
import org.geotools.styling.Style
import org.geotools.xml.Parser
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.referencing.crs.CoordinateReferenceSystem

import java.awt.*
import java.awt.image.BufferedImage
import java.util.List

class SpatialUtils {
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
            def classificationMeans = means.exists() ? new CSVReader(new FileReader(means)).readAll() : [[]]

            try {
                final SimpleFeatureType type = createFeatureType(means.exists() ? classificationMeans.get(0) : null);

                List<SimpleFeature> features = new ArrayList<SimpleFeature>();
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);

                WKTReader wkt = new WKTReader();

                values.each { value ->
                    def w = Grid2Shape.grid2Wkt(data, value, value, grid.nrows, grid.ncols, grid.xmin, grid.ymin, grid.xres, grid.yres)
                    if (!w.contains("()")) {
                        Geometry geom = wkt.read(w);
                        featureBuilder.add(geom)
                        SimpleFeature feature = featureBuilder.buildFeature(null)
                        feature.setAttribute("group", String.valueOf((int) value))

                        if (means.exists()) {
                            classificationMeans.each { cm ->
                                if (cm[0].equals(String.valueOf((int) value))) {
                                    for (int i = 1; i < cm.length; i++) {
                                        try {
                                            feature.setAttribute(classificationMeans.get(0)[i], Double.parseDouble(cm[i]))
                                        } catch (err) {
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

                Map<String, Serializable> params = new HashMap<String, Serializable>();
                params.put("url", shpFile.toURI().toURL());
                params.put("create spatial index", Boolean.TRUE);

                ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
                newDataStore.createSchema(type);

                newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);

                Transaction transaction = new DefaultTransaction("create");

                String typeName = newDataStore.getTypeNames()[0];
                SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

                DefaultFeatureCollection collection = new DefaultFeatureCollection();
                collection.addAll(features);
                featureStore.setTransaction(transaction);
                try {
                    featureStore.addFeatures(collection);
                    transaction.commit();
                } catch (Exception problem) {
                    problem.printStackTrace()
                    transaction.rollback();

                } finally {
                    transaction.close();
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
    }

    static def SimpleFeatureType createFeatureType(String[] additionalColumns) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("aloc");
        builder.setCRS(DefaultGeographicCRS.WGS84);

        builder.add("the_geom", MultiPolygon.class);
        builder.add("group", String.class);

        for (int i = 1; additionalColumns != null && i < additionalColumns.length; i++) {
            builder.add(additionalColumns[i], Double.class)
        }

        // build the type
        return builder.buildFeatureType();
    }

    static def toGeotiff(gdalPath, inputFile) throws Exception {
        def outputFile = inputFile.substring(0, inputFile.lastIndexOf('.')) + ".tif"

        String[] cmd = [gdalPath + '/gdal_translate',
                   "-of", "GTiff",
                   "-co", "COMPRESS=DEFLATE",
                   "-co", "TILED=YES",
                   "-co", "BIGTIFF=IF_SAFER"
                   , inputFile
                   , outputFile]


        Util.runCmd(cmd)

        cmd = [gdalPath + '/gdaladdo',
               "-r", "cubic"
               , outputFile
               , "2", "4", "8", "16", "32", "64"]

        Util.runCmd(cmd)
    }

    static def save4326prj(path) {

        StringBuffer sbProjection = new StringBuffer();
        sbProjection.append("GEOGCS[\"WGS 84\", ").append("\n");
        sbProjection.append("    DATUM[\"WGS_1984\", ").append("\n");
        sbProjection.append("        SPHEROID[\"WGS 84\",6378137,298.257223563, ").append("\n");
        sbProjection.append("            AUTHORITY[\"EPSG\",\"7030\"]], ").append("\n");
        sbProjection.append("        AUTHORITY[\"EPSG\",\"6326\"]], ").append("\n");
        sbProjection.append("    PRIMEM[\"Greenwich\",0, ").append("\n");
        sbProjection.append("        AUTHORITY[\"EPSG\",\"8901\"]], ").append("\n");
        sbProjection.append("    UNIT[\"degree\",0.01745329251994328, ").append("\n");
        sbProjection.append("        AUTHORITY[\"EPSG\",\"9122\"]], ").append("\n");
        sbProjection.append("    AUTHORITY[\"EPSG\",\"4326\"]] ").append("\n");

        FileUtils.writeStringToFile(new File(path), sbProjection.toString())
    }

    static def divaToAsc(path) throws FileNotFoundException, Exception {
        def asc = path + ".asc"
        def grid = new Grid(path)

        BufferedWriter fw
        try {
            fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(asc), "US-ASCII"));
            fw.append("ncols ").append(String.valueOf(grid.ncols)).append("\n");
            fw.append("nrows ").append(String.valueOf(grid.nrows)).append("\n");
            fw.append("xllcorner ").append(String.valueOf(grid.xmin)).append("\n");
            fw.append("yllcorner ").append(String.valueOf(grid.ymin)).append("\n");
            fw.append("cellsize ").append(String.valueOf(grid.xres)).append("\n");

            fw.append("NODATA_value ").append(String.valueOf(-1));

            float[] grid_data = grid.getGrid();

            for (int i = 0; i < grid.nrows; i++) {
                fw.append("\n");
                for (int j = 0; j < grid.ncols; j++) {
                    if (j > 0) {
                        fw.append(" ");
                    }
                    if (Float.isNaN(grid_data[i * grid.ncols + j])) {
                        fw.append("-1");
                    } else {
                        fw.append(String.valueOf(grid_data[i * grid.ncols + j]));
                    }
                }
            }

            fw.append("\n");
            fw.close();

            //projection file
            save4326prj(path + ".prj")
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * parse a KML containing a single placemark, or a placemark in a folder, into WKT.
     *
     * @param kmldata
     * @return WKT if valid, null on error, empty string "" when placemark not matched.
     */
    static def String getKMLPolygonAsWKT(String kmldata) throws Exception {
        Parser parser = new Parser(new org.geotools.kml.v22.KMLConfiguration());
        SimpleFeature f = (SimpleFeature) parser.parse(new StringReader(kmldata));
        Collection placemarks = (Collection) f.getAttribute("Feature");

        Geometry g = null;
        SimpleFeature sf = null;

        //for <Placemark>
        if (!placemarks.isEmpty() && !placemarks.isEmpty()) {
            sf = (SimpleFeature) placemarks.iterator().next();
            g = (Geometry) sf.getAttribute("Geometry");
        }

        //for <Folder><Placemark>
        if (g == null && sf != null) {
            placemarks = (Collection) sf.getAttribute("Feature");
            if (placemarks != null && !placemarks.isEmpty()) {
                g = (Geometry) ((SimpleFeature) placemarks.iterator().next()).getAttribute("Geometry");
            } else {
                placemarks = (Collection) sf.getAttribute("Folder");
                if (placemarks != null && !placemarks.isEmpty()) {
                    g = (Geometry) ((SimpleFeature) placemarks.iterator().next()).getAttribute("Geometry");
                }
            }
        }

        if (g != null) {
            WKTWriter wr = new WKTWriter();
            String wkt = wr.write(g);
            return wkt.replace(" (", "(").replace(", ", ",").replace(") ", ")");
        } else {
            return "";
        }

        return null;
    }

    static def BufferedImage getWktImage(wkt, width, height) throws Exception {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(DefaultGeographicCRS.WGS84);
        builder.setName("id");
        builder.add("the_geom", MultiPolygon.class);
        final SimpleFeatureType type = builder.buildFeatureType();

        List<SimpleFeature> features = new ArrayList<SimpleFeature>();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);

        WKTReader reader = new WKTReader();

        wkt.each { w ->
            Geometry geom = reader.read(w);
            featureBuilder.add(geom)
            SimpleFeature feature = featureBuilder.buildFeature(null)
            features.add(feature)
        }
        DefaultFeatureCollection collection = new DefaultFeatureCollection();
        collection.addAll(features);

        renderCollection(collection, width, height)
    }

    def static BufferedImage renderCollection(collection, width, height) {
        MapContent map = new MapContent();
        BufferedImage image = null;

        try {
            Style style = SLD.createPolygonStyle(Color.BLACK, Color.GRAY, 0.5f);
            map.addLayer(new FeatureLayer(collection, style));

            GTRenderer renderer = new StreamingRenderer();
            renderer.setMapContent(map);

            Rectangle imageBounds;
            ReferencedEnvelope mapBounds;
            mapBounds = map.getMaxBounds();
            double heightToWidth = mapBounds.getSpan(1) / mapBounds.getSpan(0);
            if (heightToWidth * width > height) {
                imageBounds = new Rectangle(
                        0, 0, (int) Math.round(height / heightToWidth), height);
            } else {
                imageBounds = new Rectangle(
                        0, 0, width, (int) Math.round(width * heightToWidth));
            }

            image = new BufferedImage(imageBounds.width.toInteger(), imageBounds.height.toInteger(), BufferedImage.TYPE_INT_RGB);

            Graphics2D gr = image.createGraphics();
            gr.setPaint(Color.WHITE);
            gr.fill(imageBounds);

            renderer.paint(gr, imageBounds, mapBounds);
        } catch (Exception e) {

        } finally {
            map.dispose();
        }

        image
    }

    def
    static BufferedImage getShapeFileFeaturesAsImage(File shpFileDir, String featureIndexes, width, height) throws IOException {

        if (!shpFileDir.exists() || !shpFileDir.isDirectory()) {
            throw new IllegalArgumentException("Supplied directory does not exist or is not a directory");
        }

        DefaultFeatureCollection collection = new DefaultFeatureCollection();
        FileDataStore store = null
        SimpleFeatureIterator it = null

        try {

            File shpFile = null;
            for (File f : shpFileDir.listFiles()) {
                if (f.getName().endsWith(".shp")) {
                    shpFile = f;
                    break;
                }
            }

            if (shpFile == null) {
                throw new IllegalArgumentException("No .shp file present in directory");
            }

            store = FileDataStoreFinder.getDataStore(shpFile);

            SimpleFeatureSource featureSource = store.getFeatureSource(store.getTypeNames()[0]);
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();
            it = featureCollection.features();

            //transform CRS to the same as the shapefile (at least try)
            //default to 4326
            CoordinateReferenceSystem crs = null;
            try {
                crs = store.getSchema().getCoordinateReferenceSystem();
                if (crs == null) {
                    //attempt to parse prj
                    try {
                        File prjFile = new File(shpFile.getPath().substring(0, shpFile.getPath().length() - 3) + "prj");
                        if (prjFile.exists()) {
                            String prj = FileUtils.readFileToString(prjFile);

                            if (prj.equals("PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Mercator_Auxiliary_Sphere\"],PARAMETER[\"False_Easting\",0.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",0.0],PARAMETER[\"Standard_Parallel_1\",0.0],PARAMETER[\"Auxiliary_Sphere_Type\",0.0],UNIT[\"Meter\",1.0]]")) {
                                //support for arcgis online default shp exports
                                crs = CRS.decode("EPSG:3857");
                            } else {
                                crs = CRS.parseWKT(FileUtils.readFileToString(prjFile));
                            }
                        }
                    } catch (Exception e) {
                    }

                    if (crs == null) {
                        crs = DefaultGeographicCRS.WGS84;
                    }
                }
            } catch (Exception e) {
            }

            List<SimpleFeature> features = new ArrayList<SimpleFeature>();

            int i = 0;
            boolean all = "all".equalsIgnoreCase(featureIndexes)
            def indexes = []
            if (!all) featureIndexes.split(",").each { indexes.push(it.toInteger()) }
            while (it.hasNext()) {
                SimpleFeature feature = (SimpleFeature) it.next();
                if (all || indexes.contains(i)) {
                    features.add(feature)
                }
                i++;
            }


            collection.addAll(features);
        } catch (Exception e) {

        } finally {
            if (it != null) {
                try {
                    it.close()
                } catch (Exception e) {
                }
            }
            if (store != null) {
                try {
                    store.dispose()
                } catch (Exception e) {
                }
            }
        }

        renderCollection(collection, width, height)
    }

    def
    static String getShapeFileFeaturesAsWkt(File shpFileDir, String featureIndexes) throws IOException {

        if (!shpFileDir.exists() || !shpFileDir.isDirectory()) {
            throw new IllegalArgumentException("Supplied directory does not exist or is not a directory");
        }

        List geometries = new ArrayList<Geometry>()
        FileDataStore store = null
        SimpleFeatureIterator it = null

        try {

            File shpFile = null;
            for (File f : shpFileDir.listFiles()) {
                if (f.getName().endsWith(".shp")) {
                    shpFile = f;
                    break;
                }
            }

            if (shpFile == null) {
                throw new IllegalArgumentException("No .shp file present in directory");
            }

            store = FileDataStoreFinder.getDataStore(shpFile);

            SimpleFeatureSource featureSource = store.getFeatureSource(store.getTypeNames()[0]);
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();
            it = featureCollection.features();

            //transform CRS to the same as the shapefile (at least try)
            //default to 4326
            CoordinateReferenceSystem crs = null;
            try {
                crs = store.getSchema().getCoordinateReferenceSystem();
                if (crs == null) {
                    //attempt to parse prj
                    try {
                        File prjFile = new File(shpFile.getPath().substring(0, shpFile.getPath().length() - 3) + "prj");
                        if (prjFile.exists()) {
                            String prj = FileUtils.readFileToString(prjFile);

                            if (prj.equals("PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Mercator_Auxiliary_Sphere\"],PARAMETER[\"False_Easting\",0.0],PARAMETER[\"False_Northing\",0.0],PARAMETER[\"Central_Meridian\",0.0],PARAMETER[\"Standard_Parallel_1\",0.0],PARAMETER[\"Auxiliary_Sphere_Type\",0.0],UNIT[\"Meter\",1.0]]")) {
                                //support for arcgis online default shp exports
                                crs = CRS.decode("EPSG:3857");
                            } else {
                                crs = CRS.parseWKT(FileUtils.readFileToString(prjFile));
                            }
                        }
                    } catch (Exception e) {
                    }

                    if (crs == null) {
                        crs = DefaultGeographicCRS.WGS84;
                    }
                }
            } catch (Exception e) {
            }

            List<SimpleFeature> features = new ArrayList<SimpleFeature>();

            int i = 0;
            boolean all = "all".equalsIgnoreCase(featureIndexes)
            def indexes = []
            if (!all) featureIndexes.split(",").each { indexes.push(it.toInteger()) }
            while (it.hasNext()) {
                SimpleFeature feature = (SimpleFeature) it.next();
                if (all || indexes.contains(i)) {
                    geometries.add(feature.getDefaultGeometry())
                }
                i++;
            }

            GeometryFactory factory = FactoryFinder.getGeometryFactory(null);

            // note the following geometry collection may be invalid (say with overlapping polygons)
            GeometryCollection geometryCollection =
                    (GeometryCollection) factory.buildGeometry(geometries);

            return geometryCollection.union().toString()

        } catch (Exception e) {
            throw e
        } finally {
            if (it != null) {
                try {
                    it.close()
                } catch (Exception e) {
                }
            }
            if (store != null) {
                try {
                    store.dispose()
                } catch (Exception e) {
                }
            }
        }

        return null
    }
}
