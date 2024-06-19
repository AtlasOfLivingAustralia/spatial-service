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

package au.org.ala.spatial

import au.org.ala.spatial.grid.Diva2bil
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.util.UploadSpatialResource
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.httpclient.methods.FileRequestEntity
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.geotools.data.shapefile.ShapefileDataStore
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.feature.type.AttributeDescriptor
import org.springframework.scheduling.annotation.Scheduled

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

@Slf4j
class ManageLayersService {

    def groovySql

    FieldService fieldService
    LayerService layerService
    SpatialObjectsService spatialObjectsService
    TasksService tasksService
    SpatialConfig spatialConfig
    PublishService publishService

    def listUploadedFiles() {
        def list = []

        //get all uploaded files
        def layersDir = spatialConfig.data.dir
        def path = new File(layersDir + "/uploads/")

        if (path.exists()) {
            for (File f : path.listFiles()) {
                if (f.isDirectory()) {
                    log.debug 'getting ' + f.getName()
                    def upload = getUpload(f.getName())
                    if (upload.size() > 0) {
                        list.add(upload)
                    }
                }
            }
        }

        list
    }

    Map getUpload(String uploadId, boolean canRetry = true) {

        def upload = [:]

        String layersDir = spatialConfig.data.dir
        File f = new File(layersDir + "/uploads/" + uploadId)

        List fields = []

        if (f.exists()) {
            f.listFiles().each { file ->
                if (file.getPath().toLowerCase().endsWith("original.name")) {
                    upload.put("raw_id", uploadId)
                    upload.put("created", Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime())

                    try {

                        def layerIdFile = new File(layersDir + "/uploads/" + uploadId + "/layer.id")
                        if (layerIdFile.exists()) {
                            upload.put("layer_id", layerIdFile.text)
                        }

                        def originalNameFile = new File(layersDir + "/uploads/" + uploadId + "/original.name")
                        if (originalNameFile.exists()) {
                            String originalName = originalNameFile.text
                            upload.put("filename", originalName)

                            //default name (unique) and displayname
                            def idx = 1
                            def cleanName = originalName.toLowerCase().replaceAll('[^0-9a-z_]', '_').replace('__', '_').replace('__', '_')
                            def checkName = cleanName
                            while (layerService.getLayerByName(checkName) != null) {
                                idx = idx + 1
                                checkName = cleanName + '_' + idx
                            }
                            upload.put("name", checkName)
                            upload.put("displayname", originalName)
                        }

                        File distributionFile = new File(layersDir + "/uploads/" + uploadId + "/distribution.id")
                        if (distributionFile.exists()) {
                            upload.put("data_resource_uid", distributionFile.text)
                        }

                        File checklistFile = new File(layersDir + "/uploads/" + uploadId + "/checklist.id")
                        if (checklistFile.exists()) {
                            upload.put("checklist", checklistFile.text)
                        }

                        List<Task> creationTask = Task.findAllByNameAndTag('LayerCreation', uploadId)
                        if (creationTask.size() > 0) {
                            if (creationTask.get(0).status < 2) {
                                upload.put("layer_creation", "running")
                            } else if (creationTask.get(0).status == 2) {
                                upload.put("layer_creation", "cancelled")
                            } else if (creationTask.get(0).status == 3) {
                                upload.put("layer_creation", "error")
                            } //else finished
                        }

                    } catch (IOException e) {
                        log.error "error reading layer.id file in: " + f.getPath(), e
                    }
                } else if (file.getName().startsWith("field.id.")) {
                    fields.add(fieldService.getFieldById(file.getName().substring("field.id.".length()), false))

                    upload.put("fields", fields)
                }
            }

            if (!upload.containsKey('filename')) {
                // process manually uploaded file
                processUpload(f, f.getName())

                // try again
                if (canRetry) {
                    upload = getUpload(uploadId, false)
                }
            }
        }

        //no upload dir, look in existing layers, at layer.id
        if (!upload.containsKey("raw_id")) {
            try {
                Layers layer = layerService.getLayerById(Integer.parseInt(uploadId))
                if (layer != null) {
                    upload.put("raw_id", uploadId)
                    upload.put("layer_id", uploadId)
                    upload.put("filename", "")

                    List<Fields> fieldList = fieldService.getFields()
                    for (Fields fs : fieldList) {
                        if (fs.getSpid() == uploadId) {
                            fields.add(fs)
                        }
                    }

                    upload.put("fields", fields)
                }
            } catch (Exception ignored) {

            }
        }

        return upload
    }

