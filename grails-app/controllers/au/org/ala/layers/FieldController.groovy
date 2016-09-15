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

package au.org.ala.layers

import au.org.ala.layers.dao.FieldDAO
import au.org.ala.layers.dao.ObjectDAO
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.layers.dto.Objects
import grails.converters.JSON

class FieldController {

    FieldDAO fieldDao
    ObjectDAO objectDao

    def index() {
        if (params.containsKey('q')) {
            search()
        } else {
            render fieldDao.getFields().collect { it.toMap().findAll { it.value != null } } as JSON
        }
    }

    /**
     * list fields table with db only records
     * @return
     */
    def db() {
        render fieldDao.getFieldsByDB().collect { it.toMap().findAll { it.value != null } } as JSON
    }

    /**
     * This method returns a single field by field id
     *
     * Paging is available for object lists because some fields have many objects
     *
     * @param id
     * @return
     */
    def show(String id) {
        Integer start = params.containsKey('start') ? Integer.parseInt(params.start.toString()) : 0
        Integer pageSize = params.containsKey('pageSize') ? Integer.parseInt(params.pageSize.toString()) : -1

        //test field id value
        Field field = fieldDao.getFieldById(id, false)

        if (field == null || id == null) {
            render(status: 404, text: 'Invalid field id')
        } else {

            Map map = field.toMap()
            map.put('number_of_objects', field.getNumber_of_objects())

            //include field objects
            log.error('field id: ' + id)
            List objects = objectDao.getObjectsById(id, start, pageSize)
            List list = objects.collect { Objects it ->
                [name  : it.name, id: it.id, description: it.description, pid: it.pid,
                 wmsurl: it.wmsurl, area_km: it.area_km, fieldname: it.fieldname,
                 bbox  : it.bbox, fid: it.fid]
            }
            map.put('objects', list)

            render map as JSON
        }
    }

    def search() {
        def q = params.containsKey('q') ? params.q.toString() : ''
        render formatFields(fieldDao.getFieldsByCriteria(q), params.containsKey('q')) as JSON
    }

    def searchLayers() {
        render formatLayers(fieldDao.getLayersByCriteria(params.q.toString())) as JSON
    }

    private List formatFields(List list, boolean includeLayer = false) {
        list.collect { Field f ->
            Map m = [name             : f.getName(), id: f.getId(), sid: f.getSid(), sname: f.getSname(), sdesc: f.getSdesc(),
                     spid             : f.getSpid(), addtomap: f.isAddtomap(), enabled: f.isEnabled(), analysis: f.isAnalysis(),
                     defaultlayer     : f.isDefaultlayer(), desc: f.getDesc(), indb: f.isIndb(), intersect: f.isIntersect(),
                     namesearch       : f.isNamesearch(), layerbranch: f.isLayerbranch(), type: f.getType(),
                     number_of_objects: f.getNumber_of_objects(), wms: f.getWms()]
            if (includeLayer) m += [layer: f.getLayer().toMap()]
            m
        }
    }

    private List formatLayers(List list) {
        list.collect { Layer l ->
            l.toMap()
        }
    }
}
