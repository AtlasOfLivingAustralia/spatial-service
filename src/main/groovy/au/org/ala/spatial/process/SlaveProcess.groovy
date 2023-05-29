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

import au.org.ala.spatial.FileService
import au.org.ala.spatial.LayerIntersectService
import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.dto.ProcessSpecification
import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.SpatialConfig
import au.org.ala.spatial.Util
import au.org.ala.spatial.Distributions
import au.org.ala.spatial.DistributionsService
import au.org.ala.spatial.Fields
import au.org.ala.spatial.FieldService
import au.org.ala.spatial.GridCutterService
import au.org.ala.spatial.Layers
import au.org.ala.spatial.LayerService
import au.org.ala.spatial.OutputParameter
import au.org.ala.spatial.SpatialObjects
import au.org.ala.spatial.SpatialObjectsService
import au.org.ala.spatial.TabulationGeneratorService
import au.org.ala.spatial.TabulationService
import au.org.ala.spatial.TasksService
import au.org.ala.spatial.dto.TaskWrapper
import au.org.ala.spatial.util.OccurrenceData
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.intersect.SimpleRegion
import au.org.ala.spatial.intersect.SimpleShapeFile
import au.org.ala.spatial.legend.GridLegend
import au.org.ala.spatial.legend.Legend
import au.org.ala.spatial.legend.LegendEqualArea
import au.org.ala.spatial.dto.LayerFilter
import au.org.ala.ws.service.WebService
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.geotools.geometry.jts.WKTReader2
import org.grails.web.json.JSONArray
import org.locationtech.jts.geom.Geometry
import org.yaml.snakeyaml.util.UriEncoder

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

@Slf4j
class SlaveProcess {

    FieldService fieldService
    LayerService layerService
    DistributionsService distributionsService
    TasksService tasksService
    TaskWrapper taskWrapper
    TabulationService tabulationService
    SpatialObjectsService spatialObjectsService
    GridCutterService gridCutterService
    LayerIntersectService layerIntersectService
    TabulationGeneratorService tabulationGeneratorService
    FileService fileService
    WebService webService

    SpatialConfig spatialConfig

    // start the task
    void start() {}

    // abort the task
    void stop() {}


    def getFile(String path, String remoteSpatialServiceUrl) {
        def remote = peekFile(path, remoteSpatialServiceUrl)

        //compare p list with local files
        def fetch = []
        remote.each { file ->
            if (file.exists) {
                def local = new File(spatialConfig.data.dir + file.path)
                if (!local.exists() || local.lastModified() < file.lastModified) {
                    fetch.add(file.path)
                }
            }
        }

        if (fetch.size() < remote.size()) {
            //fetch only some
            fetch.each {
                getFile(it, remoteSpatialServiceUrl)
            }
        } else if (fetch.size() > 0) {
            //fetch all files

            def tmpFile = File.createTempFile('resource', '.zip')

            try {
                def shortpath = path.replace(spatialConfig.data.dir, '')
                def url = remoteSpatialServiceUrl + "/master/resource?resource=" + URLEncoder.encode(shortpath, 'UTF-8') +
                        "&api_key=" + spatialConfig.serviceKey

                def os = new BufferedOutputStream(new FileOutputStream(tmpFile))
                def streamObj = Util.getStream(url)
                try {
                    if (streamObj?.call) {
                        os << streamObj?.call?.getResponseBodyAsStream()
                    }
                    os.flush()
                    os.close()
                } catch (Exception e) {
                    log.error e.getMessage(), e
                }
                streamObj?.call?.releaseConnection()

                def zf = new ZipInputStream(new FileInputStream(tmpFile))
                try {
                    def entry
                    while ((entry = zf.getNextEntry()) != null) {
                        def filepath = spatialConfig.data.dir + entry.getName()
                        def f = new File(filepath)
                        f.getParentFile().mkdirs()

                        //TODO: copyInputStreamToFile closes the stream even if there are more entries
                        def fout = new FileOutputStream(f)
                        IOUtils.copy(zf, fout);
                        fout.close()

                        zf.closeEntry()

                        //update lastmodified time
                        remote.each { file ->
                            if (entry.name.equals(file.path)) {
                                f.setLastModified(file.lastModified)
                            }
                        }
                    }
                } finally {
                    try {
                        zf.close()
                    } catch (err) {
                        log.error('Error in reading uploaded file: '+ err.printStackTrace())
                    }
                }
            } catch (err) {
                log.error "failed to get: " + path, err
            }

            tmpFile.delete()
        }
    }

