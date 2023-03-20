/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial


import au.org.ala.spatial.dto.IntersectionFile
import groovy.sql.Sql
import org.codehaus.jackson.map.DeserializationConfig
import org.codehaus.jackson.map.ObjectMapper

/**
 * @author ajay
 */
//@CompileStatic
class FieldService {

    LayerService layerService
    SpatialObjectsService spatialObjectsService
    Sql groovySql
    SpatialConfig spatialConfig

    Fields getFieldById(String id, boolean enabledFieldsOnly = true) {
        log.info("Getting enabled field info for id = " + id)
        String sql = "select *, number_of_objects from fields, (select count(*) as number_of_objects from objects where fid = :id ) o where id = :id "
        if (enabledFieldsOnly) {
            sql += " and enabled=true"
        }

        Fields result = null

        groovySql.query(sql, [id: id], {
            result = it as Fields
            if ("a".equalsIgnoreCase(result.type) || "b".equalsIgnoreCase(result.type)) {
                // fetch object count for this 'grid as contextual'
                IntersectionFile f = layerService.getIntersectionFile(id)
                if (f != null && f.getClasses() != null) {
                    result.number_of_objects = f.getClasses().size()
                }
            }
        })

        result
    }

    List<Fields> getFieldsByDB() {
        log.info("Getting a list of all enabled fields with indb")
        Fields.findAllByEnabledAndIndb(true, true)
    }


//    synchronized void addField(Field field) {
//        log.info("Add new field for " + field.getName())
//
//        Map<String, Object> parameters = field.toMap()
//        parameters.remove("id")
//        parameters.remove("layer")
//
//        //calc new fieldId
//        String idPrefix = "Contextual".equalsIgnoreCase(layerDao.getLayerById(Integer.parseInt(field.getSpid()), false).getType())
//                ? "cl" : "el"
//
//        //test for requested id
//        String newId = field.getId()
//
//        if (newId == null || getFieldById(newId) != null) {
//            newId = getFieldById(idPrefix + field.getSpid()) == null ? idPrefix + field.getSpid() : null
//            if (newId == null) {
//                //calculate next field Id using general form: prefix (n x 1000 + layerId)
//                String idEnd = field.getSpid()
//                while (idEnd.length() < 3) {
//                    idEnd = "0" + idEnd
//                }
//                int maxNFound = 0
//                for (Field f : getFields(false)) {
//                    if (f.getId().startsWith(idPrefix) && f.getId().endsWith(idEnd)) {
//                        if (f.getId().length() - idEnd.length() > 2) {
//                            int n = Integer.parseInt(f.getId().substring(2, f.getId().length() - idEnd.length()))
//                            if (n > maxNFound) {
//                                maxNFound = n
//                            }
//                        }
//                    }
//                }
//
//                newId = idPrefix + (maxNFound + 1) + idEnd
//            }
//        }
//
//        parameters.put("id", newId)
//        //fix for field 'desc' and 'intersect'
//        if (parameters.containsKey("desc")) {
//            parameters.put("\"desc\"", parameters.get("desc"))
//            parameters.remove("desc")
//        }
//        if (parameters.containsKey("intersect")) {
//            parameters.put("\"intersect\"", parameters.get("intersect"))
//            parameters.remove("intersect")
//        }
//
//        insertField.execute(parameters)
//
//        field.setId(newId)
//    }
//
//
//    void updateField(Field field) {
//        log.info("Updating field metadata for " + field.getName())
//
//        String sql = "update fields set name=:name, " +
//                "\"desc\"=:desc, type=:type, " +
//                "spid=:spid, sid=:sid, sname=:sname, " +
//                "sdesc=:sdesc, indb=:indb, enabled=:enabled, " +
//                "namesearch=:namesearch, defaultlayer=:defaultlayer, " +
//                "\"intersect\"=:intersect, layerbranch=:layerbranch, analysis=:analysis," +
//                " addtomap=:addtomap where id=:id"
//
//        Map map = field.toMap()
//        map.remove("layer")
//        namedParameterJdbcTemplate.update(sql, map)
//    }
//
//
    void delete(String fieldId) {
        Fields f = getFieldById(fieldId)

        if (f != null) {
            groovySql.execute("delete from objects where fid=?", [f.getId()] as List<Object>)
            groovySql.execute("delete from fields where id=?", [f.getId()] as List<Object>)
        }
    }

    void updateField(Fields field) {
        log.info("Updating field metadata for " + field.getName())

        String sql = "update fields set name=:name, " +
                "\"desc\"=:desc, type=:type, " +
                "spid=:spid, sid=:sid, sname=:sname, " +
                "sdesc=:sdesc, indb=:indb, enabled=:enabled, " +
                "namesearch=:namesearch, defaultlayer=:defaultlayer, " +
                "\"intersect\"=:intersect, layerbranch=:layerbranch, analysis=:analysis," +
                " addtomap=:addtomap where id=:id"

        Map map = field as Map
        map.remove("layer")
        Fields.executeUpdate(sql, map)
    }

