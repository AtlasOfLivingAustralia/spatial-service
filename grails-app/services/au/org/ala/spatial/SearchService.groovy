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

import au.org.ala.spatial.dto.GridClass
import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.dto.SearchObject
import grails.converters.JSON
import groovy.sql.Sql
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource

import java.sql.ResultSet
import java.util.Map.Entry

class SearchService {

    LayerService layerService
    FieldService fieldService
    def dataSource

    List<SearchObject> findByCriteria(String criteria, int offset, int limit) {
        return findByCriteria(criteria, offset, limit, new ArrayList<String>(), new ArrayList<String>())
    }

    List<SearchObject> findByCriteria(String criteria, int offset, int limit, List<String> includeFieldIds, List<String> excludeFieldIds) {
        log.debug("Getting search results for query: " + criteria)
        String fieldFilter = ""
        List<String> fieldIds = null
        if (includeFieldIds != null && !includeFieldIds.isEmpty()) {
            def fields = fieldService.getFields(true).collect { it.id }
            fieldIds = includeFieldIds.findAll{  fields.contains(it)}
            if (fieldIds) {
                fieldFilter = ' and o.fid in ( ' + "'" + fieldIds.join("','") + "'" + ' ) '
            }
        } else if (excludeFieldIds != null && !excludeFieldIds.isEmpty()) {
            def fields = fieldService.getFields(true).collect { it.id }
            fieldIds = excludeFieldIds.findAll{  fields.contains(it)}
            if (fieldIds) {
                fieldFilter = ' and o.fid not in ( ' + "'" + fieldIds.join("','") + "'" + ' ) '
            }
        }

        String sql = 'with o as (select o.pid as pid , o.name as name, o.desc as description, o.fid as fid, ' +
                'f.name as fieldname from objects o inner join fields f on o.fid = f.id ' +
                'where o.name ilike :criteria and o.namesearch=true ' + fieldFilter + ")" +
                " select pid, name, description, fid, fieldname, (select json_agg(a.f) " +
                " from (select distinct (fid || ' | ' || fieldname) as f from o) a) as fields, " +
                " position(:nativeQ in lower(name)) as rank from o order by rank, name, pid limit :limit offset :offset"

        List<SearchObject> searchObjects = new ArrayList()

        Map parameters = [:]
        parameters.put("nativeQ", criteria)
        parameters.put("criteria", "%" + criteria + "%")
        parameters.put("limit", limit)
        parameters.put("offset", offset)

        Sql.newInstance(dataSource).query(sql, parameters, { ResultSet rs ->
            while (rs.next()) {
                SearchObject so = new SearchObject()
                so.pid = rs.getObject(1)
                so.name = rs.getObject(2)
                so.description = rs.getObject(3)
                so.fid = rs.getObject(4)
                so.fieldname = rs.getObject(5)
                so.fields = rs.getObject(6)

                searchObjects.add(so)
            }
        })

        // get a list of fieldMatches to include when `offset` > 0 makes searchObjects empty
        List<SearchObject> additionalFields = new ArrayList<>()
        String fieldMatches = null
        if (searchObjects.size() == 0 && offset > 0) {
            sql = "select distinct (f.id || ' | ' || f.name) as fields from objects o inner join fields f on o.fid = f.id where o.name ilike :criteria and o.namesearch=true " + fieldFilter
            Sql.newInstance(dataSource).execute(sql, parameters, { ResultSet rs ->
                if (rs.next()) {
                    SearchObject so = new SearchObject()
                    so.fields = rs.getObject(1)
                    additionalFields.add(so)
                }
            })
        }

        List<SearchObject> result = addGridClassesToSearch(searchObjects, additionalFields, criteria, limit, includeFieldIds, excludeFieldIds)

        return result
    }


    private List<SearchObject> addGridClassesToSearch(List<SearchObject> search, List<String> additionalFields, String criteria, int limit, List<String> includeFieldIds, List<String> excludeFieldIds) {
        criteria = criteria.toLowerCase()
        int vacantCount = limit - search.size()

        // insert matched fields into the fieldSet
        Set fieldSet = new HashSet()
        if (additionalFields) {
            fieldSet.addAll(additionalFields)
        }

        if (vacantCount > 0) {
            for (Entry<String, IntersectionFile> e : layerService.getIntersectionFiles().entrySet()) {
                IntersectionFile f = e.getValue()
                boolean fieldAdded = false
                if ("a".equalsIgnoreCase(f.getType()) && f.getClasses() != null && e.getKey() == f.getFieldId() &&
                        (includeFieldIds == null || includeFieldIds.isEmpty() || includeFieldIds.contains(f.getFieldId())) &&
                        (excludeFieldIds == null || excludeFieldIds.isEmpty() || !excludeFieldIds.contains(f.getFieldId()))) {
                    //search
                    for (Entry<Integer, GridClass> c : f.getClasses().entrySet()) {
                        if ((c.getValue().getName().toLowerCase().indexOf(criteria)) >= 0) {
                            search.add(new SearchObject([
                                    pid        : f.getLayerPid() + ':' + c.getKey(),
                                    name       : c.getValue().getName(),
                                    description: null,
                                    fid        : f.getFieldId(),
                                    fieldname  : f.getFieldName(),
                                    fields     : ""]))
                            if (!fieldAdded) {
                                fieldSet.add(f.getFieldId() + "|" + f.getFieldName())
                                fieldAdded = true
                            }
                        }
                    }
                }
            }

            // update fields value in result list
            if (!fieldSet.isEmpty()) {
                String fieldSetString = (fieldSet as JSON).toString()
                for (SearchObject so : search) {
                    so.setFields(fieldSetString)
                }
            }
        }
        return search
    }
}
