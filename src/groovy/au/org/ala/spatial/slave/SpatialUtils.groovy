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
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.MultiPolygon
import com.vividsolutions.jts.io.WKTReader
import org.apache.commons.io.FileUtils
import org.geotools.data.DefaultTransaction
import org.geotools.data.Transaction
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureSource
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.feature.simple.SimpleFeatureTypeBuilder
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType

class SpatialUtils {
    static void grid2shp(String grdPath) {
        File shpFile = new File(grdPath + '.shp')
        Grid grid = new Grid(grdPath)
        if (grid != null) {
            //TODO: review 2GB limit

            //get list of unique grid values
            def data = grid.getGrid()
            def values = [] as Set
            data.each { values.add(it) }

            def classificationMeans = new CSVReader(new FileReader(shpFile.getParent() + File.separator + "classification_means.csv")).readAll()

            try {
                final SimpleFeatureType type = createFeatureType(classificationMeans.get(0));

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
                    //log.error("error pricessing shape file: " + shpFile.getAbsolutePath(), problem);
                    transaction.rollback();

                } finally {
                    transaction.close();
                }

                //log.debug("Active Area shapefile written to: " + shpFile.getAbsolutePath())

            } catch (Exception e) {
                //log.error("Unable to save shapefile: " + shpFile.getAbsolutePath(), e);
            }
        }
    }

    static def SimpleFeatureType createFeatureType(String[] additionalColumns) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("aloc");
        builder.setCRS(DefaultGeographicCRS.WGS84);

        builder.add("the_geom", MultiPolygon.class);
        builder.add("group", String.class);

        for (int i = 1; i < additionalColumns.length; i++) {
            builder.add(additionalColumns[i], Double.class)
        }

        // build the type
        return builder.buildFeatureType();
    }

    static def toGeotiff(gdalPath, inputFile) throws Exception {
        def outputFile = inputFile.substring(0, inputFile.lastIndexOf('.')) + ".tif"

        def cmd = [gdalPath + '/gdal_translate',
                   "-of", "GTiff",
                   "-co", "COMPRESS=DEFLATE",
                   "-co", "TILED=YES",
                   "-co", "BIGTIFF=IF_SAFER"
                   , inputFile
                   , outputFile]


        Utils.runCmd(cmd as String[])

        cmd = [gdalPath + '/gdaladdo',
               "-r", "cubic"
               , outputFile
               , "2", "4", "8", "16", "32", "64"]

        Utils.runCmd(cmd as String[])
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
}
