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

package au.org.ala.spatial.service

import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.util.Bil2diva
import au.org.ala.layers.util.Diva2bil
import au.org.ala.spatial.Util
import au.org.ala.spatial.util.UploadSpatialResource
import grails.converters.JSON
import org.apache.commons.httpclient.methods.FileRequestEntity
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.io.FileUtils
import org.codehaus.jackson.map.ObjectMapper
import org.geotools.data.shapefile.ShapefileDataStore
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import org.json.simple.parser.JSONParser
import org.opengis.feature.simple.SimpleFeatureType
import org.opengis.feature.type.AttributeDescriptor
import org.springframework.scheduling.annotation.Scheduled

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

class ManageLayersService {

    def grailsApplication
    def layerIntersectDao
    def fieldDao
    def layerDao
    def objectDao
    def tasksService
    def slaveService

    PublishService getPublishService() {
        grailsApplication.mainContext.publishService
    }

    def listUploadedFiles() {
        def list = []

        //get all uploaded files
        def layersDir = grailsApplication.config.data.dir
        def path = new File(layersDir + "/uploads/")

        if (path.exists()) {
            for (File f : path.listFiles()) {
                if (f.isDirectory()) {
                    log.error 'getting ' + f.getName()
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

        def layersDir = grailsApplication.config.data.dir
        def f = new File(layersDir + "/uploads/" + uploadId)

        List fields = []

        if (f.exists()) {
            f.listFiles().each { file ->
                if (file.getPath().toLowerCase().endsWith("original.name")) {
                    upload.put("raw_id", uploadId)
                    upload.put("created", Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime())

                    try {

                        def layerIdFile = new File(layersDir + "/uploads/" + uploadId + "/layer.id")
                        if (layerIdFile.exists()) {
                            upload.put("layer_id", FileUtils.readFileToString(layerIdFile))
                        }

                        def originalNameFile = new File(layersDir + "/uploads/" + uploadId + "/original.name")
                        if (originalNameFile.exists()) {
                            String originalName = FileUtils.readFileToString(originalNameFile)
                            upload.put("filename", originalName)

                            //default name (unique) and displayname
                            def idx = 1
                            def cleanName = originalName.toLowerCase().replaceAll('[^0-9a-z_]', '_').replace('__', '_').replace('__', '_')
                            def checkName = cleanName
                            while (layerDao.getLayerByName(checkName) != null) {
                                idx = idx + 1
                                checkName = cleanName + '_' + idx
                            }
                            upload.put("name", checkName)
                            upload.put("displayname", originalName)
                        }

                        def distributionFile = new File(layersDir + "/uploads/" + uploadId + "/distribution.id")
                        if (distributionFile.exists()) {
                            upload.put("data_resource_uid", FileUtils.readFileToString(distributionFile))
                        }

                        def checklistFile = new File(layersDir + "/uploads/" + uploadId + "/checklist.id")
                        if (checklistFile.exists()) {
                            upload.put("checklist", FileUtils.readFileToString(checklistFile))
                        }

                        def creationTask = Task.findAllByNameAndTag('LayerCreation', uploadId)
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
                    fields.add(fieldDao.getFieldById(file.getName().substring("field.id.".length()), false))

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
                Layer layer = layerDao.getLayerById(Integer.parseInt(uploadId))
                if (layer != null) {
                    upload.put("raw_id", uploadId)
                    upload.put("layer_id", uploadId)
                    upload.put("filename", "")

                    List<Field> fieldList = fieldDao.getFields()
                    for (Field fs : fieldList) {
                        if (fs.getSpid().equals(uploadId)) {
                            fields.add(fs)
                        }
                    }

                    upload.put("fields", fields)
                }
            } catch (Exception e) {

            }
        }

        return upload
    }

    //files manually added to layersDir/upload/ need processing
    def processUpload(pth, newName) {
        if (pth.exists()) {
            def columns = []
            def name = ''
            def shp = null
            def hdr = null
            def bil = null
            def grd = null
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
                            } catch (IOException e) {
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
                log.info("Files found..." + f.getName())
            }

            //diva to bil
            if (grd != null && bil == null) {
                log.info("Converting DIVA to BIL")
                def n = grd.getPath().substring(0, grd.getPath().length() - 4)
                Diva2bil.diva2bil(n, n)
                bil = n + '.hdr'
                name = grd.getName().substring(0, grd.getName().length() - 4)
                count = count + 1
            }

            //rename the file
            if (count == 1) {
            pth.listFiles().each { f ->
                if (!name.equals(newName) && f.getName().length() > 4 &&
                        f.getName().substring(0, f.getName().length() - 4).equals(name)) {
                    def newF = new File(f.getParent() + "/" + f.getName().replace(name, newName))
                    if (!newF.getPath().equals(f.getPath()) && !newF.exists()) {
                        FileUtils.moveFile(f, newF)
                        log.info("Moving file ${f.getName()} to ${newF.getName()}")
                    }
                }
            }

            //store name
            FileUtils.write(new File(pth.getPath() + "/original.name"), name)
            log.info("Original file name stored..." + name)
            }

            shp = new File(pth.getPath() + "/" + newName + ".shp")
            bil = new File(pth.getPath() + "/" + newName + ".bil")
            def tif = new File(pth.getPath() + "/" + newName + ".tif")
            if (!shp.exists() && bil.exists() && !tif.exists()) {
                log.info("BIL detected...")
                bilToGTif(bil, tif)
            } else {
                log.info("SHP: ${shp.getPath()}, TIF: ${tif.getPath()}, BIL: ${bil.getPath()}")
                log.info("SHP available: ${shp.exists()}, TIF available: ${tif.exists()}, BIL available: ${bil.exists()}")
            }

            def map = [:]

            if (!shp.exists() && !tif.exists()) {
                log.error("No SHP or TIF available....")
                map.put("error", "no layer files")
            } else {
                def errors = publishService.layerToGeoserver([files: [shp.exists() ? shp.getPath() : bil.getPath()]], null)

                if (errors) {
                    log.error("Errors uploading to geoserver...." + errors.inspect())
                    map.put("error", errors.inspect())
                } else {
                    map.put("raw_id", name)
                    map.put("columns", columns)
                    map.put("test_id", newName)
                    map.put("test_url",
                            grailsApplication.config.geoserver.url.toString() +
                                    "/ALA/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + newName +
                                    "&styles=&bbox=-180,-90,180,90&width=512&height=507&srs=EPSG:4326&format=application/openlayers")

                    FileUtils.writeStringToFile(new File(pth.getPath() + "/upload.json"), JSONValue.toJSONString(map))
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
                    output[1] = 'UNAUTHORIZED: ' + url;
                    break
            }

        }

        return output
    }

    def bilToGTif(bil, geotiff) {

        log.info("BIL conversion to GeoTIFF with gdal_translate...")
        //bil 2 geotiff (?)
        String[] cmd = [grailsApplication.config.gdal.dir.toString() + "/gdal_translate", "-of", "GTiff",
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

        cmd = [grailsApplication.config.gdal.dir + '/gdaladdo',
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

    def getAllLayers(url) {
        def layers
        def fields
        if (!url) {
            layers = layerDao.getLayersForAdmin()
            fields = fieldDao.getFields()
        } else {
            try {
                layers = []
                JSON.parse(Util.getUrl("${url}/layers?all=true")).each {
                    def layer = new ObjectMapper().readValue(it.toString(), Map.class)
                    layers.push(layer)
                }
            } catch (err) {
                log.error 'failed to get all layers', err
            }
            try {
                fields = []
                JSON.parse(Util.getUrl("${url}/fields?all=true")).each {
                    def field = new ObjectMapper().readValue(it.toString(), Field.class)
                    fields.push(field)
                }
            } catch (err) {
                log.error 'failed to get all fields', err
            }
        }

        def list = []
        layers.each { l ->
            def m = l instanceof Map ? l : l.toMap()

            //get fields
            def fs = []
            fields.each { f ->
                if (f.getSpid().equals(String.valueOf(m.get('id')))) {
                    fs.add(f)
                }
            }
            m.put("fields", fs)

            list.add(m)
        }

        return list
    }

    def layerMap(layerId) {
        String layersDir = grailsApplication.config.data.dir

        //fetch info
        def map = [:]

        Map upload = getUpload(layerId)

        map.putAll(upload)

        try {
            JSONParser jp = new JSONParser()
            JSONObject jo = (JSONObject) jp.parse(FileUtils.readFileToString(new File(layersDir + "/uploads/" + layerId + "/upload.json")))
            map.putAll(jo)
        } catch (Exception e) {
            try {
                Layer l = layerDao.getLayerById(Integer.parseInt(layerId.replaceAll("[ec]l", "")), false)
                if (upload.size() == 0) map.putAll(l.toMap())
                //try to get from layer info
                map.put("raw_id", l.getId())
                if (!map.containsKey("layer_id")) {
                    map.put("layer_id", l.getId() + "")
                }
                //TODO: stop this failing when the table is not yet created
                //map.put("columns", layerDao.getLayerColumns(l.getId()));
                map.put("test_url",
                        grailsApplication.config.geoserver.url +
                                "/ALA/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + l.getName() +
                                "&styles=&bbox=-180,-90,180,90&width=512&height=507&srs=EPSG:4326&format=application/openlayers")
            } catch (Exception e2) {
                log.error("failed to find layer for rawId: " + layerId, e2);
            }

        }

        map.put("has_layer", map.containsKey("layer_id"))
        if (map.containsKey("layer_id")) {
            Layer layer = layerDao.getLayerById(Integer.parseInt(map.layer_id), false)

            if(layer) {
                map.putAll(layer.toMap())
            }

            List<Field> fieldList = fieldDao.getFields()
            def fields = []
            for (Field fs : fieldList) {
                if (fs.getSpid().equals(map.get('layer_id'))) {
                    fields.add(fs)
                }
            }

            map.put("fields", fields)

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
        List<Layer> layers = layerDao.getLayersForAdmin()
        for (Layer l : layers) {
            classifications.add(l.getClassification1() + " > " + l.getClassification2())
        }
        List<String> classes = new ArrayList<String>(classifications)
        Collections.sort(classes)
        map.put("classifications", classes)

        return map
    }

    def deleteLayer(String id) {

        String layersDir = grailsApplication.config.data.dir
        String geoserverUrl = grailsApplication.config.geoserver.url
        String geoserverUsername = grailsApplication.config.geoserver.username
        String geoserverPassword = grailsApplication.config.geoserver.password

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
                    Field field = (Field) o

                    fieldDao.delete(field.getId())

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
            layerDao.delete(layerId)

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
            if (it.containsKey('layer_id') && it.layer_id.equals(id)) {
                new File(grailsApplication.config.data.dir.toString() + "/uploads/" + it.raw_id + "/layer.id").delete()
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

        //Soft deletes
        File dir = new File(layersDir + "/uploads/" + id + "/");
        if (dir.exists()) {
            try {
                def deletedUploadsDir = new File(layersDir + "/uploads-deleted/")
                FileUtils.forceMkdir(deletedUploadsDir)
                FileUtils.moveDirectory(dir, new File(layersDir + "/uploads-deleted/" + id ));
            } catch (Exception e) {
                log.error("Problem moving directory. Unable to move", e.getMessage())
            }
        }
    }

    def deleteField(String fieldId) {
        String layersDir = layerIntersectDao.getConfig().getLayerFilesPath()

        //fields
        Field field = fieldDao.getFieldById(fieldId)
        if (field != null) {

            fieldDao.delete(fieldId)

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

        String layersDir = grailsApplication.config.data.dir

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

        int countInDB = 0
        for (Field f : fieldDao.getFieldsByDB()) {
            if (f.getSpid().equals(String.valueOf(fieldMap.get("id")))) {
                countInDB++
            }
        }
        boolean isContextual = "Contextual".equalsIgnoreCase(String.valueOf(layerMap.get("type")))
        fieldMap.put("indb", countInDB == 0)
        fieldMap.put("intersect", false) //countInDB == 0 && isContextual);
        fieldMap.put("analysis", countInDB == 0)
        fieldMap.put("addtomap", countInDB == 0)
        fieldMap.put("enabled", true)

        fieldMap.put("requestedId", (isContextual ? "cl" : "el") + layerId)

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
                ((List) fieldMap.get("columns")).size > 0) {
            fieldMap.put("sid", ((List) fieldMap.get("columns")).get(0))
            fieldMap.put("sname", ((List) fieldMap.get("columns")).get(0))
            //"sdesc" is optional
        }

        return fieldMap
    }

    double[] getExtents(String rawId) {
        String geoserverUrl = grailsApplication.config.geoserver.url
        String geoserverUsername = grailsApplication.config.geoserver.username
        String geoserverPassword = grailsApplication.config.geoserver.password

        double[] extents = null

        try {
            String[] out = httpCall("GET",
                    geoserverUrl + "/rest/workspaces/ALA/datastores/" + rawId + "/featuretypes/" + rawId + ".json",
                    geoserverUsername, geoserverPassword,
                    null,
                    null,
                    "text/plain")

            JSONParser jp = new JSONParser()
            JSONObject jo = (JSONObject) jp.parse(out[1])

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

            JSONParser jp = new JSONParser()
            try {
                JSONObject jo = (JSONObject) jp.parse(out[1])
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

    def fieldMap(fieldId) {
        def layer = layerDao.getLayerById(Integer.parseInt(fieldDao.getFieldById(fieldId, false).spid), false)

        def map = fieldMapDefault(String.valueOf(layer.id))
        map.put("layerName", layer.name) // layer name for wms requests

        def field = fieldDao.getFieldById(fieldId, false)
        map.put("id", field.getId())
        map.put("desc", field.getDesc())
        map.put("name", field.getName())
        map.put("sdesc", field.getSdesc())
        map.put("sid", field.getSid())
        map.put("sname", field.getSname())
        map.put("spid", field.getSpid())
        map.put("type", field.getType())
        map.put("addtomap", field.isAddtomap())
        map.put("analysis", field.isAnalysis())
        map.put("defaultlayer", field.isDefaultlayer())
        map.put("enabled", field.isEnabled())
        map.put("indb", field.isIndb())
        map.put("intersect", field.isIntersect())
        map.put("layerbranch", field.isLayerbranch())
        map.put("namesearch", field.isNamesearch())
        map.put("is_field", true)

        map
    }


    def createOrUpdateLayer(Map layer, String id, boolean createTask = true) {
        Map retMap = [:]

        retMap.put('layer_id', id)

        if (layer.name == null || layer.name.isEmpty()) {
            retMap.put("error", "name parameter missing")
            retMap.putAll(layerMap(String.valueOf(id)))
            retMap.putAll(layer)
        } else {
            //UPDATE
            Integer intId = null
            try {
                //look for upload layer.id to use instead of upload id
                File idfile = new File(grailsApplication.config.data.dir.toString() + "/uploads/" + id + "/layer.id")
                if (idfile.exists()) {
                    //update id
                    layer.id = FileUtils.readFileToString(idfile)
                }
                intId = Integer.parseInt(layer.id.toString())
            } catch (err) {
                log.debug 'unable to read uploads layer.id for ' + id
            }
            def originalLayer = intId == null ? null : layerDao.getLayerById(intId, false)
            if (originalLayer != null) {
                try {
                    if (layer.containsKey('classification1')) originalLayer.setClassification1(layer.classification1.toString())
                    if (layer.containsKey('classification2')) originalLayer.setClassification2(layer.classification2.toString())
                    if (layer.containsKey('citation_date')) originalLayer.setCitation_date(layer.citation_date.toString())
                    if (layer.containsKey('datalang')) originalLayer.setDatalang(layer.datalang.toString())
                    if (layer.containsKey('description')) originalLayer.setDescription(layer.description.toString())
                    if (layer.containsKey('displayname')) originalLayer.setDisplayname(layer.displayname.toString())
                    if (layer.containsKey('domain')) originalLayer.setDomain(layer.domain.toString())
                    if (layer.containsKey('enabled')) originalLayer.setEnabled('on'.equals(layer.enabled))
                    if (layer.containsKey('environmentalvalueunits')) originalLayer.setEnvironmentalvalueunits(layer.environmentalvalueunits.toString())
                    if (layer.containsKey('keywords')) originalLayer.setKeywords(layer.keywords.toString())
                    if (layer.containsKey('licence_level')) originalLayer.setLicence_level(layer.licence_level.toString())
                    if (layer.containsKey('licence_link')) originalLayer.setLicence_link(layer.licence_link.toString())
                    if (layer.containsKey('licence_notes')) originalLayer.setLicence_notes(layer.licence_notes.toString())
                    if (layer.containsKey('mddatest')) originalLayer.setMddatest(layer.mddatest.toString())
                    if (layer.containsKey('mdhrlv')) originalLayer.setMdhrlv(layer.mdhrlv.toString())
                    if (layer.containsKey('metadatapath')) originalLayer.setMetadatapath(layer.metadatapath.toString())
                    if (layer.containsKey('notes')) originalLayer.setNotes(layer.notes.toString())
                    if (layer.containsKey('respparty_role')) originalLayer.setRespparty_role(layer.respparty_role.toString())
                    if (layer.containsKey('source_link')) originalLayer.setSource_link(layer.source_link.toString())
                    if (layer.containsKey('source')) originalLayer.setSource(layer.source.toString())
                    if (layer.containsKey('type')) originalLayer.setType(layer.type.toString())

                    layerDao.updateLayer(originalLayer)

                    //record layer.id
                    FileUtils.write(new File(grailsApplication.config.data.dir.toString() + "/uploads/" + id + "/layer.id"), String.valueOf(originalLayer.getId()))

                    retMap.put('message', 'Layer updated')
                } catch (err) {
                    log.error 'error updating layer: ' + id, err
                    retMap.put('error', 'error updating layer: ' + err.getMessage())
                }
            } else {
                try {
                    //defaults, in case of missing values
                    def defaultLayer = layerMap(id)
                    if (!layer.containsKey('name')) layer.put('name', defaultLayer.name)
                    if (!layer.containsKey('environmentalvaluemin')) layer.put('environmentalvaluemin', defaultLayer.environmentalvaluemin)
                    if (!layer.containsKey('environmentalvaluemax')) layer.put('environmentalvaluemax', defaultLayer.environmentalvaluemax)
                    if (!layer.containsKey('extent')) layer.put('extent', defaultLayer.extent)
                    if (!layer.containsKey('domain')) layer.put('domain', defaultLayer.domain)
                    if (!layer.containsKey('maxlatitude')) layer.put('maxlatitude', String.valueOf(defaultLayer.maxlatitude))
                    if (!layer.containsKey('minlatitude')) layer.put('minlatitude', String.valueOf(defaultLayer.minlatitude))
                    if (!layer.containsKey('maxlongitude')) layer.put('maxlongitude', String.valueOf(defaultLayer.maxlongitude))
                    if (!layer.containsKey('minlongitude')) layer.put('minlongitude', String.valueOf(defaultLayer.minlongitude))
                    if (!layer.containsKey('displayname')) layer.put('displayname', defaultLayer.displayname)
                    if (!layer.containsKey('enabled')) layer.put('enabled', 'on')
                    else if (layer.enabled != null && String.valueOf(layer.enabled).equalsIgnoreCase('true')) layer.put('enabled', 'on')
                    if (!layer.containsKey('environmentalvalueunits')) layer.put('environmentalvalueunits', defaultLayer.environmentalvalueunits)
                    if (!layer.containsKey('type')) layer.put('type', defaultLayer.type)

                    //CREATE
                    layer.remove('id')

                    if (layerDao.getLayerByName(layer.name.toString(), false) != null) {
                        retMap.put("error", "name: " + layer.name + " is not unique")
                        retMap.putAll(layerMap(String.valueOf(id)))
                        retMap.putAll(layer)
                    }

                    //default values from the name
                    layer.displayPath = grailsApplication.config.geoserver.url +
                            "/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" +
                            layer.name + "&format=image/png&styles="

                    def newLayer = new Layer()
                    if (layer.displayPath != null) newLayer.setDisplaypath(layer.displayPath.toString())
                    if (layer.environmentalvaluemin != null) newLayer.setEnvironmentalvaluemin(layer.environmentalvaluemin.toString())
                    if (layer.environmentalvaluemax != null) newLayer.setEnvironmentalvaluemax(layer.environmentalvaluemax.toString())
                    if (layer.extent != null) newLayer.setExtent(layer.extent.toString())
                    if (layer.maxlatitude != null) newLayer.setMaxlatitude(Double.parseDouble(String.valueOf(layer.maxlatitude)))
                    if (layer.minlatitude != null) newLayer.setMinlatitude(Double.parseDouble(String.valueOf(layer.minlatitude)))
                    if (layer.maxlongitude != null) newLayer.setMaxlongitude(Double.parseDouble(String.valueOf(layer.maxlongitude)))
                    if (layer.minlongitude != null) newLayer.setMinlongitude(Double.parseDouble(String.valueOf(layer.minlongitude)))
                    if (layer.name != null) newLayer.setName(layer.name.toString())
                    if (layer.classification1 != null) newLayer.setClassification1(layer.classification1.toString())
                    if (layer.classification2 != null) newLayer.setClassification2(layer.classification2.toString())
                    if (layer.citation_date != null) newLayer.setCitation_date(layer.citation_date.toString())
                    if (layer.datalang != null) newLayer.setDatalang(layer.datalang.toString())
                    if (layer.description != null) newLayer.setDescription(layer.description.toString())
                    if (layer.displayname != null) newLayer.setDisplayname(layer.displayname.toString())
                    if (layer.domain != null) newLayer.setDomain(layer.domain.toString())
                    newLayer.setEnabled('on'.equals(layer.enabled))
                    if (layer.environmentalvalueunits != null) newLayer.setEnvironmentalvalueunits(layer.environmentalvalueunits.toString())
                    if (layer.keywords != null) newLayer.setKeywords(layer.keywords.toString())
                    if (layer.licence_level != null) newLayer.setLicence_level(layer.licence_level.toString())
                    if (layer.licence_link != null) newLayer.setLicence_link(layer.licence_link.toString())
                    if (layer.licence_notes != null) newLayer.setLicence_notes(layer.licence_notes.toString())
                    if (layer.mddatest != null) newLayer.setMddatest(layer.mddatest.toString())
                    if (layer.mdhrlv != null) newLayer.setMdhrlv(layer.mdhrlv.toString())
                    if (layer.metadatapath != null) newLayer.setMetadatapath(layer.metadatapath.toString())
                    if (layer.notes != null) newLayer.setNotes(layer.notes.toString())
                    if (layer.respparty_role != null) newLayer.setRespparty_role(layer.respparty_role.toString())
                    if (layer.source != null) newLayer.setSource_link(layer.source_link.toString())
                    if (layer.source != null) newLayer.setSource(layer.source.toString())
                    if (layer.type != null) newLayer.setType(layer.type.toString())
                    if (layer?.path_orig) {
                        newLayer.setPath_orig(layer.path_orig.toString())
                    } else {
                        if ("environmental".equalsIgnoreCase(layer.type.toString())) {
                            newLayer.setPath_orig('layer/' + layer.name)
                        } else if (new File(grailsApplication.config.data.dir.toString() + "/uploads/" + id + "/" + id + ".shp").exists()) {
                            newLayer.setPath_orig('layer/' + layer.name)
                        } else {
                            newLayer.setPath_orig('layer/' + layer.name)
                        }
                    }

                    newLayer.setDt_added(new Date())

                    //attempt to set layer id
                    if (layer.containsKey('requestedId') && layer.requestedId.length() > 0) {
                        newLayer.setId(Long.parseLong(String.valueOf(layer.requestedId)))
                    } else {
                        newLayer.setId(0)
                    }

                    //create default layers table entry, this updates layer.id
                    Task.withNewTransaction {
                        layerDao.addLayer(newLayer)
                    }

                    //record layer.id
                    FileUtils.write(new File(grailsApplication.config.data.dir.toString() + "/uploads/" + id + "/layer.id"), String.valueOf(newLayer.getId()))

                    if (createTask) {
                        tasksService.create('LayerCreation', id, [layerId: String.valueOf(newLayer.getId()), uploadId: String.valueOf(id)], null, null, null)
                    }

                    retMap.put('layer_id', newLayer.id)
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

    def createOrUpdateField(Map field, String id, boolean createTask = true) {

        def retMap = [:]

        if (field.type.equalsIgnoreCase("c") &&
                (field.sid == null || field.sid.isEmpty())) {
            retMap.put("error", "name parameter missing")
        } else {
            //make it simple, sname = sid
            if (field.sname == null || field.sname.isEmpty()) {
                field.put('sname', field.sid)
            }

            //swap 'true' with 'on'
            if (field.containsKey('addtomap') && "true".equalsIgnoreCase(String.valueOf(field.addtomap))) field.addtomap = 'on'
            if (field.containsKey('analysis') && "true".equalsIgnoreCase(String.valueOf(field.analysis))) field.analysis = 'on'
            if (field.containsKey('defaultlayer') && "true".equalsIgnoreCase(String.valueOf(field.defaultlayer))) field.defaultlayer = 'on'
            if (field.containsKey('enabled') && "true".equalsIgnoreCase(String.valueOf(field.enabled))) field.enabled = 'on'
            if (field.containsKey('indb') && "true".equalsIgnoreCase(String.valueOf(field.indb))) field.indb = 'on'
            if (field.containsKey('intersect') && "true".equalsIgnoreCase(String.valueOf(field.intersect))) field.intersect = 'on'
            if (field.containsKey('layerbranch') && "true".equalsIgnoreCase(String.valueOf(field.layerbranch))) field.layerbranch = 'on'
            if (field.containsKey('namesearch') && "true".equalsIgnoreCase(String.valueOf(field.namesearch))) field.namesearch = 'on'

            //UPDATE
            Field originalField = fieldDao.getFieldById(id, false)
            if (originalField != null) {

                //update select values
                try {
                    //flag background processes that need running 
                    boolean updateIntersect = field.intersect != null && field.intersect != originalField.isIntersect() && field.intersect
                    boolean updateNameSearch = field.namesearch != null && "on".equalsIgnoreCase(field.namesearch.toString()) != originalField.isNamesearch()

                    boolean b = field.addtomap != null ? "on".equalsIgnoreCase(field.addtomap.toString()) : false
                    originalField.setAddtomap(b)

                    b = field.analysis != null ? "on".equalsIgnoreCase(field.analysis.toString()) : false
                    originalField.setAnalysis(b)

                    b = field.defaultlayer != null ? "on".equalsIgnoreCase(field.defaultlayer.toString()) : false
                    originalField.setDefaultlayer(b)
                    originalField.setDesc(field.desc.toString())
                    originalField.setName(field.name.toString())

                    b = field.enabled != null ? "on".equalsIgnoreCase(field.enabled.toString()) : false
                    originalField.setEnabled(b)

                    b = field.indb != null ? "on".equalsIgnoreCase(field.indb.toString()) : false
                    originalField.setIndb(b)

                    b = field.intersect != null ? "on".equalsIgnoreCase(field.intersect.toString()) : false
                    originalField.setIntersect(b)

                    b = field.layerbranch != null ? "on".equalsIgnoreCase(field.layerbranch.toString()) : false
                    originalField.setLayerbranch(b)

                    b = field.namesearch != null ? "on".equalsIgnoreCase(field.namesearch.toString()) : false
                    originalField.setNamesearch(b)

                    originalField.setType(field.type.toString())

                    fieldDao.updateField(originalField)

                    if (createTask && updateIntersect) {
                        tasksService.create('TabulationCreate', null, [:])
                    }

                    retMap.put('message', 'Field updated')

                    if (createTask && updateNameSearch) {
                        tasksService.create('NameSearchUpdate', null, null)
                    }
                } catch (err) {
                    log.error 'error updating field: ' + id, err
                }
            } else {

                //defaults, in case of missing values
                def defaultField = fieldMapDefault(id)
                if (!field.containsKey('addtomap')) field.put('addtomap', 'on')
                if (!field.containsKey('analysis')) field.put('analysis', 'on')
                if (!field.containsKey('defaultlayer')) field.put('defaultlayer', 'on')
                if (!field.containsKey('enabled')) field.put('enabled', 'on')
                if (!field.containsKey('intersect')) field.put('intersect', 'off')
                if (!field.containsKey('namesearch')) field.put('namesearch', 'on')
                if (!field.containsKey('layerbranch')) field.put('layerbranch', 'off')
                if (!field.containsKey('name')) field.put('name', defaultField.name)
                if (!field.containsKey('type')) field.put('name', defaultField.type)

                Map lyr
                if (field.containsKey('spid')) {
                    lyr = layerMap(field.spid)
                } else {
                    lyr = layerMap(id)
                }

                if ("contextual".equalsIgnoreCase(lyr.type.toString())) {
                    //match case insensitive for sname, sid, sdesc
                    if (defaultField.columns instanceof List) {
                        if (field.containsKey('sid') && !defaultField.columns.contains(field.sid)) {
                            defaultField.columns.each { if (it.equalsIgnoreCase(field.sid)) field.sid = it }
                        }
                        if (field.containsKey('sname') && !defaultField.columns.contains(field.sname)) {
                            defaultField.columns.each { if (it.equalsIgnoreCase(field.sname)) field.sname = it }
                        }
                        if (field.containsKey('sdesc') && !defaultField.columns.contains(field.sdesc)) {
                            defaultField.columns.each { if (it.equalsIgnoreCase(field.sdesc)) field.sdesc = it }
                        }
                    }
                }

                def newField = new Field()

                newField.setSpid(String.valueOf(lyr.id))

                boolean b = field.analysis != null ? "on".equals(field.addtomap) : false
                newField.setAddtomap(b)

                b = field.analysis != null ? "on".equals(field.analysis) : false
                newField.setAnalysis(b)

                b = field.defaultlayer != null ? "on".equals(field.defaultlayer) : false
                newField.setDefaultlayer(b)

                newField.setDesc(field.desc.toString())
                newField.setName(field.name.toString())

                b = field.enabled != null ? "on".equals(field.enabled) : false
                newField.setEnabled(b)

                b = field.indb != null ? "on".equals(field.indb) : false
                newField.setIndb(b)

                b = field.intersect != null ? "on".equals(field.intersect) : false
                newField.setIntersect(b)

                b = field.layerbranch != null ? "on".equals(field.layerbranch) : false
                newField.setLayerbranch(b)

                b = field.namesearch != null ? "on".equals(field.namesearch) : false
                newField.setNamesearch(b)
                if ("contextual".equalsIgnoreCase(lyr.type.toString())) {
                    newField.setSdesc(field.sdesc.toString())
                    newField.setSid(field.sid.toString())
                    newField.setSname(field.sname.toString())
                }
                newField.setType(field.type.toString())

                //attempt to set field id
                if (field.containsKey('requestedId')) {
                    newField.setId(field.requestedId.toString())
                } else {
                    newField.setId(null)
                }

                //create default layers table entry, this updates field.id
                Task.withNewTransaction {
                    fieldDao.addField(newField)
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
            String info = FileUtils.readFileToString(grd)
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
        String dir = grailsApplication.config.data.dir

        //fetch info
        Map map = [:]

        Map upload = getUpload(uploadId)

        if (upload.size() > 0) {
            map.putAll(upload)

            try {
                JSONParser jp = new JSONParser()
                File f = new File(dir + "/uploads/" + uploadId + "/upload.json")
                if (f.exists()) {
                    JSONObject jo = (JSONObject) jp.parse(FileUtils.readFileToString(f))
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
        String dir = grailsApplication.config.data.dir

        //fetch info
        Map map = [:]

        Map upload = getUpload(uploadId)

        if (upload.size() > 0) {
            map.putAll(upload)

            try {
                JSONParser jp = new JSONParser()
                File f = new File(dir + "/uploads/" + uploadId + "/upload.json")
                if (f.exists()) {
                    JSONObject jo = (JSONObject) jp.parse(FileUtils.readFileToString(f))
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
                FileUtils.write(new File(grailsApplication.config.data.dir.toString() + "/uploads/" + uploadId + "/distribution.id"),
                        data.data_resource_uid.toString())

                tasksService.create('DistributionCreation', uploadId,
                        [data_resource_uid: String.valueOf(data.data_resource_uid), uploadId: String.valueOf(uploadId)])

                retMap.put('data_resource_uid', data.data_resource_uid)
                retMap.put('message', 'Distribution import started')
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
            FileUtils.write(new File(grailsApplication.config.data.dir.toString() + "/uploads/" + uploadId + "/checklist.id"),
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
                layerDao.getConnection().createStatement().execute(
                        'DELETE FROM distributions WHERE data_resource_uid = \'' + m.data_resource_uid + '\';')
            }
        } catch (err) {
            log.error 'failed to delete data resource uid records for uploadId: ' + id, err
        }

        try {
            def f = new File(grailsApplication.config.data.dir.toString() + "/uploads/" + id + "/distribution.id")
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
                layerDao.getConnection().createStatement().execute(
                        'DELETE FROM distributions WHERE data_resource_uid = \'' + m.checklist + '\';')
            }
        } catch (err) {
            log.error 'failed to delete data resource uid records for uploadId: ' + id, err
        }

        try {
            def f = new File(grailsApplication.config.data.dir.toString() + "/uploads/" + id + "/checklist.id")
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
    def updateFromRemote(spatialServiceUrl, fieldId) {
        def field = JSON.parse(httpCall("GET",
                spatialServiceUrl + "/field/${fieldId}?pageSize=0",
                null, null,
                null,
                null,
                "application/json")[1])

        def layer = JSON.parse(httpCall("GET",
                spatialServiceUrl + "/layer/${field.spid}?pageSize=0",
                null, null,
                null,
                null,
                "application/json")[1])

        //update postgres
        layer.requestedId = layer.id + ''
        field.requestedId = field.id + ''

        //fix displaypath
        def origDisplayPath = layer.displaypath
        layer.displaypath = grailsApplication.config.geoserver.url + layer.displaypath.substring(layer.displaypath.indexOf("/gwc/"))

        //create as disabled if creating
        if (!layerDao.getLayerById(layer.id, false)) {
            layer.enabled = false
        }
        createOrUpdateLayer(layer as Map, layer.id + '', false)

        if (fieldDao.getFieldById(field.id, false) == null) {
            field.enabled = false
            createOrUpdateField(field as Map, field.requestedId + '', false)
        } else {
            createOrUpdateField(field as Map, field.id + '', false)
        }

        Map input = [layerId: layer.requestedId, fieldId: field.id, sourceUrl: spatialServiceUrl, displayPath: origDisplayPath] as Map
        Task task = tasksService.create("LayerCopy", UUID.randomUUID(), input)

        task

    }

    // Schedule to run once, 5 mins after startup
    // Create outline/polygon style for Vector layer
    // Create a linear/none linear style for each Raster layer
    @Scheduled(initialDelay = 3000000L, fixedDelay = Long.MAX_VALUE)
    def fixLayerStyles() {
        def geoserverUrl = grailsApplication.config.geoserver.url
        def geoserverUsername = grailsApplication.config.geoserver.username
        def geoserverPassword = grailsApplication.config.geoserver.password

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

        au.org.ala.spatial.util.UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, "outline")
        File tmpFile = File.createTempFile("sld", "xml")
        FileUtils.writeStringToFile(tmpFile, data, "UTF-8");
        au.org.ala.spatial.util.UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + "outline",
                extra, geoserverUsername, geoserverPassword, tmpFile.path);

        def fields = fieldDao.getFieldsByCriteria('')
        fields.each { field ->
            def styleName = field.id
            def layerName = field.layer.name

            def stylesRequired = []
            if ('c'.equals(field.type)) {
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
                FileUtils.writeStringToFile(tmpFile, getLinearStyle(layerName, false), "UTF-8");
                UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + linear,
                        extra, geoserverUsername, geoserverPassword, tmpFile.path)
            }

            stylesRequired.each { style ->
                data = "<style><name>" + style + "</name></style>"
                UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + layerName + "/styles", "",
                        geoserverUsername, geoserverPassword, data)
                UploadSpatialResource.addGwcStyle(geoserverUrl, layerName, style, geoserverUsername, geoserverPassword)
            }
        }
    }

    private def getLinearStyle(String name, boolean reversed) {

        String dir = grailsApplication.config.data.dir
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

        String classSld = '<?xml version="1.0" encoding="UTF-8"?><StyledLayerDescriptor xmlns="http://www.opengis.net/sld">' +
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

    
