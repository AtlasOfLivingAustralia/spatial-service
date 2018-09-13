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

import au.org.ala.spatial.util.UploadSpatialResource
import grails.converters.JSON
import org.apache.commons.io.FileUtils
import org.springframework.jdbc.core.JdbcTemplate

import javax.sql.DataSource

class PublishService {

    def grailsApplication
    def layerIntersectDao
    def manageLayersService
    JdbcTemplate jdbcTemplate
    def tasksService
    def fileService
    def dataSource
    def layerDao
    def fieldDao
    def objectDao

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource
        jdbcTemplate = new JdbcTemplate(dataSource)
    }

    // Unpacks a published zip file and performs some actions.
    // Run time should be kept to a minimum because a spatial-slave is waiting for this to complete
    // before flagging task as finished.
    // 
    // returns error map
    Map publish(zip) {
        //TODO: use a queue

        // unpack zip
        fileService.unzip(zip.getPath(), zip.getParent(), false)

        // read spec.json
        def path = zip.getParent()
        def spec = grails.converters.JSON.parse(FileUtils.readFileToString(new File(path + '/spec.json')))

        // deploy outputs
        spec.output.each { k, output ->
            if ('file'.equalsIgnoreCase(k) || 'metadata'.equalsIgnoreCase(k)) {
                // no activity required. The unzip takes care of this

            } else if ('shapefile'.equalsIgnoreCase(k) || 'raster'.equalsIgnoreCase(k) || 'layer'.equalsIgnoreCase(k) ||
                    'layers'.equalsIgnoreCase(k) || 'envelopes'.equalsIgnoreCase(k)) {
                // note: identify single area shapefile and raster as contextual layer variations

                // put into geoserver or as new layer
                spec.history.putAll(layerToGeoserver(output, path))

                // update output.wms

                // update output.geoserverLayerName

                // register as a layer (single area or contextual or environmental)
            } else if ('sld'.equalsIgnoreCase(k)) {
                addStyle(output, path)
            } else if ('areas'.equalsIgnoreCase(k)) {
                addArea(output, path)
            } else if ('sql'.equalsIgnoreCase(k)) {
                // trigger sql execution
                spec.history.putAll(runSql(output, path))
            } else if ('append'.equalsIgnoreCase(k)) {
                if (output.files != null) {
                    def idx = output.files.get(0).indexOf('?')
                    def file = output.files.get(0).substring(0, idx)
                    def append = output.files.get(0).substring(idx + 1) + '\n'
                    FileUtils.writeStringToFile(new File(grailsApplication.config.data.dir + file), append, true)
                }
            }
        }

        // create requested tasks
        // deploy outputs
        spec.output.each { k, output ->
            if ('process'.equalsIgnoreCase(k)) {
                output.files.each { file ->
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
        spec.output.each { k, output ->
            if ('download'.equalsIgnoreCase(k)) {
                fileService.zip(zip.getParent() + File.separator + "download.zip", zip.getParent(), output.files)
            }
        }

        //delete zip
        zip.delete()

        spec
    }

    def runSqlStatement(sql) {
        def errors = [:]
        def conn = dataSource.getConnection()
        def statement = conn.createStatement()
        try {
            try {
                statement.execute(sql)
            } catch (err) {
                errors.put(String.valueOf(System.currentTimeMillis()), 'failed to run sql: ' + sql)
                log.error 'failed to run sql: ' + sql, err
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

    def addStyle(output, path) {
        def errors = [:]
        try {
            if (grailsApplication.config.geoserver.canDeploy) {

                def geoserverUrl = grailsApplication.config.geoserver.url
                def geoserverUsername = grailsApplication.config.geoserver.username
                def geoserverPassword = grailsApplication.config.geoserver.password

                output.files.each { file ->

                    def p = (file.startsWith('/') ? grailsApplication.config.data.dir + file : path + '/' + file)
                    def name = new File(p).getName().replace(".sld", "")

                    //Create style
                    String extra = "";
                    String out = UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                            extra, geoserverUsername, geoserverPassword, name)
                    if (!out.startsWith("200") && !out.startsWith("201")) {
                        //ignore errors
                        // errors.put(String.valueOf(System.currentTimeMillis()), out)
                    }

                    //Upload sld
                    out = UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + name,
                            extra, geoserverUsername, geoserverPassword, p);
                    if (!out.startsWith("200") && !out.startsWith("201")) {
                        errors.put(String.valueOf(System.currentTimeMillis()), out)
                    } else {
                        //when the sld is for a field, apply to the layer as the default sld
                        def field = fieldDao.getFieldById(name)
                        if (field != null) {
                            def layer = layerDao.getLayerById(Integer.parseInt(field.spid))
                            if (layer != null) {
                                //Apply style
                                String data = "<layer><enabled>true</enabled><defaultStyle><name>" + name +
                                        "</name></defaultStyle></layer>";
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

    def addArea(output, path) {
        def errors = [:]
        try {
            def newAreas = []
            output.files.each { json ->

                def values = JSON.parse(json)
                def p = (values.file.startsWith('/') ? grailsApplication.config.data.dir + values.file : path + '/' + values.file)

                String wkt = FileUtils.readFileToString(new File(p))

                String generatedPid = objectDao.createUserUploadedObject(wkt, values.name, values.description, null);

                newAreas.add(generatedPid)
            }
            //replace areas with pids
            output.files = newAreas
        } catch (err) {
            log.error 'failed to upload area: ' + output + ', ' + path, err
        }

        errors
    }

    def runSql(output, path) {
        def errors = [:]
        def conn = dataSource.getConnection()
        def statement = conn.createStatement()
        try {
            output.files.each { file ->
                def p = (file.startsWith('/') ? grailsApplication.config.data.dir + file : path + '/' + file)
                try {
                    statement.execute(FileUtils.readFileToString(new File(p)))
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

    def layerToGeoserver(output, path) {
        def errors = [:]
        if (grailsApplication.config.geoserver.canDeploy) {

            def geoserverUrl = grailsApplication.config.geoserver.url
            def geoserverUsername = grailsApplication.config.geoserver.username
            def geoserverPassword = grailsApplication.config.geoserver.password

            output.files.each { f ->
                def p = path == null ? f : (f.startsWith('/') ? grailsApplication.config.data.dir + f : path + '/' + f)
                def file = f

                if (f.startsWith("{")) {
                    // parse 'file' out of JSON
                    def values = JSON.parse(f)
                    p = (values.file.startsWith('/') ? grailsApplication.config.data.dir + values.file : path + '/' + values.file)
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
                            manageLayersService.httpCall("DELETE",
                                    geoserverUrl + "/rest/workspaces/ALA/coveragestores/" + name,
                                    geoserverUsername, geoserverPassword,
                                    null, null, "text/plain")

                            String[] result = manageLayersService.httpCall("PUT",
                                    geoserverUrl + "/rest/workspaces/ALA/coveragestores/" +
                                            name + "/external.geotiff?configure=first",
                                    geoserverUsername, geoserverPassword,
                                    null,
                                    "file://" + geotiff.getPath(),
                                    "text/plain");
                            if (result[0] != "200" && result[0] != "201") {
                                errors.put(String.valueOf(System.currentTimeMillis()), result[0] + ": " + result[1])
                            }

                            //return prj
                            if (tmpPrj.exists()) FileUtils.moveFile(tmpPrj, oldPrj)

                            if (sld.exists()) {
                                //Create style
                                String extra = "";
                                String out = UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                                        extra, geoserverUsername, geoserverPassword, name)
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }

                                //Upload sld
                                out = UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + name,
                                        extra, geoserverUsername, geoserverPassword, sld.getPath());
                                if (!out.startsWith("200") && !out.startsWith("201")) {
                                    errors.put(String.valueOf(System.currentTimeMillis()), out)
                                }

                                //Apply style
                                String data = "<layer><enabled>true</enabled><defaultStyle><name>" + name +
                                        "</name></defaultStyle></layer>";
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

                    manageLayersService.httpCall("DELETE",
                            geoserverUrl + "/rest/workspaces/ALA/datastores/" + name,
                            geoserverUsername, geoserverPassword,
                            null, null, "text/plain")

                    String[] result = manageLayersService.httpCall("PUT",
                            geoserverUrl + "/rest/workspaces/ALA/datastores/" + name + "/external.shp",
                            geoserverUsername, geoserverPassword,
                            null,
                            "file://" + shp.getPath(),
                            "text/plain");

                    if (!"201".equals(result[0])) {
                        errors.put(String.valueOf(System.currentTimeMillis()), 'failed to upload shp to geoserver: ' + shp.getPath())
                        log.error 'failed to upload shp to geoserver: ' + shp.getPath()
                    } else {
                        if (sld.exists()) {
                            //Create style
                            String extra = "";
                            String out = UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                                    extra, geoserverUsername, geoserverPassword, name)
                            if (!out.startsWith("200") && !out.startsWith("201")) {
                                errors.put(String.valueOf(System.currentTimeMillis()), out)
                            }

                            //Upload sld
                            out = UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + name,
                                    extra, geoserverUsername, geoserverPassword, sld.getPath());
                            if (!out.startsWith("200") && !out.startsWith("201")) {
                                errors.put(String.valueOf(System.currentTimeMillis()), out)
                            }

                            //Apply style
                            String data = "<layer><enabled>true</enabled><defaultStyle><name>" + name +
                                    "</name></defaultStyle></layer>";
                            out = UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + name, extra,
                                    geoserverUsername, geoserverPassword, data);
                            if (!out.startsWith("200") && !out.startsWith("201")) {
                                errors.put(String.valueOf(System.currentTimeMillis()), out)
                            }
                        }
                    }

                }
            }
        }
        errors
    }
}
