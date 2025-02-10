/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial.grid

import au.org.ala.spatial.dto.GridClass
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.util.SpatialUtils
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.codehaus.jackson.map.ObjectMapper
import org.geotools.data.DataUtilities
import org.geotools.data.DefaultTransaction
import org.geotools.data.Transaction
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.shapefile.ShapefileDataStoreFactory
import org.geotools.data.simple.SimpleFeatureSource
import org.geotools.data.simple.SimpleFeatureStore
import org.geotools.feature.DefaultFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geojson.feature.FeatureJSON
import org.geotools.kml.KML
import org.geotools.kml.KMLConfiguration
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.geotools.xsd.Encoder
import org.locationtech.jts.geom.MultiPolygon
import org.opengis.feature.simple.SimpleFeature
import org.opengis.feature.simple.SimpleFeatureType


import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @author Adam
 */
@Slf4j
//@CompileStatic
 class GridClassBuilder {

    final public static int[] colours = [0x003366CC, 0x00DC3912, 0x00FF9900, 0x00109618, 0x00990099, 0x000099C6, 0x00DD4477, 0x0066AA00, 0x00B82E2E, 0x00316395, 0x00994499, 0x0022AA99, 0x00AAAA11, 0x006633CC, 0x00E67300, 0x008B0707, 0x00651067, 0x00329262, 0x005574A6, 0x003B3EAC, 0x00B77322, 0x0016D620, 0x00B91383, 0x00F4359E, 0x009C5935, 0x00A9C413, 0x002A778D, 0x00668D1C, 0x00BEA413, 0x000C5922, 0x00743411]

    static HashMap<Integer, GridClass> buildFromGrid(String filePath) throws IOException {
        File wktDir = new File(filePath)
        wktDir.mkdirs()

        int[] wktMap = null

        //track values for the SLD
        ArrayList<Integer> maxValues = new ArrayList<Integer>()
        ArrayList<String> labels = new ArrayList<String>()

        HashMap<Integer, GridClass> classes = new HashMap<Integer, GridClass>()
        Properties p = new Properties()
        p.load(new FileReader(filePath + ".txt"))

        boolean mergeProperties = false

        Map<String, Set<Integer>> groupedKeys = new HashMap<String, Set<Integer>>()
        Map<Integer, Integer> translateKeys = new HashMap<Integer, Integer>()
        Map<String, Integer> translateValues = new HashMap<String, Integer>()
        ArrayList<Integer> keys = new ArrayList<Integer>()
        for (String key : p.stringPropertyNames()) {
            try {
                int k = Integer.parseInt(key)
                keys.add(k)

                //grouping of property file keys by value
                String value = p.getProperty(key)
                Set<Integer> klist = groupedKeys.get(value)
                if (klist == null) klist = new HashSet<Integer>()
                else mergeProperties = true
                klist.add(k)
                groupedKeys.put(value, klist)

                if (!translateValues.containsKey(value)) translateValues.put(value, translateValues.size() + 1)
                translateKeys.put(k, translateValues.get(value))

            } catch (NumberFormatException ignored) {
                log.debug("Excluding shape key '" + key + "'")
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            }
        }

        Collections.sort(keys)

        Grid g = new Grid(filePath)
        boolean generateWkt = false //((long) g.nrows) * ((long) g.ncols) < (long) Integer.MAX_VALUE;

        if (mergeProperties) {
            g.replaceValues(translateKeys)

            if (!new File(filePath + ".txt.old").exists())
                FileUtils.moveFile(new File(filePath + ".txt"), new File(filePath + ".txt.old"))

            StringBuilder sb = new StringBuilder()
            for (String value : translateValues.keySet()) {
                sb.append(translateValues.get(value)).append("=").append(value).append('\n')
            }
            new File(filePath + ".txt").write( sb.toString())

            return buildFromGrid(filePath)
        }

        if (generateWkt) {
            for (String name : groupedKeys.keySet()) {
                try {
                    Set<Integer> klist = groupedKeys.get(name)

                    String key = klist.iterator().next().toString()
                    int k = Integer.parseInt(key)

                    GridClass gc = new GridClass()
                    gc.setName(name)
                    gc.setId(k)

                    if (klist.size() == 1) klist = null

                    log.debug("getting wkt for " + filePath + " > " + key)

                    Map wktIndexed = Envelope.getGridSingleLayerEnvelopeAsWktIndexed(filePath + "," + key + "," + key, klist, wktMap)

                    //write class wkt
                    File zipFile = new File(filePath + File.separator + key + ".wkt.zip")
                    ZipOutputStream zos = null
                    try {
                        zos = new ZipOutputStream(new FileOutputStream(zipFile))
                        zos.putNextEntry(new ZipEntry(key + ".wkt"))
                        zos.write(((String) wktIndexed.get("wkt")).bytes)
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
                    }
                    BufferedOutputStream bos = null
                    try {
                        bos = new BufferedOutputStream(new FileOutputStream(filePath + File.separator + key + ".wkt"))
                        bos.write(((String) wktIndexed.get("wkt")).bytes)
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
                    log.debug("wkt written to file")
                    gc.setArea_km(SpatialUtils.calculateArea((String) wktIndexed.get("wkt")) / 1000.0 / 1000.0 as Double)

                    //store map
                    wktMap = (int[]) wktIndexed.get("map")

                    //write wkt index
                    FileWriter fw = null
                    try {
                        fw = new FileWriter(filePath + File.separator + key + ".wkt.index")
                        fw.append((String) wktIndexed.get("index"))
                        fw.flush()
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    } finally {
                        if (fw != null) {
                            try {
                                fw.close()
                            } catch (Exception e) {
                                log.error(e.getMessage(), e)
                            }
                        }
                    }
                    //write wkt index a binary, include extents (minx, miny, maxx, maxy) and area (sq km)
                    int minPolygonNumber = 0
                    int maxPolygonNumber = 0

                    RandomAccessFile raf = null
                    try {
                        raf = new RandomAccessFile(filePath + File.separator + key + ".wkt.index.dat", "rw")

                        String[] index = ((String) wktIndexed.get("index")).split("\n")

                        for (int i = 0; i < index.length; i++) {
                            if (index[i].length() > 1) {
                                String[] cells = index[i].split(",")
                                int polygonNumber = Integer.parseInt(cells[0])
                                raf.writeInt(polygonNumber)   //polygon number
                                int polygonStart = Integer.parseInt(cells[1])
                                raf.writeInt(polygonStart)   //character offset

                                if (i == 0) {
                                    minPolygonNumber = polygonNumber
                                } else if (i == index.length - 1) {
                                    maxPolygonNumber = polygonNumber
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    } finally {
                        if (raf != null) {
                            try {
                                raf.close()
                            } catch (Exception e) {
                                log.error(e.getMessage(), e)
                            }
                        }
                    }

                    //for SLD
                    maxValues.add(gc.getMaxShapeIdx())
                    labels.add(name.replace("\"", "'"))
                    gc.setMinShapeIdx(minPolygonNumber)
                    gc.setMaxShapeIdx(maxPolygonNumber)

                    log.debug("getting multipolygon for " + filePath + " > " + key)
                    MultiPolygon mp = Envelope.getGridEnvelopeAsMultiPolygon(filePath + "," + key + "," + key)
                    gc.setBbox(mp.getEnvelope().toText().replace(" (", "(").replace(", ", ","))

                    classes.put(k, gc)

                    try {
                        //write class kml
                        zos = null
                        try {
                            zos = new ZipOutputStream(new FileOutputStream(filePath + File.separator + key + ".kml.zip"))

                            zos.putNextEntry(new ZipEntry(key + ".kml"))
                            Encoder encoder = new Encoder(new KMLConfiguration())
                            encoder.setIndenting(true)
                            encoder.encode(mp, KML.Geometry, zos)
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
                        }
                        log.debug("kml written to file")

                        final SimpleFeatureType TYPE = DataUtilities.createType("class", "the_geom:MultiPolygon,id:Integer,name:String")
                        FeatureJSON fjson = new FeatureJSON()
                        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE)
                        SimpleFeature sf = featureBuilder.buildFeature(null)

                        //write class geojson
                        zos = null
                        try {
                            zos = new ZipOutputStream(new FileOutputStream(filePath + File.separator + key + ".geojson.zip"))
                            zos.putNextEntry(new ZipEntry(key + ".geojson"))
                            featureBuilder.add(mp)
                            featureBuilder.add(k)
                            featureBuilder.add(name)

                            fjson.writeFeature(sf, zos)
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
                        }
                        log.debug("geojson written to file")

                        //write class shape file
                        File newFile = new File(filePath + File.separator + key + ".shp")
                        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory()
                        Map<String, Serializable> params = new HashMap<String, Serializable>()
                        params.put("url", newFile.toURI().toURL())
                        params.put("create spatial index", Boolean.FALSE)
                        ShapefileDataStore newDataStore = null
                        try {
                            newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params)
                            newDataStore.createSchema(TYPE)
                            newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84)
                            Transaction transaction = new DefaultTransaction("create")
                            String typeName = newDataStore.getTypeNames()[0]
                            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName)
                            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource
                            featureStore.setTransaction(transaction)
                            List<SimpleFeature> features = new ArrayList<SimpleFeature>()

                            DefaultFeatureCollection collection = new DefaultFeatureCollection()
                            collection.addAll(features)
                            featureStore.setTransaction(transaction)

                            features.add(sf)
                            featureStore.addFeatures(collection)
                            transaction.commit()
                            transaction.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        } finally {
                            if (newDataStore != null) {
                                try {
                                    newDataStore.dispose()
                                } catch (Exception e) {
                                    log.error(e.getMessage(), e)
                                }
                            }
                        }

                        zos = null
                        try {
                            zos = new ZipOutputStream(new FileOutputStream(filePath + File.separator + key + ".shp.zip"))
                            //add .dbf .shp .shx .prj
                            String[] exts = [".dbf", ".shp", ".shx", ".prj"]
                            for (String ext : exts) {
                                zos.putNextEntry(new ZipEntry(key + ext))
                                FileInputStream fis = null
                                try {
                                    fis = new FileInputStream(filePath + File.separator + key + ext)
                                    byte[] buffer = new byte[1024]
                                    int size
                                    while ((size = fis.read(buffer)) > 0) {
                                        zos.write(buffer, 0, size)
                                    }
                                } catch (Exception e) {
                                    log.error(e.getMessage(), e)
                                } finally {
                                    if (fis != null) {
                                        try {
                                            fis.close()
                                        } catch (Exception e) {
                                            log.error(e.getMessage(), e)
                                        }
                                    }
                                }
                                //remove unzipped files
                                new File(filePath + File.separator + key + ext).delete()
                            }
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
                        }
                        log.debug("shape file written to zip")
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }

            //write polygon mapping
            g.writeGrid(filePath + File.separator + "polygons", wktMap, g.xmin, g.ymin, g.xmax, g.ymax, g.xres, g.yres, g.nrows, g.ncols)

            //copy the header file to get it exactly the same, but change the data type
            copyHeaderAsInt(filePath + ".grd", filePath + File.separator + "polygons.grd")
        } else {
            //build classes without generating polygons
            List<float[]> info = new ArrayList<>(keys.size())
            for (int j = 0; j < keys.size(); j++) {
                while (info.size() <= keys.get(j)) {
                    info.add(null);
                }
                info.set(keys.get(j), new float[]{0, Float.NaN, Float.NaN, Float.NaN, Float.NaN})
            }

            g.getClassInfo(info)

            for (int j = 0; j < keys.size(); j++) {
                int k = keys.get(j)
                String key = String.valueOf(k)

                String name = p.getProperty(key)

                GridClass gc = new GridClass()
                gc.setName(name)
                gc.setId(k)

                //for SLD
                maxValues.add(Integer.valueOf(key))
                labels.add(name.replace("\"", "'"))
                gc.setMinShapeIdx(Integer.valueOf(key))
                gc.setMaxShapeIdx(Integer.valueOf(key))

                float[] stats = info[keys.get(j)]

                //only include if area > 0
                if (stats[0] > 0) {
                    gc.setBbox("POLYGON((" + stats[1] + " " + stats[2] + "," + stats[1] + " " + stats[4] + "," +
                            stats[3] + " " + stats[4] + "," + stats[3] + " " + stats[2] + "," +
                            stats[1] + " " + stats[2] + "))")

                    gc.setArea_km((double) stats[0])
                    classes.put(k, gc)
                }
            }
        }

        //write sld
        exportSLD(filePath + File.separator + "polygons.sld", new File(filePath + ".txt").getName(), maxValues, labels)

        writeProjectionFile(filePath + File.separator + "polygons.prj")

        //write .classes.json
        ObjectMapper mapper = new ObjectMapper()
        mapper.writeValue(new File(filePath + ".classes.json"), classes)

        return classes
    }

    static void exportSLD(String filename, String name, ArrayList<Integer> maxValues, ArrayList<String> labels) {
        StringBuffer sld = new StringBuffer()
        /* header */
        sld.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sld.append("<sld:StyledLayerDescriptor xmlns=\"http://www.opengis.net/sld\" xmlns:sld=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.0.0\">")
        sld.append("<sld:NamedLayer>")
        sld.append("<sld:Name>raster</sld:Name>")
        sld.append(" <sld:UserStyle>")
        sld.append("<sld:Name>raster</sld:Name>")
        sld.append("<sld:Title>A very simple color map</sld:Title>")
        sld.append("<sld:Abstract>A very basic color map</sld:Abstract>")
        sld.append("<sld:FeatureTypeStyle>")
        sld.append(" <sld:Name>name</sld:Name>")
        sld.append("<sld:FeatureTypeName>Feature</sld:FeatureTypeName>")
        sld.append(" <sld:Rule>")
        sld.append("   <sld:RasterSymbolizer>")
        sld.append(" <sld:Geometry>")
        sld.append(" <ogc:PropertyName>geom</ogc:PropertyName>")
        sld.append(" </sld:Geometry>")
        sld.append(" <sld:ChannelSelection>")
        sld.append(" <sld:GrayChannel>")
        sld.append("   <sld:SourceChannelName>1</sld:SourceChannelName>")
        sld.append(" </sld:GrayChannel>")
        sld.append(" </sld:ChannelSelection>")
        sld.append(" <sld:ColorMap type=\"intervals\">")

        //sort labels
        List<String> sortedLabels = new ArrayList<String>(labels)
        Collections.sort(sortedLabels)

        /* outputs */
        sld.append("\n<sld:ColorMapEntry color=\"#ffffff\" opacity=\"0\" quantity=\"1\"/>\n")
        for (int j = 0; j < sortedLabels.size(); j++) {
            int i = 0
            while (i < labels.size()) {
                if (labels.get(i) == sortedLabels.get(j))
                    break
                i++
            }
            sld.append("<sld:ColorMapEntry color=\"#" + getHexColour(colours[i % colours.length]) + "\" quantity=\"" + (maxValues.get(i) + 1) + ".0\" label=\"" + labels.get(i) + "\" opacity=\"1\"/>\r\n")
        }

        /* footer */
        sld.append("</sld:ColorMap></sld:RasterSymbolizer></sld:Rule></sld:FeatureTypeStyle></sld:UserStyle></sld:NamedLayer></sld:StyledLayerDescriptor>")

        /* write */
        FileWriter fw = null
        try {
            fw = new FileWriter(filename)
            fw.append(sld.toString())
            fw.flush()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (fw != null) {
                try {
                    fw.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    static String getHexColour(int colour) {
        String s = Integer.toHexString(colour)
        while (s.length() > 6) {
            s = s.substring(1)
        }
        while (s.length() < 6) {
            s = "0" + s
        }
        return s
    }

    private static void writeProjectionFile(String filename) {
        FileWriter spWriter = null
        try {
            spWriter = new FileWriter(filename)

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

            //spWriter.write("spname, longitude, latitude \n");
            spWriter.append(sbProjection.toString())
            spWriter.flush()

        } catch (IOException e) {
            log.error(e.getMessage(), e)
        } finally {
            if (spWriter != null) {
                try {
                    spWriter.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    private static void copyHeaderAsInt(String src, String dst) {
        BufferedReader br = null
        FileWriter fw = null
        try {
            br = new BufferedReader(new FileReader(src))
            fw = new FileWriter(dst)
            String line
            while ((line = br.readLine()) != null) {
                if (line.startsWith("DataType=")) {
                    fw.write("DataType=INT\n")
                } else {
                    fw.write(line)
                    fw.write("\n")
                }
            }
            fw.flush()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (br != null) {
                try {
                    br.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
            if (fw != null) {
                try {
                    fw.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }
}
