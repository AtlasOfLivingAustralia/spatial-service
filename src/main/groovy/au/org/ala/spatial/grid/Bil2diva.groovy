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

import groovy.util.logging.Slf4j

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat

import groovy.transform.CompileStatic
//@CompileStatic
@Slf4j
class Bil2diva {

    static boolean bil2diva(String bilFilename, String divaFilename, String unitsString) {
        //if input is directory, bil2div on all .bil files in the dir
        File dir = new File(bilFilename)
        if (dir.isDirectory()) {
            new File(divaFilename).mkdirs()

            boolean success = false
            for (File f : dir.listFiles()) {
                if (f.getName().toLowerCase().endsWith(".bil")) {
                    String name = f.getName().substring(0, f.getName().length() - 4)
                    boolean ret = bil2diva(bilFilename + File.separator + name,
                            divaFilename + File.separator + name,
                            unitsString)
                    //successful if there is at least 1 conversion
                    if (ret) success = true
                }
            }
            return success
        }

        log.info("Running .bil to diva grid conversion for: " + bilFilename)
        boolean ret = true
        BufferedReader br = null
        FileWriter fw = null
        FileInputStream fis = null
        FileOutputStream fos = null
        try {
            File headerFile = new File(bilFilename + ".hdr")
            File bilFile = new File(bilFilename + ".bil")

            String line
            br = new BufferedReader(new FileReader(headerFile))
            HashMap<String, String> map = new HashMap<String, String>()
            while ((line = br.readLine()) != null) {
                int p = line.indexOf(" ")
                if (p < 0) {
                    p = line.indexOf("\t")
                }
                if (p > 0) {
                    map.put(line.substring(0, p).trim().toLowerCase(), line.substring(p).trim().toLowerCase())
                }
            }

            fw = new FileWriter(divaFilename + ".grd")

            fw.write("[General]\n")
            fw.write("Creator=Bil2diva\n")

            SimpleDateFormat sdf = new SimpleDateFormat("yyyymmdd")
            fw.write("Created=" + sdf.format(new Date()) + "\n")

            fw.write("Title=" + headerFile.getName().replace(".hdr", "") + "\n")

            fw.write("[GeoReference]\n")
            fw.write("Projection=GEOGRAPHIC\n")
            fw.write("Datum=WGS84\n")
            fw.write("Mapunits=DEGREES\n")

            int ncols = Integer.parseInt(map.get("ncols"))
            int nrows = Integer.parseInt(map.get("nrows"))
            double minx = Double.parseDouble(map.get("ulxmap"))
            double maxy = Double.parseDouble(map.get("ulymap"))
            double divx = Double.parseDouble(map.get("xdim"))
            double divy = Double.parseDouble(map.get("ydim"))

            fw.write("Columns=" + ncols + "\n")
            fw.write("Rows=" + nrows + "\n")
            fw.write("MinX=" + (float) (minx - divx / 2.0) + "\n")
            fw.write("MaxX=" + (float) (minx + ncols * divx - divx / 2.0) + "\n")
            fw.write("MinY=" + (float) (maxy - nrows * divy + divy / 2.0) + "\n")
            fw.write("MaxY=" + (float) (maxy + divy / 2.0) + "\n")
            fw.write("ResolutionX=" + map.get("xdim") + "\n")
            fw.write("ResolutionY=" + map.get("ydim") + "\n")

            int nbits = Integer.parseInt(map.get("nbits"))

            String pixelType = map.get("pixeltype")
            if (pixelType != null) {
                pixelType = pixelType.toUpperCase()
            }
            //if (pixelType == null) {
            if (nbits == 8) {
                if (pixelType != null && pixelType.contains("U")) {
                    pixelType = "UBYTE"
                } else {
                    pixelType = "BYTE"
                }
            } else if (nbits == 16) {
                pixelType = "SHORT"
            } else if (nbits == 32) {
                if (pixelType != null
                        && (pixelType.contains("F")
                        || pixelType == "REAL"
                        || pixelType == "SINGLE")) {
                    pixelType = "FLOAT"
                } else {
                    pixelType = "INT"
                }
            } else if (nbits == 64) {
                if (pixelType != null
                        && (pixelType.contains("D")
                        || pixelType.contains("F"))) {
                    pixelType = "DOUBLE"
                } else {
                    pixelType = "LONG"
                }
            }
            // }
            fw.write("[Data]\n")
            fw.write("DataType=" + pixelType.toUpperCase() + "\n")

            String byteOrder = map.get("byteorder")
            if (byteOrder == null || byteOrder == "m") {
                fw.write("ByteOrder=MSB\n")
            }

            String noDataValueString = map.get("nodata")

            double missingValue
            if (noDataValueString == null) {
                missingValue = Double.MAX_VALUE * -1
            } else {
                missingValue = Double.parseDouble(noDataValueString)
            }

            log.info("Reading .bil min and max values")
            double[] minmax = getMinMax(nbits, pixelType, nrows, ncols, byteOrder, missingValue, bilFile)

            //If no nodata value was supplied, use the minimum value - 1.
            if (noDataValueString == null) {
                noDataValueString = Double.toString(minmax[0] - 1)
            }

            fw.write("NoDataValue=" + noDataValueString + "\n")

            fw.write("MinValue=" + minmax[0] + "\n")
            fw.write("MaxValue=" + minmax[1] + "\n")

            fw.write("Transparent=0\n")

            String units = unitsString
            fw.write("Units=" + units + "\n")

            fw.flush()

            log.info("Creating diva grid file: " + divaFilename)

            //copy bil to gri
            fis = new FileInputStream(bilFile)
            fos = new FileOutputStream(divaFilename + ".gri")
            byte[] buf = new byte[1024 * 1024]
            int len
            while ((len = fis.read(buf)) > 0) {
                fos.write(buf, 0, len)
            }
            fos.flush()
        } catch (Exception e) {
            ret = false
            log.error(e.getMessage(), e)
        } finally {
            if (fw != null) {
                try {
                    fw.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
            if (br != null) {
                try {
                    br.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
            if (fos != null) {
                try {
                    fos.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }

        log.info(".bil to diva grid conversion complete")
        return ret
    }

    static double[] getMinMax(int nbits, String datatype, int nrows, int ncols, String strByteOrder, double missingValue, File bilFile) {
        double[] minmax = new double[2]
        minmax[0] = Double.NaN
        minmax[1] = Double.NaN
        try {
            RandomAccessFile raf = new RandomAccessFile(bilFile, "r")
            FileChannel channel = raf.getChannel()

            ByteOrder byteOrder = ByteOrder.BIG_ENDIAN

            if (strByteOrder == null || strByteOrder == "m") {
                byteOrder = ByteOrder.BIG_ENDIAN
            } else {
                byteOrder = ByteOrder.LITTLE_ENDIAN
            }

            ByteBuffer byteBuffer


            while (channel.position() < channel.size()) {
                long bytesLeft = channel.size() - channel.position()
                if (bytesLeft < 1024) {
                    byteBuffer = ByteBuffer.allocate((int) bytesLeft)
                } else {
                    byteBuffer = ByteBuffer.allocate(1024)
                }

                channel.read(byteBuffer)

                byteBuffer.order(byteOrder)
                byteBuffer.position(0)

                while (byteBuffer.hasRemaining()) {
                    if (datatype.equalsIgnoreCase("UBYTE")
                            || datatype.equalsIgnoreCase("INT1U")) {

                        double ret = byteBuffer.get()
                        if (ret < 0) {
                            ret += 256
                        }
                        updateMinMax(minmax, ret, missingValue)

                    } else if (datatype.equalsIgnoreCase("BYTE")
                            || datatype.equalsIgnoreCase("INT1BYTE")
                            || datatype.equalsIgnoreCase("INT1B")) {


                        updateMinMax(minmax, byteBuffer.get(), missingValue)
                    } else if (nbits == 16 /*datatype.equalsIgnoreCase("SHORT")
                    || datatype.equalsIgnoreCase("INT2BYTES")
                    || datatype.equalsIgnoreCase("INT2B")
                    || datatype.equalsIgnoreCase("INT16")
                    || datatype.equalsIgnoreCase("INT2S")*/) {

                        updateMinMax(minmax, byteBuffer.getShort(), missingValue)

                    } else if (datatype.equalsIgnoreCase("INT")
                            || datatype.equalsIgnoreCase("INTEGER")
                            || datatype.equalsIgnoreCase("INT4BYTES")
                            || datatype.equalsIgnoreCase("INT4B")
                            || datatype.equalsIgnoreCase("INT32")
                            || datatype.equalsIgnoreCase("SMALLINT")) {

                        updateMinMax(minmax, byteBuffer.getInt(), missingValue)

                    } else if (datatype.equalsIgnoreCase("LONG")
                            || datatype.equalsIgnoreCase("INT8BYTES")
                            || datatype.equalsIgnoreCase("INT8B")
                            || datatype.equalsIgnoreCase("INT64")) {

                        updateMinMax(minmax, (double) byteBuffer.getLong(), missingValue)

                    } else if (datatype.equalsIgnoreCase("FLOAT")
                            || datatype.equalsIgnoreCase("FLT4BYTES")
                            || datatype.equalsIgnoreCase("FLT4B")
                            || datatype.equalsIgnoreCase("FLOAT32")
                            || datatype.equalsIgnoreCase("FLT4S")
                            || datatype.equalsIgnoreCase("REAL")
                            || datatype.equalsIgnoreCase("SINGLE")) {

                        updateMinMax(minmax, byteBuffer.getFloat(), missingValue)

                    } else if (datatype.equalsIgnoreCase("DOUBLE")
                            || datatype.equalsIgnoreCase("FLT8BYTES")
                            || datatype.equalsIgnoreCase("FLT8B")
                            || datatype.equalsIgnoreCase("FLOAT64")
                            || datatype.equalsIgnoreCase("FLT8S")) {

                        updateMinMax(minmax, byteBuffer.getDouble(), missingValue)

                    } else {
                        log.info("UNKNOWN TYPE: " + datatype)
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        return minmax
    }

    static void updateMinMax(double[] minmax, double d, double missingValue) {
        if ((float) d != (float) missingValue) {
            if (Double.isNaN(minmax[0])) {
                minmax[0] = d
                minmax[1] = d
            } else {
                if (minmax[0] > d) {
                    minmax[0] = d
                }
                if (minmax[1] < d) {
                    minmax[1] = d
                }
            }
        }
    }
}

