/**
 * ************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * *************************************************************************
 */
package au.org.ala.spatial


import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.intersect.SimpleRegion
import au.org.ala.spatial.intersect.SimpleShapeFile
import au.org.ala.spatial.dto.LayerFilter

/**
 * Class for region cutting test data grids
 *
 * @author adam
 */

import groovy.util.logging.Slf4j
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel

//@CompileStatic
@Slf4j
class GridCutterService {

    SpatialConfig spatialConfig
    LayerService layerService
    FieldService fieldService
    SpatialObjectsService spatialObjectsService

    /**
     * exports a list of layers cut against a region
     * <p/>
     * Cut layer files generated are input layers with grid cells outside of
     * region set as missing.
     *
     * @param layers list of layer fieldIds to be cut as String[].
     * @param resolution target resolution as String
     * @param region null or region to cut against as SimpleRegion. Cannot be
     *                        used with envelopes.
     * @param envelopes nul or region to cut against as LayerFilter[]. Cannot be
     *                        used with region.
     * @param extentsFilename output filename and path for writing output
     *                        extents.
     * @return directory containing the cut grid files.
     */
    String cut2(String[] layers, String resolution, SimpleRegion region, LayerFilter[] envelopes, String extentsFilename) {
        String[] layerTypes = new String[layers.length]
        String[] fieldIds = new String[layers.length]
        for (int i = 0; i < layers.length; i++) {
            IntersectionFile f = layerService.getIntersectionFile(layers[i])

            fieldIds[i] = f != null ? f.getFieldId() : null
            layerTypes[i] = fieldService.getFieldById(layers[i]).getType()
        }

        return cut2(layers, resolution, region, envelopes, extentsFilename, layerTypes, fieldIds)
    }

