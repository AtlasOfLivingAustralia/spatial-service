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
package au.org.ala.spatial


import au.org.ala.spatial.dto.GridClass
import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.intersect.IntersectCallback
import au.org.ala.spatial.grid.GridCacheReader
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.intersect.SamplingThread
import au.org.ala.spatial.intersect.SimpleShapeFile

import java.util.Map.Entry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue

/**
 * Implementation of the sampling.
 *
 * @author adam
 */
class LayerIntersectService {

    /**
     * log4j logger
     */
    LinkedBlockingQueue<GridCacheReader> gridReaders = null
    int gridGroupCount = 0
    Object initLock = new Object()

    FieldService fieldService
    LayerService layerService
    SpatialObjectsService spatialObjectsService
    SpatialConfig spatialConfig

    String reload() {
        //TODO
//        String error = null;
//        try {
//            if (intersectConfig == null) {
//                init();
//            }
//            synchronized (initLock) {
//                int oldGridCacheReaderCount = intersectConfig.getGridCacheReaderCount();
//
////                intersectConfig = new IntersectConfig(fieldDao, layerDao);
//
//                ArrayList<GridCacheReader> newGridReaders = new ArrayList<GridCacheReader>();
//                for (int i = 0; i < intersectConfig.getGridCacheReaderCount(); i++) {
//                    GridCacheReader gcr = fixGridCacheReaderNames(new GridCacheReader(intersectConfig.getGridCachePath()));
//                    newGridReaders.add(gcr);
//                    gridGroupCount = gcr.getGroupCount();
//                }
//                if (newGridReaders.isEmpty()) {
//                    newGridReaders = null;
//                }
//
//                //remove old grid readers
//                for (int i = 0; i < oldGridCacheReaderCount; i++) {
//                    gridReaders.take();
//                }
//
//                //add new gridReaders
//                gridReaders.addAll(newGridReaders);
//
//                return null;
//            }
//        } catch (Exception e) {
//            log.error("error reloading properties and table images", e);
//            error = "error reloading properties and table images";
//        }
//        return error;
    }

    void init() {
        //TODO
//        if (intersectConfig == null) {
//            synchronized (initLock) {
//                if (intersectConfig != null) {
//                    return;
//                }
//                intersectConfig = new IntersectConfig(fieldDao, layerDao);
//                gridReaders = new LinkedBlockingQueue<GridCacheReader>();
//                for (int i = 0; i < intersectConfig.getGridCacheReaderCount(); i++) {
//                    GridCacheReader gcr = fixGridCacheReaderNames(new GridCacheReader(intersectConfig.getGridCachePath()));
//                    try {
//                        gridReaders.put(gcr);
//                    } catch (InterruptedException ex) {
//                        log.error("failed to add a GridCacheReader");
//                    }
//                    gridGroupCount = gcr.getGroupCount();
//                }
//                if (gridReaders.size() == 0) {
//                    gridReaders = null;
//                }
//            }
//        }
    }