    //files manually added to layersDir/upload/ need processing
    def processUpload(File pth, String newName) {
        if (pth.exists()) {
            def columns = []
            String name = ''
            File shp = null
            File hdr = null
            File bil = null
            File grd = null
            def count = 0

            //flatten directories
            def moved = true
            while (moved) {
                moved = false
                pth.listFiles().each { f ->
                    if (f.isDirectory()) {
                        f.listFiles().each { sf ->
                            try {
                                FileUtils.moveToDirectory(sf, f.getParentFile(), false)
                            } catch (IOException ignored) {
                                // try a copy + delete
                                if (sf.isFile()) {
                                    FileUtils.copyFileToDirectory(sf, f.getParentFile())
                                } else if (sf.isDirectory()) {
                                    FileUtils.copyDirectoryToDirectory(sf, f.getParentFile())
                                }
                                sf.delete()
                            }
                        }
                        f.delete()
                        moved = true
                    }
                }
            }
            pth.listFiles().each { f ->
                if (count == 0 && f.getPath().toLowerCase().endsWith(".shp")) {
                    shp = f
                    name = f.getName().substring(0, f.getName().length() - 4)
                    columns.addAll(getShapeFileColumns(f))
                    count = count + 1
                } else if (f.getPath().toLowerCase().endsWith(".hdr")) {
                    bil = f
                    name = f.getName().substring(0, f.getName().length() - 4)
                    count = count + 1
                } else if (f.getPath().toLowerCase().endsWith(".grd")) {
                    grd = f
                }
                log.debug("Files found..." + f.getName())
            }

            //diva to bil
            if (grd != null && bil == null) {
                log.debug("Converting DIVA to BIL")
                def n = grd.getPath().substring(0, grd.getPath().length() - 4)
                Diva2bil.diva2bil(n, n)
                bil = n + '.hdr'
                name = grd.getName().substring(0, grd.getName().length() - 4)
                count = count + 1
            }

            //rename the file
            if (count == 1) {
                pth.listFiles().each { f ->
                    if (name != newName && f.getName().length() > 4 &&
                            f.getName().substring(0, f.getName().length() - 4) == name) {
                        def newF = new File(f.getParent() + "/" + f.getName().replace(name, newName))
                        if (newF.getPath() != f.getPath() && !newF.exists()) {
                            FileUtils.moveFile(f, newF)
                            log.debug("Moving file ${f.getName()} to ${newF.getName()}")
                        }
                    }
                }

                //store name
                new File(pth.getPath() + "/original.name").write(name)
                log.debug("Original file name stored..." + name)
            }

            shp = new File(pth.getPath() + "/" + newName + ".shp")
            bil = new File(pth.getPath() + "/" + newName + ".bil")
            def tif = new File(pth.getPath() + "/" + newName + ".tif")
            if (!shp.exists() && bil.exists() && !tif.exists()) {
                log.debug("BIL detected...")
                bilToGTif(bil, tif)
            } else {
                log.debug("SHP: ${shp.getPath()}, TIF: ${tif.getPath()}, BIL: ${bil.getPath()}")
                log.debug("SHP available: ${shp.exists()}, TIF available: ${tif.exists()}, BIL available: ${bil.exists()}")
            }

            def map = [:]

            if (!shp.exists() && !tif.exists()) {
                log.error("No SHP or TIF available....")
                map.put("error", "no layer files")
            } else {
                def errors = publishService.layerToGeoserver(new OutputParameter([
                        file: ([shp.exists() ? shp.getPath() : bil.getPath()] as JSON).toString()
                ]), null)

                if (errors) {
                    log.error("Errors uploading to geoserver...." + errors.inspect())
                    map.put("error", errors.inspect())
                } else {
                    map.put("raw_id", name)
                    map.put("columns", columns)
                    map.put("test_id", newName)
                    map.put("test_url",
                            spatialConfig.geoserver.url.toString() +
                                    "/ALA/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + newName +
                                    "&styles=&bbox=-180,-90,180,90&width=512&height=507&srs=EPSG:4326&format=application/openlayers")

                    new File(pth.getPath() + "/upload.json").write(JSONValue.toJSONString(map))
                }
            }

            return map
        } else {
            log.error("Path does not exist..." + pth)
        }

        return [error: pth + " does not exist!" ]
    }

    /**
     * sends a PUT or POST call to a URL using authentication and including a
     * file upload
     *
     * @param type one of UploadSpatialResource.PUT for a PUT call or
     *                     UploadSpatialResource.POST for a POST call
     * @param url URL for PUT/POST call
     * @param username account username for authentication
     * @param password account password for authentication
     * @param resourcepath local path to file to upload, null for no file to
     *                     upload
     * @param contenttype file MIME content type
     * @return server response status code as String or empty String if
     * unsuccessful
     */
    def httpCall(String type, String url, String username, String password, String resourcepath, String resourcestr, String contenttype) {
        def output = ["", ""]

        def entity = null
        if (resourcepath != null) {
            def input = new File(resourcepath)
            entity = new FileRequestEntity(input, contenttype)
        } else if (resourcestr != null) {
            try {
                entity = new StringRequestEntity(resourcestr, contenttype, "UTF-8")
            } catch (UnsupportedEncodingException e) {
                log.error 'failed to encode contenttype: ' + contenttype, e
            }
        }

        def response = Util.urlResponse(type, url, null, [:], entity, type == "PUT" ? true : null, username, password)

        // Execute the request
        if (response) {
            if (response.statusCode) {
                output[0] = String.valueOf(response.statusCode)
            }
            if (response.text) {
                output[1] = response.text
            }
            //Add extra info
            switch (response.statusCode) {
                case "401":
                    output[1] = 'UNAUTHORIZED: ' + url
                    break
            }

        }

        return output
    }

    def bilToGTif(File bil, File geotiff) {

        log.debug("BIL conversion to GeoTIFF with gdal_translate...")
        //bil 2 geotiff (?)
        String[] cmd = [spatialConfig.gdal.dir.toString() + "/gdal_translate", "-of", "GTiff",
                        "-co", "COMPRESS=DEFLATE", "-co", "TILED=YES", "-co", "BIGTIFF=IF_SAFER",
                        bil.getPath(), geotiff.getPath()]

        def builder = new ProcessBuilder(cmd)
        builder.environment().putAll(System.getenv())
        builder.redirectErrorStream(true)
        try {
            def proc = builder.start()
            proc.waitFor()
        } catch (Exception e) {
            log.error "error running gdal_translate", e
        }

        cmd = [spatialConfig.gdal.dir + '/gdaladdo',
               "-r", "cubic"
               , geotiff.getPath()
               , "2", "4", "8", "16", "32"]

        builder = new ProcessBuilder(cmd)
        builder.environment().putAll(System.getenv())
        builder.redirectErrorStream(true)
        try {
            def proc = builder.start()
            proc.waitFor()
        } catch (Exception e) {
            log.error "error running gdal_translate", e
        }
    }

    List<Layers> getAllLayers(url) {
        List<Layers> layers
        List<Fields> fields
        if (!url) {
            layers = layerService.getLayersForAdmin()
            fields = fieldService.getFields(true)
        } else {
            try {
                layers = []
                JSON.parse(Util.getUrl("${url}/layers?all=true")).each {
                    Layers layer = it as Layers
                    layer.id = it['id']
                    layers.push(layer)
                }
            } catch (err) {
                log.error 'failed to get all layers', err
            }
            try {
                fields = []
                JSON.parse(Util.getUrl("${url}/fields?all=true")).each {
                    Fields field = it as Fields
                    field.id = it['id']
                    fields.push(field)
                }
            } catch (err) {
                log.error 'failed to get all fields', err
            }
        }

        List<Layers> list = []
        layers.each { Layers l ->
            //get fields
            def fs = []
            fields.each { f ->
                if (f.getSpid() == String.valueOf(l.id)) {
                    fs.add(f)
                }
            }
            l.fields = fs

            list.add(l)
        }

        list
    }

