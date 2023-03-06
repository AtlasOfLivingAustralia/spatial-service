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

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.spatial.util.MapCache

import javax.annotation.PostConstruct

class DistributionsService {

    def grailsApplication

    def groovySql

    private final String SELECT_CLAUSE = "select gid,spcode,scientific,authority_,common_nam,\"family\",genus_name,specific_n,min_depth," +
            "max_depth,pelagic_fl,coastal_fl,desmersal_fl,estuarine_fl,family_lsid,genus_lsid,caab_species_number," +
            "caab_family_number,group_name,metadata_u,wmsurl,lsid,type,area_name,pid,checklist_name,area_km,notes," +
            "geom_idx,image_quality,data_resource_uid,endemic";

    // caching
    def checklistsById = new HashMap()
    def distributionsById = new HashMap()
    def tracksById = new HashMap()
    def checklistsByLsid = new HashMap()
    def distributionsByLsid = new HashMap()
    def tracksByLsid = new HashMap()

    @PostConstruct
    void init() {
        refreshCache()
    }

    def refreshCache() {
        try {
            groovySql.execute(SELECT_CLAUSE + " from distributions", { row ->
                if ("c".equals(row.type)) {
                    checklistsById.put(row.spcode, row)
                    def list = checklistsByLsid.getOrDefault(row.lsid, [])
                    list.add(row)
                    checklistsByLsid.putAt(row.lsid, list)
                } else if ("e".equals(row.type)) {
                    distributionsById.put(row.spcode, row)
                    def list = distributionsByLsid.getOrDefault(row.lsid, [])
                    list.add(row)
                    distributionsByLsid.putAt(row.lsid, list)
                } else if ("t".equals(row.type)) {
                    tracksById.put(row.spcode, row)
                    def list = tracksByLsid.getOrDefault(row.lsid, [])
                    list.add(row)
                    tracksByLsid.putAt(row.lsid, list)
                }
            })
        } catch (Exception e) {
            log.error("failed to refresh distribiutions cache", e)
        }
    }

    String wrap(String s) {
        if (s == null) return ""
        else "\"" + s.replace("\"", "\"\"").replace("\\", "\\\\") + "\"";
    }

    List<Distributions> areaQuery(String type, String wkt) {
        def params = [wkt: wkt, type: type]

        List results = new ArrayList()

         groovySql.query(SELECT_CLAUSE + "ST_AREA(ST_INTERSECTION(the_geom, ST_GEOMFROMTEXT( :wkt , 4326))) as intersectArea " +
                 "from distributions where ST_INTERSECTS(the_geom, ST_GEOMFROMTEXT( :wkt , 4326)) and type = :type",
                 params,
                 { it ->
                     results.add(it)
                 })

        results
    }

    void addImageUrls(List<Distributions> list) {
        for (Distributions d : list) {
            addImageUrl(d)
        }
    }

    def mapCache() {
        return MapCache.getMapCache(grailsApplication.config.distributions.cache.dir,
                grailsApplication.config.geoserver.url + grailsApplication.config.distributions.geoserver.image.url)
    }

    void addImageUrl(Distributions d) {
        d.setImageUrl(grailsApplication.config.grails.serverURL.toString() + "/distribution/map/png/" + d.getGeom_idx())
    }

    def show(Long id, params, String type) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false
        def distributions = null
        if (noWkt) {
            if ('c'.equals(type)) {
                distributions = checklistsById.get(id)
            } else if ('e'.equals(type)) {
                distributions = distributionsById.get(id)
            } else if ('t'.equals(type)) {
                distributions = tracksById.get(id)
            }
        } else {
            distributions = Distributions.findByIdAndType(id, type)
        }

        if (distributions) {
            addImageUrl(distributions)
        } else {
            'invalid distribution spcode'
        }
    }
}
