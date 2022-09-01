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

import au.org.ala.spatial.service.Distributions
import au.org.ala.spatial.service.Objects
import grails.converters.JSON

class TrackController {

    def distributionsService

    def index() {
        String wkt = params?.wkt
        String pid = params?.pid
        if (pid) {
            wkt = Objects.findById(pid)?.geometry
        } else if (wkt && wkt.toString().isNumber()) {
            wkt = Objects.findById(wkt)?.geometry
        }

        List checklists
        if (wkt) {
            checklists = distributionsService.areaQuery(Distributions.TRACK, wkt)
        } else {
            checklists = distributionsService.tracksById.values()
        }

        render checklists as JSON
    }

    def show(Long id) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false

        def distribution
        if (noWkt) {
            distribution = distributionsService.tracksById.get(id)
        } else {
            distribution = Distributions.findBySpcodeAndType(id, Distributions.TRACK)
        }

        if (distribution) {
            render(status: 404, text: 'invalid distribution spcode')
        } else {
            distributionsService.addImageUrl(distribution)
            render distribution as JSON
        }
    }

    def lsid(String lsid) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false

        def distribution
        if (noWkt) {
            distribution = distributionsService.tracksByLsid.get(lsid)
        } else {
            distribution = Distributions.findByLsidAndType(lsid, Distributions.TRACK)
        }

        if (distribution instanceof List && distribution.size() > 0) {
            distribution = distribution[0]
        }

        if (!distribution) {
            render(status: 404, text: 'invalid checklist lsid')
        } else {
            distributionsService.addImageUrl(distribution)
            render distribution as JSON
        }
    }

    def lsids(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? Boolean.parseBoolean(params.nowkt) : false

        def distributions
        if (noWkt) {
            distributions = distributionsService.tracksByLsid.get(lsid)
        } else {
            distributions = Distributions.findAllByLsidAndType(lsid, Distributions.TRACK)
        }

        if (!distributions) {
            render(status: 404, text: 'invalid checklist lsid')
        } else {
            distributionsService.addImageUrls(distributions)
            render distributions as JSON
        }
    }
}