    List<Layers> getLayersByCriteria(String keywords) {
        mapsToLayers(getByKeywords(keywords))
    }

    List<Map<String, Object>> getByKeywords(String keywords) {
        log.info("Getting a list of all enabled fields by criteria: " + keywords)
        String sql = ""
        sql += "select f.*, l.* from fields f inner join layers l on f.spid = l.id || '' where "
        sql += "l.enabled=true AND f.enabled=true AND ( "
        sql += "l.keywords ilike :keywords "
        sql += "or l.displayname ilike :keywords "
        sql += "or l.name ilike :keywords "
        sql += "or l.domain ilike :keywords "
        sql += "or f.name ilike :keywords "
        sql += ") order by f.name "

        keywords = "%" + keywords.toLowerCase() + "%"

        List<Map<String, Object>> maps = new ArrayList()

        groovySql.query(sql, [keywords: keywords], {
            Map<String, Object> row = it as Map
            maps.add(row)
        })

        maps
    }

    List<Fields> getFieldsByCriteria(String keywords) {
        mapsToFields(getByKeywords(keywords))
    }


    private List<Layers> mapsToLayers(List<Map<String, Object>> maps) {
        List<Layers> list = new ArrayList<Layers>()

        ObjectMapper om = new ObjectMapper()
        om.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        for (Map<String, Object> map : maps) {
            try {
                Map field = new HashMap()
                Map layer = new HashMap()
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().startsWith("layer_"))
                        layer.put(entry.getKey().substring("layer_".length()), entry.getValue())
                    else field.put(entry.getKey(), entry.getValue())
                }
                Fields f = om.readValue(om.writeValueAsString(field), Fields.class)
                Layers l = om.readValue(om.writeValueAsString(layer), Layers.class)
                updateDisplayPath(l)
                f.setLayer(l)
                l.setDisplaypath(l.getDisplaypath().replace("&styles=", "") + "&style=" + f.getId())
                l.setDisplayname(f.getName())
                l.setPid(f.getId())
                list.add(l)
            } catch (Exception e) {
                log.error("failed to read field/layer " + map.get("id"), e)
            }
        }

        return list
    }

    private List<Fields> mapsToFields(List<Map<String, Object>> maps) {
        List<Fields> list = new ArrayList<Fields>()

        ObjectMapper om = new ObjectMapper()
        om.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        for (Map<String, Object> map : maps) {
            try {
                Map field = new HashMap()
                Map layer = new HashMap()
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().startsWith("layer_"))
                        layer.put(entry.getKey().substring("layer_".length()), entry.getValue())
                    else field.put(entry.getKey(), entry.getValue())
                }
                Fields f = om.readValue(om.writeValueAsString(field), Fields.class)
                Layers l = om.readValue(om.writeValueAsString(layer), Layers.class)
                updateDisplayPath(l)
                f.setLayer(l)

                l.setDisplaypath(l.getDisplaypath().replace("&styles=", "") + "&style=" + f.getId())

                list.add(f)
            } catch (Exception e) {
                log.error("failed to read field/layer " + map.get("id"), e)
            }
        }

        return list
    }

    List<Fields> getFields(boolean includeAdmin = false) {
        if (includeAdmin) {
            Fields.findAll()
        } else {
            Fields.findAllByEnabled(true)
        }
    }

    void updateDisplayPaths(List<Layers> layers) {
        if (layers == null) {
            return
        }

        for (Layers layer : layers) {
            updateDisplayPath(layer)
        }
    }

    void updateDisplayPath(Layers layer) {
        if (layer && layer.getDisplaypath() != null) {
            if (!layer.getDisplaypath().startsWith("/")) {
                layer.setDisplaypath(layer.getDisplaypath().replace(DistributionsService.GEOSERVER_URL_PLACEHOLDER, spatialConfig.geoserver.url))
            } else {
                layer.setDisplaypath(spatialConfig.geoserver.url + layer.getDisplaypath())
            }
        }
    }

    Fields get(String id, String q, int start, int pageSize) {
        //test field id value
        Fields field = getFieldById(id, false)

        if (field) {

            //include field objects
            log.info('field id: ' + id)
            List<SpatialObjects> objects = spatialObjectsService.getObjectsById(id, start, pageSize, q)
            List<SpatialObjects> list = objects.collect { SpatialObjects it ->
                [name  : it.name, id: it.id, description: it.desc, pid: it.pid,
                 wmsurl: it.wmsurl, area_km: it.area_km, fieldname: it.fieldname,
                 bbox  : it.bbox, fid: it.fid] as SpatialObjects
            }
            field.objects = list
        }

        field
    }
}
