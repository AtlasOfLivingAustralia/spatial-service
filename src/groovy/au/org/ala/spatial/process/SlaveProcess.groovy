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

package au.org.ala.spatial.process

import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.intersect.SimpleRegion
import au.org.ala.layers.intersect.SimpleShapeFile
import au.org.ala.layers.legend.GridLegend
import au.org.ala.layers.legend.Legend
import au.org.ala.layers.legend.LegendEqualArea
import au.org.ala.layers.util.LayerFilter
import au.org.ala.spatial.Util
import au.org.ala.spatial.slave.FileLockService
import au.org.ala.spatial.slave.SlaveService
import au.org.ala.spatial.slave.Task
import au.org.ala.spatial.slave.TaskService
import au.org.ala.spatial.util.OccurrenceData
import grails.converters.JSON
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.web.json.JSONObject
import org.gdal.ogr.Geometry
import org.json.simple.JSONArray
import org.json.simple.parser.JSONParser

@Commons
class SlaveProcess {

    TaskService taskService
    SlaveService slaveService
    GrailsApplication grailsApplication
    FileLockService fileLockService
    Task task

    // start the task
    void start() {}

    // abort the task
    void stop() {}

    // define inputs and outputs
    Map spec() {
        Map s = JSON.parse(this.class.getResource("/processes/" + this.class.simpleName + ".json").text) as Map

        if (!s.containsKey('private')) {
            s.put('private', [:])
        }

        s.private.put('classname', this.class.getCanonicalName())

        s
    }


    String getTaskPath() {
        taskService.getBasePath(task)
    }

    List getLayers() {
        List layers = null

        try {
            String url = task.input.layersServiceUrl + '/layers'
            layers = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error 'failed to get all layers', err
        }

        layers
    }

    List getFields() {
        List fields = null

        try {
            //include layers info by using ?q=
            String url = task.input.layersServiceUrl + '/fields?q='
            fields = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error 'failed to get all fields', err
        }

        fields
    }

    List getDistributions() {
        List distributions = null

        try {
            String url = task.input.layersServiceUrl + '/distributions'
            distributions = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error 'failed to get all distributions', err
        }

        distributions
    }

    List getChecklists() {
        List checklists = null

        try {
            String url = task.input.layersServiceUrl + '/checklists'
            checklists = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error 'failed to get all checklists', err
        }

        checklists
    }

    List getTabulations() {
        List tabulations = null

        try {
            String url = task.input.layersServiceUrl + '/tabulation/list'
            tabulations = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error 'failed to get tabulations list', err
        }

        tabulations
    }

    Map getLayer(id) {
        Map layer = null

        try {
            String url = task.input.layersServiceUrl + '/layer/' + id
            layer = JSON.parse(Util.getUrl(url)) as Map
        } catch (err) {
            log.error 'failed to lookup layer: ' + id, err
        }

        layer
    }

    Map getField(id) {
        Map field = null

        try {
            String url = task.input.layersServiceUrl + '/field/show/' + id + "?pageSize=0"
            field = JSON.parse(Util.getUrl(url)) as Map
        } catch (err) {
            log.error 'failed to lookup field: ' + id, err
        }

        field
    }

    List getObjects(fieldId) {
        List objects = null

        try {
            String url = task.input.layersServiceUrl + '/field/' + fieldId
            Map field = (JSONObject) JSON.parse(Util.getUrl(url))
            objects = field.objects as List
        } catch (err) {
            log.error 'failed to lookup objects: ' + fieldId, err
        }

        objects
    }

    String getWkt(objectId) {
        String wkt = null

        try {
            if (objectId != null) {
                String url = task.input.layersServiceUrl + '/shapes/wkt/' + objectId
                wkt = Util.getUrl(url)
            }
        } catch (err) {
            log.error "failed to lookup object wkt: ${objectId}", err
        }

        wkt
    }

