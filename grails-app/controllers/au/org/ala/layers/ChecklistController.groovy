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

import au.org.ala.layers.dao.DistributionDAO
import au.org.ala.layers.dao.ObjectDAO
import au.org.ala.layers.dto.Distribution
import grails.converters.JSON

class ChecklistController {

    DistributionDAO distributionDao
    ObjectDAO objectDao

    def index() {
        String wkt = params?.wkt
        if (params?.wkt && params.wkt.toString().isNumber()) {
            wkt = objectDao.getObjectsGeometryById(params.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        Double min_depth = params.containsKey('min_depth') ? params.min_depth : -1.0
        Double max_depth = params.containsKey('max_depth') ? params.max_depth : -1.0
        String lsids = params.containsKey('lsids') ? params.lsids : ''
        Integer geom_idx = params.containsKey('geom_idx') ? params.geom_idx : -1
        String pid = params.containsKey('pid') ? params.pid : ''

        if (pid.length() > 0) {
            wkt = objectDao.getObjectsGeometryById(pid, 'wkt')
        }

        List checklists = distributionDao.queryDistributions(wkt, min_depth, max_depth, geom_idx, lsids, Distribution.SPECIES_CHECKLIST, null as String[], null as Boolean)

        render checklists as JSON
    }

    def show(Long id) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false
        Distribution distribution = distributionDao.getDistributionBySpcode(id, Distribution.SPECIES_CHECKLIST, noWkt)

        if (distribution == null) {
            render(status: 404, text: 'invalid distribution spcode')
        } else {
            addImageUrl(distribution)
            render distribution as JSON
        }
    }

    void addImageUrl(Distribution d) {
        d.setImageUrl(grailsApplication.config.grails.serverURL.toString() + "/distribution/map/png/" + d.getGeom_idx())
    }

    def lsids() {
        List distributions = distributionDao.queryDistributions(null, -1, -1,
                null, null, null, null, null, null, null, null, null,
                null, null, Distribution.SPECIES_CHECKLIST, null, null)

        def lsids = [:]

        distributions.each { map ->
            def c = 1
            if (lsids.containsKey(map.lsid)) c += lsids.get(map.lsid)
            lsids.put(map.lsid, c)
        }

        render lsids as JSON
    }
}
