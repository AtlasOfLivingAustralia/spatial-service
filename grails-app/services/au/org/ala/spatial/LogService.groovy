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

import groovy.sql.Sql
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import java.sql.ResultSet
import java.time.Instant
import java.time.format.DateTimeFormatter

@CompileStatic
class LogService {

    def authService
    SpatialConfig spatialConfig
    def dataSource

    List<String> category1 = []
    List<String> category2 = []
    List<String> logColumns = ["category1", "category2", "data", "session_id", "user_id"]
    Map<String, String> extraColumns = [year: "year(created)", month: "month(created)"]

    @CompileDynamic
    void init() {
        if (!category1) {
            category1 = Log.executeQuery("SELECT category1 FROM Log WHERE category1 IS NOT NULL AND category1 <> 'httpService' GROUP BY category1").findAll { it?.toString()?.replaceAll('[^a-zA-Z0-9]', '') == it?.toString() }.collect { it as String }
        }
        if (!category2) {
            category2 = Log.executeQuery("SELECT category2 FROM Log WHERE category2 IS NOT NULL AND category1 <> 'httpService' GROUP BY category2").findAll { it?.toString()?.replaceAll('[^a-zA-Z0-9]', '') == it?.toString() }.collect { it as String }
        }
    }

    String columnFormat(String str) {
        if (str == 'sessionId') str = 'session_id'
        if (str == 'userId') str = 'user_id'
        str
    }

    def search(Map params, String userId, boolean userIsAdmin) {
        String groupByParam = columnFormat(params.groupBy as String ?: '')
        String countByParam = columnFormat(params.countBy as String ?: '')
        params.groupBy = groupByParam
        params.countBy = countByParam

        if (!groupByParam && !userIsAdmin) {
            params.groupBy = 'id'
            groupByParam = 'id'
        }

        if (!countByParam && !userIsAdmin) {
            params.countBy = 'record'
            countByParam = 'record'
        }

        if (groupByParam && countByParam) { //search all over logs
            init()
            List<String> columns = (groupByParam).split(',').collect { String it -> logColumns.contains(it) ? it : extraColumns.containsKey(it) ? extraColumns.get(it) : null }.findAll { it != null }
            List<String> counts = count(countByParam)
            def where = where(params, userId, userIsAdmin)
            String groupBy = groupByParam ? "GROUP BY ${columns.join(',')} ORDER BY ${columns.join(',')} DESC" : "ORDER BY created DESC"

            List<String> headers = columns.toList()
            if (counts) headers.addAll(counts.collect { String it -> it.replaceAll(".* AS ", "") })

            String sql = "SELECT ${((columns as List<String>) + (counts as List<String>)).join(",")} FROM Log ${where} ${groupBy} LIMIT ? OFFSET ?"
            def response = []
            Sql.newInstance(dataSource).query(sql, [params.max as Integer ?: 10, params.offset as Integer ?: 0] as List<Object>, { ResultSet rs ->
                if (rs.next()) {
                    response.add(toMap(rs, headers))
                }
            })
            return response
        } else if (params.category2) { //search a type of work log
            String sql = "SELECT user_id, category2, session_id, data, created FROM Log WHERE category2 = ? ORDER BY created DESC LIMIT ? OFFSET ?"
            def response = []
            Sql.newInstance(dataSource).query(sql, [params.category2, params.max as Integer ?: 10, params.offset as Integer ?: 0] as List<Object>, { ResultSet rs ->
                if (rs.next()) {
                    response.add(toMap(rs, ["user_id", "category2", "session_id", "data", "created"]))
                }
            })
            return response
        } else if (userIsAdmin) {
            String sql = "SELECT user_id, category2, session_id, data, created FROM Log ORDER BY created DESC LIMIT ? OFFSET ?"
            def response = []
            Sql.newInstance(dataSource).query(sql, [params.max as Integer ?: 10, params.offset as Integer ?: 0] as List<Object>, { ResultSet rs ->
                if (rs.next()) {
                    response.add(toMap(rs, ["user_id", "category2", "session_id", "data", "created"]))
                }
            })
            return response
        } else {
            String where = where(params, userId, userIsAdmin)

            String sql = "SELECT user_id, category2, session_id, data, created FROM Log ${where} ORDER BY created DESC LIMIT ? OFFSET ?"
            def response = []
            Sql.newInstance(dataSource).query(sql, [params.max as Integer ?: 10, params.offset as Integer ?: 0] as List<Object>, { ResultSet rs ->
                if (rs.next()) {
                    response.add(toMap(rs, ["user_id", "category2", "session_id", "data", "created"]))
                }
            })
            return response
        }
    }