    String cut2(String[] layers, String resolution, SimpleRegion region, LayerFilter[] envelopes, String extentsFilename, String[] layerTypes, String[] fieldIds) {
        //check if resolution needs changing
        resolution = confirmResolution(layers, resolution, fieldIds)

        //get extents for all layers
        double[][] extents = getLayerExtents(resolution, layers[0], layerTypes[0], fieldIds[0])
        for (int i = 1; i < layers.length; i++) {
            extents = internalExtents(extents, getLayerExtents(resolution, layers[i], layerTypes[i], fieldIds[i]))
            if (!isValidExtents(extents)) {
                return null
            }
        }
        //do extents check for contextual envelopes as well
        if (envelopes != null) {
            extents = internalExtents(extents, getLayerFilterExtents(envelopes, fieldIds))
            if (!isValidExtents(extents)) {
                return null
            }
        }

        //get mask and adjust extents for filter
        byte[][] mask
        int w = 0, h = 0
        double res = Double.parseDouble(resolution)
        if (region != null) {
            extents = internalExtents(extents, region.getBoundingBox())

            if (!isValidExtents(extents)) {
                return null
            }

            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
            mask = getRegionMask(res, extents, w, h, region)
        } else if (envelopes != null) {
            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
            mask = getEnvelopeMaskAndUpdateExtents(resolution, res, extents, h, w, envelopes, layerTypes, fieldIds)
            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
        } else {
            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
            mask = getMask(res, extents, w, h)
        }

        //mkdir in index location
        String newPath = null
        try {
            //newPath = layerService.getAnalysisTmpLayerFilesPath() + File.separator + System.currentTimeMillis() + File.separator
            File directory = new File(newPath)
            directory.mkdir()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        //apply mask
        for (int i = 0; i < layers.length; i++) {
            applyMask(newPath, resolution, extents, w, h, mask, layers[i], fieldIds[i])
        }

        //write extents file
        writeExtents(extentsFilename, extents, w, h)

        return newPath
    }

    double[][] internalExtents(double[][] e1, double[][] e2) {
        double[][] internalExtents = new double[2][2]

        internalExtents[0][0] = Math.max(e1[0][0], e2[0][0])
        internalExtents[0][1] = Math.max(e1[0][1], e2[0][1])
        internalExtents[1][0] = Math.min(e1[1][0], e2[1][0])
        internalExtents[1][1] = Math.min(e1[1][1], e2[1][1])

        return internalExtents
    }

    boolean isValidExtents(double[][] e) {
        return e[0][0] < e[1][0] && e[0][1] < e[1][1]
    }

    double[][] getLayerExtents(String resolution, String layer, String layerType, String fieldId) {
        double[][] extents = new double[2][2]

        if (getLayerPath(resolution, layer, fieldId) == null
                && "c".equalsIgnoreCase(layerType)) {
            //use world extents here, remember to do object extents later.
            extents[0][0] = -180
            extents[0][1] = -90
            extents[1][0] = 180
            extents[1][1] = 90

        } else {
            Grid g = Grid.getGrid(getLayerPath(resolution, layer, fieldId))

            extents[0][0] = g.xmin
            extents[0][1] = g.ymin
            extents[1][0] = g.xmax
            extents[1][1] = g.ymax
        }

        return extents
    }

    String getLayerPath(String resolution, IntersectionFile f, String layer) {
        String field = f != null ? f.getFieldId() : null

        return getLayerPath(resolution, layer, field)
    }

    String standardFile(File file, String resolution, String field) {
        //move up a resolution when the file does not exist at the target resolution
        try {
            while (!file.exists()) {
                TreeMap<Double, String> resolutionDirs = new TreeMap<Double, String>()
                for (File dir : new File(spatialConfig.data.dir + File.separator + 'standard_layer').listFiles()) {
                    if (dir.isDirectory()) {
                        try {
                            resolutionDirs.put(Double.parseDouble(dir.getName()), dir.getName())
                        } catch (Exception ignored) {
                        }
                    }
                }

                String newResolution = resolutionDirs.higherEntry(Double.parseDouble(resolution)).getValue()

                if (newResolution == resolution) {
                    break
                } else {
                    resolution = newResolution
                    file = new File(spatialConfig.data.dir + File.separator + 'standard_layer' + File.separator + resolution + File.separator + field + ".grd")
                }
            }
        } catch (Exception ignored) {
        }
        resolution
    }

    String getLayerPath(String resolution, String layer, String field) {
        File file = new File(spatialConfig.data.dir + File.separator + 'standard_layer' + File.separator + resolution + File.separator + field + ".grd")

        resolution = standardFile(file, resolution, field)

        String layerPath = spatialConfig.data.dir + File.separator + 'standard_layer' + File.separator + resolution + File.separator + field

        if (new File(layerPath + ".grd").exists()) {
            return layerPath
        } else {
            //look for an analysis layer
            String[] info = layerService.getAnalysisLayerInfo(layer)
            if (info != null) {
                return info[1]
            } else {
                log.info("getLayerPath, cannot find for: " + layer + ", " + resolution)
                return null
            }
        }
    }

     boolean existsLayerPath(String resolution, IntersectionFile f, String layer, boolean do_not_lower_resolution) {
        String field = f != null ? f.getFieldId() : null

        return existsLayerPath(resolution, layer, do_not_lower_resolution, field)
    }

    boolean existsLayerPath(String resolution, String layer, boolean do_not_lower_resolution, String field) {
        File file = new File(spatialConfig.data.dir + File.separator + 'standard_layer' + File.separator + resolution + File.separator + field + ".grd")

        //move up a resolution when the file does not exist at the target resolution
        if (!do_not_lower_resolution) {
            resolution = standardFile(file, resolution, field)
        }

        String layerPath = spatialConfig.data.dir + File.separator + 'standard_layer' + File.separator + resolution + File.separator + field

        if (new File(layerPath + ".grd").exists()) {
            return true
        } else {
            //look for an analysis layer
            String[] info = layerService.getAnalysisLayerInfo(layer)
            return info != null
        }
    }

     void applyMask(String dir, String resolution, double[][] extents, int w, int h, byte[][] mask, String layer, String fieldId) {
        //layer output container
        double[] dfiltered = new double[w * h]

        //open grid and get all data
        Grid grid = Grid.getGrid(getLayerPath(resolution, layer, fieldId))
        float[] d = grid.getGrid() //get whole layer

        //set all as missing values
        for (int i = 0; i < dfiltered.length; i++) {
            dfiltered[i] = Double.NaN
        }

        double res = Double.parseDouble(resolution)

        for (int i = 0; i < mask.length; i++) {
            for (int j = 0; j < mask[0].length; j++) {
                if (mask[i][j] > 0) {
                    dfiltered[j + (h - i - 1) * w] = grid.getValues3([[j * res + extents[0][0], i * res + extents[0][1]]] as double[][], 40960)[0]
                }
            }
        }

        grid.writeGrid(dir + layer, dfiltered,
                extents[0][0],
                extents[0][1],
                extents[1][0],
                extents[1][1],
                res, res, h, w)
    }

     void writeExtents(String filename, double[][] extents, int w, int h) {
        if (filename != null) {
            FileWriter fw = null
            try {
                fw = new FileWriter(filename)
                fw.append(String.valueOf(w)).append("\n")
                fw.append(String.valueOf(h)).append("\n")
                fw.append(String.valueOf(extents[0][0])).append("\n")
                fw.append(String.valueOf(extents[0][1])).append("\n")
                fw.append(String.valueOf(extents[1][0])).append("\n")
                fw.append(String.valueOf(extents[1][1]))
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
    }

    /**
     * Get a region mask.
     * <p/>
     * Note: using decimal degree grid, probably should be EPSG900913 grid.
     *
     * @param res resolution as double
     * @param extents extents as double[][] with [0][0]=xmin, [0][1]=ymin,
     *                [1][0]=xmax, [1][1]=ymax.
     * @param h height as int.
     * @param w width as int.
     * @param region area for the mask as SimpleRegion.
     * @return
     */
      byte[][] getRegionMask(double res, double[][] extents, int w, int h, SimpleRegion region) {
        byte[][] mask = new byte[h][w]

        //can also use region.getOverlapGridCells_EPSG900913
        region.getOverlapGridCells(extents[0][0], extents[0][1], extents[1][0], extents[1][1], w, h, mask)
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                //double tx = (j + 0.5) * res + extents[0][0];
                //double ty = (i + 0.5) * res + extents[0][1];
                //if (region.isWithin_EPSG900913(tx, ty)) {
                //    mask[i][j] = 1;
                //}
                if (mask[i][j] > 0) {
                    mask[i][j] = 1
                }
            }
        }
        return mask
    }

      byte[][] getMask(double res, double[][] extents, int w, int h) {
        byte[][] mask = new byte[h][w]
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                mask[i][j] = 1
            }
        }
        return mask
    }

    /**
     * Get a mask, 0=absence, 1=presence, for a given envelope and extents.
     *
     * @param resolution resolution as String.
     * @param res resultions as double.
     * @param extents extents as double[][] with [0][0]=xmin, [0][1]=ymin,
     *                   [1][0]=xmax, [1][1]=ymax.
     * @param h height as int.
     * @param w width as int.
     * @param envelopes
     * @return mask as byte[][]
     */
      byte[][] getEnvelopeMaskAndUpdateExtents(String resolution, double res, double[][] extents, int h, int w, LayerFilter[] envelopes, String[] layerTypes, String[] fieldIds) {
        byte[][] mask = new byte[h][w]

        double[][] points = new double[h * w][2]
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                points[i + j * w][0] = extents[0][0] + (i + 0.5) * res
                points[i + j * w][1] = extents[0][1] + (j + 0.5) * res
                //mask[j][i] = 0;
            }
        }

          GeometryFactory gf = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326)

        for (int k = 0; k < envelopes.length; k++) {
            LayerFilter lf = envelopes[k]

            // if it is contextual and a grid file does not exist at the requested resolution
            // and it is not a grid processed as a shape file,
            // then get the shape file to do the intersection
            if (existsLayerPath(resolution, lf.getLayername(), true, fieldIds[k]) && lf.isContextual()
                    && "c".equalsIgnoreCase(layerTypes[k])) {

                String[] ids = lf.getIds()
                Geometry[] srs = new Geometry[ids.length]

                for (int i = 0; i < ids.length; i++) {
                    srs[i] = SpatialObjects.findByPid(ids[i]).geometry

                }
                for (int i = 0; i < points.length; i++) {
                    for (int j = 0; j < srs.length; j++) {
                        if (srs[j].contains(gf.createPoint(new Coordinate(points[i][0], points[i][1])))) {
                            mask[(int) (i / w)][i % w]++
                            break
                        }
                    }
                }
            } else {
                Grid grid = Grid.getGrid(getLayerPath(resolution, lf.getLayername(), fieldIds[k]))

                float[] d = grid.getValues3(points, 40960)

                for (int i = 0; i < d.length; i++) {
                    if (lf.isValid(d[i])) {
                        mask[(int)(i / w)][i % w]++
                    }
                }
            }
        }

        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (mask[j][i] == envelopes.length) {
                    mask[j][i] = 1
                } else {
                    mask[j][i] = 0
                }
            }
        }

        //find internal extents
        int minx = w
        int maxx = -1
        int miny = h
        int maxy = -1
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (mask[j][i] > 0) {
                    if (minx > i) {
                        minx = i
                    }
                    if (maxx < i) {
                        maxx = i
                    }
                    if (miny > j) {
                        miny = j
                    }
                    if (maxy < j) {
                        maxy = j
                    }
                }
            }
        }

        //reduce the size of the mask
        int nw = maxx - minx + 1
        int nh = maxy - miny + 1
        byte[][] smallerMask = new byte[nh][nw]
        for (int i = minx; i < maxx; i++) {
            for (int j = miny; j < maxy; j++) {
                smallerMask[j - miny][i - minx] = mask[j][i]
            }
        }

        //fix y offset
        miny += 1
        maxy += 1

        //update extents, must never be larger than the original extents (res is not negative, minx maxx miny mazy are not negative and < w & h respectively
        extents[0][0] = Math.max(extents[0][0] + minx * res, extents[0][0]) //min x value
        extents[1][0] = Math.min(extents[1][0] - (w - maxx - 1) * res, extents[1][0]) //max x value
        extents[0][1] = Math.max(extents[0][1] + miny * res, extents[0][1]) //min y value
        extents[1][1] = Math.min(extents[1][1] - (h - maxy - 1) * res, extents[1][1]) //max y value

        return smallerMask
    }

    /**
     * Write a diva grid to disk for the envelope, 0 = absence, 1 = presence.
     *
     * @param filename output filename for the grid as String.
     * @param resolution target resolution in decimal degrees as String.
     * @param envelopes envelope specification as LayerFilter[].
     * @return area in sq km as double.
     */
     double makeEnvelope(String filename, String resolution, Fields[] fields, IntersectionFile[] intersectionFiles, LayerFilter[] envelopes, long maxGridCount) {
        String[] layerTypes = new String[envelopes.length]
        String[] fieldIds = new String[envelopes.length]
        for (int i = 0; i < envelopes.length; i++) {
            IntersectionFile f = intersectionFiles[i]

            fieldIds[i] = f != null ? f.getFieldId() : null
            layerTypes[i] = fields[i].getType()
        }

        return makeEnvelope(filename, resolution, envelopes, maxGridCount, layerTypes, fieldIds)
    }

     double makeEnvelope(String filename, String resolution, LayerFilter[] envelopes, long maxGridCount, String[] layerTypes, String[] fieldIds) {

        //get extents for all layers
        double[][] extents = getLayerExtents(resolution, envelopes[0].getLayername(), layerTypes[0], fieldIds[0])
        for (int i = 1; i < envelopes.length; i++) {
            extents = internalExtents(extents, getLayerExtents(resolution, envelopes[i].getLayername(), layerTypes[i], fieldIds[i]))
            if (!isValidExtents(extents)) {
                return -1
            }
        }
        //do extents check for contextual envelopes as well
        extents = internalExtents(extents, getLayerFilterExtents(envelopes, fieldIds))
        if (!isValidExtents(extents)) {
            return -1
        }

        double res = Double.parseDouble(resolution)

        //limit the size of the grid files that can be generated
        while ((Math.abs(extents[0][1] - extents[1][0]) / res) * (Math.abs(extents[0][0] - extents[1][0]) / res) > maxGridCount * 2.0) {
            res = res * 2
        }
        if (res != Double.parseDouble(resolution)) {
            resolution = String.format("%f", res)
        }

        //get mask and adjust extents for filter
        byte[][] mask
        int w, h
        h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
        w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
        mask = getEnvelopeMaskAndUpdateExtents(resolution, res, extents, h, w, envelopes, layerTypes, fieldIds)
        h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
        if (((int) Math.ceil((extents[1][1] + res - extents[0][1]) / res)) == h) {
            extents[1][1] += res
        }
        w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
        if (((int) Math.ceil((extents[1][0] + res - extents[0][0]) / res)) == w) {
            extents[1][0] += res
        }

        float[] values = new float[w * h]
        int pos = 0
        double areaSqKm = 0
        for (int i = h - 1; i >= 0; i--) {
            for (int j = 0; j < w; j++) {
                if (i < mask.length && j < mask[i].length) {
                    values[pos] = mask[i][j]

                    if (mask[i][j] > 0) {
                        areaSqKm += SpatialUtils.cellArea(res, extents[0][1] + res * i)
                    }
                } else {
                    values[pos] = 0
                }
                pos++
            }
        }

        Grid grid = new Grid(getLayerPath(resolution, envelopes[0].getLayername(), fieldIds[0]))

        grid.writeGrid(filename, values,
                extents[0][0],
                extents[0][1],
                extents[1][0],
                extents[1][1],
                res, res, h, w)

        return areaSqKm
    }

      double[][] getLayerFilterExtents(LayerFilter[] envelopes, String[] layerTypes) {

        double[][] extents = [[-180, -90], [180, 90]]
        for (int i = 0; i < envelopes.length; i++) {
            if ("c".equalsIgnoreCase(layerTypes[i])) {
                String[] ids = envelopes[i].getIds()
                for (int j = 0; j < ids.length; j++) {
                    try {
                        double[][] bbox = SimpleShapeFile.parseWKT(spatialObjectsService.getObjectByPid(ids[j]).bbox).getBoundingBox()
                        extents = internalExtents(extents, bbox)
                    } catch (Exception e) {
                        //Expecting this to fail often!
                        log.error(e.getMessage(), e)
                    }
                }
            }
        }
        return extents
    }


    /**
     * Test if the layer filter is valid.
     * <p/>
     * The common problem is that a filter may refer to a layer that is not
     * available.
     *
     * @param resolution target resolution as String.
     * @param filter layer filter as LayerFilter[].
     * @return true iff valid filter.
     */
     boolean isValidLayerFilter(String resolution, Fields[] fields, IntersectionFile[] intersectionFiles, LayerFilter[] filter) {
        String[] layerTypes = new String[filter.length]
        String[] fieldIds = new String[filter.length]
        for (int i = 0; i < filter.length; i++) {
            IntersectionFile f = intersectionFiles[i]

            fieldIds[i] = f != null ? f.getFieldId() : null
            layerTypes[i] = fields[i].getType()
        }

        return isValidLayerFilter(resolution, filter, layerTypes, fieldIds)
    }

     boolean isValidLayerFilter(String resolution, LayerFilter[] filter, String[] layerTypes, String[] fieldIds) {
        for (int i = 0; i < filter.length; i++) {
            //it is not valid if the layer itself does not exist.
            // so if there is not grid file available to GridCutter
            // and the layer is not a contextual layer of type 'c' (ie, not a grid file based contextual layer)
            // it is not valid
            if (getLayerPath(resolution, filter[i].getLayername(), fieldIds[i]) == null
                    && !(filter[i].isContextual() && layerTypes[i].equalsIgnoreCase("c"))) {
                return false
            }
        }
        return true
    }

    /**
     * Determine the grid resolution that will be in use.
     *
     * @param layers list of layers to be used as String []
     * @param resolution target resolution as String
     * @return resolution that will be used
     */
      String confirmResolution(String[] layers, String resolution, String[] fieldIds) {
        try {
            TreeMap<Double, String> resolutions = new TreeMap<Double, String>()
            for (int i = 0; i < layers.length; i++) {
                String layer = layers[i]
                String path = getLayerPath(resolution, layer, fieldIds[i])
                int end, start
                if (path != null
                        && ((end = path.lastIndexOf(File.separator)) > 0)
                        && ((start = path.lastIndexOf(File.separator, end - 1)) > 0)) {
                    String res = path.substring(start + 1, end)
                    Double d = Double.parseDouble(res)
                    if (d < 1) {
                        resolutions.put(d, res)
                    }
                }
            }
            if (resolutions.size() > 0) {
                resolution = resolutions.firstEntry().getValue()
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
        return resolution
    }
}