    /**
     * get all files with an extension or exact match for values.
     *
     * value must begin with '/'
     *
     * @param value
     */
    void addOutputFiles(value, layers = false) {
        def fstart = value.substring(0, value.lastIndexOf('/'))
        def fend = value.substring(value.lastIndexOf('/') + 1)

        if (layers) {
            addOutput("layers", value)
        }

        if (!task.output.containsKey(value)) task.output.put(value, [])

        File file = new File("${grailsApplication.config.data.dir}${fstart}")
        for (File f : file.listFiles()) {
            if (f.getName().equals(fend) || f.getName().startsWith("${fend}.")) {
                if (layers &&
                        (f.getText().endsWith(".sld") || f.getText().endsWith(".grd") ||
                                f.getText().endsWith(".gri") || f.getText().endsWith(".tif") ||
                                f.getText().endsWith(".prj") || f.getText().endsWith(".shp"))) {
                    addOutput("layers", fstart + '/' + f.getName())
                } else {
                    addOutput("files", fstart + '/' + f.getName())
                }

            }
        }
    }

    void addOutput(name, value) {
        if (!task.output.containsKey(name)) task.output.put(name, [])
        task.output.get(name).add(value)
    }

    void addOutput(name, value, download) {
        if (!task.output.containsKey(name)) task.output.put(name, [])
        task.output.get(name).add(value)

        if (download) {
            addOutput('download', value)
        }
    }

    String sqlEscapeString(str) {

        if (str == null) {
            'null'
        } else {
            str.replace("'", "''").replace("\\", "\\\\")
        }
    }

    def facetCount(facet, species) {
        String url = species.bs + "/occurrence/facets?facets=" + facet + "&flimit=0&q=" + species.q
        String response = Util.getUrl(url)

        JSONParser jp = new JSONParser()
        ((JSONArray) jp.parse(response)).get(0).getAt("count")
    }

    def occurrenceCount(species) {
        String url = species.bs + "/occurrences/search?&facet=off&pageSize=0&q=" + species.q
        String response = Util.getUrl(url)

        JSONParser jp = new JSONParser()
        ((JSONObject) jp.parse(response)).getAt("totalRecords")
    }

    def facet(facet, species) {
        String url = species.bs + "/occurrences/facets/download?facets=" + facet + "&lookup=false&count=false&q=" + species.q
        String response = Util.getUrl(url)

        response.split("\n")
    }

