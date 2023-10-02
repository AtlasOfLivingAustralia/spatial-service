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

import au.org.ala.spatial.intersect.IniReader
import groovy.util.logging.Slf4j

import java.nio.ByteBuffer
import java.nio.ByteOrder

//@CompileStatic
@Slf4j
class Diva2bil {


    static boolean diva2bil(String divaFilename, String bilFilename) {
        boolean ret = true

        FileInputStream fis = null
        FileOutputStream fos = null
        FileWriter fw = null

        try {
            File dataFile = new File(divaFilename + ".gri")

            IniReader ir = new IniReader(divaFilename + ".grd")

            double minx = ir.getDoubleValue("GeoReference", "MinX")
            double maxy = ir.getDoubleValue("GeoReference", "MaxY")
            double xdiv = ir.getDoubleValue("GeoReference", "ResolutionX")
            double ydiv = ir.getDoubleValue("GeoReference", "ResolutionY")
            double nodatavalue = -9999
            if (ir.valueExists("Data", "NoDataValue")) {
                nodatavalue = ir.getDoubleValue("Data", "NoDataValue")
            }
            int nrows = ir.getIntegerValue("GeoReference", "Rows")
            int ncols = ir.getIntegerValue("GeoReference", "Columns")
            String type = getType(ir.getStringValue("Data", "DataType"))
            int nbytes = getByteCount(type)

            fw = new FileWriter(bilFilename + ".hdr")
            fw.append("BYTEORDER      I\n")
            fw.append("LAYOUT         BIL\n")
            fw.append("NROWS      " + nrows + "\n")
            fw.append("NCOLS      " + ncols + "\n")
            fw.append("NBANDS      1\n")
            fw.append("NBITS      " + nbytes * 8 + "\n")
            fw.append("BANDROWBYTES      " + nbytes * ncols + "\n")
            fw.append("TOTALROWBYTES      " + nbytes * ncols + "\n")
            fw.append("PIXELTYPE      " + type + "\n")
            fw.append("ULXMAP      " + (minx + xdiv / 2.0) + "\n")
            fw.append("ULYMAP      " + (maxy - ydiv / 2.0) + "\n")
            fw.append("XDIM      " + xdiv + "\n")
            fw.append("YDIM      " + ydiv + "\n")
            fw.append("NODATA      " + nodatavalue + "\n")
            fw.flush()

            //copy gri to bil
            fis = new FileInputStream(dataFile)
            fos = new FileOutputStream(bilFilename + ".bil")
            byte[] buf = new byte[1024 * 1024]
            int len
            while ((len = fis.read(buf)) > 0) {
                fos.write(buf, 0, len)
            }
            fos.flush()

            log.debug("finished")
        } catch (Exception e) {
            ret = false
            log.error(e.getMessage(), e)
        } finally {
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
            if (fw != null) {
                try {
                    fw.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
        return ret
    }

    static String getType(String datatype) {
        datatype = datatype.toUpperCase()

        // Expected from grd file
        if (datatype == "INT1BYTE") {
            datatype = "BYTE"
        } else if (datatype == "INT2BYTES") {
            datatype = "SHORT"
        } else if (datatype == "INT4BYTES") {
            datatype = "INT"
        } else if (datatype == "INT8BYTES") {
            datatype = "LONG"
        } else if (datatype == "FLT4BYTES") {
            datatype = "FLOAT"
        } else if (datatype == "FLT8BYTES") {
            datatype = "DOUBLE"
        } // shorthand for same
        else if (datatype == "INT1B" || datatype == "BYTE") {
            datatype = "BYTE"
        } else if (datatype == "INT1U" || datatype == "UBYTE") {
            datatype = "UBYTE"
        } else if (datatype == "INT2B" || datatype == "INT16" || datatype == "INT2S") {
            datatype = "SHORT"
        } else if (datatype == "INT4B") {
            datatype = "INT"
        } else if (datatype == "INT8B" || datatype == "INT32") {
            datatype = "LONG"
        } else if (datatype == "FLT4B" || datatype == "FLOAT32" || datatype == "FLT4S") {
            datatype = "FLOAT"
        } else if (datatype == "FLT8B") {
            datatype = "DOUBLE"
        } // if you rather use Java keyworddatatype...
        else if (datatype == "BYTE") {
            datatype = "BYTE"
        } else if (datatype == "SHORT") {
            datatype = "SHORT"
        } else if (datatype == "INT") {
            datatype = "INT"
        } else if (datatype == "LONG") {
            datatype = "LONG"
        } else if (datatype == "FLOAT") {
            datatype = "FLOAT"
        } else if (datatype == "DOUBLE") {
            datatype = "DOUBLE"
        } // some backwards compatibility
        else if (datatype == "INTEGER") {
            datatype = "INT"
        } else if (datatype == "SMALLINT") {
            datatype = "INT"
        } else if (datatype == "SINGLE") {
            datatype = "FLOAT"
        } else if (datatype == "REAL") {
            datatype = "FLOAT"
        } else {
            log.debug("GRID unknown type: " + datatype)
            datatype = "UNKNOWN"
        }

        return datatype
    }

    static int getByteCount(String datatype) {
        int nbytes
        if (datatype == "BYTE" || datatype == "UBYTE") {
            nbytes = 1
        } else if (datatype == "SHORT") {
            nbytes = 2
        } else if (datatype == "INT") {
            nbytes = 4
        } else if (datatype == "LONG") {
            nbytes = 8
        } else if (datatype == "SINGLE") {
            nbytes = 4
        } else if (datatype == "FLOAT") {
            nbytes = 4
        } else if (datatype == "DOUBLE") {
            nbytes = 8
        } else {
            nbytes = 0
        }
        return nbytes
    }

    static double[] getMinMax(int nbits, String datatype, int nrows, int ncols, String byteOrder, double missingValue, File bilFile) {
        double[] minmax = new double[2]
        minmax[0] = Double.NaN
        minmax[1] = Double.NaN
        RandomAccessFile raf = null
        try {
            raf = new RandomAccessFile(bilFile, "r")
            byte[] b = new byte[(int) raf.length()]
            raf.read(b)
            ByteBuffer bb = ByteBuffer.wrap(b)

            if (byteOrder == null || byteOrder == "m") {
                bb.order(ByteOrder.BIG_ENDIAN)
            } else {
                bb.order(ByteOrder.LITTLE_ENDIAN)
            }

            int i
            int length = nrows * ncols
            if (datatype.equalsIgnoreCase("UBYTE")
                    || datatype.equalsIgnoreCase("INT1U")) {
                for (i = 0; i < length; i++) {
                    double ret = bb.get()
                    if (ret < 0) {
                        ret += 256
                    }
                    updateMinMax(minmax, ret, missingValue)
                }
            } else if (datatype.equalsIgnoreCase("BYTE")
                    || datatype.equalsIgnoreCase("INT1BYTE")
                    || datatype.equalsIgnoreCase("INT1B")) {

                for (i = 0; i < length; i++) {
                    updateMinMax(minmax, bb.get(), missingValue)
                }
            } else if (nbits == 16 /*datatype.equalsIgnoreCase("SHORT")
                    || datatype.equalsIgnoreCase("INT2BYTES")
                    || datatype.equalsIgnoreCase("INT2B")
                    || datatype.equalsIgnoreCase("INT16")
                    || datatype.equalsIgnoreCase("INT2S")*/) {
                for (i = 0; i < length; i++) {
                    updateMinMax(minmax, bb.getShort(), missingValue)
                }
            } else if (datatype.equalsIgnoreCase("INT")
                    || datatype.equalsIgnoreCase("INTEGER")
                    || datatype.equalsIgnoreCase("INT4BYTES")
                    || datatype.equalsIgnoreCase("INT4B")
                    || datatype.equalsIgnoreCase("INT32")
                    || datatype.equalsIgnoreCase("SMALLINT")) {
                for (i = 0; i < length; i++) {
                    updateMinMax(minmax, bb.getInt(), missingValue)
                }
            } else if (datatype.equalsIgnoreCase("LONG")
                    || datatype.equalsIgnoreCase("INT8BYTES")
                    || datatype.equalsIgnoreCase("INT8B")
                    || datatype.equalsIgnoreCase("INT64")) {
                for (i = 0; i < length; i++) {
                    updateMinMax(minmax, (double) bb.getLong(), missingValue)
                }
            } else if (datatype.equalsIgnoreCase("FLOAT")
                    || datatype.equalsIgnoreCase("FLT4BYTES")
                    || datatype.equalsIgnoreCase("FLT4B")
                    || datatype.equalsIgnoreCase("FLOAT32")
                    || datatype.equalsIgnoreCase("FLT4S")
                    || datatype.equalsIgnoreCase("REAL")
                    || datatype.equalsIgnoreCase("SINGLE")) {
                for (i = 0; i < length; i++) {
                    updateMinMax(minmax, bb.getFloat(), missingValue)
                }
            } else if (datatype.equalsIgnoreCase("DOUBLE")
                    || datatype.equalsIgnoreCase("FLT8BYTES")
                    || datatype.equalsIgnoreCase("FLT8B")
                    || datatype.equalsIgnoreCase("FLOAT64")
                    || datatype.equalsIgnoreCase("FLT8S")) {
                for (i = 0; i < length; i++) {
                    updateMinMax(minmax, bb.getDouble(), missingValue)
                }
            } else {
                log.error("UNKNOWN TYPE: " + datatype)
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        return minmax
    }

    static void updateMinMax(double[] minmax, double d, double missingValue) {
        if (d != missingValue) {
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
