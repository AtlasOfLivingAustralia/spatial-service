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

import au.org.ala.layers.dto.AttributionDTO
import au.org.ala.layers.dto.Distribution
import au.org.ala.layers.dto.MapDTO
import au.org.ala.spatial.service.Distributions
import au.org.ala.spatial.service.Objects
import au.org.ala.spatial.util.AttributionCache
import grails.converters.JSON

class DistributionController {

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
            checklists = distributionsService.areaQuery(Distributions.EXPERT_DISTRIBUTION, wkt)
        } else {
            checklists = distributionsService.checklistsById.values()
        }

        render checklists as JSON
    }

    def listLsids() {
        def lsids = distributionsService.distributionsByLsid.keySet()

        render lsids as JSON
    }

    def show(Long id) {
        if (id == null) {
            render status: 400, text: "Path parameter `id` is not an integer."
            return
        }

        def distribution = distributionsService.show(id, params, Distribution.EXPERT_DISTRIBUTION)

        if (distribution instanceof String) {
            render(status: 404, text: distribution)
        } else {
            render distribution as JSON
        }
    }

    def lsid(String lsid) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false

        def distribution
        if (noWkt) {
            distribution = distributionsService.distributionsByLsid.get(lsid)
        } else {
            distribution = Distributions.findByLsidAndType(lsid, Distributions.EXPERT_DISTRIBUTION)
        }

        if (distribution instanceof List && distribution.size() > 0) {
            distribution = distribution[0]
        }

        if (!distribution) {
            render(status: 404, text: 'invalid distribution lsid')
        } else {
            distributionsService.addImageUrl(distribution)
            render distribution as JSON
        }
    }

    def lsids(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? Boolean.parseBoolean(params.nowkt) : false

        def distributions
        if (noWkt) {
            distributions = distributionsService.distributionsByLsid.get(lsid)
        } else {
            distributions = Distributions.findAllByLsidAndType(lsid, Distributions.EXPERT_DISTRIBUTION)
        }

        if (!distributions) {
            render(status: 404, text: 'invalid distributions lsid')
        } else {
            distributionsService.addImageUrls(distributions)
            render distributions as JSON
        }
    }

    /*
     * get one distribution map by lsid
     */
    def lsidMapFirst(String lsid) {

        MapDTO m = new MapDTO()

        Distribution distribution = distributionsService.distributionsByLsid(lsid)

        if (distribution != null) {
            m.setDataResourceUID(distribution.getData_resource_uid())
            m.setUrl((grailsApplication.config.grails.serverURL + "/distribution/map/png/" + distribution.getGeom_idx()) as String)

            // set the attribution info
            AttributionDTO dto = AttributionCache.getCache().getAttributionFor(distribution.getData_resource_uid())
            m.setAvailable(true)
            m.setDataResourceName(dto.getName())
            m.setLicenseType(dto.getLicenseType())
            m.setLicenseVersion(dto.getLicenseVersion())
            m.setRights(dto.getRights())
            m.setDataResourceUrl(dto.getWebsiteUrl())
            m.setMetadataUrl(dto.getAlaPublicUrl())
        }

        render m as JSON
    }

    def clearAttributionCache() {
        AttributionCache.getCache().clear()
    }

    def map(String geomIdx) {
        InputStream input = distributionsService.mapCache().getCachedMap(geomIdx)
        try {
            response.contentType = 'image/png'
            response.outputStream << input
            response.outputStream.flush()
        } finally {
            try {
                input.close()
            } catch (err) {
            }
            try {
                response.outputStream.close()
            } catch (err) {
            }
        }
    }

    def overviewMapPng(String geomIdx) {
        map(geomIdx)
    }

    def overviewMapPngLsid(String lsid) {
        image(response, lsid, null, null)
    }

    def overviewMapPngSpcode(Long spcode) {
        if (spcode == null) {
            render status: 400, text: "Path parameter `spcode` is not an integer."
            return
        }
        image(response, null, spcode, null)
    }

    def overviewMapPngName(String name) {
        image(response, null, null, name)
    }

    def overviewMapSeed() {
        distributionsService.overviewMapSeed(Distribution.EXPERT_DISTRIBUTION)
    }

    /**
     * returns writes one image to the HttpServletResponse for lsid, spcode or scientificName match
     * *
     *
     * @param response
     * @param lsid
     * @param spcode
     * @param scientificName
     * @throws Exception
     */
    def image(response, String lsid, Long spcode, String scientificName) {
        Long geomIdx = null

        try {
            if (spcode != null) {
                geomIdx = distributionsService.distributionsById.get(spcode)?.geom_idx
            } else if (lsid != null) {
                geomIdx = distributionsService.distributionsByLsid.get(lsid)?.get(0)?.geom_idx()
            } else if (scientificName != null) {
                geomIdx = distributionsService.distributionsByLsid.get(lsid)?.get(0)?.geom_idx()
            }
        } catch (err) {
            log.error 'no distribution found:' + lsid + ',' + spcode + ',' + scientificName, err
        }

        if (geomIdx != null) {
            map(String.valueOf(geomIdx))
        } else {
            response.sendError(404)
        }
    }
}
