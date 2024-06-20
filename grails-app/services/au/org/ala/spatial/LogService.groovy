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

import java.sql.ResultSet
import java.time.Instant
import java.time.format.DateTimeFormatter

//@CompileStatic
class LogService {

    def authService
    SpatialConfig spatialConfig
    def dataSource

    def category1
    def category2
    def logColumns = ["category1", "category2", "data", "session_id", "user_id"]
    def extraColumns = [year: "year(created)", month: "month(created)"]

    def init() {
        if (!category1) {
            category1 = Log.executeQuery("SELECT category1 FROM Log WHERE category1 IS NOT NULL AND category1 <> 'httpService' GROUP BY category1").findAll { it.replaceAll('[^a-zA-Z0-9]', '') == it }
        }
        if (!category2) {
            category2 = Log.executeQuery("SELECT category2 FROM Log WHERE category2 IS NOT NULL AND category1 <> 'httpService' GROUP BY category2").findAll { it.replaceAll('[^a-zA-Z0-9]', '') == it }
        }
    }

    def columnFormat(str) {
        if (str == 'sessionId') str = 'session_id'
        if (str == 'userId') str = 'user_id'
        str
    }
    def search(params, userId, userIsAdmin) {
        params.groupBy = columnFormat(params.groupBy)
        params.countBy = columnFormat(params.countBy)

        if (params.groupBy && params.countBy) { //search all over logs
            init()
            List columns = (params.groupBy as String)?.split(',')?.collect { logColumns.contains(it) ? it : extraColumns.containsKey(it) ? extraColumns.get(it) : null }?.findAll { it != null }
            def counts = count(params.countBy as String)
            def where = where(params, userId, userIsAdmin)
            def groupBy = params.groupBy ? "GROUP BY ${columns.join(',')} ORDER BY ${columns.join(',')} DESC" : "ORDER BY created DESC"

            def sql = "SELECT ${(columns + counts).join(",")} FROM Log ${where} ${groupBy}"
            def response
            if (params.max)
                response = Log.executeQuery(sql.toString(), [max: params.max, offset: params.offset ?: 0])
            else
                response = Log.executeQuery(sql.toString(), [offset: params.offset ?: 0])

            List headers = columns.toList()
            if (counts) headers.addAll(counts.collect { it -> (it as String).replaceAll(".* AS ", "") })

            response.collect { it -> toMap(it, headers) }
        } else if (params.category2) { //search a type of work log
            def result = Log.executeQuery("SELECT user_id, category2, session_d, data, created FROM Log WHERE category2 ='" + (params.category2 as String) + "' ORDER BY created DESC", [max: params.max as Integer ?: 10, offset: params.offset as Integer ?: 0])
            result.collect { it -> toMap(it, ["user_id", "category2", "session_id", "data", "created"]) }
        }

    }

    def searchCount(params, userId, userIsAdmin) {
        def sql = buildCountSql(params, userId, userIsAdmin)
        def response
        Sql.newInstance(dataSource).query(sql.toString(), { ResultSet rs ->
            if (rs.next()) {
                response = rs.getInt(1)
            }
        })

        response
    }

    def buildCountSql(params, userId, userIsAdmin) {
        init()

        def columns = 'id'
        if (params.groupBy) {
            columns = (params.groupBy as String)?.split(',')?.collect { logColumns.contains(it) ? it : extraColumns.containsKey(it) ? extraColumns.get(it) : null }?.findAll { it != null }
        }
        def where = where(params, userId, userIsAdmin)

        def sql = "SELECT COUNT(DISTINCT ${columns.join(',')}) FROM Log ${where}"

        sql
    }

    def toMap(list, List headers) {
        def map = [:]

        if (list) {
            if (list.getClass().isArray()) {
                for (int i = 0; i < list.size() && i < headers.size(); i++) {
                    map.put(headers[i], list[i])
                }
            } else if (headers.size() > 0) {
                map.put(headers[0], list)
            }
        }

        map
    }

    def count(String countBy) {
        def countColumns = []

        countBy?.split(',')?.each { by ->
            if ("record" == by) {
                countColumns.push("count(*) AS records")
            }
            if ("category1" == by) {
                category1.each { countColumns.push("SUM(CASE WHEN category1 = '${it}' THEN 1 ELSE 0 END) AS ${it}") }
            }
            if ("category2" == by) {
                category2.each { countColumns.push("SUM(CASE WHEN category2 = '${it}' THEN 1 ELSE 0 END) AS ${it}") }
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

    def where(params, userId, userIsAdmin) {
        def clause = []

        if (!userIsAdmin || "true" != params.admin) {
            clause.add("user_id = '${userId}'")
        }

        if (params.category1 && category1.contains(params.category1)) {
            clause.add("category1 = '${params.category1}'")
        }

        if (params.category2 && category2.contains(params.category2)) {
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
            def userIds = Log.executeQuery("SELECT user_id FROM Log WHERE user_id is not null GROUP BY user_id")

            def roleList = (params.excludeRoles as String).split(',')

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