    Vector samplingFull(String fieldIds, double longitude, double latitude) {
        init()

        Vector out = new Vector()

        for (String id : fieldIds.split(",")) {
            Layers layer = null
            int newid = cleanObjectId(id)

            IntersectionFile f = layerService.getIntersectionFile(id)
            if (f != null) {
                layer = layerService.getLayerByName(f.getLayerName(), false)
            } else {
                if (newid != -1) {
                    layer = layerService.getLayerById(newid, false)
                }
                if (layer == null) {
                    layer = layerService.getLayerByName(id, false)
                }
            }

            double[][] p = [[longitude, latitude]]

            if (layer != null) {
                if ("contextual" == layer.type.toLowerCase() && (f != null && f.getClasses() == null)) {
                    SpatialObjects o = spatialObjectsService.getObjectByIdAndLocation(f.getFieldId(), longitude, latitude)
                    if (o != null) {
                        Map m = new HashMap()
                        m.put("field", id)
                        m.put("value", o.getName())
                        m.put("layername", f.getFieldName())
                        m.put("pid", o.getPid())
                        m.put("description", o.description)

                        out.add(m)
                    } else {
                        Map m = new HashMap()
                        m.put("field", id)
                        m.put("value", "")
                        m.put("layername", f.getFieldName())

                        out.add(m)
                    }
                } else if ("environmental" == layer.type.toLowerCase() || (f != null && f.getClasses() != null)) {
                    Grid g = new Grid(spatialConfig.data.dir + File.separator + layer.getPath_orig())
                    if (g != null) {
                        float[] v = g.getValues3(p, 40960)

                        Map m = new HashMap()
                        m.put("field", id)
                        m.put("layername", f.getFieldName())   //close enough

                        if (f != null && f.getClasses() != null) {
                            GridClass gc = f.getClasses().get((int) v[0])
                            m.put("value", (gc == null ? "" : gc.getName()))
                            if (gc != null) {
                                //TODO: re-enable intersection for type 'a' after correct implementation
                                //TODO: of 'defaultField' fields table column

                                //some grid classes may not have individual polygons created
                                if (new File(f.getFilePath() + File.separator + "polygons.grd").exists()) {
                                    g = new Grid(f.getFilePath() + File.separator + "polygons")
                                    if (g != null) {
                                        int v0 = (int) v[0]
                                        v = g.getValues(p)
                                        m.put("pid", f.getLayerPid() + ':' + v0 + ':' + ((int) v[0]))
                                    }
                                } else {
                                    //no shapes available
                                    m.put("pid", f.getLayerPid() + ':' + ((int) v[0]))
                                }
                            }
                        }
                        if (!m.containsKey("value")) {
                            m.put("value", (Float.isNaN(v[0]) ? "" : v[0]))
                            m.put("units", layer.getEnvironmentalvalueunits())
                        }
                        out.add(m)
                    } else {
                        log.error("Cannot find grid file: " + spatialConfig.data.dir + File.separator + layer.getPath_orig())
                        Map m = new HashMap()
                        m.put("field", id)
                        m.put("value", "")
                        m.put("layername", f.getFieldName())   //close enough

                        out.add(m)
                    }
                }
            } else {
                String[] info = layerService.getAnalysisLayerInfo(id)

                if (info != null) {
                    String gid = info[0]
                    String filename = info[1]
                    String name = info[2]
                    Grid grid = new Grid(filename)

                    if (grid != null && (new File(filename + ".grd").exists())) {
                        float[] v = grid.getValues(p)
                        if (v != null) {
                            Map m = new HashMap()
                            m.put("field", id)
                            m.put("layername", name + "(" + gid + ")")
                            if (Float.isNaN(v[0])) {
                                m.put("value", "")
                            } else {
                                m.put("value", (Float.isNaN(v[0]) ? "" : v[0]))
                            }

                            out.add(m)
                        }
                    }
                }
            }
        }

        return out
    }

    /**
     * Single coordinate sampling.
     *
     * @param fieldIds comma separated field ids.
     * @param longitude
     * @param latitude
     * @return the intersection value for each input field id as a \n separated
     * String.
     */