    def downloadSpecies(species) {
        OccurrenceData od = new OccurrenceData()
        String[] s = od.getSpeciesData(species.q, species.bs, null, null)

        def newFiles = []

        if (s[0] != null) {
            //mkdir in index location
            String newPath = null
            try {
                newPath = getTaskPath() + "/tmp/" + System.currentTimeMillis() + File.separator
                new File(newPath).mkdirs()

                File f = new File(newPath + File.separator + "species_points.csv")
                FileUtils.writeStringToFile(f, s[0])
                newFiles.add(f)
                if (s[1] != null) {
                    f = new File(newPath + File.separator + "removedSpecies.txt")
                    FileUtils.writeStringToFile(f, s[1])
                    newFiles.add(f)
                }
            } catch (err) {
                log.error 'failed to create tmp species file for task ' + task.id, err
            }
        }

        newFiles
    }

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
    String cutGrid(String[] layers, String resolution, SimpleRegion region, LayerFilter[] envelopes, String extentsFilename) {
        //check if resolution needs changing
        resolution = confirmResolution(layers, resolution)

        //get extents for all layers
        double[][] extents = getLayerExtents(resolution, layers[0])
        for (int i = 1; i < layers.length; i++) {
            extents = internalExtents(extents, getLayerExtents(resolution, layers[i]))
            if (!isValidExtents(extents)) {
                return null
            }
        }
        //do extents check for contextual envelopes as well
        if (envelopes != null) {
            extents = internalExtents(extents, getLayerFilterExtents(envelopes))
            if (!isValidExtents(extents)) {
                return null
            }
        }

        //get mask and adjust extents for filter
        byte[][] mask
        int h
        int w
        double res = Double.parseDouble(resolution)
        if (region != null) {
            extents = internalExtents(extents, region.getBoundingBox())

            if (!isValidExtents(extents)) {
                return null
            }

            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
            mask = getRegionMask(extents, w, h, region)
        } else if (envelopes != null) {
            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
            mask = getEnvelopeMaskAndUpdateExtents(resolution, res, extents, h, w, envelopes)
            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
        } else {
            h = (int) Math.ceil((extents[1][1] - extents[0][1]) / res)
            w = (int) Math.ceil((extents[1][0] - extents[0][0]) / res)
            mask = getMask(w, h)
        }

        //mkdir in index location
        String newPath = null
        try {
            newPath = getTaskPath() + "/tmp/" + System.currentTimeMillis() + File.separator
            new File(newPath).mkdirs()
        } catch (Exception e) {
            e.printStackTrace()
        }

        //apply mask
        for (int i = 0; i < layers.length; i++) {
            applyMask(newPath, resolution, extents, w, h, mask, layers[i])
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

    double[][] getLayerExtents(String resolution, String layer) {
        double[][] extents = new double[2][2]

        if (getLayerPath(resolution, layer) == null
                && layer.startsWith("cl")) {
            //use world extents here, remember to do object extents later.
            extents[0][0] = -180
            extents[0][1] = -90
            extents[1][0] = 180
            extents[1][1] = 90

        } else {
            Grid g = Grid.getGrid(getLayerPath(resolution, layer))

            extents[0][0] = g.xmin
            extents[0][1] = g.ymin
            extents[1][0] = g.xmax
            extents[1][1] = g.ymax
        }

        return extents
    }

    String getLayerPath(String resolution, String layer) {
        String standardLayersDir = grailsApplication.config.data.dir + '/standard_layer/'
        File file = new File(standardLayersDir + resolution + '/' + layer + '.grd')

        //move up a resolution when the file does not exist at the target resolution
        try {
            while (!file.exists()) {
                TreeMap<Double, String> resolutionDirs = new TreeMap<Double, String>()
                for (File dir : new File(standardLayersDir).listFiles()) {
                    if (dir.isDirectory()) {
                        try {
                            resolutionDirs.put(Double.parseDouble(dir.getName()), dir.getName())
                        } catch (Exception e) {
                            //ignore other dirs
                        }
                    }
                }

                String newResolution = resolutionDirs.higherEntry(Double.parseDouble(resolution)).getValue()

                if (newResolution.equals(resolution)) {
                    break
                } else {
                    resolution = newResolution
                    file = new File(standardLayersDir + File.separator + resolution + File.separator + layer + ".grd")
                }
            }
        } catch (Exception e) {
            //ignore
        }

        String layerPath = standardLayersDir + File.separator + resolution + File.separator + layer

        if (new File(layerPath + ".grd").exists()) {
            return layerPath
        } else {
            return null
        }
    }

    /**
     * Determine the grid resolution that will be in use.
     *
     * @param layers list of layers to be used as String []
     * @param resolution target resolution as String
     * @return resolution that will be used
     */
    private String confirmResolution(String[] layers, String resolution) {
        try {
            TreeMap<Double, String> resolutions = new TreeMap<Double, String>()
            for (String layer : layers) {
                String path = getLayerPath(resolution, layer)
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
            log.error 'failed to find a al requested layers: ' + layers.join(','), e
        }
        return resolution
    }

    private double[][] getLayerFilterExtents(LayerFilter[] envelopes) {
        double[][] extents = [[-180, -90], [180, 90]]
        for (int i = 0; i < envelopes.length; i++) {
            if (envelopes[i].getLayername().startsWith("cl")) {
                String[] ids = envelopes[i].getIds()
                for (int j = 0; j < ids.length; j++) {
                    try {
                        String obj = Util.getUrl(task.input.layersServiceUrl.toString() + '/object/' + ids[j])
                        double[][] bbox = SimpleShapeFile.parseWKT(obj.bbox.toString()).getBoundingBox()
                        extents = internalExtents(extents, bbox)
                    } catch (Exception e) {
                        log.error 'failed to bbox for ' + ids[j], e
                    }

                }
            }
        }
        return extents
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
    private byte[][] getRegionMask(double[][] extents, int w, int h, SimpleRegion region) {
        byte[][] mask = new byte[h][w]

        //can also use region.getOverlapGridCells_EPSG900913
        region.getOverlapGridCells(extents[0][0], extents[0][1], extents[1][0], extents[1][1], w, h, mask)
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                if (mask[i][j] > 0) {
                    mask[i][j] = 1
                }
            }
        }
        return mask
    }

    private byte[][] getMask(int w, int h) {
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
    private byte[][] getEnvelopeMaskAndUpdateExtents(String resolution, double res, double[][] extents, int h, int w, LayerFilter[] envelopes) {
        byte[][] mask = new byte[h][w]

        double[][] points = new double[h * w][2]
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                points[i + j * w][0] = (double) (extents[0][0] + (i + 0.5) * res)
                points[i + j * w][1] = (double) (extents[0][1] + (j + 0.5) * res)
            }
        }

        for (int k = 0; k < envelopes.length; k++) {
            LayerFilter lf = envelopes[k]

            // if it is contextual and a grid file does not exist at the requested resolution
            // and it is not a grid processed as a shape file,
            // then get the shape file to do the intersection
            if (existsLayerPath(resolution, lf.getLayername(), true) && lf.isContextual()
                    && "c".equalsIgnoreCase(getField(lf.getLayername()).type.toString())) {

                String[] ids = lf.getIds()
                SimpleRegion[] srs = new SimpleRegion[ids.length]

                for (int i = 0; i < ids.length; i++) {
                    srs[i] = SimpleShapeFile.parseWKT(
                            Util.getUrl(task.input.layersServiceUrl.toString() + "/shape/wkt/" + ids[i])
                    )

                }
                for (int i = 0; i < points.length; i++) {
                    for (int j = 0; j < srs.length; j++) {
                        if (srs[j].isWithin(points[i][0], points[i][1])) {
                            mask[i / w][i % w]++
                            break
                        }
                    }
                }
            } else {
                Grid grid = Grid.getGrid(getLayerPath(resolution, lf.getLayername()))

                float[] d = grid.getValues3(points, 40960)

                for (int i = 0; i < d.length; i++) {
                    if (lf.isValid(d[i])) {
                        mask[i / w][i % w]++
                    }
                }
            }
        }

        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (mask[j][i] == envelopes.length.byteValue()) {
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

        //update extents, must never be larger than the original extents (res is not negative, minx maxx miny mazy are not negative and < w & h respectively
        extents[0][0] = Math.max(extents[0][0] + minx * res, extents[0][0]) //min x value
        extents[1][0] = Math.min(extents[1][0] - (w - maxx - 1) * res, extents[1][0]) //max x value
        extents[0][1] = Math.max(extents[0][1] + miny * res, extents[0][1]) //min y value
        extents[1][1] = Math.min(extents[1][1] - (h - maxy - 1) * res, extents[1][1]) //max y value

        return smallerMask
    }

    void writeExtents(String filename, double[][] extents, int w, int h) {
        if (filename != null) {
            try {
                FileWriter fw = new FileWriter(filename)
                fw.append(String.valueOf(w)).append("\n")
                fw.append(String.valueOf(h)).append("\n")
                fw.append(String.valueOf(extents[0][0])).append("\n")
                fw.append(String.valueOf(extents[0][1])).append("\n")
                fw.append(String.valueOf(extents[1][0])).append("\n")
                fw.append(String.valueOf(extents[1][1]))
                fw.close()
            } catch (Exception e) {
                e.printStackTrace()
            }
        }
    }

    void applyMask(String dir, String resolution, double[][] extents, int w, int h, byte[][] mask, String layer) {
        //layer output container
        double[] dfiltered = new double[w * h]

        //open grid and get all data
        def path = getLayerPath(resolution, layer)
        Grid grid = Grid.getGrid(path)

        //set all as missing values
        for (int i = 0; i < dfiltered.length; i++) {
            dfiltered[i] = Double.NaN
        }

        double res = Double.parseDouble(resolution)

        if (new File(path + '.grd').length() < 100 * 1024 * 1024) {
            grid.getGrid()
        }

        for (int i = 0; i < mask.length; i++) {
            for (int j = 0; j < mask[0].length; j++) {
                if (mask[i][j] > 0) {
                    def ds = new double[1][2]
                    ds[0][0] = j * res + extents[0][0]
                    ds[0][1] = i * res + extents[0][1]
                    dfiltered[j + (h - i - 1) * w] = grid.getValues3(ds, 1024 * 1024)[0]
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

    boolean existsLayerPath(String resolution, String field, boolean do_not_lower_resolution) {

        File file = new File(grailsApplication.config.data.dir.toString() + '/standard_layer/' + File.separator + resolution + File.separator + field + ".grd")

        //move up a resolution when the file does not exist at the target resolution
        try {
            while (!file.exists() && !do_not_lower_resolution) {
                TreeMap<Double, String> resolutionDirs = new TreeMap<Double, String>()
                for (File dir : new File(grailsApplication.config.data.dir.toString() + '/standard_layer/').listFiles()) {
                    if (dir.isDirectory()) {
                        try {
                            resolutionDirs.put(Double.parseDouble(dir.getName()), dir.getName())
                        } catch (Exception e) {
                            //ignore
                        }
                    }
                }

                String newResolution = resolutionDirs.higherEntry(Double.parseDouble(resolution)).getValue()

                if (newResolution.equals(resolution)) {
                    break
                } else {
                    resolution = newResolution
                    file = new File(grailsApplication.config.data.dir.toString() + '/standard_layer/' + File.separator + resolution + File.separator + field + ".grd")
                }
            }
        } catch (err) {
            log.error 'failed to find path for: ' + field + ' : ' + resolution, err
        }

        String layerPath = grailsApplication.config.data.dir + '/standard_layer/' + File.separator + resolution + File.separator + field

        return new File(layerPath + ".grd").exists()
    }

    void convertAsc(String asc, String grd, Boolean saveImage = false) {
        try {
            task.message = "asc > grd"
            //read asc
            BufferedReader br = new BufferedReader(new FileReader(asc))
            String s

            //maxent output grid is:
            s = br.readLine()
            int ncols = Integer.parseInt(s.replace("ncols", "").trim())

            s = br.readLine()
            int nrows = Integer.parseInt(s.replace("nrows", "").trim())

            s = br.readLine()
            double lng1 = Double.parseDouble(s.replace("xllcorner", "").trim())

            s = br.readLine()
            double lat1 = Double.parseDouble(s.replace("yllcorner", "").trim())

            s = br.readLine()
            double div = Double.parseDouble(s.replace("cellsize", "").trim())

            s = br.readLine()
            double nodata = Double.parseDouble(s.replace("NODATA_value", "").trim())

            double[] data = new double[ncols * nrows]
            for (int i = 0; i < ncols * nrows; i++) {
                data[i] = Double.NaN
            }
            int r = 0
            while ((s = br.readLine()) != null) {
                String[] row = s.split(" ")
                for (int i = 0; i < row.length && i < ncols; i++) {
                    double v = Double.parseDouble(row[i])
                    if (v != nodata) {
                        data[r * ncols + i] = v
                    }
                }
                r++
                if (r == nrows) {
                    break
                }
            }
            br.close()

            Grid g = new Grid(null)
            g.writeGrid(grd, data, lng1, lat1, lng1 + ncols * div, lat1 + nrows * div, div, div, nrows, ncols)

            if (!saveImage) {
                GridLegend.generateGridLegend(grd, grd + '.sld', 1, false)
            } else {
                try {
                    g = new Grid(grd)
                    float[] d = g.getGrid()
                    java.util.Arrays.sort(d)

                    Legend legend = new LegendEqualArea()
                    legend.generate(d)
                    legend.determineGroupSizes(d)
                    legend.evaluateStdDevArea(d)

                    //must 'unsort' d
                    d = null
                    g = null
                    System.gc()
                    g = new Grid(grd)
                    d = g.getGrid()
                    legend.exportSLD(g, grd + ".sld", "", false, true)
                    legend.exportImage(d, g.ncols, grd + ".png", 1, true)
                    legend.generateLegend(grd + "_legend.png")
                } catch (Exception e) {
                    GridLegend.generateGridLegend(grd, grd + '.sld', 1, false)
                    log.error('failed to make sld', e)
                }
            }

            def cmd = [grailsApplication.config.gdal.dir + "/gdal_translate", "-of", "GTiff", "-a_srs", "EPSG:4326",
                       "-co", "COMPRESS=DEFLATE", "-co", "TILED=YES", "-co", "BIGTIFF=IF_SAFER",
                       asc, grd + ".tif"]
            task.message = "asc > tif"
            runCmd(cmd.toArray(new String[cmd.size()]), false)

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    String getSpeciesList(species) {
        String speciesList = null

        try {
            String url = species.bs + "/occurrences/facets/download?facets=names_and_lsid&lookup=true&count=true&q=" + species.q
            speciesList = Util.getUrl(url)
        } catch (err) {
            log.error 'failed to get species list', err
        }

        speciesList
    }

    def getAreaWkt(area) {
        if (area.wkt) {
            return area.wkt
        }

        if (area.pid) {
            return getWkt(area.pid)
        }

        if (area.bbox) {
            return "POLYGON ((${area.bbox[0]} ${area.bbox[1]},${area.bbox[0]} ${area.bbox[3]}," +
                    "${area.bbox[2]} ${area.bbox[3]},${area.bbox[2]} ${area.bbox[1]},${area.bbox[0]} ${area.bbox[1]}))"
        }

        return null
    }

    def processArea(area) {
        def wkt = getAreaWkt(area)

        def region = null
        def envelope = null
        if (wkt.startsWith("ENVELOPE")) {
            envelope = LayerFilter.parseLayerFilters(wkt)
        } else {
            region = SimpleShapeFile.parseWKT(wkt)
        }

        [region, envelope]
    }

    def getSpeciesArea(species, area) {
        if (!species.q) {
            return species
        }

        if (species.q.startsWith('qid:') && species.q.substring(4).isLong()) {
            def qid = Util.getQid(species.bs, species.q.substring(4))
            species.putAll(qid)
        }

        def q = [species.q] as Set
        if (species.fq) q.addAll(species.fq)
        if (species.fqs) q.addAll(species.fqs)

        if (area.q) {
            q.addAll(area.q)
        } else if (area.wkt) {
            if (!species.wkt) {
                species.wkt = area.wkt
            } else {
                Geometry g1 = Geometry.CreateFromWkt(species.wkt)
                Geometry g2 = Geometry.CreateFromWkt(area.wkt)

                try {
                    Geometry intersection = g1.Intersection(g2)
                    if (intersection.Area() > 0) {
                        species.wkt = intersection.ExportToWkt()
                    } else {
                        species.wkt = null
                        q = ["-*:*"]
                    }
                } catch (Exception e) {
                    species.wkt = null
                    q = ["-*:*"]
                }
            }
        }

        if (q.size()) q.remove("*:*")

        species.q = q[0]
        if (q.size() > 1) species.fq = q.toList().subList(1, q.size())

        species.q = "qid:" + Util.makeQid(species)
        species.fq = null
        species.wkt = null

        species
    }

    def runCmd(String[] cmd, Boolean logToTask) {
        Util.runCmd(cmd, logToTask, task)
    }

}