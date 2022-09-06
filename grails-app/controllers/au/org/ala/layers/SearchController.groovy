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

package au.org.ala.layers

import grails.converters.JSON
import org.apache.commons.lang3.StringUtils

class SearchController {

    def searchDao

    /*
     * perform a search operation
     */

    def search() {
        def q = params.get('q', null)
        def limit = params.int('limit', 20)
        def offset = params.int('start', 0)

        def includeFieldIds = params.get('include', '')
        def excludeFieldIds = params.get('exclude', '')

        if (q == null) {
            render status: 404, text: 'No search parameter q.'
        }
        try {
            q = URLDecoder.decode(q, "UTF-8")
            q = q.trim().toLowerCase()
        } catch (UnsupportedEncodingException ex) {
            render status: 404, text: 'Failed to parse search parameter q'
        }

        // Results can differ greatly between the old and new search methods.
        if (StringUtils.isEmpty(includeFieldIds) && StringUtils.isEmpty(excludeFieldIds)) {
            render searchDao.findByCriteria(q,offset,limit) as JSON
        } else {
            List<String> includeIds
            List<String> excludeIds
            if (StringUtils.isNotEmpty(includeFieldIds)) {
                includeIds = Arrays.asList(includeFieldIds.split(","))
            } else {
                includeIds = new ArrayList()
            }
            if (StringUtils.isNotEmpty(excludeFieldIds)) {
                excludeIds = Arrays.asList(excludeFieldIds.split(","))
            } else {
                excludeIds = new ArrayList()
            }
            render searchDao.findByCriteria(q,offset,limit, (List<String>) includeIds, (List<String>) excludeIds) as JSON
        }
    }
}
