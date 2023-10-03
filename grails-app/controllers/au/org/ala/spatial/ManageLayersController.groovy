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

import grails.converters.JSON
import grails.converters.XML
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.hibernate.criterion.CriteriaSpecification
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

import javax.transaction.Transactional
import java.text.SimpleDateFormat

@RequirePermission
@Transactional
class ManageLayersController {

    ManageLayersService manageLayersService
    FileService fileService
    TasksService tasksService
    SpatialConfig spatialConfig

    FieldService fieldService
    LayerService layerService

    /**
     * admin only or api_key
     *
     * @return
     */
    def index() {
    }

    /**
     * admin only
     *
     * @return
     */
    def layers() {
        log.debug("List avaliable layers")
        Map map = [:]
        map.put("layers", manageLayersService.getAllLayers(null))

        map
    }

    /**
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def remote() {
        if (!params?.remoteUrl) params.remoteUrl = spatialConfig.spatialService.remote

        def remote = manageLayersService.getAllLayers(params?.remoteUrl)
        def local = manageLayersService.getAllLayers(null)

        Map<String, Map> all = [:]
        remote.each { Layers a ->
            a.fields.each { Fields b ->
                b.layer = a
                all.put(b.id, [remote: b, local: null])
            }
        }
        local.each { Layers a ->
            a.fields.each { Fields b ->
                b.layer = a
                Map item = all.get(b.id) ?: [remote: null, local: b]
                item.local = b
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
        map.put("localUrl", spatialConfig.grails.serverURL)

        map
    }

    /**
     * admin only
     *
     * @return
     */

    @RequireAdmin
    def uploads() {
        Map map = [:]

        map.put("files", manageLayersService.listUploadedFiles())

        map
    }

    /**
     * query status, or start download, of local copy of id,names_and_lsid,longitude,latitude
     * that is used in various background processes
     *
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def records() {
        Map map = [:]

        //create a task to get status or download records
        if (params.containsKey('download')) {
            //create task
            Task task = tasksService.create('DownloadRecords', '', []).task

            render task as JSON
        }

        File f = new File((spatialConfig.data.dir + '/sampling/records.csv') as String)
        if (f.exists()) {
            map.put("last_refreshed", new SimpleDateFormat("dd/MM/yyyy hh:mm:ss").format(new Date(f.lastModified())))
        } else {
            map.put("last_refreshed", "never")
        }

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
     * admin only
     *
     * @param req
     * @param apiKey
     * @return
     * @throws Exception
     */
    @RequireAdmin
    def upload() {
        log.debug("Receiving upload of zip file")
        String id = String.valueOf(System.currentTimeMillis())

        def file
        try {
            file = request.getFile('file')
            log.debug("Receiving upload of zip file: " + file)
            File uploadPath = new File((spatialConfig.data.dir + '/uploads/' + id) as String)
            File uploadFile = new File((spatialConfig.data.dir + '/uploads/' + id + '/id.zip') as String)
            uploadPath.mkdirs()
            file.transferTo(uploadFile)

            log.debug("Unzipping upload of zip file...")
            fileService.unzip(uploadFile.getPath(), uploadFile.getParent(), true)
            log.debug("Unzipped upload of zip file.")

            //delete uploaded zip now that it has been unzipped
            uploadFile.delete()
            log.debug("Deleting original zip. File moved to: " + spatialConfig.data.dir + '/uploads/' + id)

            def result = manageLayersService.processUpload(uploadFile.getParentFile(), id)
            if (result.error){
                log.error("Problem processing upload. " + result.error)
                return redirect(action: 'uploads', params: [error: result.error])
            }
        } catch (err) {
            log.error 'upload failed - ' + err.getMessage(), err
            return redirect(action: 'uploads', params: [error: err.getMessage() + " - see logs for details"])
        }

        redirect(action: 'layer', id: id, params: [message: 'Upload successful'])
    }

    @RequireAdmin
    def defaultGeoserverStyles() {
        manageLayersService.fixLayerStyles()
    }

