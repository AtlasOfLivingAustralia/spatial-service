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

import au.org.ala.spatial.Util
import groovy.util.logging.Slf4j

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat

import groovy.transform.CompileStatic
@Slf4j
class Bil2diva {

    static boolean bil2diva(String bilFilename, String divaFilename, String unitsString, String gdalDir, Integer timeout) {
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
                            unitsString, gdalDir, timeout)
                    //successful if there is at least 1 conversion
                    if (ret) success = true
                }
            }
            return success
        }

        log.debug("Running .bil to diva grid conversion for: " + bilFilename)
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

            log.debug("Reading .bil min and max values")
            double[] minmax = getMinMax(bilFile, gdalDir, timeout)

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

            log.debug("Creating diva grid file: " + divaFilename)

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

        log.debug(".bil to diva grid conversion complete")
        return ret
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

    static def getMinMax(File bil, gdalDir, timeout) {
        double[] minmax = new double[2]

        try {
            String[] cmd = [
                    gdalDir + "/gdalinfo",
                    "-mm",
                    bil.getPath()]

            StringBuffer sb = new StringBuffer();
            Util.runCmd(cmd, false, null, timeout, sb)

            String prefix = "Computed Min/Max="
            String end = "\n"
            String info = sb.toString()
            int start = info.indexOf(prefix)
            if (start > 0) {
                int endIdx = info.indexOf(end, start)
                String[] minMaxStr = info.substring(start + prefix.length(), endIdx).split(",")
                if (minMaxStr.length == 2) {
                    minmax[0] = Double.parseDouble(minMaxStr[0])
                    minmax[1] = Double.parseDouble(minMaxStr[1])
                }
            }
        } catch (IOException e) {
            log.error("failed to get min max values in bil file: " + bil.getPath(), e)
        }
        return minmax
    }
}

