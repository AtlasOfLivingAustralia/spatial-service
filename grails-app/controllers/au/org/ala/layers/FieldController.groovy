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
import au.org.ala.layers.dto.Objects
import grails.converters.JSON

class FieldController {

    FieldDAO fieldDao
    ObjectDAO objectDao

    def index() {
        if (params.containsKey('q')) {
            search()
        } else {
            render fieldDao.getFields() as JSON
        }
    }

    /**
     * list fields table with db only records
     * @return
     */
    def db() {
        render fieldDao.getFieldsByDB() as JSON
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
        render fieldDao.getFieldsByCriteria(q), params.containsKey('q') as JSON
    }

    def searchLayers() {
        render fieldDao.getLayersByCriteria(params.q.toString()) as JSON
    }
}