    Map layerMap(String layerId) {
        String layersDir = spatialConfig.data.dir

        def map = [:]

        Map upload = getUpload(layerId)

        map.putAll(upload)

        try {
            JSONObject jo = (JSONObject) JSON.parse(new File(layersDir + "/uploads/" + layerId + "/upload.json").text)
            map.putAll(jo)
        } catch (Exception ignored) {
            try {
                Layers l = layerService.getLayerById(Integer.parseInt(layerId.replaceAll('[ec]l', "")), false)
                if (l) {
                    if (!upload.name) {
                        map.putAll(l.properties)
                        map.put('id', l.id)
                    }

                    //try to get from layer info
                    map.put("raw_id", l.getId())
                    if (!map.containsKey("layer_id")) {
                        map.put("layer_id", l.getId() + "")
                    }
                    //TODO: stop this failing when the table is not yet created
                    //map.put("columns", layerDao.getLayerColumns(l.getId()));
                    map.put("test_url",
                            spatialConfig.geoserver.url +
                                    "/ALA/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + l.getName() +
                                    "&styles=&bbox" +
                                    "=-180,-90,180,90&width=512&height=507&srs=EPSG:4326&format=application/openlayers")
                }
            } catch (Exception e2) {
                log.error("failed to find layer for rawId: " + layerId)
            }

        }

        map.put("has_layer", map.containsKey("layer_id"))
        if (map.containsKey("layer_id")) {
            Layers layer = layerService.getLayerById(Integer.parseInt(map.layer_id as String), false)

            if(layer) {
                map.putAll(layer.properties)
            }

            map.put("fields", Fields.findAllBySpid(map.get('layer_id')))

        } else {
            //fetch defaults

            //extents
            double[] extents = getExtents(layerId)
            if (extents == null) {
                extents = [-180, -90, 180, 90]
            }
            map.put("minlongitude", extents[0])
            map.put("minlatitude", extents[1])
            map.put("maxlongitude", extents[2])
            map.put("maxlatitude", extents[3])

            File shp = new File(layersDir + "/uploads/" + layerId + "/" + layerId + ".shp")
            File bil = new File(layersDir + "/uploads/" + layerId + "/" + layerId + ".bil")
            if (shp.exists()) {
                List columns = getShapeFileColumns(shp)

                map.put("columns", columns)
                map.put("type", "Contextual")
            } else if (bil.exists()) {
                double[] minmax = getMinMax(bil)
                map.put("environmentalvaluemin", minmax[0])
                map.put("environmentalvaluemax", minmax[1])

                map.put("type", "Environmental")
            }

            map.put("enabled", true)
        }

        //get list of available classifications
        Set<String> classifications = new HashSet<String>()
        List<Layers> layers = layerService.getLayersForAdmin()
        for (Layers l : layers) {
            classifications.add(l.getClassification1() + " > " + l.getClassification2())
        }
        List<String> classes = new ArrayList<String>(classifications)
        Collections.sort(classes)
        map.put("classifications", classes)

        map.remove('class')
        map
    }

    def deleteLayer(String id) {

        String layersDir = spatialConfig.data.dir
        String geoserverUrl = spatialConfig.geoserver.url
        String geoserverUsername = spatialConfig.geoserver.username
        String geoserverPassword = spatialConfig.geoserver.password

        Map map = null
        try {
            map = layerMap(id)
        } catch (err) {
            log.error 'failed to get layer map for layer to delete: ' + id, err
        }

        //fields
        if (map != null && map.containsKey("fields")) {
            List fields = (List) map.get("fields"); for (Object o : fields) {
                if (o != null) {
                    Fields field = (Fields) o

                    fieldService.delete(field.getId())

                    // analysis files
                    String[] dirs = ["/standard_layer/"]
                    for (String d : dirs) {
                        File df = new File(layersDir + d)
                        if (df.exists()) {
                            File[] files = df.listFiles()
                            for (File f : files) {
                                if (f.isDirectory()) {
                                    File[] files2 = f.listFiles()
                                    for (File f2 : files2) {
                                        if (f2.getName().startsWith(field.getId() + ".")) {
                                            FileUtils.deleteQuietly(f2)
                                        }
                                    }
                                } else if (f.getName().startsWith(field.getId() + ".")) {
                                    FileUtils.deleteQuietly(f)
                                }
                            }
                        }
                    }

                    // tabulation
                    //TODO:

                    // association distances
                    //TODO:
                }
            }
        }

        //layer
        if (map != null && map.containsKey("layer_id")) {
            String layerId = (String) map.get("layer_id")
            String name = (String) map.get("name")

            httpCall("DELETE",
                    geoserverUrl + "/rest/workspaces/ALA/datastores/" + name + "?recurse=true", ///external.shp",
                    geoserverUsername, geoserverPassword,
                    null, null,
                    "text/plain")
            httpCall("DELETE",
                    geoserverUrl + "/rest/workspaces/ALA/coveragestores/" + name + "?recurse=true", //"/external.geotiff",
                    geoserverUsername, geoserverPassword,
                    null, null,
                    "text/plain")

            // layers table
            layerService.delete(layerId)

            // layer files
            String[] dirs = ["/layer/"]
            for (String d : dirs) {
                File dir = new File(layersDir + d)
                if (dir.exists()) {
                    for (File f : dir.listFiles()) {
                        if (f.getName().startsWith(name + ".")) {
                            FileUtils.deleteQuietly(f)
                        }
                    }
                }
            }
        }

        //layer id in raw upload
        def allUploads = listUploadedFiles()
        allUploads.each {
            if (it.containsKey('layer_id') && it.layer_id == id) {
                new File(spatialConfig.data.dir.toString() + "/uploads/" + it.raw_id + "/layer.id").delete()
            }
        }

        //raw upload
        //TODO: tidy messages for id == name (layer already deleted)
//        String[] result;
//        result = httpCall("DELETE",
//                geoserverUrl + "/rest/workspaces/ALA/datastores/" + id + "?recurse=true", ///external.shp",
//                geoserverUsername, geoserverPassword,
//                null,null,
//                "text/plain");
//        result = httpCall("DELETE",
//                geoserverUrl + "/rest/workspaces/ALA/coveragestores/" + id + "?recurse=true", //"/external.geotiff",
//                geoserverUsername, geoserverPassword,
//                null,null,
//                "text/plain");
    }

