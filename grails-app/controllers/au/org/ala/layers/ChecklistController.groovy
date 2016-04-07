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
        String wkt = params.containsKey('wkt') ? params.wkt : ''
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

    def show(Long spcode) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt : false
        render distributionDao.getDistributionBySpcode(spcode, Distribution.SPECIES_CHECKLIST, noWkt) as JSON
    }
}
