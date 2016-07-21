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

import au.org.ala.layers.dto.Objects
import au.org.ala.layers.util.LayerFilter
import au.org.ala.layers.util.SpatialConversionUtils
import grails.converters.JSON

class ObjectController {

    def fieldDao
    def objectDao

    /*
     * This method returns a single object, provided an object pid
     *
     */

    def show(String pid) {
        def obj = objectDao.getObjectByPid(pid)
        if (obj != null) render obj as JSON
    }

    /*
     * This method returns all objects associated with a field and at a point
     *
     */

    def listByLocation(String id, Double lat, Double lng) {
        def limit = params.containsKey('limit') ? params.limit : 40

        def field = fieldDao.getFieldById(id)

        if (field == null) {
            render(status: 404, text: 'Invalid field id')
        } else {
            def objects = objectDao.getNearestObjectByIdAndLocation(id, limit, lng, lat) as JSON

            render objects as JSON
        }
    }

    /*
     * This method returns all objects associated with a field and intersecting with 
     * provided WKT param* 
     *
     */

    def listByWkt(String id) {
        def limit = params.containsKey('limit') ? params.limit : 40
        def wkt = params?.wkt

        def field = fieldDao.getFieldById(id)

        if (field == null) {
            render(status: 404, text: 'Invalid field id')
        } else {
            if (wkt.startsWith("ENVELOPE(")) {

                //get results of each filter term
                def filters = LayerFilter.parseLayerFilters(wkt)
                def all = []
                filters.each {
                    all.add(objectDao.getObjectsByIdAndIntersection(id, limit, it));
                }

                //merge common entries only
                def objectCounts = [:]
                def list = all[0]
                list.each {
                    objectCounts.put(it.getPid(), 1)
                }
                all.subList(1, all.size()).each {
                    it.each { t ->
                        def v = objectCounts.get(t.getPid())
                        if (v != null) {
                            objectCounts.put(t.getPid(), v + 1)
                        }
                    }
                }
                def inAllGroups = []
                list.each {
                    if (objectCounts.get(it.getPid()) == all.size()) {
                        inAllGroups.add(it)
                    }
                }

                render inAllGroups as JSON
            } else if (wkt.startsWith("OBJECT(")) {

                def pid = wkt.substring("OBJECT(".length(), wkt.length() - 1)
                def objects = objectDao.getObjectsByIdAndIntersection(id, limit, pid)

                render objects as JSON

            } else if (wkt.startsWith("GEOMETRYCOLLECTION")) {
                def collectionParts = SpatialConversionUtils.getGeometryCollectionParts(wkt)

                def objectsSet = [] as Set

                collectionParts.each {
                    objectsSet.addAll(objectDao.getObjectsByIdAndArea(id, limit, it))
                }

                render objectsSet as JSON
            } else {
                render objectDao.getObjectsByIdAndArea(id, limit, wkt) as JSON
            }
        }
    }

    def fieldObjects(String id) {
        Integer start = params.containsKey('start') ? params.start as Integer : 0
        Integer pageSize = params.containsKey('pageSize') ? params.pageSize as Integer : -1

        render objectDao.getObjectsById(id, start, pageSize) as JSON
    }

    def fieldObjectsPoint(String id, Double lat, Double lng) {
        Integer limit = params.containsKey('limit') ? params.limit as Integer : 40
        render objectDao.getNearestObjectByIdAndLocation(id, limit, lng, lat) as JSON
    }

    def objectsInArea(String id) {
        Integer limit = params.containsKey('limit') ? params.limit as Integer : 40

        String wkt = params.wkt;

        if (wkt.startsWith("ENVELOPE(")) {
            //get results of each filter
            LayerFilter[] filters = LayerFilter.parseLayerFilters(wkt);
            List<List<Objects>> all = new ArrayList<List<Objects>>();
            for (int i = 0; i < filters.length; i++) {
                all.add(objectDao.getObjectsByIdAndIntersection(id, limit, filters[i]));
            }
            //merge common entries only
            HashMap<String, Integer> objectCounts = new HashMap<String, Integer>();
            List<Objects> list = all.get(0);
            for (int j = 0; j < list.size(); j++) {
                objectCounts.put(list.get(j).getPid(), 1);
            }
            for (int i = 1; i < all.size(); i++) {
                List<Objects> t = all.get(i);
                for (int j = 0; j < t.size(); j++) {
                    Integer v = objectCounts.get(t.get(j).getPid());
                    if (v != null) {
                        objectCounts.put(t.get(j).getPid(), v + 1);
                    }
                }
            }
            List<Objects> inAllGroups = new ArrayList<Objects>(list.size());
            for (int j = 0; j < list.size(); j++) {
                if (objectCounts.get(list.get(j).getPid()) == all.size()) {
                    inAllGroups.add(list.get(j));
                }
            }

            render inAllGroups as JSON
        } else if (wkt.startsWith("OBJECT(")) {
            String pid = wkt.substring("OBJECT(".length(), wkt.length() - 1);
            render objectDao.getObjectsByIdAndIntersection(id, limit, pid) as JSON
        } else if (wkt.startsWith("GEOMETRYCOLLECTION")) {
            List<String> collectionParts = SpatialConversionUtils.getGeometryCollectionParts(wkt);

            Set<Objects> objectsSet = new HashSet<Objects>();

            for (String part : collectionParts) {
                objectsSet.addAll(objectDao.getObjectsByIdAndArea(id, limit, part));
            }

            render new ArrayList<Objects>(objectsSet) as JSON
        } else {
            render objectDao.getObjectsByIdAndArea(id, limit, wkt) as JSON
        }
    }

}
