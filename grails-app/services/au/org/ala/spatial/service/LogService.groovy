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

package au.org.ala.spatial.service

class LogService {

    def category1;
    def category2;
    def logColumns = ["category1", "category2", "data", "sessionId", "userId"]

    def init() {
        if (!category1) {
            category1 = Log.executeQuery("SELECT category1 FROM Log WHERE category1 IS NOT NULL AND category1 <> 'httpService' GROUP BY category1")
        }
        if (!category2) {
            category2 = Log.executeQuery("SELECT category2 FROM Log WHERE category2 IS NOT NULL AND category1 <> 'httpService' GROUP BY category2")
        }
    }

    def search(params, userId, userIsAdmin) {
        init();

        def columns = logColumns.contains(params.groupBy) ? params.groupBy : logColumns.join(",");
        def counts = count(params.countBy)
        def where = where(params, userId, userIsAdmin)
        def groupBy = params.groupBy ? "GROUP BY ${columns} ORDER BY ${columns} DESC" : "ORDER BY created DESC"

        def sql = "SELECT ${columns} ${counts} FROM Log ${where} ${groupBy}"

        def response = Log.executeQuery(sql.toString(), [max: params.max ?: 10, offset: params.offset ?: 0])

        def headers = columns.split(",").toList()
        if (counts) headers.addAll(counts.substring(1).split(",").collect { it -> it.replaceAll(".* AS ", "") })

        response.collect { it -> toMap(it, headers) }
    }

    def searchCount(params, userId, userIsAdmin) {
        def sql = buildCountSql(params, userId, userIsAdmin)

        def response = Log.executeQuery(sql.toString())

        response[0]
    }

    def buildCountSql(params, userId, userIsAdmin) {
        init();

        def columns = logColumns.contains(params.groupBy) ? params.groupBy : 'id';
        def where = where(params, userId, userIsAdmin)

        def sql = "SELECT COUNT(DISTINCT ${columns}) FROM Log ${where}"

        sql
    }

    def toMap(list, headers) {
        def map = [:]

        if (list) {
            if (list.getClass().isArray()) {
                for (int i = 0; i < list.size() && i < headers.size(); i++) {
                    map.put(headers.getAt(i), list.getAt(i))
                }
            } else if (headers.size() > 0) {
                map.put(headers.getAt(0), list)
            }
        }

        map
    }

    def count(countBy) {
        if ("record".equals(countBy)) {
            return ", count(*) AS records"
        } else if ("category1".equals(countBy)) {
            return category1.collect { it -> ", SUM(CASE WHEN category1 = '${it}' THEN 1 ELSE 0 END) AS ${it}" }.join("")
        } else if ("category2".equals(countBy)) {
            return category2.collect { it -> ", SUM(CASE WHEN category2 = '${it}' THEN 1 ELSE 0 END) AS ${it}" }.join("")
        } else {
            return ""
        }
    }

    def where(params, userId, userIsAdmin) {
        def clause = []

        if (!userIsAdmin || !"true".equals(params.admin)) {
            clause.add("userId = '${userId}'")
        }

        if (params.category1 && category1.contains(params.category1)) {
            clause.add("category1 = '${params.category1}'")
        }

        if (params.category2 && category2.contains(params.category2)) {
            clause.add("category2 = '${params.category2}'")
        }

        if (params.sessionId && Long.parseLong(params.sessionId)) {
            clause.add("sessionId = '${params.sessionId}'")
        }

        clause.add("sessionId IS NOT NULL AND category1 IS NOT NULL AND category1 <> 'httpService'")

        if (clause) {
            return "WHERE ${clause.join(" AND ")}"
        } else {
            return ""
        }
    }
}
