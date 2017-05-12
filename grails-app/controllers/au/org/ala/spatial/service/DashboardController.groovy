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

import au.org.ala.web.AuthService

class DashboardController {

    AuthService authService

    def all() {
        def userId = authService.getUserId()

        def limit = params?.age ? " WHERE datediff(dateadd(now(), -${params?.age?.toInteger()}, 'd'),created) > 0 " : ""

        def sessions = []
        def categorySummary = []
        if (userId != null && authService.userInRole("ALA_ADMIN")) {
            sessions = Task.executeQuery("SELECT sessionId, " +
                    "MIN(created) as created, " +
                    "(MAX(created) - MIN(created)) as duration " +
                    "FROM log " + limit +
                    "GROUP BY sessionId")

            def sessionsMap = [:]
            sessions.each { s ->
                sessionsMap.put(s.sessionId, s)
            }

            def categories = Task.executeQuery("SELECT sessionId, category1, count(category1) as count " +
                    "FROM log " + limit)

            categorySummary = Task.executeQuery("SELECT category1, count(category1) as count " +
                    "FROM log " + limit)

            categories.each { c ->
                def s = sessionsMap.get(c.sessionId)
                def cat = [:]
                if (s.containsKey('categories')) cat = s.categories
                cat[c.category1] = c.count
                s['categories'] = cat
            }

            sessions = sessionsMap.values()
        }

        [sessions: sessions, categorySummary: categorySummary]
    }

    def categoryDetailAll(id) {
        def userId = authService.getUserId()

        def limit = params?.age ? " WHERE datediff(dateadd(now(), -${params?.age?.toInteger()}, 'd'),created) > 0 " : ""

        def categoryDetail = []
        if (userId) {
            def validCategories = Task.executeQuery("SELECT category1 " +
                    "FROM log " + limit)

            if (validCategories.contains(id)) {
                categoryDetail = Task.executeQuery("SELECT category2, count(category2) as count " +
                        "FROM log " + (limit ?: " WHERE ") + " AND category1 = '$id';")
            }
        }
        [categoryDetail: categoryDetail]
    }

    def index() {
        def userId = authService.getUserId()

        def sessions = []
        def categorySummary = []
        if (userId != null && authService.userInRole("ALA_ADMIN")) {
            sessions = Task.executeQuery("SELECT sessionId, " +
                    "MIN(created) as created, " +
                    "(MAX(created) - MIN(created)) as duration " +
                    "FROM log WHERE userId = '" + userId + "'" +
                    "GROUP BY sessionId")

            def sessionsMap = [:]
            sessions.each { s ->
                sessionsMap.put(s.sessionId, s)
            }

            def categories = Task.executeQuery("SELECT sessionId, category1, count(category1) as count " +
                    "FROM log WHERE userId = '" + userId + "'")

            categorySummary = Task.executeQuery("SELECT category1, count(category1) as count " +
                    "FROM log WHERE userId = '" + userId + "'")

            categories.each { c ->
                def s = sessionsMap.get(c.sessionId)
                def cat = [:]
                if (s.containsKey('categories')) cat = s.categories
                (cat[c.category1] = c.count)
                (s['categories'] = cat)
            }

            sessions = sessionsMap.values()
        }

        [sessions: sessions, categorySummary: categorySummary]
    }

    def categoryDetail(id) {
        def userId = authService.getUserId()
        def categoryDetail = []
        if (userId) {
            def validCategories = Task.executeQuery("SELECT category1 " +
                    "FROM log WHERE userId = '" + userId + "'")

            if (validCategories.contains(id)) {
                categoryDetail = Task.executeQuery("SELECT category2, count(category2) as count " +
                        "FROM log WHERE userId = '" + userId + "' AND category1 = '" + id + "';")
            }
        }
        [categoryDetail: categoryDetail]
    }

    def detail(id) {
        def userId = authService.getUserId()

        def detail = []
        if (userId != null) {
            detail = Task.executeQuery("SELECT * FROM task WHERE sessionId = '$id' AND userId = '$userId';")
        }

        detail
    }

}
