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

import java.util.Map.Entry

//@CompileStatic
class SearchService {

    LayerService layerService
    Sql groovySql

//
//
//    List<SearchObject> findByCriteria(final String criteria, int limit) {
//        log.info("Getting search results for query: " + criteria)
//        String sql = "select pid, id, name, \"desc\" as description, fid, fieldname from searchobjects(?,?)"
//        return addGridClassesToSearch(jdbcTemplate.query(sql, "%" + criteria + "%", limit), criteria, limit, null, null)
//    }
//

    List<SearchObject> findByCriteria(String criteria, int offset, int limit) {
        return findByCriteria(criteria, offset, limit, new ArrayList<String>(), new ArrayList<String>())
    }

//
//    List<SearchObject> findByCriteria(final String criteria, int limit, List<String> includeFieldIds, List<String> excludeFieldIds) {
//        return findByCriteria(criteria, 0, limit, includeFieldIds, excludeFieldIds)
//    }


    List<SearchObject> findByCriteria(String criteria, int offset, int limit, List<String> includeFieldIds, List<String> excludeFieldIds) {
        log.info("Getting search results for query: " + criteria)
        String fieldFilter = ""
        List<String> fieldIds = null
        if (includeFieldIds != null && !includeFieldIds.isEmpty()) {
            fieldFilter = ' and o.fid in ( :fieldIds ) '
            fieldIds = includeFieldIds
        } else if (excludeFieldIds != null && !excludeFieldIds.isEmpty()) {
            fieldFilter = ' and o.fid not in ( :fieldIds ) '
            fieldIds = excludeFieldIds
        }

        String sql = 'with o as (select o.pid as pid ,o.id as id, o.name as name, o.desc as description, o.fid as fid, f.name as fieldname from objects o inner join fields f on o.fid = f.id where o.name ilike :criteria and o.namesearch=true ' + fieldFilter + ")" +
                ' select pid, id, name, description, fid, fieldname, (select json_agg(a.f) from (select distinct (fid || ' | ' || fieldname) as f from o) a) as fields, position(:nativeQ in lower(name)) as rank from o order by rank, name, pid limit :limit offset :offset'

        List<SearchObject> searchObjects = new ArrayList()

        MapSqlParameterSource parameters = new MapSqlParameterSource()
        parameters.addValue("nativeQ", criteria)
        parameters.addValue("criteria", "%" + criteria + "%")
        parameters.addValue("limit", limit)
        parameters.addValue("offset", offset)

        if (!fieldFilter.isEmpty()) {
            // use fieldFilter
            parameters.addValue("fieldIds", fieldIds)
        }

        groovySql.execute(sql, parameters) {
            searchObjects.add(it as SearchObject)
        }

        // get a list of fieldMatches to include when `offset` > 0 makes searchObjects empty
        List<SearchObject> additionalFields = new ArrayList<>()
        String fieldMatches = null
        if (searchObjects.size() == 0 && offset > 0) {
            sql = "select distinct (f.id || '|' || f.name) as fields from objects o inner join fields f on o.fid = f.id where o.name ilike :criteria and o.namesearch=true " + fieldFilter
            groovySql.execute(sql, parameters) {
                additionalFields.add(it as SearchObject)
            }
        }

        List<SearchObject> result = addGridClassesToSearch(searchObjects, criteria, limit, includeFieldIds, excludeFieldIds)

        // insert fields that are missing due to `offset` > 0
        if (additionalFields && result) {
            // additionalFields.fields each contain a concatenated fid and field name instead of the default JSON Array
            List<String> fields = []
            for (SearchObject af : additionalFields) {
                fields.add(af.fields)
            }

            for (SearchObject so : result) {
                so.setFields((fields as JSON) as String)
            }
        }

        return result
    }


    private List<SearchObject> addGridClassesToSearch(List<SearchObject> search, String criteria, int limit, List<String> includeFieldIds, List<String> excludeFieldIds) {
        criteria = criteria.toLowerCase()
        int vacantCount = limit - search.size()

        // insert matched fields into the fieldSet
        Set fieldSet = new HashSet()
        List<String> initialFields = null
        if (search.size() > 0 && search.get(0).getFields() != null) {
            initialFields = JSON.parse(search.get(0).getFields()) as List<String>
        } else {
            initialFields = []
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
                                    id         : f.getLayerPid() + ':' + c.getKey(),
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
                initialFields.addAll(fieldSet)
                String fieldSetString = initialFields.toString()
                for (SearchObject so : search) {
                    so.setFields(fieldSetString)
                }
            }
        }
        return search
    }
}
