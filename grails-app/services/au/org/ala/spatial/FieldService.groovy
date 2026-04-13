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
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

import java.sql.ResultSet
import java.sql.ResultSetMetaData

@CompileStatic
class FieldService {

    LayerService layerService
    SpatialObjectsService spatialObjectsService
    def dataSource
    SpatialConfig spatialConfig

    Fields getFieldById(String id, boolean enabledFieldsOnly = true) {
        log.debug("Getting enabled field info for id = " + id)
        String sql = "select *, number_of_objects from fields, (select count(*) as number_of_objects from objects where fid = :id ) o where id = :id "
        if (enabledFieldsOnly) {
            sql += " and enabled=true"
        }

        Fields field = null

        Sql.newInstance(dataSource).query(sql, [id: id], { ResultSet rs ->
            if (rs.next()) {
                field = new Fields()
                ResultSetMetaData meta = rs.getMetaData()
                int colCount = meta.getColumnCount()
                for (int col = 1; col <= colCount; col++) {
                    String label = meta.getColumnLabel(col)
                    if (field.hasProperty(label)) {
                        field.setProperty(label, rs.getObject(col))
                    }
                }
                if ("a".equalsIgnoreCase(field.type) || "b".equalsIgnoreCase(field.type)) {
                    // fetch object count for this 'grid as contextual'
                    IntersectionFile f = layerService.getIntersectionFile(id)
                    if (f != null && f.getClasses() != null) {
                        field.number_of_objects = f.getClasses().size()
                    }
                }
            }
        })

        field
    }

    @CompileDynamic
    List<Fields> getFieldsByDB() {
        log.debug("Getting a list of all enabled fields with indb")
        Fields.findAllByEnabledAndIndb(true, true)
    }

    /**
     * Return the count of fields of a layer, no matter they are enabled or not
     */
    @CompileDynamic
    int countBySpid(String spid) {
        Fields.countBySpid(spid) as int
    }

    /**
     * Return the largest sequence number + 1.
     */
    @CompileDynamic
    def calculateNextSequenceId(String spid) {
        List<String> requestIds = (Fields.findAllBySpid(spid) as List<Fields>).collect { Fields f -> f.id }
        if (requestIds.size() == 0) {
            return ''
        } else {
            int maxSequenceNumber = (int) requestIds
                    .findAll { String id -> id.endsWith(spid) }
                    .collect { String id -> id.replaceFirst(/^.{2}/, '').replaceAll(spid, "") }
                    .collect { String s -> s == '' ? 0 : s.toInteger() }
                    .max()
            return (maxSequenceNumber ? maxSequenceNumber + 1 : '')
        }
    }

    void delete(String fieldId) {
        Fields f = getFieldById(fieldId, false)

        if (f != null) {
            Sql.newInstance(dataSource).execute("delete from objects where fid=?", [f.getId()] as List<Object>)
            Sql.newInstance(dataSource).execute("delete from fields where id=?", [f.getId()] as List<Object>)
        }
    }

    void updateField(Fields field) {
        log.debug("Updating field metadata for " + field.getName())

        String sql = "update fields set name=:name, " +
                "\"desc\"=:desc, type=:type, " +
                "spid=:spid, sname=:sname, " +
                "sdesc=:sdesc, indb=:indb, enabled=:enabled, " +
                "namesearch=:namesearch, defaultlayer=:defaultlayer, " +
                "\"intersect\"=:intersect, layerbranch=:layerbranch, analysis=:analysis," +
                " addtomap=:addtomap where id=:id"

        Map map = field.properties
        map.put('id', field.id)

        Sql.newInstance(dataSource).executeUpdate(sql, map)
    }

    List<Layers> getLayersByCriteria(String keywords) {
        mapsToLayers(getByKeywords(keywords))
    }

    List<Fields> getByKeywords(String keywords) {
        log.debug("Getting a list of all enabled fields by criteria: " + keywords)
        String sql = ""
        sql += "select f.*, l.* from fields f inner join layers l on f.spid = l.id || '' where "
        sql += "l.enabled=true AND f.enabled=true AND ( "
        sql += "l.keywords ilike :keywords "
        sql += "or l.displayname ilike :keywords "
        sql += "or l.name ilike :keywords "
        sql += "or l.domain ilike :keywords "
        sql += "or f.name ilike :keywords "
        sql += ") order by f.name "

        keywords = keywords == null ? "%" : ("%" + keywords.toLowerCase() + "%")

        List<Fields> fields = new ArrayList()

        Sql.newInstance(dataSource).query(sql, [keywords: keywords], { ResultSet it ->
            while (it.next()) {
                Fields field = new Fields()
                Layers layer = new Layers()

                ResultSetMetaData meta = it.getMetaData()
                int colCount = meta.getColumnCount()
                int firstTableOid = -1
                int fieldColEnd = -1

                // We can't use PostgreSQL tableOid here; approximate by using column index split
                // fields columns come first (from 'f.*'), then layer columns (from 'l.*')
                // Determine field/layer split by checking which properties exist on each
                for (int col = 1; col <= colCount; col++) {
                    String label = meta.getColumnLabel(col)
                    if (field.properties.containsKey(label) && fieldColEnd == -1) {
                        field.setProperty(label, it.getObject(col))
                    } else {
                        if (fieldColEnd == -1) fieldColEnd = col
                        if (layer.properties.containsKey(label)) {
                            layer.setProperty(label, it.getObject(col))
                        }
                    }
                }

                updateDisplayPath(layer)
                layer.displaypath = layer.displaypath.replace("&styles=", "") + "&style=" + field.id

                // field name will be unique but layer display name may not be
                layer.displayname = field.name

                field.layer = layer
                fields.add(field)
            }
        })

        fields
    }

    List<Fields> getFieldsByCriteria(String keywords) {
        getByKeywords(keywords)
    }


    private static List<Layers> mapsToLayers(List<Fields> fields) {
        fields.collect { it.layer }.asList()
    }

    private List<Fields> mapsToFields(List<Map<String, Object>> maps) {
        List<Fields> list = new ArrayList<Fields>()

        ObjectMapper om = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
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

    @CompileDynamic
    List<Fields> getFields(boolean includeAdmin = false) {
        // wrap in a transaction if it is not already, unsure why this is necessary for some instances
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (includeAdmin) {
                Fields.findAll() as List<Fields>
            } else {
                Fields.findAllByEnabled(true) as List<Fields>
            }
        } else {
            Fields.withTransaction {
                if (includeAdmin) {
                    Fields.findAll() as List<Fields>
                } else {
                    Fields.findAllByEnabled(true) as List<Fields>
                }
            } as List<Fields>
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
            log.debug('field id: ' + id)
            field.objects = spatialObjectsService.getObjectsById(id, start, pageSize, q)
        }

        field
    }
}
