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
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.apache.log4j.Logger

class LogController {

    final Logger logger = Logger.getLogger(LogController.class)

    def logService
    AuthService authService

    @Transactional
    def index() {
        def log = new Log(params)
        log.data = request.JSON.toString()

        if (!log.save()) {
            log.errors.each {
                logger.error(it)
            }
        }

        render status: 200
    }

    def search() {
        def searchResult = logService.search(params, authService.getUserId(),
                authService.userInRole(grailsApplication.config.auth.admin_role))

        def totalCount = logService.searchCount(params, authService.getUserId(),
                authService.userInRole(grailsApplication.config.auth.admin_role))

        if ("application/json".equals(request.getHeader("accept"))) {
            def map = [records: searchResult, totalCount: totalCount]
            render map as JSON
        } else {
            [searchResult: searchResult, totalCount: totalCount]
        }
    }

}