    def deleteField(String fieldId) {
        String layersDir = spatialConfig.data.dir

        //fields
        Fields field = fieldService.getFieldById(fieldId, false)
        if (field != null) {

            fieldService.delete(fieldId)

            // analysis files
            String[] dirs = ["/analysis/"]
            for (String d : dirs) {
                File[] files = new File(layersDir + d).listFiles()
                for (int i = 0; files != null && i < files.length; i++) {
                    File f = files[i]
                    if (f.isDirectory()) {
                        File[] files2 = f.listFiles()
                        for (int j = 0; files2 != null && j < files.length; j++) {
                            File f2 = files2[j]
                            if (f2.getName().startsWith(field.getId() + ".")) {
                                //FileUtils.deleteQuietly(f2);
                            }
                        }
                    } else if (f.getName().startsWith(field.getId() + ".")) {
                        //FileUtils.deleteQuietly(f);
                    }
                }
            }

            // tabulation

            // association distances
        }
    }

    def fieldMapDefault(String layerId) {
        def layerMap = layerMap(layerId)

        String layersDir = spatialConfig.data.dir

        Map fieldMap = new HashMap()
        fieldMap.putAll(layerMap)

        //fix default layer name
        if (layerMap.containsKey("displayname")) {
            fieldMap.put("displayname", layerMap.get("displayname"))
            fieldMap.put("name", layerMap.get("displayname"))
        }

        //fix default layer description
        if (layerMap.containsKey("description")) {
            fieldMap.put("desc", layerMap.get("displayname"))
        }

        fieldMap.remove("id")

        fieldMap.put("raw_id", layerId)

        int countInDB = fieldService.countBySpid(layerId)
        boolean isContextual = "Contextual".equalsIgnoreCase(String.valueOf(layerMap.get("type")))

        fieldMap.put("indb", countInDB == 0)
        fieldMap.put("intersect", false) //countInDB == 0 && isContextual);
        fieldMap.put("analysis", countInDB == 0)
        fieldMap.put("addtomap", countInDB == 0)
        fieldMap.put("enabled", true)

        // convention of field name
        def sid = fieldService.calculateNextSequenceId(layerId)
        fieldMap.put("requestedId", (isContextual ? "cl" : "el") + sid + layerId)


        //type
        //Contextual and shapefile = c, Environmental = e, Contextual and grid file = a & b
        if (isContextual) {
            fieldMap.put("type", "c")
        } else if (isContextual) {
            fieldMap.put("type", "a")
        } else {
            fieldMap.put("type", "e")
        }

        File shp = new File(layersDir + "/uploads/" + layerId + "/" + layerId + ".shp")
        File bil = new File(layersDir + "/uploads/" + layerId + "/" + layerId + ".bil")
        File loadedShp = new File(layersDir + "/layer/" + layerMap.get('name') + ".shp")

        //TODO: do not set defaults
        fieldMap.put("filetype", "bil")
        fieldMap.put("columns", [])

        if (loadedShp.exists()) {
            fieldMap.put("filetype", "shp")

            List columns = getShapeFileColumns(loadedShp)
            fieldMap.put("columns", columns)
        } else if (shp.exists()) {
            fieldMap.put("filetype", "shp")

            List columns = getShapeFileColumns(shp)
            fieldMap.put("columns", columns)
        } else if (bil.exists()) {
//            fieldMap.put("filetype", "bil");
//            fieldMap.put("columns", [:]);
        }

        if (isContextual && fieldMap.containsKey("columns") && fieldMap.get("columns") != null &&
                ((List) fieldMap.get("columns")).size() > 0) {
            fieldMap.put("sname", ((List) fieldMap.get("columns")).get(0))
            //"sdesc" is optional
        }

        return fieldMap
    }

    double[] getExtents(String rawId) {
        String geoserverUrl = spatialConfig.geoserver.url
        String geoserverUsername = spatialConfig.geoserver.username
        String geoserverPassword = spatialConfig.geoserver.password

        double[] extents = null

        try {
            String[] out = httpCall("GET",
                    geoserverUrl + "/rest/workspaces/ALA/datastores/" + rawId + "/featuretypes/" + rawId + ".json",
                    geoserverUsername, geoserverPassword,
                    null,
                    null,
                    "text/plain")


            JSONObject jo = (JSONObject) JSON.parse(out[1])

            JSONObject bbox = (JSONObject) ((JSONObject) jo.get("featureType")).get("nativeBoundingBox")

            extents = new double[4]

            extents[0] = Double.parseDouble(bbox.get("minx").toString())
            extents[1] = Double.parseDouble(bbox.get("miny").toString())
            extents[2] = Double.parseDouble(bbox.get("maxx").toString())
            extents[3] = Double.parseDouble(bbox.get("maxy").toString())
        } catch (err) {
            log.debug 'failed feature layer, try coverage layer ' + rawId
            //try tif
            String[] out = httpCall("GET",
                    geoserverUrl + "/rest/workspaces/ALA/coverages/" + rawId + ".json",
                    geoserverUsername, geoserverPassword,
                    null,
                    null,
                    "text/plain")


            try {
                JSONObject jo = (JSONObject) JSON.parse(out[1])
                JSONObject bbox = (JSONObject) ((JSONObject) jo.get("coverage")).get("nativeBoundingBox")

                extents = new double[4]
                extents[0] = Double.parseDouble(bbox.get("minx").toString())
                extents[1] = Double.parseDouble(bbox.get("miny").toString())
                extents[2] = Double.parseDouble(bbox.get("maxx").toString())
                extents[3] = Double.parseDouble(bbox.get("maxy").toString())
            } catch (err2) {
                log.error 'failed to parse bbox for upload id ' + rawId
            }

        }

        return extents
    }

    /**
     * Have to determine if it is creating a new field or editing an existing field
     *
     * Assumption:
     * If it is field id, it is an 'edit an existing field' function
     * If it is a layer id, then it should be 'create new field' function
     *
     * Since function fieldMapDefault creates a new 'requestId' ( assigned to id after ) with incremental sequence number
     * So, if it is an edit function, the requestId should be unchanged.
     *
     *
     * @param fieldId  It is field id if starts with el/cl, otherwise layer id
     * @return
     */
    def fieldMap(String fieldId) {
        def layer = layerService.getLayerById(Integer.parseInt(fieldService.getFieldById(fieldId, false).spid), false)

        def map = fieldMapDefault(String.valueOf(layer.id))
        map.put("layerName", layer.name) // layer name for wms requests

        def field = fieldService.getFieldById(fieldId, false)

        if (fieldId.startsWith('cl') || fieldId.startsWith('el')) {
            //It stands for 'editing' not creating a new field
            //Restore  requestedId
            map.put("requestedId", field.getId())
        }
        map.put("id", field.getId())
        map.put("desc", field.getDesc())
        map.put("name", field.getName())
        map.put("sdesc", field.getSdesc())
        map.put("sname", field.getSname())
        map.put("spid", field.getSpid())
        map.put("type", field.getType())
        map.put("addtomap", field.addtomap)
        map.put("analysis", field.analysis)
        map.put("defaultlayer", field.defaultlayer)
        map.put("enabled", field.enabled)
        map.put("indb", field.indb)
        map.put("intersect", field.intersect)
        map.put("layerbranch", field.layerbranch)
        map.put("namesearch", field.namesearch)
        map.put("is_field", true)

        map
    }