    def searchCount(Map params, String userId, boolean userIsAdmin) {
        String sql = buildCountSql(params, userId, userIsAdmin)
        def response
        Sql.newInstance(dataSource).query(sql, { ResultSet rs ->
            if (rs.next()) {
                response = rs.getInt(1)
            }
        })

        response
    }

    String buildCountSql(Map params, String userId, boolean userIsAdmin) {
        init()

        List<String> columns = ['id']
        if (params.groupBy) {
            columns = (params.groupBy as String).split(',').collect { String it -> logColumns.contains(it) ? it : extraColumns.containsKey(it) ? extraColumns.get(it) : null }.findAll { it != null }
        }
        String where = where(params, userId, userIsAdmin)

        "SELECT COUNT(DISTINCT ${columns.join(',')}) FROM Log ${where}"
    }

    Map toMap(ResultSet list, List<String> headers) {
        Map map = [:]

        if (list) {
            for (int i = 0; i < headers.size(); i++) {
                // list is a ResultSet, 1 to n
                map.put(headers[i], list.getObject(i + 1))
            }
        }

        map
    }

    List<String> count(String countBy) {
        List<String> countColumns = []

        countBy?.split(',')?.each { String by ->
            if ("record" == by) {
                countColumns.push("count(*) AS records")
            }
            if ("category1" == by) {
                category1.each { String it -> countColumns.push("SUM(CASE WHEN category1 = '${it}' THEN 1 ELSE 0 END) AS ${it}".toString()) }
            }
            if ("category2" == by) {
                category2.each { String it -> countColumns.push("SUM(CASE WHEN category2 = '${it}' THEN 1 ELSE 0 END) AS ${it}".toString()) }
            }
            if ("session" == by) {
                countColumns.push("count(distinct session_id) AS sessions")
            }
            if ("user" == by) {
                countColumns.push("count(distinct user_id) AS users")
            }
        }

        countColumns
    }

    @CompileDynamic
    String where(Map params, String userId, boolean userIsAdmin) {
        List<String> clause = []

        if (!userIsAdmin || "true" != params.admin as String) {
            clause.add("user_id = '${userId}'")
        }

        if (params.category1 && category1.contains(params.category1 as String)) {
            clause.add("category1 = '${params.category1}'")
        }

        if (params.category2 && category2.contains(params.category2 as String)) {
            clause.add("category2 = '${params.category2}'")
        }

        if (params.sessionId && params.sessionId as Long) {
            clause.add("session_id = '${params.sessionId}'")
        }

        if (params.startDate && params.endDate) {
            def start = DateTimeFormatter.ISO_LOCAL_DATE.parse(params.startDate as String)
            def end = 'now'.equalsIgnoreCase(params.endDate as String) ? Instant.now() : DateTimeFormatter.ISO_LOCAL_DATE.parse(params.endDate as String)
            clause.add("created between '${DateTimeFormatter.ISO_LOCAL_DATE.format(start)}' and '${DateTimeFormatter.ISO_LOCAL_DATE.format(end)}'")
        }

        if (params.excludeRoles) {
            // get user ids in the log
            List userIds = Log.executeQuery("SELECT user_id FROM Log WHERE user_id is not null GROUP BY user_id")

            List<String> roleList = (params.excludeRoles as String).split(',').toList()

            // list of users with these excluded roles
            def usersInfoResp = authService.getUserDetailsById(userIds)
            List excludedUserIds = []
            if (usersInfoResp?.success) {
                usersInfoResp.users.each { id, userData ->
                    if (userData.roles.findAll { role -> roleList.contains(role) }) {
                        excludedUserIds.push(id)
                    }
                }
            }

            if (excludedUserIds.size()) {
                clause.add("user_id not in ('${excludedUserIds.join("','")}')")
            }
        }

        clause.add("session_id IS NOT NULL AND category1 IS NOT NULL AND category1 <> 'httpService'")

        if (clause) {
            return "WHERE ${clause.join(" AND ")}"
        } else {
            return ""
        }
    }
}