    String sampling(String fieldIds, double longitude, double latitude) {
        init()

        double[][] p = [[longitude, latitude]]
        String[] fields = fieldIds.split(",")

        //count el fields
        int elCount = 0
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].length() > 0 && fields[i].charAt(0) == 'e' as char) {
                elCount++
            }
        }

        StringBuilder sb = new StringBuilder()
        HashMap<String, Float> gridValues = null
        for (String fid : fields) {
            IntersectionFile f = layerService.getIntersectionFile(fid)

            if (sb.length() > 0) {
                sb.append("\n")
            }

            if (f != null) {
                if (f.getShapeFields() != null && layerService.getShapeFileCache() != null) {
                    SimpleShapeFile ssf = layerService.getShapeFileCache().get(f.getFilePath())
                    if (ssf != null) {
                        int column_idx = ssf.getColumnIdx(f.getShapeFields())
                        String[] categories = ssf.getColumnLookup(column_idx)
                        int[] idx = ssf.getColumnIdxs(f.getShapeFields())
                        int value = ssf.intersectInt(longitude, latitude)
                        if (value >= 0) {
                            sb.append(categories[idx[value]])
                        }
                    } else {
                        SpatialObjects o = spatialObjectsService.getObjectByIdAndLocation(f.getFieldId(), longitude, latitude)
                        if (o != null) {
                            sb.append(o.getName())
                        }
                    }
                } else {
                    if (gridValues == null && gridReaders != null && elCount > gridGroupCount) {
                        try {
                            GridCacheReader gcr = gridReaders.take()
                            gridValues = gcr.sample(longitude, latitude)
                            gridReaders.put(gcr)
                        } catch (Exception e) {
                            log.error("GridCacheReader failed.", e)
                        }
                    }

                    if (gridValues != null) {
                        Float v = gridValues.get(fid)
                        if (v == null && !gridValues.containsKey(fid)) {
                            Grid g = new Grid(f.getFilePath())
                            if (g != null) {
                                float fv = g.getValues(p)[0]
                                if (f.getClasses() != null) {
                                    GridClass gc = f.getClasses().get((int) fv)
                                    if (gc != null) {
                                        sb.append(gc.getName())
                                    }
                                } else {
                                    if (!Float.isNaN(fv)) {
                                        sb.append(String.valueOf(fv))
                                    }
                                }
                            }
                        } else {
                            if (f.getClasses() != null) {
                                GridClass gc = f.getClasses().get(v.intValue())
                                if (gc != null) {
                                    sb.append(gc.getName())
                                }
                            } else {
                                if (v != null && !v.isNaN()) {
                                    sb.append(String.valueOf(v))
                                }
                            }
                        }
                    } else {
                        Grid g = new Grid(f.getFilePath())
                        if (g != null) {
                            float fv = g.getValues(p)[0]
                            if (f.getClasses() != null) {
                                GridClass gc = f.getClasses().get((int) fv)
                                if (gc != null) {
                                    sb.append(gc.getName())
                                }
                            } else {
                                if (!Float.isNaN(fv)) {
                                    sb.append(String.valueOf(fv))
                                }
                            }
                        }
                    }
                }
            } else {
                String[] info = layerService.getAnalysisLayerInfo(fid)

                if (info != null) {
                    String filename = info[1]
                    Grid grid = new Grid(filename)

                    if (grid != null && (new File(filename + ".grd").exists())) {
                        sb.append(String.valueOf(grid.getValues(p)[0]))
                    }
                }
            }
        }

        return sb.toString()
    }


    HashMap<String, String> sampling(double longitude, double latitude) {
        init()

        HashMap<String, String> output = new HashMap<String, String>()

        if (layerService.getShapeFileCache() != null) {
            HashMap<String, SimpleShapeFile> ssfs = layerService.getShapeFileCache().getAll()
            for (Entry<String, SimpleShapeFile> entry : ssfs.entrySet()) {
                String s = entry.getValue().intersect(longitude, latitude)
                if (s == null) {
                    s = ""
                }
                output.put(entry.getKey(), s)
            }
        }

        if (gridReaders != null) {
            GridCacheReader gcr = null
            HashMap<String, Float> gridValues = null
            try {
                gcr = gridReaders.take()
                gridValues = gcr.sample(longitude, latitude)
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            } finally {
                if (gcr != null) {
                    try {
                        gridReaders.put(gcr)
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    }
                }
            }
            if (gridValues != null) {
                for (Entry<String, Float> entry : gridValues.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isNaN()) {
                        output.put(entry.getKey(), "")
                    } else {
                        output.put(entry.getKey(), entry.getValue().toString())
                    }
                }
            }
        }

        return output
    }


    HashMap[] sampling(String pointsString, int gridcacheToUse) {
        init()

        //parse points
        String[] pointsArray = pointsString.split(",")
        double[][] points = new double[pointsArray.length / 2][2]
        for (int i = 0; i < pointsArray.length; i += 2) {
            try {
                points[(int) (i / 2)][1] = Double.parseDouble(pointsArray[i])
                points[(int) (i / 2)][0] = Double.parseDouble(pointsArray[i + 1])
            } catch (Exception ignored) {
                points[(int) (i / 2)][1] = Double.NaN
                points[(int) (i / 2)][0] = Double.NaN
            }
        }

        //output structure
        HashMap[] output = new HashMap[points.length]
        for (int i = 0; i < points.length; i++) {
            output[i] = new HashMap()
        }

        if (0 == gridcacheToUse) {
            String fids = ""
            for (Fields f : fieldService.getFields()) {
                if (f.enabled && f.indb) {
                    if (!fids.isEmpty()) {
                        fids += ","
                    }
                    fids += f.getId()
                }
            }
            String[] fidsSplit = fids.split(",")
            ArrayList<String> sample = sampling(fidsSplit, points)
            for (int i = 0; i < sample.size(); i++) {
                String[] column = sample.get(i).split("\n")
                for (int j = 0; j < column.length; j++) {
                    output[j].put(fidsSplit[i], column[j])
                }
            }

        } else if (1 == gridcacheToUse) {

            //contextual intersections
            if (layerService.getShapeFileCache() != null) {
                HashMap<String, SimpleShapeFile> ssfs = layerService.getShapeFileCache().getAll()
                for (Entry<String, SimpleShapeFile> entry : ssfs.entrySet()) {

                    for (int i = 0; i < points.length; i++) {
                        output[i].put(entry.getKey(), entry.getValue().intersect(points[i][0], points[i][1]))
                    }
                }
            }

            //environmental intersections
            if (gridReaders != null) {
                GridCacheReader gcr = null

                try {
                    gcr = gridReaders.take()
                    for (int i = 0; i < points.length; i++) {
                        output[i].putAll(gcr.sample(points[i][0], points[i][1]))
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                } finally {
                    if (gcr != null) {
                        try {
                            gridReaders.put(gcr)
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                }
            }
        }

        return output
    }


    ArrayList<String> sampling(String fieldIds, String pointsString) {
        init()

        //parse points
        String[] pointsArray = pointsString.split(",")
        double[][] points = new double[pointsArray.length / 2][2]
        for (int i = 0; i < pointsArray.length; i += 2) {
            try {
                points[(int)(i / 2)][1] = Double.parseDouble(pointsArray[i])
                points[(int)(i / 2)][0] = Double.parseDouble(pointsArray[i + 1])
            } catch (Exception ignored) {
                points[(int)(i / 2)][1] = Double.NaN
                points[(int)(i / 2)][0] = Double.NaN
            }
        }

        //parse fids
        String[] fidsArray = fieldIds.split(",")

        return sampling(fidsArray, points)
    }


    ArrayList<String> sampling(String[] fieldIds, double[][] points, IntersectCallback callback) {
        init()
        IntersectionFile[] intersectionFiles = new IntersectionFile[fieldIds.length]
        for (int i = 0; i < fieldIds.length; i++) {
            intersectionFiles[i] = layerService.getIntersectionFile(fieldIds[i])
            if (intersectionFiles[i] == null) {
                //test for local analysis layer
                String[] info = layerService.getAnalysisLayerInfo(fieldIds[i])
                if (info != null) {
                    intersectionFiles[i] = new IntersectionFile(fieldIds[i],
                            info[1], null, fieldIds[i], fieldIds[i], null, null, null, null)
                } else {
                    log.warn("failed to find layer for id '" + fieldIds[i] + "'")
                }
            }
        }
        if (callback == null)
            callback = new DummyCallback()
        sampling(intersectionFiles, points, callback)
    }


    ArrayList<String> sampling(String[] fieldIds, double[][] points) {
        sampling(fieldIds, points, new DummyCallback())
    }


    ArrayList<String> sampling(IntersectionFile[] intersectionFiles, double[][] points) {
        sampling(intersectionFiles, points, new DummyCallback())
    }

    ArrayList<String> sampling(IntersectionFile[] intersectionFiles, double[][] points, IntersectCallback callback) {
        init()
        if (callback == null)
            callback = new DummyCallback()
        localSampling(intersectionFiles, points, callback)
    }

    ArrayList<String> localSampling(IntersectionFile[] intersectionFiles, double[][] points, IntersectCallback callback) {
        log.debug("begin LOCAL sampling, number of threads " + spatialConfig.batch_thread_count
                + ", number of layers=" + intersectionFiles.length + ", number of coordinates=" + points.length)
        long start = System.currentTimeMillis()
        int threadCount = spatialConfig.batch_thread_count
        SamplingThread[] threads = new SamplingThread[threadCount]
        LinkedBlockingQueue<Integer> lbq = new LinkedBlockingQueue()
        CountDownLatch cdl = new CountDownLatch(intersectionFiles.length)
        ArrayList<String> output = new ArrayList<String>()
        for (int i = 0; i < intersectionFiles.length; i++) {
            output.add("")
            lbq.add(i)
        }

        callback.setLayersToSample(intersectionFiles)
        log.debug("Initialising sampling threads: " + threadCount)
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new SamplingThread(lbq,
                    cdl,
                    intersectionFiles,
                    points,
                    output,
                    spatialConfig.batch_thread_count,
                    layerService.getShapeFileCache(),
                    spatialConfig.grid_buffer_size,
                    callback
            )
            threads[i].start()
        }

        try {
            cdl.await()
        } catch (InterruptedException ex) {
            log.error(ex.getMessage(), ex)
        } finally {
            for (int i = 0; i < threadCount; i++) {
                try {
                    threads[i].interrupt()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }

        log.debug("End sampling, threads=" + threadCount
                + " layers=" + intersectionFiles.length
                + " in " + (System.currentTimeMillis() - start) + "ms")
        return output
    }

    /**
     * Clean up and just return an int for LAYER object
     *
     * @param id
     * @return
     */
    private static int cleanObjectId(String id) {
        //test field id value
        int len = Math.min(6, id.length())
        id = id.substring(0, len)
        char prefix = id.toUpperCase().charAt(0)
        String number = id.substring(2, len)
        try {
            int i = Integer.parseInt(number)
            return i
        } catch (Exception ignored) {
        }

        return -1
    }

    /**
     * update a grid cache reader with fieldIds
     */
//    GridCacheReader fixGridCacheReaderNames(GridCacheReader gcr) {
//        ArrayList<String> fileNames = gcr.getFileNames()
//        for (int i = 0; i < fileNames.size(); i++) {
//            gcr.updateNames(fileNames.get(i), layerService.getFieldIdFromFile(fileNames.get(i)))
//        }
//
//        return gcr
//    }

    /**
     * A dummy callback for convenience.
     */

class DummyCallback implements IntersectCallback {
        void setLayersToSample(IntersectionFile[] layersToSample) {
        }

        void setCurrentLayer(IntersectionFile layer) {
        }

        void setCurrentLayerIdx(Integer layer) {
        }

        void progressMessage(String message) {
        }
    }
}