    /**
     *
     * @param map params without kv pair of checkbox
     * @param id
     * @param createTask
     * @return
     */
    def createOrUpdateLayer(Map map, String id, boolean createTask = true) {
        // Unchecked checkbox won't be post via params
        Map checkboxFields = [:]
        checkboxFields["enabled"] = false
        checkboxFields.each { key, value ->
            if (!map.containsKey(key)) {
                map.put(key,value)
            }
        }

        Layers layer = Layers.get(id) ?: new Layers()
        layer.properties.each {
            if (map.containsKey(it.key)) {
                layer.properties.put(it.key, map.get(it.key))
            }
        }
        if (map.containsKey('id')) {
            try {
                layer.id = map.get('id') as Long
            } catch (Exception ignored) {}
        }

        createOrUpdateLayer(layer, id, createTask)
    }

    def createOrUpdateLayer(Layers layer, String id, boolean createTask = true) {
        Map retMap = [:]

        retMap.put('layer_id', id)

        if (!layer.name) {
            retMap.put("error", "name parameter missing")
            retMap.putAll(layerMap(String.valueOf(id)))
            retMap.putAll(layer.properties)
        } else {
            //UPDATE
            Integer intId = null
            try {
                //look for upload layer.id to use instead of upload id
                File idFile = new File(spatialConfig.data.dir.toString() + "/uploads/" + id + "/layer.id")
                if (idFile.exists()) {
                    //update id
                    layer.id = idFile.text
                }
                intId = layer.id
            } catch (ignored) {
                log.debug 'unable to read uploads layer.id for ' + id
            }
            if (id != null && id.isInteger() && Layers.countById(id)) {
                //update select values
                try {
                    //flag background processes that need running
//                    boolean updateIntersect = field.intersect != null && field.intersect != originalField.intersect && field.intersect
//                    boolean updateNameSearch = field.namesearch != null && field.namesearch != originalField.namesearch
//
//                    // remove duplicate association
//                    originalField = null

                    Fields.withTransaction {
                        if (!layer.save(flush: true, validate:true)) {
                            layer.errors.each {
                                log.error(it)
                            }
                        }
                    }

                    //record layer.id
                    FileUtils.write(new File(spatialConfig.data.dir.toString() + "/uploads/" + id + "/layer.id"), String.valueOf(layer.getId()))

                    retMap.put('message', 'Layer updated')
                } catch (err) {
                    log.error 'error updating layer: ' + id, err
                    retMap.put('error', 'error updating layer: ' + err.getMessage())
                }
            } else {
                try {
                    //defaults, in case of missing values
                    def defaultLayer = layerMap(id)
                    if (!layer.name) layer.name= defaultLayer.name
                    if (!layer.environmentalvaluemin) layer.environmentalvaluemin= defaultLayer.environmentalvaluemin
                    if (!layer.environmentalvaluemax) layer.environmentalvaluemax= defaultLayer.environmentalvaluemax
                    if (!layer.extents) layer.extents = defaultLayer.extents
                    if (!layer.domain) layer.domain = defaultLayer.domain
                    if (!layer.maxlatitude) layer.maxlatitude= Double.valueOf(defaultLayer.maxlatitude)
                    if (!layer.minlatitude) layer.minlatitude= Double.valueOf(defaultLayer.minlatitude)
                    if (!layer.maxlongitude) layer.maxlongitude= Double.valueOf(defaultLayer.maxlongitude)
                    if (!layer.minlongitude) layer.minlongitude= Double.valueOf(defaultLayer.minlongitude)
                    if (!layer.displayname) layer.displayname= defaultLayer.displayname
                    if (layer.enabled == null) layer.enabled = true
                    if (!layer.environmentalvalueunits) layer.environmentalvalueunits= defaultLayer.environmentalvalueunits
                    if (!layer.type) layer.type= defaultLayer.type

                    if (layerService.getLayerByName(layer.name.toString(), false) != null) {
                        retMap.put("error", "name: " + layer.name + " is not unique")
                        retMap.putAll(layerMap(String.valueOf(id)))
                        retMap.putAll(layer.properties)
                        retMap.put('id', layer.id)
                    }

                    //default values from the name
                    layer.displaypath = spatialConfig.geoserver.url +
                            "/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" +
                            layer.name + "&format=image/png&styles="

                    if (!layer?.path_orig) {
                        layer.path_orig = 'layer/' + layer.name
                    }

                    layer.dt_added = new Date()

                    //attempt to set layer id
                    if (layer.requestedId) {
                        layer.setId(Long.parseLong(String.valueOf(layer.requestedId)))
                    } else {
                        Long nextId = null
                        groovySql.query("SELECT nextval('layers_id_seq'::regclass)", { result ->
                            if (result.next()) {
                                nextId = result.getLong(1)
                            }
                        })
                        layer.setId(nextId)
                    }

                    //create default layers table entry, this updates layer.id
                    Task.withNewTransaction {
                        if (!layer.save(flush: true)) {
                            layer.errors.each {
                                log.error(it)
                            }
                        }
                    }

                    //record layer.id
                    FileUtils.write(new File(spatialConfig.data.dir.toString() + "/uploads/" + id + "/layer.id"), String.valueOf(layer.getId()))

                    if (createTask) {
                        tasksService.create('LayerCreation', id, [layerId: String.valueOf(layer.getId()), uploadId: String.valueOf(id)], null, null, null)
                    }

                    retMap.put('layer_id', layer.id)
                    retMap.put('message', 'Layer creation task started')
                } catch (err) {
                    log.error 'error creating layer: ' + id, err
                    retMap.put('error', 'error creating layer: ' + err.getMessage())
                }
            }
        }

        retMap
    }

