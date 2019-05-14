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
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException

class DistributionController {

    def distributionsService
    def distributionDao

    def index() {
        def list = distributionsService.index(params, Distribution.EXPERT_DISTRIBUTION)

        render list as JSON
    }

    def listLsids() {
        def lsids = distributionsService.listLsids(Distribution.EXPERT_DISTRIBUTION)

        render lsids as JSON
    }

    /**
     * index family count
     * @return
     */
    def count() {
        def distributions = distributionsService.count(params, Distribution.EXPERT_DISTRIBUTION)

        render distributions as JSON
    }

    def pointRadius() {

        def distributions = distributionsService.pointRadius(params, Distribution.EXPERT_DISTRIBUTION)

        if (distributions instanceof String) {
            render(status: 404, text: distributions)
        } else {
            render distributions as JSON
        }
    }

    def pointRadiusCount() {
        def distributions = distributionsService.pointRadiusCount(params, Distribution.EXPERT_DISTRIBUTION)

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

        def distribution = distributionsService.show(id, params, Distribution.EXPERT_DISTRIBUTION)

        if (distribution instanceof String) {
            render(status: 404, text: distribution)
        } else {
            render distribution as JSON
        }
    }

    def lsidFirst(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? Boolean.parseBoolean(params.nowkt) : false
        List distributions = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, noWkt)
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
        Boolean noWkt = params.containsKey('nowkt') ? Boolean.parseBoolean(params.nowkt) : false
        List<Distribution> distributions = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, noWkt)
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

    def lsids(String lsid) {
        Boolean noWkt = params.containsKey('nowkt') ? Boolean.parseBoolean(params.nowkt) : false
        List<Distribution> distributions = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, noWkt)
        if (distributions != null && !distributions.isEmpty()) {
            distributionsService.addImageUrls(distributions)
            render distributions.collect {
                it.toMap().findAll {
                    i -> i.value != null && "class" != i.key
                }
            } as JSON
        } else {
            response.sendError(404)
            return null
        }
    }

    /*
     * get one distribution map by lsid
     */

    def lsidMapFirst(String lsid) {

        MapDTO m = new MapDTO()

        Distribution distribution = distributionDao.findDistributionByLSIDOrName(lsid, Distribution.EXPERT_DISTRIBUTION)

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

        List distributions = distributionDao.findDistributionsByLSIDOrName(lsid, Distribution.EXPERT_DISTRIBUTION)

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
        distributionsService.cacheMaps(Distribution.EXPERT_DISTRIBUTION)

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
            } catch (err) {}
            try {
                response.outputStream.close()
            } catch (err) {}
        }
    }

    /**
     * For a given set of points and an lsid, identify the points which do not
     * fall within the expert distribution associated with the lsid.
     *
     * @param lsid the lsid associated with the expert distribution
     * @param pointsJson the points to test in JSON format. This must be a map whose
     *                   keys are point ids (strings - typically these will be
     *                   occurrence record ids). The values are maps containing the
     *                   point's decimal latitude (with key "decimalLatitude") and
     *                   decimal longitude (with key "decimalLongitude"). The decimal
     *                   latitude and longitude values must be numbers.
     * @param response the http response
     * @return A map containing the distance outside the expert distribution for
     * each point which falls outside the area defined by the
     * distribution. Keys are point ids, values are the distances
     * @throws Exception
     */
    def outliers(String lsid) {
        def pointsJson = params.get('pointsJson', null)

        if (pointsJson == null) {
            render(status: 404, text: 'missing parameter pointsJson')
        }

        try {
            JSONObject pointsMap = (JSONObject) new JSONParser().parse(pointsJson as String)

            try {
                Map outlierDistances = distributionDao.identifyOutlierPointsForDistribution(lsid, pointsMap,
                        Distribution.EXPERT_DISTRIBUTION)
                render outlierDistances as JSON
            } catch (IllegalArgumentException ex) {
                log.error 'failed to get outliers', ex
                render(status: 400, text: 'No expert distribution for species associated with supplied lsid')
                return null
            }
        } catch (ParseException ex) {
            log.error 'failed to get outliers', ex
            render(status: 400, text: 'Invalid JSON for point information')
            return null
        } catch (ClassCastException ex) {
            log.error 'failed to get outliers', ex
            render(status: 400, text: 'Invalid format for point information')
            return null
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
                geomIdx = distributionDao.getDistributionBySpcode(spcode, Distribution.EXPERT_DISTRIBUTION, true)?.getGeom_idx()
            } else if (lsid != null) {
                geomIdx = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, true)?.get(0)?.getGeom_idx()
            } else if (scientificName != null) {
                geomIdx = distributionDao.findDistributionByLSIDOrName(scientificName, Distribution.EXPERT_DISTRIBUTION)?.getGeom_idx()
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
