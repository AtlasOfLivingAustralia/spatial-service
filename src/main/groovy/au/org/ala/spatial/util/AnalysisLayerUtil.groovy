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
package au.org.ala.spatial.util

import au.org.ala.spatial.grid.Bil2diva
import au.org.ala.spatial.grid.Diva2bil
import au.org.ala.spatial.intersect.Grid
import groovy.transform.CompileStatic

/**
 * @author Adam
 */

import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class AnalysisLayerUtil {

    static boolean diva2Analysis(String srcFilepath, String dstFilepath, Double resolution, String gdalPath, boolean force) {
        try {
            File sgrd = new File(srcFilepath + ".grd")
            File sgri = new File(srcFilepath + ".gri")
            File dgrd = new File(dstFilepath + ".grd")
            File dgri = new File(dstFilepath + ".gri")
            if (force || !dgrd.exists() || !dgri.exists()
                    || (dgrd.lastModified() < sgrd.lastModified() || dgri.lastModified() < sgri.lastModified())) {
                //get new extents
                Grid g = new Grid(srcFilepath)
                double minx = (g.xmin == ((int) (g.xmin / resolution)) * resolution) ? g.xmin : ((int) (g.xmin / resolution)) * resolution + resolution
                double maxx = (g.xmax == ((int) (g.xmax / resolution)) * resolution) ? g.xmax : ((int) (g.xmax / resolution)) * resolution
                double miny = (g.ymin == ((int) (g.ymin / resolution)) * resolution) ? g.ymin : ((int) (g.ymin / resolution)) * resolution + resolution
                double maxy = (g.ymax == ((int) (g.ymax / resolution)) * resolution) ? g.ymax : ((int) (g.ymax / resolution)) * resolution

                if (maxx < minx + 2 * resolution) maxx = minx + 2 * resolution
                if (maxy < miny + 2 * resolution) maxy = miny + 2 * resolution

                new File(new File(dstFilepath).getParent()).mkdirs()

                if (minx == g.xmin && miny == g.ymin
                        && maxx == g.xmax && maxy == g.ymax
                        && resolution == g.xres && resolution == g.yres) {
                    //copy to target dir if ok
                    fileCopy(srcFilepath + ".gri", dstFilepath + ".gri")
                    fileCopy(srcFilepath + ".grd", dstFilepath + ".grd")
                } else {
                    //diva 2 bil
                    File tmpBil = File.createTempFile("tmpbil", "")
                    if (!Diva2bil.diva2bil(srcFilepath, tmpBil.getPath())) {
                        return false
                    }

                    //gdalwarp bil to target resolution
                    File tmpxBil = File.createTempFile("tmpxbil", "")
                    if (!gdal_warp(gdalPath, tmpBil.getPath() + ".bil", tmpxBil.getPath() + ".bil", resolution, minx, miny, maxx, maxy, g.nodatavalue)) {
                        return false
                    }

                    //bil 2 diva
                    if (!Bil2diva.bil2diva(tmpxBil.getPath(), dstFilepath, "")) {
                        return false
                    }

                    //cleanup
                    //tmpbil, tmpbil + .bil, tmpbil + .hdr
                    //tmpxbil, tmpxbil + .bil, tmpxbil + .hdr
                    deleteFiles(new String[]{tmpBil.getPath(), tmpBil.getPath() + ".bil", tmpBil.getPath() + ".hdr",
                            tmpxBil.getPath(), tmpxBil.getPath() + ".bil", tmpxBil.getPath() + ".hdr", tmpxBil.getPath() + ".bil.aux.xml"})
                }
            }

            return true
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
        return false
    }

    private static boolean gdal_warp(String gdalPath, String srcFilename, String dstFilename, double resolution, double minx, double miny, double maxx, double maxy, double nodatavalue) {
        Runtime runtime = Runtime.getRuntime()
        try {

            log.info("Got gdal_path: " + gdalPath)

            //gdalwarp -te 109.51 -44.37 157.28 -8.19 -tr 0.01 -0.01
            //-s_srs '" + edlconfig.s_srs + "' -t_srs '" + edlconfig.t_srs + "'
            //-of EHdr -srcnodata -9999 -dstnodata -9999
            String base_command = gdalPath + File.separator + "gdalwarp -r cubicspline -te " + minx + " " + miny + " " + maxx + " " + maxy+ " -dstnodata " + String.valueOf(nodatavalue)+ " -tr " + resolution + " " + resolution + " -of EHdr "

            String command = base_command + srcFilename + " " + dstFilename

            log.info("Exec'ing " + command)
            Process proc = runtime.exec(command)

            log.info("Setting up output stream readers")
            InputStreamReader isr = new InputStreamReader(proc.getInputStream())
            InputStreamReader eisr = new InputStreamReader(proc.getErrorStream())
            BufferedReader br = new BufferedReader(isr)
            BufferedReader ebr = new BufferedReader(eisr)
            String line

            log.info(String.format("Output of running %s is:", command))

            while ((line = br.readLine()) != null) {
                log.info(line)
            }

            while ((line = ebr.readLine()) != null) {
                log.info(line)
            }

            int exitVal = proc.waitFor()

            log.info(exitVal as String)

            if (exitVal == 0) {
                return true
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
        return false
    }

    private static void deleteFiles(String[] filesToDelete) {
        for (String s : filesToDelete) {
            try {
                new File(s).delete()
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            }
        }
    }

    private static void fileCopy(String src, String dst) {
        FileInputStream fis = null
        FileOutputStream fos = null
        try {
            fis = new FileInputStream(src)
            fos = new FileOutputStream(dst)
            byte[] buf = new byte[1024 * 1024]
            int len
            while ((len = fis.read(buf)) > 0) {
                fos.write(buf, 0, len)
            }
            fos.flush()
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
            if (fos != null) {
                try {
                    fos.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }


    static boolean gdal_rasterize(String gdalPath, String srcFilename, String dstFilename, double resolution, double minx, double miny, double maxx, double maxy) {
        Runtime runtime = Runtime.getRuntime()
        try {

            log.info("Got gdal_path: " + gdalPath)

            String layername = new File(srcFilename).getName().replace(".shp", "")

            //gdal_rasterize -ot Int16 -of EHdr -l aus1 -tr 0.01 0.01
            String base_command = gdalPath + File.separator + "gdal_rasterize -ot Int16 -of EHdr"+ " -te " + minx + " " + miny + " " + maxx + " " + maxy+ " -l " + layername+ " -a id "+ " -tr " + resolution + " " + resolution + " "

            String command = base_command + srcFilename + " " + dstFilename

            log.info("Exec'ing " + command)
            Process proc = runtime.exec(command)

            log.info("Setting up output stream readers")
            InputStreamReader isr = new InputStreamReader(proc.getInputStream())
            InputStreamReader eisr = new InputStreamReader(proc.getErrorStream())
            BufferedReader br = new BufferedReader(isr)
            BufferedReader ebr = new BufferedReader(eisr)
            String line

            log.info(String.format("Output of running %s is:", command))

            while ((line = br.readLine()) != null) {
                log.info(line)
            }

            while ((line = ebr.readLine()) != null) {
                log.info(line)
            }

            int exitVal = proc.waitFor()

            log.info(exitVal as String)

            if (exitVal == 0) {
                return true
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
        return false
    }

}