    def getShapeFileColumns(File shp) {
        def columns = []

        try {
            ShapefileDataStore sds = new ShapefileDataStore(shp.toURI().toURL())
            SimpleFeatureType schema = sds.getSchema()
            for (AttributeDescriptor ad : schema.getAttributeDescriptors()) {
                // ignore geometry columns
                if (!(ad.type instanceof org.opengis.feature.type.GeometryType)) {
                    columns.add(ad.getLocalName())
                }
            }
        } catch (IOException e) {
            log.error("failed to get dbf column names for: " + shp.getPath(), e)
        }

        if (columns.size() < 1) {
            log.warn 'no columns found for shapefile: ' + shp.getPath()
        }

        columns
    }

    /**
     *
     * @param map params from http post
     * @param id
     * @param createTask
     * @return
     */
    def createOrUpdateField(Map map, String id, boolean createTask = true) {
        //Fields of checkbox
        //If checkboxes in form are unchecked, the kv pairs of those checkboxes won't be in form params
        Map checkboxFields = [:]
        checkboxFields["enabled"] = false
        checkboxFields["indb"] = false
        checkboxFields["namesearch"] = false
        checkboxFields["defaultlayer"] = false
        checkboxFields["intersect"] = false
        checkboxFields["layerbranch"] = false
        checkboxFields["analysis"] = false
        checkboxFields["addtomap"] = false

        checkboxFields.each { key, value ->
            if (!map.containsKey(key)) {
                map.put(key,value)
            }
        }


        Fields field = Fields.get(id) ?: new Fields()
        field.properties.each {
            if (map.containsKey(it.key)) {
                field.properties.put(it.key, map.get(it.key))
            }
        }
        if (map.containsKey('id')) {
            field.id = map.get('id')
        }
        createOrUpdateField(field, id, createTask)
    }

    def createOrUpdateField(Fields field, String id, boolean createTask = true) {

        def retMap = [:]

        if (field.type.equalsIgnoreCase("c") &&
                (field.sname == null || field.sname.isEmpty())) {
            retMap.put("error", "name parameter missing")
        } else {

            //UPDATE
            if (Fields.countById(id)) {

                //update select values
                try {
                    //flag background processes that need running
//                    boolean updateIntersect = field.intersect != null && field.intersect != originalField.intersect && field.intersect
//                    boolean updateNameSearch = field.namesearch != null && field.namesearch != originalField.namesearch
//
//                    // remove duplicate association
//                    originalField = null

                    Fields.withTransaction {
                        if (!field.save(flush: true, validate:true)) {
                            field.errors.each {
                                log.error(it)
                            }
                        }
                    }

//                    if (createTask && updateIntersect) {
//                        tasksService.create('TabulationCreate', null, [:])
//                    }
//
//                    retMap.put('message', 'Field updated')
//
//                    if (createTask && updateNameSearch) {
//                        tasksService.create('NameSearchUpdate', null, null)
//                    }
                } catch (err) {
                    log.error 'error updating field: ' + id, err
                }
            } else {

                //defaults, in case of missing values
                def defaultField = fieldMapDefault(id)
                if (field.addtomap == null) field.addtomap = true
                if (field.analysis == null) field.analysis = true
                if (field.defaultlayer == null) field.defaultlayer = true
                if (field.enabled == null) field.enabled = true
                if (field.intersect == null) field.intersect = false
                if (field.namesearch == null) field.namesearch = true
                if (field.layerbranch == null) field.layerbranch = false
                if (field.name == null) field.name = defaultField.name
                if (field.type == null) field.type = defaultField.type

                Map lyr
                if (field.spid) {
                    lyr = layerMap(field.spid)
                } else {
                    lyr = layerMap(id)
                }

                if ("contextual".equalsIgnoreCase(lyr.type.toString())) {
                    //match case insensitive for sname, sdesc
                    if (defaultField.columns instanceof List) {
                        if (field.sname && !defaultField.columns.contains(field.sname)) {
                            defaultField.columns.each { if (it.equalsIgnoreCase(field.sname)) field.sname = it }
                        }
                        if (field.sdesc && !defaultField.columns.contains(field.sdesc)) {
                            defaultField.columns.each { if (it.equalsIgnoreCase(field.sdesc)) field.sdesc = it }
                        }
                    }
                }

                def newField = new Fields()

                newField.setSpid(String.valueOf(lyr.id))

                boolean b = field.analysis != null ? field.addtomap : false
                newField.setAddtomap(b)

                b = field.analysis != null ?  field.analysis : false
                newField.setAnalysis(b)

                b = field.defaultlayer != null ? field.defaultlayer : false
                newField.setDefaultlayer(b)

                newField.setDesc(field.desc.toString())
                newField.setName(field.name.toString())

                b = field.enabled != null ? field.enabled : false
                newField.setEnabled(b)

                b = field.indb != null ? field.indb : false
                newField.setIndb(b)

                b = field.intersect != null ? field.intersect : false
                newField.setIntersect(b)

                b = field.layerbranch != null ? field.layerbranch : false
                newField.setLayerbranch(b)

                b = field.namesearch != null ?  field.namesearch : false
                newField.setNamesearch(b)
                if ("contextual".equalsIgnoreCase(lyr.type.toString())) {
                    newField.setSdesc(field.sdesc.toString())
                    newField.setSname(field.sname.toString())
                }
                newField.setType(field.type.toString())

                //attempt to set field id
                if (field.requestedId) {
                    newField.setId(field.requestedId.toString())
                } else {
                    newField.setId(null)
                }

                //create default layers table entry, this updates field.id
                Task.withNewTransaction {
                    if (!newField.save(true)) {
                        newField.errors.each {
                            log.error(it)
                        }
                    }
                }

                if (createTask) {
                    //no need for field creation when type=Environmental, skip to standardizing layer
                    if ("Environmental".equalsIgnoreCase(lyr.type.toString())) {
                        tasksService.create('StandardizeLayers', newField.getId(), [fieldId: String.valueOf(newField.getId())])
                    } else {
                        tasksService.create('FieldCreation', newField.getId(), [fieldId: String.valueOf(newField.getId()), uploadId: id])
                    }
                }

                //retrieve saved info
                retMap.putAll(fieldMap(newField.getId()))
                retMap.put('message', 'Field creation started')
            }
        }

        retMap
    }

//    public void backgroundProcessing() {
//
//        taskDao.addTask(UPDATE_NAME_SEARCH, "", 1);
//        taskDao.addTask(UPDATE_THUMBNAILS, "", 2);
//        taskDao.addTask(UPDATE_FOR_ANALYSIS, "", 2);
//        taskDao.addTask(UPDATE_TABULATIONS, "", 3);
//
//        taskDao.addTask(UPDATE_LAYER_DISTANCES, "", 3);
//        taskDao.addTask(UPDATE_GRID_CACHE, "", 3);
//    }

