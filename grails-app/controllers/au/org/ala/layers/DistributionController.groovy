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
import au.org.ala.layers.dto.AttributionDTO
import au.org.ala.layers.dto.Distribution
import au.org.ala.layers.dto.MapDTO
import au.org.ala.layers.util.SpatialConversionUtils
import au.org.ala.spatial.util.AttributionCache
import au.org.ala.spatial.util.MapCache
import grails.converters.JSON
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.web.json.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException

class DistributionController {

    DistributionDAO distributionDao
    ObjectDAO objectDao

    def mapCache() {
        return MapCache.getMapCache(grailsApplication.config.distributions.cache.dir,
                grailsApplication.config.geoserver.url + grailsApplication.config.distributions.geoserver.image.url)
    }

    def index() {
        String wkt = params?.wkt
        if (params?.wkt && params.wkt.toString().isNumber()) {
            wkt = objectDao.getObjectsGeometryById(params.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        Double min_depth = params.double('min_depth', -1)
        Double max_depth = params.double('max_depth', -1)
        String lsids = params.get('lsids', '')
        Integer geom_idx = params.long('geom_idx', -1)

        String fid = params.get('fid', null)
        String objectName = params.get('objectName', null)
        Boolean pelagic = params.boolean('pelagic', null)
        Boolean coastal = params.boolean('coastal', null)
        Boolean estuarine = params.boolean('estuarine', null)
        Boolean desmersal = params.boolean('desmersal', null)
        String groupName = params.get('groupName', null)
        String[] family = params.getList('family')
        if (family.length == 0) family = null
        String[] familyLsid = params.getList('familyLsid')
        if (familyLsid.length == 0) familyLsid = null
        String[] genus = params.getList('genus')
        if (genus.length == 0) genus = null
        String[] genusLsid = params.getList('genusLsid')
        if (genusLsid.length == 0) genusLsid = null
        String[] dataResourceUid = params.getList('dataResourceUid')
        if (dataResourceUid.length == 0) dataResourceUid = null
        Boolean endemic = params.boolean('endemic', null)

        if (StringUtils.isEmpty(wkt) && fid != null && objectName != null) {
            List objects = objectDao.getObjectByFidAndName(fid, objectName)

            wkt = objects.get(0).getGeometry()
            if (wkt == null) {
                log.info('Unmatched geometry for name: ' + objectName + ' and layer ' + fid)

                render(status: 404, text: 'Unmatched geometry for name: ' + objectName + ' and layer ' + fid)

                return
            }
        }

        if (wkt.startsWith("GEOMETRYCOLLECTION")) {
            List collectionParts = SpatialConversionUtils.getGeometryCollectionParts(wkt)

            Set distributionsSet = [] as Set

            collectionParts.each { String part ->
                distributionsSet.addAll(distributionDao.queryDistributions(part, min_depth, max_depth,
                        pelagic, coastal, estuarine, desmersal, groupName, geom_idx, lsids, family, familyLsid,
                        genus, genusLsid, Distribution.EXPERT_DISTRIBUTION, dataResourceUid, endemic))
            }

            List distributions = distributionsSet as List
            addImageUrls(distributions)

            render distributions.collect { map ->
                map.toMap().findAll {
                    i -> i.value != null && "class" != i.key
                }
            } as JSON
        } else {
            List distributions = distributionDao.queryDistributions(wkt, min_depth, max_depth,
                    pelagic, coastal, estuarine, desmersal, groupName, geom_idx, lsids, family, familyLsid,
                    genus, genusLsid, Distribution.EXPERT_DISTRIBUTION, dataResourceUid, endemic)

            addImageUrls(distributions)

            render distributions as JSON
        }
    }

    def listLsids() {
        List distributions = distributionDao.queryDistributions(null, -1, -1,
                null, null, null, null, null, null, null, null, null,
                null, null, Distribution.EXPERT_DISTRIBUTION, null, null)

        def lsids = [:]

        distributions.each { map ->
            def c = 1
            if (lsids.containsKey(map.lsid)) c += lsids.get(map.lsid)
            lsids.put(map.lsid, c)
        }

        render lsids as JSON
    }

    /**
     * index family count
     * @return
     */
    def count() {
        String wkt = params?.wkt
        if (params?.wkt && params.wkt.toString().isNumber()) {
            wkt = objectDao.getObjectsGeometryById(params.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        Double min_depth = params.double('min_depth', -1)
        Double max_depth = params.double('max_depth', -1)
        String lsids = params.get('lsids', '')
        Integer geom_idx = params.long('geom_idx', -1)

        String fid = params.get('fid', null)
        String objectName = params.get('objectName', null)
        Boolean pelagic = params.boolean('pelagic', false)
        Boolean coastal = params.boolean('coastal', false)
        Boolean estuarine = params.boolean('estuarine', false)
        Boolean desmersal = params.boolean('desmersal', false)
        String groupName = params.get('groupName', null)
        String[] family = params.getList('family')
        String[] familyLsid = params.getList('familyLsid')
        String[] genus = params.getList('genus')
        String[] genusLsid = params.getList('genusLsid')
        String[] dataResourceUid = params.getList('dataResourceUid')
        Boolean endemic = params.boolean('endemic', false)

        if (StringUtils.isEmpty(wkt) && fid != null && objectName != null) {
            List objects = objectDao.getObjectByFidAndName(fid, objectName)

            wkt = objects.get(0).getGeometry()
            if (wkt == null) {
                log.info('Unmatched geometry for name: ' + objectName + ' and layer ' + fid)

                render(status: 404, text: 'Unmatched geometry for name: ' + objectName + ' and layer ' + fid)

                return
            }
        }

        List distributions = distributionDao.queryDistributionsFamilyCounts(wkt, min_depth, max_depth,
                pelagic, coastal, estuarine, desmersal, groupName, geom_idx, lsids, family, familyLsid,
                genus, genusLsid, Distribution.EXPERT_DISTRIBUTION, dataResourceUid, endemic)

        render distributions as JSON
    }

    def pointRadius() {

        Double min_depth = params.double('min_depth', -1)
        Double max_depth = params.double('max_depth', -1)
        String lsids = params.get('lsids', '')
        Integer geom_idx = params.long('geom_idx', -1)

        Boolean pelagic = params.boolean('pelagic', false)
        Boolean coastal = params.boolean('coastal', false)
        Boolean estuarine = params.boolean('estuarine', false)
        Boolean desmersal = params.boolean('desmersal', false)
        String groupName = params.get('groupName', null)
        String[] family = params.getList('family')
        String[] familyLsid = params.getList('familyLsid')
        String[] genus = params.getList('genus')
        String[] genusLsid = params.getList('genusLsid')
        String[] dataResourceUid = params.getList('dataResourceUid')
        Boolean endemic = params.boolean('endemic', false)

        Double latitude = params.double('latitude', null)
        Double longitude = params.double('longitude', null)
        Double radius = params.double('radius', null)

        if (latitude == null || longitude == null || radius == null) {
            render(status: 404, text: 'missing mandatory parameter; latitude, longitude and/or radius')
        } else {
            List distributions = distributionDao.queryDistributionsByRadius(longitude.floatValue(),
                    latitude.floatValue(), radius.floatValue(), min_depth, max_depth,
                    pelagic, coastal, estuarine, desmersal, groupName, geom_idx, lsids, family, familyLsid,
                    genus, genusLsid, Distribution.EXPERT_DISTRIBUTION, dataResourceUid, endemic)

            render distributions as JSON
        }
    }

    def pointRadiusCount() {

        Double min_depth = params.double('min_depth', -1)
        Double max_depth = params.double('max_depth', -1)
        String lsids = params.get('lsids', '')
        Integer geom_idx = params.long('geom_idx', -1)

        Boolean pelagic = params.boolean('pelagic', false)
        Boolean coastal = params.boolean('coastal', false)
        Boolean estuarine = params.boolean('estuarine', false)
        Boolean desmersal = params.boolean('desmersal', false)
        String groupName = params.get('groupName', null)
        String[] family = params.getList('family')
        String[] familyLsid = params.getList('familyLsid')
        String[] genus = params.getList('genus')
        String[] genusLsid = params.getList('genusLsid')
        String[] dataResourceUid = params.getList('dataResourceUid')
        Boolean endemic = params.boolean('endemic', false)

        Double latitude = params.double('latitude', null)
        Double longitude = params.double('longitude', null)
        Double radius = params.double('radius', null)

        if (latitude == null || longitude == null || radius == null) {
            render(status: 404, text: 'missing mandatory parameter; latitude, longitude and/or radius')
        } else {
            List distributions = distributionDao.queryDistributionsByRadiusFamilyCounts(longitude.floatValue(),
                    latitude.floatValue(), radius.floatValue(), min_depth, max_depth,
                    pelagic, coastal, estuarine, desmersal, groupName, geom_idx, lsids, family, familyLsid,
                    genus, genusLsid, Distribution.EXPERT_DISTRIBUTION, dataResourceUid, endemic)

            render distributions as JSON
        }
    }

    def show(Long id) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false
        Distribution distribution = distributionDao.getDistributionBySpcode(id, Distribution.EXPERT_DISTRIBUTION, noWkt)

        if (distribution == null) {
            render(status: 404, text: 'invalid distribution spcode')
        } else {
            addImageUrl(distribution)
            def ds = distribution.toMap().findAll {
                i -> i.value != null && "class" != i.key
            }
            render ds as JSON
        }
    }

    def lsidFirst(String lsid) {
        List distributions = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, true)
        if (distributions != null && !distributions.isEmpty()) {
            Distribution d = distributions.get(0)
            addImageUrl(d)
            render d.toMap().findAll {
                i -> i.value != null && "class" != i.key
            } as JSON
        } else {
            render(status: 404, text: 'no records for this lsid')
        }
    }


    def lsid(String lsid) {
        List distributions = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, true)
        if (distributions != null && !distributions.isEmpty()) {
            addImageUrls(distributions)
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
        Boolean noWkt = params.containsKey('nowkt') ? params.nowkt : false
        List<Distribution> distributions = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, noWkt)
        if (distributions != null && !distributions.isEmpty()) {
            addImageUrls(distributions)
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
        new Thread() {
            @Override
            void run() {
                List distributions = distributionDao.queryDistributions(null, -1, -1, null, null, null,
                        null, null, null, null, null, null, null, null,
                        Distribution.EXPERT_DISTRIBUTION, null, null)

                distributions.each { Distribution d ->
                    mapCache().cacheMap(d.getGeom_idx().toString())
                }
            }
        }.start()

        render(status: 200, text: 'started caching')
    }


    def map(String geomIdx) {
        InputStream input = mapCache().getCachedMap(geomIdx)
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
        image(response, null, spcode, null)
    }

    def overviewMapPngName(String name) {
        image(response, null, null, name)
    }

    def overviewMapSeed() {
        Thread t = new Thread() {
            @Override
            void run() {
                try {
                    List<Distribution> distributions = distributionDao.queryDistributions(null, -1, -1, null, null, null, null, null, null, null, null, null, null, null,
                            Distribution.EXPERT_DISTRIBUTION, null, null)

                    for (Distribution d : distributions) {
                        mapCache().cacheMap(d.getGeom_idx().toString())
                    }
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }
        }
        t.start()
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
                geomIdx = distributionDao.getDistributionBySpcode(spcode, Distribution.EXPERT_DISTRIBUTION, true).getGeom_idx()
            } else if (lsid != null) {
                geomIdx = distributionDao.getDistributionByLSID([lsid] as String[], Distribution.EXPERT_DISTRIBUTION, true).get(0).getGeom_idx()
            } else if (scientificName != null) {
                geomIdx = distributionDao.findDistributionByLSIDOrName(scientificName, Distribution.EXPERT_DISTRIBUTION).getGeom_idx()
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

    void addImageUrls(List<Distribution> list) {
        for (Distribution d : list) {
            addImageUrl(d)
        }
    }

    void addImageUrl(Distribution d) {
        d.setImageUrl(grailsApplication.config.grails.serverURL.toString() + "/distribution/map/png/" + d.getGeom_idx())
    }
}
