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
import au.org.ala.spatial.util.AttributionCache
import grails.converters.JSON

class TrackController {

    def distributionsService
    def distributionDao

    def index() {
        def list = distributionsService.index(params, 't')

        render list as JSON
    }

    def listLsids() {
        def lsids = distributionsService.listLsids('t')

        render lsids as JSON
    }

    /**
     * index family count
     * @return
     */
    def count() {
        def distributions = distributionsService.count(params, 't')

        render distributions as JSON
    }

    def pointRadius() {

        def distributions = distributionsService.pointRadius(params, 't')

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    def pointRadiusCount() {
        def distributions = distributionsService.pointRadiusCount(params, 't')

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    def show(Long id) {
        if (id == null) {
            render status: 400, text: "Path parameter `id` is not an integer."
            return
        }

        def distribution = distributionsService.show(params, id, 't')

        if (distribution instanceof String) {
            render(status: 404, text: distribution)
        } else {
            render distribution as JSON
        }
    }

    def lsidFirst(String lsid) {
        List distributions = distributionDao.getDistributionByLSID([lsid] as String[], 't', true)

        if (distributions == null || distributions.isEmpty()) {
            distributions = distributionDao.getDistributionByLSID([lsid.replace("https:/", "https://")] as String[], 't', true)
        }

        if (distributions != null && !distributions.isEmpty()) {
            Distribution d = distributions.get(0)
            distributionsService.addImageUrl(d)
            render d.toMap().findAll {
                i -> i.value != null && "class" != i.key
            } as JSON
        } else {
            render(status: 404, text: 'no records for this lsid')
        }
    }


    def lsid(String lsid) {
        List distributions = distributionDao.getDistributionByLSID([lsid] as String[], 't', true)
        if (distributions == null || distributions.isEmpty()) {
            distributions = distributionDao.getDistributionByLSID([lsid.replace("https:/", "https://")] as String[], 't', true)
        }

        if (distributions != null && !distributions.isEmpty()) {
            distributionsService.addImageUrl(distributions.get(0))
            render distributions.get(0).toMap().findAll {
                i -> i.value != null && "class" != i.key
            } as JSON
        } else {
            render(status: 404, text: 'no records for this lsid')
        }
    }

    def lsids(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt : false
        List<Distribution> distributions = distributionDao.getDistributionByLSID([lsid] as String[], 't', noWkt)
        if (distributions == null || distributions.isEmpty()) {
            distributions = distributionDao.getDistributionByLSID([lsid.replace("https:/", "https://")] as String[], 't', noWkt)
        }
        if (distributions != null && !distributions.isEmpty()) {
            distributionsService.addImageUrls(distributions)
            render distributions.collect {
                it.toMap().findAll {
                    i -> i.value != null && "class" != i.key
                }
            } as JSON
        } else {
            render(status: 404, text: 'no records for this lsid')
        }
    }

    /*
     * get one distribution map by lsid
     */

    def lsidMapFirst(String lsid) {

        MapDTO m = new MapDTO()

        Distribution distribution = distributionDao.findDistributionByLSIDOrName(lsid, 't')
        if (distribution == null) {
            distribution = distributionDao.findDistributionByLSIDOrName([lsid.replace("https:/", "https://")] as String[], 't')
        }

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

    def lsidMaps(String lsid) {

        List found = []

        List distributions = distributionDao.findDistributionsByLSIDOrName(lsid, 't')

        if (distributions == null || distributions.isEmpty()) {
            distributions = distributionDao.findDistributionsByLSIDOrName([lsid.replace("https:/", "https://")] as String[], 't')
        }

        if (distributions != null) {
            distributions.each { Distribution distribution ->
                MapDTO m = new MapDTO()
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

                found.add(m)
            }
        }

        render found as JSON
    }

    def clearAttributionCache() {
        AttributionCache.getCache().clear()
    }

    def cacheMaps() {
        distributionsService.cacheMaps('t')

        render(status: 200, text: 'started caching')
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
        distributionsService.overviewMapSeed('t')
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
                geomIdx = distributionDao.getDistributionBySpcode(spcode, 't', true).getGeom_idx()
            } else if (lsid != null) {
                geomIdx = distributionDao.getDistributionByLSID([lsid] as String[], 't', true).get(0).getGeom_idx()
                if (geomIdx == null) {
                    geomIdx = distributionDao.getDistributionByLSID([lsid.replace("https:/", "https://")] as String[], 't', true).get(0).getGeom_idx()
                }
            } else if (scientificName != null) {
                geomIdx = distributionDao.findDistributionByLSIDOrName(scientificName, 't').getGeom_idx()
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