    def getMinMax(File bil) {
        double[] minmax = new double[2]

        //does .grd exist?
        File grd = new File(bil.getPath().substring(0, bil.getPath().length() - 4) + ".grd")
        File hdr = new File(bil.getPath().substring(0, bil.getPath().length() - 4) + ".hdr")

        if (!grd.exists() && hdr.exists()) {
            String n = bil.getPath().substring(0, bil.getPath().length() - 4)
            Bil2diva.bil2diva(n, n, '')
        }

        try {
            String info = grd.text
            for (String line : info.split("\n")) {
                if (line.startsWith("MinValue")) {
                    minmax[0] = Double.parseDouble(line.substring("MinValue=".length()).trim())
                }
                if (line.startsWith("MaxValue")) {
                    minmax[1] = Double.parseDouble(line.substring("MaxValue=".length()).trim())
                }
            }
        } catch (IOException e) {
            log.error("failed to get min max values in bil file: " + bil.getPath(), e)
        }
        return minmax
    }

    def distributionMap(String uploadId) {
        String dir = spatialConfig.data.dir

        //fetch info
        Map map = [:]

        Map upload = getUpload(uploadId)

        if (upload.size() > 0) {
            map.putAll(upload)

            try {

                File f = new File(dir + "/uploads/" + uploadId + "/upload.json")
                if (f.exists()) {
                    JSONObject jo = (JSONObject) JSON.parse(f.text)
                    map.putAll(jo)
                }
            } catch (err) {
                log.error 'failed to get distribution info for uploadId: ' + uploadId, err
            }

            map.put("has_layer", map.containsKey("data_resource_uid"))
        }

        return map
    }

    def checklistMap(String uploadId) {
        String dir = spatialConfig.data.dir

        //fetch info
        Map map = [:]

        Map upload = getUpload(uploadId)

        if (upload.size() > 0) {
            map.putAll(upload)

            try {

                File f = new File(dir + "/uploads/" + uploadId + "/upload.json")
                if (f.exists()) {
                    JSONObject jo = JSON.parse(f.text) as JSONObject
                    map.putAll(jo)
                }
            } catch (err) {
                log.error 'failed to get distribution info for uploadId: ' + uploadId, err
            }

            map.put("has_layer", map.containsKey("checklist"))
        }

        return map
    }

    def createDistribution(Map data, String uploadId) {
        Map retMap = [:]
        Map dm = distributionMap(uploadId)

        if (data.data_resource_uid == null || data.data_resource_uid.isEmpty()) {
            retMap.put("error", "data resource uid parameter missing")
            retMap.putAll(dm)
            retMap.putAll(data)
        } else {
            if (dm.containsKey('data_resource_uid')) {
                //already created, do not recreate
            } else {
                //CREATE

                //record data_resource_uid
                FileUtils.write(new File(spatialConfig.data.dir.toString() + "/uploads/" + uploadId + "/distribution.id"),
                        data.data_resource_uid.toString())

                tasksService.create('DistributionCreation', uploadId,
                        [data_resource_uid: String.valueOf(data.data_resource_uid), uploadId: String.valueOf(uploadId)])

                retMap.put('data_resource_uid', data.data_resource_uid)
                retMap.put('message', 'Distributions import started')
            }
        }

        retMap
    }

    def createChecklist(Map data, String uploadId) {
        Map retMap = [:]
        Map dm = checklistMap(uploadId)

        if (dm.containsKey('checklist')) {
            //already created, do not recreate
        } else {
            //CREATE

            //record data_resource_uid
            FileUtils.write(new File(spatialConfig.data.dir.toString() + "/uploads/" + uploadId + "/checklist.id"),
                    data.data_resource_uid.toString())

            tasksService.create('ChecklistCreation', uploadId,
                    [data_resource_uid: String.valueOf(data.data_resource_uid), uploadId: String.valueOf(uploadId)])

            retMap.put('checklist', 'checklist')
            retMap.put('message', 'Checklist import started')
        }

        retMap
    }

    def deleteDistribution(String id) {

        def m = getUpload(id)

        try {
            if (m.containsKey('data_resource_uid')) {
                groovySql.execute(
                        'DELETE FROM distributions WHERE data_resource_uid = \'' + m.data_resource_uid + '\';')
            }
        } catch (err) {
            log.error 'failed to delete data resource uid records for uploadId: ' + id, err
        }

        try {
            def f = new File(spatialConfig.data.dir.toString() + "/uploads/" + id + "/distribution.id")
            if (f.exists()) {
                f.delete()
            }
        } catch (err) {
            log.error 'failed to delete distribution.id file', err
        }
    }

    def deleteChecklist(String id) {

        def m = getUpload(id)

        try {
            if (m.containsKey('checklist')) {
                groovySql.execute('DELETE FROM distributions WHERE data_resource_uid = \'' + m.checklist + '\'')
            }
        } catch (err) {
            log.error 'failed to delete data resource uid records for uploadId: ' + id, err
        }

        try {
            def f = new File(spatialConfig.data.dir.toString() + "/uploads/" + id + "/checklist.id")
            if (f.exists()) {
                f.delete()
            }
        } catch (err) {
            log.error 'failed to delete checklist.id file', err
        }
    }

