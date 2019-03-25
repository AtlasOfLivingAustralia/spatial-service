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

import au.org.ala.layers.dao.FieldDAO
import au.org.ala.layers.dao.LayerDAO
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.converters.XML
import grails.core.GrailsApplication
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.hibernate.criterion.CriteriaSpecification
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

import java.text.SimpleDateFormat

class ManageLayersController {

    ManageLayersService manageLayersService
    FieldDAO fieldDao
    LayerDAO layerDao
    FileService fileService
    TasksService tasksService
    GrailsApplication grailsApplication
    AuthService authService
    ServiceAuthService serviceAuthService

    def index() {
        login()
    }

    def layers() {
        login()

        Map map = [:]
        map.put("layers", manageLayersService.getAllLayers(null))

        map
    }

    def remote() {
        login()

        if (!params?.remoteUrl) params.remoteUrl = grailsApplication.config.spatialService.remote

        def remote = manageLayersService.getAllLayers(params?.remoteUrl)
        def local = manageLayersService.getAllLayers(null)

        def all = [:]
        remote.each { a ->
            a.fields.each { b ->
                def m = b.toMap()
                (m["layer"] = a)
                all.put(b.id, [remote: m, local: null])
            }
        }
        local.each { a ->
            a.fields.each { b ->
                def m = b.toMap()
                (m["layer"] = a)
                def item = all.get(b.id) ?: [remote: null, local: m]
                item.local = m
                all.put(b.id, item)
            }
        }

        def layersLocalOnly = []
        def layersRemoteOnly = []
        def layersBoth = []
        all.each { k, v ->
            def item = [id      : k, local: v.local, remote: v.remote,
                        layerId : v?.local?.spid ?: v?.remote?.spid,
                        dt_added: v?.local?.last_update ?: v?.remote?.last_update,
                        name    : v?.local?.name ?: v?.remote?.name]
            if (v.local && v.remote) {
                layersBoth.push(item)
            } else if (v.local) {
                layersLocalOnly.push(item)
            } else {
                layersRemoteOnly.push(item)
            }
        }

        Map map = [:]
        map.put("layersLocalOnly", layersLocalOnly)
        map.put("layersRemoteOnly", layersRemoteOnly)
        map.put("layersBoth", layersBoth)
        map.put("spatialServiceUrl", params.remoteUrl)
        map.put("localUrl", grailsApplication.config.grails.serverURL)

        map
    }

    def uploads() {
        login()

        Map map = [:]

        map.put("files", manageLayersService.listUploadedFiles())

        map
    }

    /**
     * query status, or start download, of local copy of id,names_and_lsid,longitude,latitude
     * that is used in various background processes
     * @return
     */
    def records() {
        login()

        Map map = [:]

        //create a task to get status or download records
        if (params.containsKey('download')) {
            //create task
            Task task = tasksService.create('DownloadRecords', '', [])

            render task as JSON
        }

        File f = new File((grailsApplication.config.data.dir + '/sampling/records.csv') as String)
        if (f.exists()) {
            map.put("last_refreshed", new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date(f.lastModified())))
        } else {
            map.put("last_refreshed", "never")
        }

        //TODO: include status of the existing, waiting or running, 'DownloadRecords' task

