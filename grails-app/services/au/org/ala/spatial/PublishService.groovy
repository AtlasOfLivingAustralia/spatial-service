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

import au.org.ala.spatial.SpatialConfig
import au.org.ala.spatial.dto.TaskWrapper
import au.org.ala.spatial.util.UploadSpatialResource
import grails.converters.JSON
import org.apache.commons.io.FileUtils

class PublishService {

    def manageLayersService
    def tasksService
    def fileService
    def dataSource
    LayerService layerService
    FieldService fieldService
    SpatialObjectsService spatialObjectsService
    SpatialConfig spatialConfig

    // Unpacks a published zip file and performs some actions.
    // Run time should be kept to a minimum because a spatial-slave is waiting for this to complete
    // before flagging task as finished.
    //
    // returns error map
    void publish(TaskWrapper taskWrapper) {
        // deploy outputs
        taskWrapper.task.output.each { OutputParameter output ->
            String k = output.name
            if ('file'.equalsIgnoreCase(k) || 'metadata'.equalsIgnoreCase(k)) {
                // no activity required. The unzip takes care of this

            } else if ('delete'.equalsIgnoreCase(k)) {
                delete(output, taskWrapper.path)

            } else if ('shapefile'.equalsIgnoreCase(k) || 'raster'.equalsIgnoreCase(k) || 'layer'.equalsIgnoreCase(k) ||
                    'layers'.equalsIgnoreCase(k) || 'envelopes'.equalsIgnoreCase(k)) {
                // note: identify single area shapefile and raster as contextual layer variations

                // put into geoserver or as new layer
                taskWrapper.task.history.putAll(layerToGeoserver(output, taskWrapper.path))

                // update output.wms

                // update output.geoserverLayerName

                // register as a layer (single area or contextual or environmental)
            } else if ('sld'.equalsIgnoreCase(k)) {
                //skipSLDCreation
                addStyle(output, taskWrapper.path)
            } else if ('areas'.equalsIgnoreCase(k)) {
                addArea(output, taskWrapper.path)
            } else if ('sql'.equalsIgnoreCase(k)) {
                // trigger sql execution
                taskWrapper.task.history.putAll(runSql(output, taskWrapper.path))
            } else if ('append'.equalsIgnoreCase(k)) {
                if (output.file != null) {
                    String f = JSON.parse(output.file).get(0)
                    def idx = f.indexOf('?')
                    String file = f.substring(0, idx)
                    String append = f.substring(idx + 1) + '\n'
                    new File(spatialConfig.data.dir + file).append(append)
                }
            }
        }

        // create requested tasks
        // deploy outputs
        taskWrapper.task.output.each { OutputParameter output ->
            String k = output.name
            if ('process'.equalsIgnoreCase(k)) {
                JSON.parse(output.file).each { file ->
                    def pos = file.toString().indexOf(' ')
                    def name
                    def input
                    def tag
                    if (pos <= 0) {
                        name = file
                        input = [:]
                        tag = ''
                    } else {
                        name = file.substring(0, pos)
                        input = JSON.parse(file.substring(pos + 1))
                        tag = file.substring(pos + 1)
                    }
                    tasksService.create(name, tag, input)
                }
            }
        }

        // create download zip
        taskWrapper.task.output.each { OutputParameter output ->
            String k = output.name
            if ('download'.equalsIgnoreCase(k)) {
                fileService.zip(taskWrapper.path + File.separator + "download.zip", taskWrapper.path, JSON.parse(output.file))
            }
        }

        // Fix layer styles after LayerCopy and FieldCreation
        if (taskWrapper.spec.name == 'LayerCopy' || taskWrapper.spec.name == 'FieldCreation') {
            manageLayersService.fixLayerStyles()
        }
    }