    /**
     * update postgres
     * update files
     * update geoserver
     *
     * @param spatialServiceUrl
     * @param fieldId
     * @return
     */
    def updateFromRemote(String spatialServiceUrl, String fieldId) {
        def f = JSON.parse(httpCall("GET",
                spatialServiceUrl + "/field/${fieldId}?pageSize=0",
                null, null,
                null,
                null,
                "application/json")[1])
        Fields field = f as Fields
        field.id = f.id

        def l = JSON.parse(httpCall("GET",
                spatialServiceUrl + "/layer/${field.spid}?pageSize=0",
                null, null,
                null,
                null,
                "application/json")[1])
        Layers layer = l as Layers
        layer.id = l.id

        //update postgres
        layer.requestedId = layer.id
        field.requestedId = field.id

        //fix displaypath
        def origDisplayPath = layer.displaypath
        layer.displaypath = spatialConfig.geoserver.url + layer.displaypath.substring(layer.displaypath.indexOf("/gwc/"))

        //create as disabled if creating
        if (!layerService.getLayerById(layer.id, false)) {
            layer.enabled = false
        }
        createOrUpdateLayer(layer, layer.id + '', false)

        if (fieldService.getFieldById(field.id, false) == null) {
            field.enabled = false
            createOrUpdateField(field, field.requestedId + '', false)
        } else {
            createOrUpdateField(field, field.id + '', false)
        }

        Map input = [layerId: layer.requestedId, fieldId: field.id, sourceUrl: spatialServiceUrl, displayPath: origDisplayPath] as Map
        Task task = tasksService.create("LayerCopy", UUID.randomUUID(), input).task

        task

    }

    // Schedule to run once, 5 mins after startup
    // Create outline/polygon style for Vector layer
    // Create a linear/none linear style for each Raster layer
    @Scheduled(initialDelay = 3000000L, fixedDelay = Long.MAX_VALUE)
    def fixLayerStyles() {
        String geoserverUrl = spatialConfig.geoserver.url
        String geoserverUsername = spatialConfig.geoserver.username
        String geoserverPassword = spatialConfig.geoserver.password

        // create outline style
        def data = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                "<StyledLayerDescriptor version=\"1.0.0\"\n" +
                "  xsi:schemaLocation=\"http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd\"\n" +
                "  xmlns=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\"\n" +
                "  xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\n" +
                "  <NamedLayer>\n" +
                "    <Name></Name>\n" +
                "    <UserStyle>\n" +
                "      <Title>An outline polygon style</Title>\n" +
                "      <FeatureTypeStyle>\n" +
                "        <Rule>\n" +
                "          <Title>outline polygon</Title>\n" +
                "          <PolygonSymbolizer>\n" +
                "            <Stroke>\n" +
                "              <CssParameter name=\"stroke\">#000000</CssParameter>\n" +
                "              <CssParameter name=\"stroke-width\">0.5</CssParameter>\n" +
                "            </Stroke>\n" +
                "          </PolygonSymbolizer>\n" +
                "\n" +
                "        </Rule>\n" +
                "\n" +
                "      </FeatureTypeStyle>\n" +
                "    </UserStyle>\n" +
                "  </NamedLayer>\n" +
                "</StyledLayerDescriptor>\n"

        def extra = ''

        UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, "outline")
        File tmpFile = File.createTempFile("sld", "xml")
        tmpFile.write(data)
        UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + "outline",
                extra, geoserverUsername, geoserverPassword, tmpFile.path)

        def fields = fieldService.getFieldsByCriteria('')
        fields.each { field ->
            String styleName = field.id
            String layerName = field.layer.name

            List<String> stylesRequired = []
            if ('c' == field.type) {
                stylesRequired.push(styleName)
                stylesRequired.push('outline')
                stylesRequired.push('polygon')
            } else {
                stylesRequired.push(layerName)

                def linear = layerName + "_linear"
                stylesRequired.push(linear)

                // add layerName_linear sld if required
                UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                        extra, geoserverUsername, geoserverPassword, linear)
                tmpFile = File.createTempFile("sld", "xml")
                tmpFile.write(getLinearStyle(layerName, false))
                UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + linear,
                        extra, geoserverUsername, geoserverPassword, tmpFile.path)
            }

            stylesRequired.each { String style ->
                data = "<style><name>" + style + "</name></style>"
                UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + layerName + "/styles", "",
                        geoserverUsername, geoserverPassword, data)
                UploadSpatialResource.addGwcStyle(geoserverUrl, layerName, style, geoserverUsername, geoserverPassword)
            }
        }
    }

    private def getLinearStyle(String name, boolean reversed) {

        String dir = spatialConfig.data.dir
        def diva = new Grid(dir + "/layer/" + name)

        def min = diva.minval.round(2)
        def max = diva.maxval.round(2)
        def mid = ((diva.maxval + diva.minval)/2).round(2)
        def midMin = ((mid + diva.minval)/2).round(2)
        def midMax = ((diva.maxval + mid)/2).round(2)

        def nodatavalue = diva.nodatavalue

        def colour1
        def colour2
        def colour3
        def colour4
        def colour5

        if (reversed) {
            colour1 = '0x323232'
            colour2 = '0x636363'
            colour3 = '0x808080'
            colour4 = '0xa6a6a6'
            colour5 = '0xe2e2e2'
        } else {
            colour1 = '0xe2e2e2'
            colour2 = '0xa6a6a6'
            colour3 = '0x808080'
            colour4 = '0x636363'
            colour5 = '0x323232'
        }

        '<?xml version="1.0" encoding="UTF-8"?><StyledLayerDescriptor xmlns="http://www.opengis.net/sld">' +
                '<NamedLayer><Name>ALA:' + name + '</Name>' +
                '<UserStyle><FeatureTypeStyle><Rule><RasterSymbolizer><Geometry></Geometry><ColorMap>' +
                (nodatavalue < min ? '<ColorMapEntry color="0xffffff" opacity="0" quantity="' + nodatavalue + '"/>' : '') +
                '<ColorMapEntry color="'+colour1 +'" opacity="1" quantity="' + min + '" label="' + ((float) min) + " " + diva.units + '"/>' +
                '<ColorMapEntry color="'+colour2 +'" opacity="1" quantity="' + midMin + '"/>' +
                '<ColorMapEntry color="'+colour3 +'" opacity="1" quantity="' + mid + '" label="' + ((float) mid) + " " + diva.units + '"/>' +
                '<ColorMapEntry color="'+colour4 +'" opacity="1" quantity="' + midMax + '"/>' +
                '<ColorMapEntry color="'+colour5 +'" opacity="1" quantity="' + max + '" label="' + ((float) max) + " " + diva.units + '"/>' +
                (nodatavalue > max ? '<ColorMapEntry color="0xffffff" opacity="0" quantity="' + nodatavalue + '"/>' : '') +
                '</ColorMap></RasterSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>'
    }
}