    /**
     * importLayer with values from layers-service/layer/{id} url
     * if /data/spatial-data/uploads/{id}/{id}.* does not exist an attempt will be made to copy it from
     * /data/ala/data/layers/ready/shape or /diva
     *
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def importLayer() {
        InputStream is = new URL(params.url.toString()).openStream()
        String str = ""
        try {
            str = is.text
        } finally {
            is.close()
        }

        JSONObject jo = (JSONObject) JSON.parse(str)
        String id = jo.get('name')
        jo.put('raw_id', id)
        jo.put('requestedId', String.valueOf(jo.get('id')))

        def input = [:]

        for (def key : jo.keySet()) {
            input.put(key, jo.get(key))
        }

        String dir = spatialConfig.data.dir + '/uploads/' + id
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
     *
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def importField() {
        InputStream is = new URL(params.url.toString()).openStream()
        String str = ""
        try {
            str = is.text
        } finally {
            is.close()
        }

        JSONObject jo = (JSONObject) JSON.parse(str)
        jo.put('requestedId', jo.get('id'))

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
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    @Transactional
    def layer(String id) {
        String layerId = id
        Map map = [:]
        if ("POST".equalsIgnoreCase(request.method)) {
            if (params.containsKey("name")) {
                map.putAll manageLayersService.createOrUpdateLayer(params, layerId)
            } else {
                map.putAll manageLayersService.createOrUpdateLayer(request.JSON as Map, layerId)
            }
            redirect(action: 'layers', params: map)
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
            } as List
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
    @RequireAdmin
    def deleteUpload(String id) {
        delete(id, "uploads")
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     * @param id
     * @return
     */
    @RequireAdmin
    def deleteLayer(String id) {
        delete(id, "layers")
    }

    /**
     * delete field with fieldId, layer with layerId, distribution with data_resource_uid
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def delete(String id, String action) {
        if (fieldService.getFieldById(id, false) == null) {
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
            def layerId = fieldService.getFieldById(id, false).spid
            manageLayersService.deleteField(id)

            redirect(action: action, id: layerId)
        }
    }


    /**
     * create/update (POST) or get (GET) field
     *
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def field(String id) {
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
                    def f =  manageLayersService.createOrUpdateField(params, id)
                    map.putAll(f.properties)
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
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def distribution(String id) {
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
     * admin only
     *
     * @param id
     * @return
     */
    @RequireAdmin
    def checklist(String id) {
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
     * admin only
     */
    @RequireAdmin
    def copy() {
        String spatialServiceUrl = params.spatialServiceUrl
        String fieldId = params.fieldId

        manageLayersService.updateFromRemote(spatialServiceUrl, fieldId)
        redirect(controller: "Tasks", action: "index")

    }

    /**
     * admin only
     *
     * @return
     */
    @RequireAdmin
    def enable() {
        if (params.id.isNumber()) {
            def layer = layerDao.getLayerById(params.id.toInteger(), false)
            layer.enabled = true
            layerService.updateLayer(layer)
        } else {
            def field = fieldDao.getFieldById(params.id, false)
            field.enabled = true
            fieldService.updateField(field)
        }
        render ''
    }

    /**
     * to deliver resources (area WKT and layer files) to slaves in a zip
     * a layer: 'cl...', 'el...', will provide the sample-able files (original extents) - shape files or diva grids
     * a layer: 'cl..._res', 'el..._res', will provide the standardized files at the requested resolution
     *          (or next detailed) - shape files or diva grids
     *
     * admin only or api_key, do not redirect to CAS
     * @return
     */
    @RequireAdmin
    def resource() {
        OutputStream outputStream = null
        try {
            outputStream = response.outputStream as OutputStream
            //write resource
            response.setContentType("application/octet-stream")
            response.setHeader("Content-disposition", "attachment;filename=${params.resource}.zip")
            fileService.write(outputStream, params.resource as String)
            outputStream.flush()
        } catch (err) {
            log.error(err.getMessage(), err)
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (err) {
                    log.error(err.getMessage(), err)
                }
            }
        }
    }

    /**
     * for slaves to peek at a resource on the master
     *
     * admin only or api_key, do not redirect to CAS
     *
     * @return
     */
    @RequireAdmin
    def resourcePeek() {
        //write resource
        render fileService.info(params.resource.toString()) as JSON
    }
}