    def addStyle(OutputParameter output, path) {
        def errors = [:]
        try {
            if (spatialConfig.geoserver.canDeploy.toBoolean()) {

                def geoserverUrl = spatialConfig.geoserver.url
                def geoserverUsername = spatialConfig.geoserver.username
                def geoserverPassword = spatialConfig.geoserver.password

                JSON.parse(output.file).each { file ->

                    def p = (file.startsWith('/') ? spatialConfig.data.dir + file : path + '/' + file)
                    def name = new File(p).getName().replace(".sld", "")

                    //Create style
                    String extra = ""
                    String out = UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                            extra, geoserverUsername, geoserverPassword, name)
                    if (!out.startsWith("200") && !out.startsWith("201")) {
                        //ignore errors
                        errors.put(String.valueOf(System.currentTimeMillis()), out)
                    }

                    //Upload sld
                    out = UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + name,
                            extra, geoserverUsername, geoserverPassword, p)
                    if (!out.startsWith("200") && !out.startsWith("201")) {
                        errors.put(String.valueOf(System.currentTimeMillis()), out)
                    } else {
                        //when the sld is for a field, apply to the layer as the default sld
                        def field = fieldService.getFieldById(name, false)
                        if (field != null) {
                            def layer = layerService.getLayerById(Integer.parseInt(field.spid), false)
                            if (layer != null) {
                                //Apply style
                                String data = "<layer><enabled>true</enabled><defaultStyle><name>" + name +
                                        "</name></defaultStyle></layer>"
                                out = UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + layer.name, extra,
                                        geoserverUsername, geoserverPassword, data)
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    //ignore errors
                                    // errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }

                                //add Style to layer styles
                                data = "<style><name>" + name + "</name></style>"
                                out = UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + layer.name + "/styles", extra,
                                        geoserverUsername, geoserverPassword, data)
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    //ignore errors
                                    // errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }

                                out = UploadSpatialResource.addGwcStyle(geoserverUrl, layer.name, name, geoserverUsername, geoserverPassword)
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    //ignore errors
                                    // errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }
                            }
                        }

                    }
                }

            }
        } catch (err) {
            log.error 'failed to upload sld: ' + output + ', ' + path, err
        }

        errors

    }

    def delete(OutputParameter output, path) {
        def errors = [:]
        try {
            JSON.parse(output.file).each { file ->

                def p = (file.startsWith('/') ? spatialConfig.data.dir + file : path + '/' + file)
                def f = new File(p)
                if (f.exists()) {
                    try {
                        f.delete()
                    } catch (err) {
                        log.error 'failed to delete file: ' + file + ', ' + path, err
                    }
                }
            }
        } catch (err) {
            log.error 'failed to delete file: ' + output + ', ' + path, err
        }

        errors

    }

    def addArea(OutputParameter output, String path) {
        def errors = [:]
        try {
            def newAreas = []
            JSON.parse(output.file).each { json ->

                def values = JSON.parse(json)
                String p = (values.file.startsWith('/') ? spatialConfig.data.dir + values.file : path + '/' + values.file)

                String wkt = new File(p).text

                String generatedPid = spatialObjectsService.createUserUploadedObject(wkt, values.name, values.description, null)

                newAreas.add(generatedPid)
            }
            //replace areas with pids
            output.file = (newAreas as JSON).toString()
        } catch (err) {
            log.error 'failed to upload area: ' + output + ', ' + path, err
        }

        errors
    }

    def runSql(OutputParameter output, path) {
        def errors = [:]
        def conn = dataSource.getConnection()
        def statement = conn.createStatement()
        try {
            JSON.parse(output.file).each { file ->
                String p = (file.startsWith('/') ? spatialConfig.data.dir + file : path + '/' + file)
                try {
                    statement.execute(new File(p).text)
                } catch (err) {
                    errors.put(String.valueOf(System.currentTimeMillis()), 'failed to run sql: ' + p)
                    log.error 'failed to run sql: ' + p, err
                }
            }
        } catch (err) {
            log.error err
        } finally {
            if (statement != null) {
                statement.close()
            }
            if (conn != null) {
                conn.close()
            }
        }
        errors
    }

    def callGeoserver(String type, String urlPath, String file, String resource) {
        return manageLayersService.httpCall(type,
                spatialConfig.geoserver.url + urlPath,
                spatialConfig.geoserver.username,
                spatialConfig.geoserver.password,
                file, resource, "text/plain")
    }

    def callGeoserver(String type, String urlPath, String file, String resource, String contentType) {
        return manageLayersService.httpCall(type,
                spatialConfig.geoserver.url + urlPath,
                spatialConfig.geoserver.username,
                spatialConfig.geoserver.password,
                file, resource, contentType)
    }

    def callGeoserverDelete(String urlPath) {
        def getResponse = callGeoserver("GET", urlPath, null, null)

        // only delete when there is a response status code 2xx
        if (getResponse && getResponse[0].startsWith("2")) {
            return callGeoserver("DELETE", urlPath, null, null)
        } else {
            return null
        }
    }

    Map<String, String> layerToGeoserver(OutputParameter output, path) {
        Map<String, String> errors = [:]
        if (spatialConfig.geoserver.canDeploy.toBoolean()) {

            def geoserverUrl = spatialConfig.geoserver.url
            def geoserverUsername = spatialConfig.geoserver.username
            def geoserverPassword = spatialConfig.geoserver.password

            JSON.parse(output.file).each { f ->
                def p = path == null ? f : (f.startsWith('/') ? spatialConfig.data.dir + f : path + '/' + f)
                def file = f

                if (f.startsWith("{")) {
                    // parse 'file' out of JSON
                    def values = JSON.parse(f)
                    p = (values.file.startsWith('/') ? spatialConfig.data.dir + values.file : path + '/' + values.file)
                    file = values.file
                }
                if (!file.endsWith('.tif') && !file.endsWith('.shp')) {
                    if (new File(p + '.tif').exists()) {
                        p = p + '.tif'
                        file = file + '.tif'
                    } else if (new File(p + '.shp').exists()) {
                        p = p + '.shp'
                        file = file + '.shp'
                    }
                }
                if (file.endsWith('.tif')) {
                    def geotiff = new File(p)
                    def sld = new File(p.replace(".tif", ".sld"))
                    def name = geotiff.getName().replace('.tif', '')

                    if (geotiff.exists()) {
                        try {

                            //TODO: Why is.prj interfering with Geoserver discovering .tif is EPSG:4326?
                            def oldPrj = new File(p.replace('.tif', '.prj'))
                            def tmpPrj = new File(p.replace('.tif', '.prj.tmp'))
                            if (oldPrj.exists()) FileUtils.moveFile(oldPrj, tmpPrj)

                            //attempt to delete
                            callGeoserverDelete("/rest/workspaces/ALA/coveragestores/" + name)

                            if (!spatialConfig.geoserver.spatialservice.colocated) {
                                // delete the tif file if it exists
                                callGeoserverDelete("/rest/resource/data/" + name + ".tif")

                                // delete the prj file if it exists
                                callGeoserverDelete("/rest/resource/data/" + name + ".prj")

                                // upload the tif file
                                callGeoserver("PUT", "/rest/resource/data/" + name + ".tif", geotiff.getPath(), null)

                                // create the layer
                                callGeoserver("PUT", "/rest/workspaces/ALA/coveragestores/" + name + "/external.geotiff?configure=first",
                                        null, "file://" + spatialConfig.geoserver.remote.geoserver_data_dir + "/data/" + name + ".tif")

                                // upload the prj file
                                if (tmpPrj.exists()) {
                                    callGeoserver("PUT", "/rest/resource/data/" + name + ".prj", tmpPrj.getPath(), null)
                                }
                            } else {
                                String[] result = callGeoserver("PUT", "/rest/workspaces/ALA/coveragestores/" + name + "/external.geotiff?configure=first",
                                        null, "file://" + geotiff.getPath())
                                if (result[0] != "200" && result[0] != "201") {
                                    errors.put(String.valueOf(System.currentTimeMillis()), result[0] + ": " + result[1])
                                }
                            }

                            //return prj
                            if (tmpPrj.exists()) FileUtils.moveFile(tmpPrj, oldPrj)

                            if (sld.exists()) {
                                //Create style
                                String extra = ""
                                String out = UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                                        extra, geoserverUsername, geoserverPassword, name)
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }

                                //Upload sld
                                out = UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + name,
                                        extra, geoserverUsername, geoserverPassword, sld.getPath())
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }

                                //Apply style
                                String data = "<layer><enabled>true</enabled><defaultStyle><name>" + name +
                                        "</name></defaultStyle></layer>"
                                out = UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + name, extra,
                                        geoserverUsername, geoserverPassword, data)
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }
                            }

                        } catch (err) {
                            log.error 'failed to upload geotiff to geoserver: ' + geotiff.getPath(), err
                        }
                    }
                } else if (file.endsWith('.shp')) {
                    def shp = new File(p)
                    def name = shp.getName().replace('.shp', '')
                    def sld = new File(name + ".sld")


                    callGeoserverDelete("/rest/workspaces/ALA/datastores/" + name)

                    if (spatialConfig.geoserver.remote.geoserver_data_dir) {
                        for (String filetype : ["shp", "prj", "shx", "dbf", "fix", "sbn", "sbx", "fbn", "fbx", "qix", "cpg", "shp.xml", "atx", "mxs", "ixs", "ain", "aih"]) {
                            // delete file if it exists
                            callGeoserverDelete("/rest/resource/data/" + name + "." + filetype)

                            // upload the file
                            File uploadFile = new File(shp.getPath().replace(".shp", "." + filetype))
                            if (uploadFile.exists()) {
                                callGeoserver("PUT", "/rest/resource/data/" + name + "." + filetype, uploadFile.getPath(), null)
                            }
                        }

                        // create the layer
                        callGeoserver("PUT", "/rest/workspaces/ALA/datastores/" + name + "/external.shp",
                                null, "file://" + shp.getPath())
                    } else {

                        if (spatialConfig.geoserver.spatialservice.colocated) {

                            String[] result = callGeoserver("PUT", "/rest/workspaces/ALA/datastores/" + name + "/external.shp",
                                    null, "file://" + shp.getPath())
                            if ("201" != result[0]) {
                                errors.put(String.valueOf(System.currentTimeMillis()), 'failed to upload shp to geoserver: ' + shp.getPath())
                                log.error 'Failed to upload shp to geoserver: ' + shp.getPath() + ". Check geoserver logs for details"
                            }

                        } else {
                            for (String filetype : ["shp", "prj", "shx", "dbf", "fix", "sbn", "sbx", "fbn", "fbx", "qix", "cpg", "shp.xml", "atx", "mxs", "ixs", "ain", "aih"]) {
                                // upload the file
                                File uploadFile = new File(shp.getPath().replace(".shp", "." + filetype))
                                if (uploadFile.exists()) {
                                    String[] result = callGeoserver("PUT", "/rest/workspaces/ALA/datastores/" + name + "/file.shp",
                                            uploadFile.getPath(), null, "application/octet-stream")
                                    if ("201" != result[0]) {
                                        errors.put(String.valueOf(System.currentTimeMillis()), 'failed to upload file to geoserver: ' + uploadFile.getPath())
                                        log.error 'Failed to load shp into co-located geoserver: ' + shp.getPath() + ". Check geoserver logs for details"
                                    }
                                }
                            }
                        }
                    }

                    if (sld.exists()) {
                        //Create style
                        def out = UploadSpatialResource.sld(geoserverUrl + "/rest/styles/", geoserverUsername, geoserverPassword, name, name, sld.getPath())

                        if (!out.startsWith("200") && !out.startsWith("201")) {
                            errors.put(String.valueOf(System.currentTimeMillis()), out)
                        }
                    }
                }
            }
        }
        errors
    }
}