        render map as JSON
    }

    /**
     * Upload of a shapefile or bil file in a zip
     * <p/>
     * Creates temporary geoserver entry for display.
     * <p/>
     * Returns map of:
     * id, for future reference.
     * columns, listing of column name containted in the .dbf
     * url, for display and inspection
     *
     * @param req
     * @param apiKey
     * @return
     * @throws Exception
     */
    def upload() {
        login()

        String id = String.valueOf(System.currentTimeMillis())

        def file
        try {
            file = request.getFile('file')

            File uploadPath = new File((grailsApplication.config.data.dir + '/uploads/' + id) as String)
            File uploadFile = new File((grailsApplication.config.data.dir + '/uploads/' + id + '/id.zip') as String)
            uploadPath.mkdirs()
            file.transferTo(uploadFile)

            fileService.unzip(uploadFile.getPath(), uploadFile.getParent(), true)

            //delete uploaded zip now that it has been unzipped
            uploadFile.delete()

            def result = manageLayersService.processUpload(uploadFile.getParentFile(), id)

            if (result.error){
                return redirect(action: 'uploads')
            }
        } catch (err) {
            err.printStackTrace()
            log.error 'upload failed', err
        }

        redirect action: 'layer', id: id
    }

    /**
     * importLayer with values from layers-service/layer/{id} url
     * if /data/spatial-data/uploads/{id}/{id}.* does not exist an attempt will be made to copy it from
     * /data/ala/data/layers/ready/shape or /diva
     * @return
     */
    def importLayer() {
        login()

        JSONParser jp = new JSONParser()
        InputStream is = new URL(params.url.toString()).openStream()
        String str = ""
        try {
            str = IOUtils.toString(is)
        } finally {
            is.close()
        }

        JSONObject jo = (JSONObject) jp.parse(str)
        String id = jo.get('name')
        jo.put('raw_id', id)
        jo.put('requestedId', String.valueOf(jo.get('id')))

        def input = [:]

        for (def key : jo.keySet()) {
            input.put(key, jo.get(key))
        }

        String dir = grailsApplication.config.data.dir + '/uploads/' + id
        File uploadDir = new File(dir)

        if (!uploadDir.exists()) {
            new File(dir).mkdirs()

            for (File src : new File('/data/ala/data/layers/ready/shape/').listFiles()) {
                if (src.getName().startsWith(id + '.')) {
                    FileUtils.copyFile(src, new File((uploadDir + '/' + src.getName()) as String))
                }
            }

            for (File src : new File('/data/ala/data/layers/ready/diva/').listFiles()) {
                if (src.getName().startsWith(id + '.')) {
                    FileUtils.copyFile(src, new File((uploadDir + '/' + src.getName()) as String))
                }
            }
        }

        Map map = [:]
        map.putAll manageLayersService.createOrUpdateLayer(input, id)

        render map as JSON
    }

    /**
     * importField with values from layers-service/field/{id} url
     * layer must exist
     * @return
     */
    def importField() {
        login()

        JSONParser jp = new JSONParser()
        InputStream is = new URL(params.url.toString()).openStream()
        String str = ""
        try {
            str = IOUtils.toString(is)
        } finally {
            is.close()
        }

        JSONObject jo = (JSONObject) jp.parse(str)
        jo.put('requestedId', jo.get('id'))
        if (jo.containsKey('sname')) jo.put('sid', jo.get('sname'))

        String id = jo.get("spid")
        jo.put('raw_id', id)

        Map input = [:]

        for (String key : jo.keySet()) {
            input.put(key, jo.get(key))
        }

        Map map = [:]
        map.putAll manageLayersService.createOrUpdateField(input, id)

        render map as JSON
    }

    /**
     * create/update (POST) or get (GET) layer
     *
     * @param id
     * @return
     */
    def layer(String id) {
        login()

        String layerId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("name")) {
                map.putAll manageLayersService.createOrUpdateLayer(params, layerId)
            } else {
                map.putAll manageLayersService.createOrUpdateLayer(request.JSON as Map, layerId)
            }
            redirect( action: 'layers', params: map )
        }

        //show
        map.putAll manageLayersService.layerMap(layerId)

        //tasks
        if (map.containsKey('layer_id')) {
            List list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(map.get('layer_id')))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     * @param id
     * @return
     */
    def deleteUpload(String id) {
        delete(id, "uploads")
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     * @param id
     * @return
     */
    def deleteLayer(String id) {
        delete(id, "layers")
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     * @param id
     * @return
     */
    def delete(String id, String action) {
        login()

        if (fieldDao.getFieldById(id, false) == null) {
            Map m = manageLayersService.getUpload(id, false)
            if (m == null || (!m.containsKey('data_resource_uid') && !m.containsKey('checklist'))) {
                manageLayersService.deleteLayer(id)
            } else if (!m.containsKey('checklist')) {
                manageLayersService.deleteDistribution(id)
            } else {
                manageLayersService.deleteChecklist(id)
            }
            redirect(action: action)
        } else {
            def layerId = fieldDao.getFieldById(id, false).spid
            manageLayersService.deleteField(id)

            redirect(action: action, id: layerId)
        }
    }


    /**
     * create/update (POST) or get (GET) field
     *
     * @param id
     * @return
     */
    def field(String id) {
        login()

        Map map = [:]
        Map layer = id.startsWith('cl') || id.startsWith('el') ? [:] : manageLayersService.layerMap(id)
        if (layer.size() < 2) {
            //this is a field id
            if ("POST".equalsIgnoreCase(request.method)) {
                //update field
                if (params.containsKey("name")) {
                    map.putAll manageLayersService.createOrUpdateField(params, id)
                } else {
                    map.putAll manageLayersService.createOrUpdateField(request.JSON as Map, id)
                }
                redirect(action: 'layers')
            }

            map.putAll(manageLayersService.fieldMap(id))

        } else {
            //this is a layer id
            if ("POST".equalsIgnoreCase(request.method)) {
                //add field to layerId
                if (params.containsKey("name")) {
                    map.putAll manageLayersService.createOrUpdateField(params, id)
                } else {
                    map.putAll manageLayersService.createOrUpdateField(request.JSON as Map, id)
                }

                //redirect to this newly created field
//                redirect(action: 'field', id: map.id)
                redirect(action: 'layers')

            } else {
                map.putAll(manageLayersService.fieldMapDefault(id))
            }
        }

        //tasks
        if (map.containsKey('layer_id')) {
            def list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(id))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * create/update (POST) or get (GET) distribution
     *
     * @param id
     * @return
     */
    def distribution(String id) {
        login()

        String uploadId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("data_resource_uid")) {
                map.putAll manageLayersService.createDistribution(params, uploadId)
            } else {
                map.putAll manageLayersService.createDistribution(request.JSON as Map, uploadId)
            }

            //redirect to this newly created distribution
            redirect(action: 'distribution', id: map.data_resource_uid)
        }

        //show
        map.putAll manageLayersService.distributionMap(uploadId)

        //tasks
        if (map.containsKey('data_resource_uid')) {
            def list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(map.get('data_resource_uid')))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * create/update (POST) or get (GET) distribution
     *
     * @param id
     * @return
     */
    def checklist(String id) {
        login()

        String uploadId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("data_resource_uid")) {
                map.putAll manageLayersService.createChecklist(params, uploadId)
            } else {
                map.putAll manageLayersService.createChecklist(request.JSON as Map, uploadId)
            }

            //redirect to this newly created distribution
            redirect(action: 'checklist', id: map.data_resource_uid)
        }

        //show
        map.putAll manageLayersService.checklistMap(uploadId)

        //tasks
        if (map.containsKey('checklist')) {
            def list = Task.createCriteria().list() {
                resultTransformer(CriteriaSpecification.ALIAS_TO_ENTITY_MAP)

                createAlias('input', 'input')

                projections {
                    groupProperty('id', 'id')
                    groupProperty('message', 'message')
                    groupProperty('status', 'status')
                    groupProperty('name', 'name')
                    groupProperty('created', 'created')
                    groupProperty('tag', 'tag')
                }

                eq('input.value', String.valueOf(map.get('checklist')))
            }
            map.put('task', list)
        } else {
            map.put('task', [])
        }

        withFormat {
            html { map }
            js { render map as JSON }
            json { render map as JSON }
            xml { render map as XML }
        }
    }

    /**
     * Copy a remote field to local file system, local geoserver and local postgres.
     *
     * If it already exists it will be updated.
     *
     */
    def copy() {
        login()

        def spatialServiceUrl = params.spatialServiceUrl;
        def fieldId = params.fieldId;

        manageLayersService.updateFromRemote(spatialServiceUrl, fieldId)
        redirect(controller: "Tasks", action: "index" )

    }

    private def login() {

        if (grailsApplication.config.security.cas.disableCAS.toBoolean() || grailsApplication.config.security.cas.bypass.toBoolean()){
            return
        }

        if (serviceAuthService.isValid(params['api_key'])) {
            log.info("API key is valid")
            return
        } else if (!authService.getUserId()) {
//            log.info("Unable to get userID - redirecting - should path be in CAS config ? " )
//            redirect(url: grailsApplication.config.security.cas.loginUrl + "?service=" +
//                    grailsApplication.config.grails.serverURL + createLink(controller: 'manageLayers', action: 'index'))

            Map err = [error: 'not logged in']
            render err as JSON
        } else if (!authService.userInRole(grailsApplication.config.auth.admin_role)) {
            Map err = [error: 'not authorised']
            render err as JSON
        }
    }


    def enable() {
        if (params.id.isNumber()) {
            def layer = layerDao.getLayerById(params.id.toInteger(), false)
            layer.enabled = true
            layerDao.updateLayer(layer)
        } else {
            def field = fieldDao.getFieldById(params.id, false)
            field.enabled = true
            fieldDao.updateField(field)
        }

        render ''
    }
}