    List peekFile(String path, String spatialServiceUrl = spatialConfig.spatialService.url) {
        String shortpath = path.replace(spatialConfig.data.dir.toString(), '')

        if (spatialServiceUrl.equals(spatialConfig.grails.serverURL)) {
            return fileService.info(shortpath.toString())
        }

        List map = [[path: '', exists: false, lastModified: System.currentTimeMillis()]]

        try {
            String url = spatialServiceUrl + "/master/resourcePeek?resource=" + URLEncoder.encode(shortpath, 'UTF-8') +
                    "&api_key=" + spatialConfig.serviceKey

            map = JSON.parse(Util.getUrl(url)) as List
        } catch (err) {
            log.error "failed to get: " + path, err
        }

        map
    }

    String getInput(String name) {
        // config inputs
        if ('bieUrl' == name) return  spatialConfig.bie.baseURL
        if ('biocacheServiceUrl' == name) return  spatialConfig.biocacheServiceUrl
        if ('phyloServiceUrl' == name) return  spatialConfig.phyloServiceUrl
        if ('sandboxHubUrl' == name) return  spatialConfig.sandboxHubUrl
        if ('sandboxBiocacheServiceUrl' == name) return spatialConfig.sandboxBiocacheServiceUrl
        if ('namematchingUrl' == name) return spatialConfig.namematching.url
        if ('geoserverUrl' == name) return spatialConfig.geoserver.url
        if ('userId' == name) return taskWrapper.task.userId

        // task inputs
        taskWrapper.task.input.find { it.name == name}?.value as String
    }

    // define inputs and outputs
    ProcessSpecification spec(ProcessSpecification config) {
        ProcessSpecification s
        if (config == null) {
            def json = JSON.parse(this.class.getResource("/processes/" + this.class.simpleName + ".json").text)
            s = new ProcessSpecification()
            s.name = json.name
            s.description = json.description
            s.isBackground = json.isBackground
            s.version = json.version

            s.input = new HashMap()
            json.input.each { key, value ->
                ProcessSpecification.InputSpecification is = new ProcessSpecification.InputSpecification()
                is.description = value.description
                is.type = value.type.toUpperCase()

                ProcessSpecification.ConstraintSpecification c = new ProcessSpecification.ConstraintSpecification()
                value.constraints?.each { ckey, cvalue ->
                    if (ckey == 'selection') {
                        c.selection = cvalue.toUpperCase()
                    } else if (ckey == 'content') {
                        c.content = cvalue
                    } else if (c.properties.containsKey(ckey)) {
                        c.setProperty(ckey, cvalue)
                    }
                }
                is.constraints = c

                s.input.put(key, is)
            }

            s.output = new HashMap()
            json.output?.each { key, value ->
                ProcessSpecification.OutputSpecification is = new ProcessSpecification.OutputSpecification()
                is.description = value.description

                s.output.put(key.toUpperCase(), is)
            }

            s.privateSpecification = new ProcessSpecification.PrivateSpecification()
            json.private?.each { key, value ->
                if (s.privateSpecification.properties.containsKey(key)) {
                    s.privateSpecification.setProperty(key, value)
                }
            }

        } else {
            s = config.clone()
        }

        if (!s.privateSpecification) {
            s.privateSpecification = new ProcessSpecification.PrivateSpecification()
        }

        s.privateSpecification.classname = this.class.getCanonicalName()

        updateSpec(s)

        s
    }

    void updateSpec(ProcessSpecification spec) {}

