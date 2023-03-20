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


import java.time.Instant
import java.time.format.DateTimeFormatter

//@CompileStatic
class LogService {

    def authService
    SpatialConfig spatialConfig

    def category1
    def category2
    def logColumns = ["category1", "category2", "data", "sessionId", "userId"]
    def extraColumns = [year: "year(created)", month: "month(created)"]

    def init() {
        if (!category1) {
            category1 = Log.executeQuery("SELECT category1 FROM Log WHERE category1 IS NOT NULL AND category1 <> 'httpService' GROUP BY category1").findAll { it.replaceAll('[^a-zA-Z0-9]', '') == it }
        }
        if (!category2) {
            category2 = Log.executeQuery("SELECT category2 FROM Log WHERE category2 IS NOT NULL AND category1 <> 'httpService' GROUP BY category2").findAll { it.replaceAll('[^a-zA-Z0-9]', '') == it }
        }
    }

    def search(params, userId, userIsAdmin) {
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
            def result = Log.executeQuery("SELECT userId, category2, sessionId, data, created FROM Log WHERE category2 ='" + (params.category2 as String) + "' ORDER BY created DESC", [max: params.max as Integer ?: 10, offset: params.offset as Integer ?: 0])
            result.collect { it -> toMap(it, ["userId", "category2", "sessionId", "data", "created"]) }
        }

    }

    def searchCount(params, userId, userIsAdmin) {
        def sql = buildCountSql(params, userId, userIsAdmin)
        def response = Log.executeQuery(sql.toString())

        response[0]
    }

    def buildCountSql(params, userId, userIsAdmin) {
        init()

        def columns = 'id'
        if (params.groupBy) {
            columns = (params.groupBy as String)?.split(',')?.collect { logColumns.contains(it) ? it : extraColumns.containsKey(it) ? extraColumns.get(it) : null }?.findAll { it != null }
        }
        def where = where(params, userId, userIsAdmin)

        def sql = "SELECT COUNT(DISTINCT ${columns}) FROM Log ${where}"

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
                countColumns.push("count(distinct sessionId) AS sessions")
            }
            if ("user" == by) {
                countColumns.push("count(distinct userId) AS users")
            }
        }

        countColumns
    }

    def where(params, userId, userIsAdmin) {
        def clause = []

        if (!userIsAdmin || "true" != params.admin) {
            clause.add("userId = '${userId}'")
        }

        if (params.category1 && category1.contains(params.category1)) {
            clause.add("category1 = '${params.category1}'")
        }

        if (params.category2 && category2.contains(params.category2)) {
            clause.add("category2 = '${params.category2}'")
        }

        if (params.sessionId && params.sessionId as Long) {
            clause.add("sessionId = '${params.sessionId}'")
        }

        if (params.startDate && params.endDate) {
            def start = DateTimeFormatter.ISO_LOCAL_DATE.parse(params.startDate as String)
            def end = 'now'.equalsIgnoreCase(params.endDate as String) ? Instant.now() : DateTimeFormatter.ISO_LOCAL_DATE.parse(params.endDate as String)
            clause.add("created between '${DateTimeFormatter.ISO_LOCAL_DATE.format(start)}' and '${DateTimeFormatter.ISO_LOCAL_DATE.format(end)}'")
        }

        if (params.excludeRoles) {
            // get user ids in the log
            def userIds = Log.executeQuery("SELECT userId FROM Log WHERE userId is not null GROUP BY userId")

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
                clause.add("userId not in ('${excludedUserIds.join("','")}')")
            }
        }

        clause.add("sessionId IS NOT NULL AND category1 IS NOT NULL AND category1 <> 'httpService'")

        if (clause) {
            return "WHERE ${clause.join(" AND ")}"
        } else {
            return ""
        }
    }
}