    String getTaskPath() {
        taskWrapper.path + '/'
    }

    String getTaskPathById(taskId) {
        spatialConfig.data.dir + '/public/' + taskId + '/'
    }

    List<Layers> getLayers() {
        layerService.getLayers()
    }

    List<Fields> getFields() {
        fieldService.getFieldsByCriteria("")
    }

    List<Distributions> getDistributions() {
        distributionsService.queryDistributions([:], true, Distributions.EXPERT_DISTRIBUTION)
    }

    List<Distributions> getChecklists() {
        distributionsService.queryDistributions([:], true, Distributions.SPECIES_CHECKLIST)
    }

    List getTabulations() {
        tabulationService.listTabulations()
    }

    Layers getLayer(String id) {
        layerService.getLayerById(id as Long, false)
    }

    Fields getField(String id) {
        fieldService.get(id, null, 0, 0)
    }

    List<SpatialObjects> getObjects(String fieldId) {
        fieldService.get(fieldId, null, 0, -1)?.objects
    }

    String getWkt(objectId) {
        OutputStream baos = new ByteArrayOutputStream()
        spatialObjectsService.wkt(objectId, baos)

        baos.toString()
    }

    String getEnvelopeWkt(String objectId) {
        String wkt = ''

        try {
            String taskId = objectId.replace("ENVELOPE", "")

            wkt = new File(spatialConfig.data.dir + "/public/" + taskId + "/" + taskId + ".shp").text
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
    void addOutputFiles(String value, Boolean layers = false) {
        def fstart = value.substring(0, value.lastIndexOf('/'))
        def fend = value.substring(value.lastIndexOf('/') + 1)

        if (layers) {
            addOutput("layers", value)
        }

        OutputParameter op = taskWrapper.task.output.find { it.name == value }
        if (!op) {
            op = new OutputParameter([name: value, file: ([] as JSON).toString()])
            taskWrapper.task.output.add(op)
        }

        File file = new File("${spatialConfig.data.dir}${fstart}")
        for (File f : file.listFiles()) {
            if (f.getName() == fend || f.getName().startsWith("${fend}.")) {
                if (layers &&
                        (f.getName().endsWith(".sld") || f.getName().endsWith(".grd") ||
                                f.getName().endsWith(".gri") || f.getName().endsWith(".tif") ||
                                f.getName().endsWith(".prj") || f.getName().endsWith(".shp"))) {
                    addOutput("layers", fstart + '/' + f.getName())
                } else {
                    addOutput("files", fstart + '/' + f.getName())
                }

            }
        }
    }

    void addOutput(String name, String value,Boolean  download = false) {
        OutputParameter op = taskWrapper.task.output.find {OutputParameter it -> it.name == name }
        if (!op) {
            op = new OutputParameter([name: name, file: ([] as JSON).toString()])
            taskWrapper.task.output.add(op)
        }
        List values = (List) JSON.parse(op.file)
        values.add(value)
        op.file = (values as JSON).toString()

        if (download && !"download".equalsIgnoreCase(name)) {
            addOutput('download', value)
        }
    }

    static String sqlEscapeString(String str) {

        if (str == null) {
            'null'
        } else {
            str.replace("'", "''").replace("\\", "\\\\")
        }
    }

    static def facetOccurenceCount(String facet, SpeciesInput species) {
        String url = species.bs + "/occurrence/facets?facets=" + facet + "&flimit=-1&fsort=index&q=" + species.q.join('&fq=')
        String response = Util.getUrl(url)

        ((JSONArray) JSON.parse(response))
    }

    Integer facetCount(String facet, SpeciesInput species) {
        return facetCount(facet, species, null)
    }

    Integer facetCount(String facet, SpeciesInput species, String extraFq) {
        String fq = ''
        if (extraFq) fq = '&fq=' + UriEncoder.encode(extraFq)

        String url = species.bs + "/occurrence/facets?facets=" + facet + "&flimit=0&q=" + species.q.join('&fq=') + fq
        try {
            String response = Util.getUrl(url)

            def results = (List) JSON.parse(response)
            if (results) {
                return results.get(0)["count"] as Integer
            }
        } catch (err) {
            log.error 'failed get facet count for: ' + taskWrapper.id + ", " + url, err
        }

        return 0
    }

    def occurrenceCount(SpeciesInput species) {
        return occurrenceCount(species, null)
    }

    Integer occurrenceCount(SpeciesInput species, String extraFq) {
        String fq = ''
        if (extraFq) fq = '&fq=' + UriEncoder.encode(extraFq)

        String url = species.bs + "/occurrences/search?&facet=off&pageSize=0&q=" + species.q.join('&fq=') + fq
        String response = Util.getUrl(url)

        JSON.parse(response).totalRecords as Integer
    }

    String [] facet(String facet, SpeciesInput species) {
        String url = species.bs + "/occurrences/facets/download?facets=" + facet + "&lookup=false&count=false&q=" + species.q?.join('&fq=')
        String response = streamBytes(url, facet)
        if (response) {
            response.split("\n")
        } else {
            [] as String[]
        }
    }

    String streamBytes(String url, String name) {
        taskWrapper.task.message = "fetching ${name}"

        HttpClient client = HttpClient.newHttpClient()
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build()

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw new Exception("Error reading from biocache-service: " + response.statusCode())
        }

        InputStream inputStream = null
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        byte[] buffer = new byte[1024]
        try {
            inputStream = response.body()

            int count = 0
            int len
            while ((len = inputStream.read(buffer)) > 0) {
                count += len
                taskWrapper.task.message = "fetching ${name}: ${count} bytes"

                outputStream.write(buffer, 0, len)
            }

            new String(outputStream.toByteArray(), StandardCharsets.UTF_8)
        } catch (err) {
            log.error(url, err)
            ""
        } finally {
            if (inputStream) {
                inputStream.close()
            }
        }
    }

    List<File> downloadSpecies(SpeciesInput species) {
        OccurrenceData od = new OccurrenceData()
        String[] s = od.getSpeciesData(species.q.join('&fq='), species.bs, null)

        def newFiles = []

        if (s[0] != null) {
            //mkdir in index location
            String newPath = null
            try {
                newPath = getTaskPath() + "/tmp/" + System.currentTimeMillis() + File.separator
                new File(newPath).mkdirs()

                File f = new File(newPath + File.separator + "species_points.csv")
                f.write(s[0])
                newFiles.add(f)
                if (s[1] != null) {
                    f = new File(newPath + File.separator + "removedSpecies.txt")
                    f.write(s[1])
                    newFiles.add(f)
                }
            } catch (err) {
                log.error 'failed to create tmp species file for task ' + taskWrapper.id, err
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

    static double[][] internalExtents(double[][] e1, double[][] e2) {
        double[][] internalExtents = new double[2][2]

        internalExtents[0][0] = Math.max(e1[0][0], e2[0][0])
        internalExtents[0][1] = Math.max(e1[0][1], e2[0][1])
        internalExtents[1][0] = Math.min(e1[1][0], e2[1][0])
        internalExtents[1][1] = Math.min(e1[1][1], e2[1][1])

        return internalExtents
    }

    static boolean isValidExtents(double[][] e) {
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
        String standardLayersDir = spatialConfig.data.dir + '/standard_layer/'

        File file = new File(standardLayersDir + resolution + '/' + layer + '.grd')
        log.debug("Get grid from: " + file.path)
        //move up a resolution when the file does not exist at the target resolution
        try {
            while (!file.exists()) {
                TreeMap<Double, String> resolutionDirs = new TreeMap<Double, String>()
                for (File dir : new File(standardLayersDir).listFiles()) {
                    if (dir.isDirectory()) {
                        try {
                            resolutionDirs.put(Double.parseDouble(dir.getName()), dir.getName())
                        } catch (Exception ignored) {
                            //ignore other dirs
                        }
                    }
                }

                String newResolution = resolutionDirs.higherEntry(Double.parseDouble(resolution)).getValue()

                if (newResolution == resolution) {
                    break
                } else {
                    resolution = newResolution
                    file = new File(standardLayersDir + File.separator + resolution + File.separator + layer + ".grd")
                }
            }
        } catch (Exception e) {
            //ignore
            taskLog(e.message)
            log.error(e.message)
        }

        String layerPath = standardLayersDir + File.separator + resolution + File.separator + layer

        if (new File(layerPath + ".grd").exists()) {
            return layerPath
        } else {
            taskLog("Fatal error: Cannot calcuate grid due to missing the layer file: " + layerPath)
            log.error("Fatal error: Cannot calcuate grid due to missing the layer file: " + layerPath)
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
                        SpatialObjects obj = spatialObjectsService.getObjectByPid(ids[j])
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
    private static byte[][] getRegionMask(double[][] extents, int w, int h, SimpleRegion region) {
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

    private static byte[][] getMask(int w, int h) {
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
                            Util.getUrl(getInput('layersServiceUrl').toString() + "/shape/wkt/" + ids[i])
                    )

                }
                for (int i = 0; i < points.length; i++) {
                    for (int j = 0; j < srs.length; j++) {
                        if (srs[j].isWithin(points[i][0], points[i][1])) {
                            mask[(int)(i / w)][i % w]++
                            break
                        }
                    }
                }
            } else {
                Grid grid = Grid.getGrid(getLayerPath(resolution, lf.getLayername()))

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

    static void writeExtents(String filename, double[][] extents, int w, int h) {
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
                log.error(e.getMessage(), e)
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

        File file = new File(spatialConfig.data.dir.toString() + '/standard_layer/' + File.separator + resolution + File.separator + field + ".grd")

        //move up a resolution when the file does not exist at the target resolution
        try {
            while (!file.exists() && !do_not_lower_resolution) {
                TreeMap<Double, String> resolutionDirs = new TreeMap<Double, String>()
                for (File dir : new File(spatialConfig.data.dir.toString() + '/standard_layer/').listFiles()) {
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
                    file = new File(spatialConfig.data.dir.toString() + '/standard_layer/' + File.separator + resolution + File.separator + field + ".grd")
                }
            }
        } catch (err) {
            log.error 'failed to find path for: ' + field + ' : ' + resolution, err
        }

        String layerPath = spatialConfig.data.dir + '/standard_layer/' + File.separator + resolution + File.separator + field

        return new File(layerPath + ".grd").exists()
    }

    void convertAsc(String asc, String grd, Boolean saveImage = false) {
        try {
            taskWrapper.task.message = "asc > grd"
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

            Grid g = new Grid()
            g.writeGrid(grd, data, lng1, lat1, lng1 + ncols * div, lat1 + nrows * div, div, div, nrows, ncols)

            if (!saveImage) {
                GridLegend.generateGridLegend(grd, grd + '.sld', 1, false)
            } else {
                try {
                    g = new Grid(grd)
                    float[] d = g.getGrid()
                    Arrays.sort(d)

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

            def cmd = [spatialConfig.gdal.dir + "/gdal_translate", "-of", "GTiff", "-a_srs", "EPSG:4326",
                       "-co", "COMPRESS=DEFLATE", "-co", "TILED=YES", "-co", "BIGTIFF=IF_SAFER",
                       asc, grd + ".tif"]
            taskWrapper.task.message = "asc > tif"
            runCmd(cmd.toArray(new String[cmd.size()]), false, spatialConfig.admin.timeout)

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    String getSpeciesList(species) {
        return getSpeciesList(species, null, true, true)
    }

    String getSpeciesList(SpeciesInput species, String extraFq, lookup, count) {
        String speciesList = null

        try {
            String fq = ''
            if (extraFq) fq = '&fq=' + UriEncoder.encode(extraFq)
            String url = species.bs + "/occurrences/facets/download?facets=names_and_lsid&lookup=" + lookup + "&count=" + count + "&q=" + species.q.join('&fq=') + fq
            taskLog("Loading species ...")
            log.debug("Loading species from: " + url)
            speciesList = Util.getUrl(url)
        } catch (err) {
            taskLog("Failed to get species list.")
            log.error 'failed to get species list', err
        }

        speciesList
    }

    def getAreaWkt(AreaInput area) {
        if (area.type == 'envelope') {
            return getEnvelopeWkt(area.pid)
        }

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

    RegionEnvelope processArea(AreaInput area) {
        def wkt = getAreaWkt(area)

        def region = null
        def envelope = null
        if (wkt.startsWith("ENVELOPE")) {
            envelope = LayerFilter.parseLayerFilters(wkt)
        } else {
            region = SimpleShapeFile.parseWKT(wkt)
        }

        new RegionEnvelope(region, envelope)
    }



    /**
     * Create a new species by combining a species and area.
     *
     * @param species
     * @param area array of areas, only the first area is used
     * @return
     */
    SpeciesInput getSpeciesArea(SpeciesInput speciesIn, List<AreaInput> areas) {
        // copy species
        SpeciesInput species = speciesIn.clone()

        // check for absent area
        if (areas.size() == 0) {
            return species
        }

        // support areas and single area
        AreaInput area = areas[0]

        if (!species.q) {
            return species
        }

        if (species.q[0].startsWith('qid:') && species.q[0].substring(4).isLong()) {
            SpeciesInput qid = Util.getQid(species.bs, species.q[0].substring(4))
            species.q = qid.q
            species.wkt = qid.wkt
        }

        def q = species.q as Set

        def wkt = null

        if (area.q && area.q.size() > 0) {
            if (area.q.startsWith('[')) {
                // parse list
                q.addAll(area.q.substring(1, area.q.length() - 1).split(','))
            } else {
                q.add(area.q)
            }
        } else if (area.wkt && area.wkt?.size() > 0) {
            wkt = area.wkt
        } else if (area.pid && area.pid?.size() > 0) {
            wkt = getAreaWkt(area)
        }

        // area does not have q, has wkt or pid
        if (!(area.q && area.q.size() > 0) && wkt) {
            // use area wkt if species does not have one
            if (!species.wkt) {
                species.wkt = wkt
            } else {
                // find intersection of species wkt and area wkt
                WKTReader2 wktReader = new WKTReader2()

                Geometry g1 = wktReader.read(species.wkt)
                Geometry g2 = wktReader.read(wkt)

                try {
                    Geometry tmp = g1.intersection(g2)

                    // Use CCW for exterior rings. Normalizing will use the JTS default (CW). Reverse makes it CCW.
                    tmp.normalize()
                    Geometry intersection = tmp.reverse()

                    if (intersection.area > 0) {
                        species.wkt = intersection.toText()
                    } else {
                        species.wkt = null
                        q = ["-*:*"]
                    }
                } catch (Exception e) {
                    log.error("Failed to retrieve intersection area", e)
                    species.wkt = null
                    q = ["-*:*"]
                }
            }
        }

        if (q.size() > 1) q.remove("*:*")

        species.q = []
        species.q.addAll(q)

        species.q = ["qid:" + Util.makeQid(species, webService)]
        species.fq = null
        species.wkt = null

        species
    }

    def runCmd(String[] cmd, Boolean logToTask, Long timeout) {
        Util.runCmd(cmd, logToTask, taskWrapper, timeout)
    }

    def setMessage(String msg) {
        taskWrapper.task.message = msg
    }

    def taskLog(String msg) {
        taskWrapper.task.history.put(System.currentTimeMillis() as String, msg)
        taskWrapper.task.message = msg
    }

    static InputStream getUrlStream(String url) throws IOException {
        URLConnection c = new URL(url).openConnection()
        InputStream is = c.getInputStream()
        return is
    }


}

class RegionEnvelope {
    SimpleRegion region
    LayerFilter[] envelope

    RegionEnvelope(SimpleRegion region, LayerFilter[] envelope) {
        this.region = region
        this.envelope = envelope
    }
}
