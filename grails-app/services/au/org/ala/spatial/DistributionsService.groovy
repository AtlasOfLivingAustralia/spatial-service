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

package au.org.ala.spatial


import au.org.ala.spatial.util.MapCache
import groovy.sql.Sql
import org.locationtech.jts.io.WKTReader

import java.sql.ResultSet

//@CompileStatic
class DistributionsService {

    SpatialConfig spatialConfig
    Sql groovySql

    private final String SELECT_CLAUSE = "select gid,spcode,scientific,authority_,common_nam,family,genus_name,specific_n,min_depth," +
            "max_depth,pelagic_fl,coastal_fl,desmersal_fl,estuarine_fl,family_lsid,genus_lsid,caab_species_number," +
            "caab_family_number,group_name,metadata_u,wmsurl,lsid,type,area_name,pid,checklist_name,area_km,notes," +
            "geom_idx,image_quality,data_resource_uid,endemic"

    SpatialObjectsService spatialObjectsService

    List<Distributions> addImageUrls(List<Distributions> list) {
        for (Distributions d : list) {
            addImageUrl(d)
        }

        list
    }

    Distributions addImageUrl(Distributions d) {
        if (d) {
            d.setImageUrl(spatialConfig.grails.serverURL + "/distribution/map/png/" + d.getGeom_idx())
            updateWMSUrl(d)
        }
        d
    }

    def mapCache() {
        return MapCache.getMapCache(spatialConfig.distributions.cache.dir, spatialConfig.geoserver.url + spatialConfig.distributions.geoserver.image.url)
    }

    def show(Long id, Map params, String type) {
        boolean noWkt = params.containsKey('nowkt') ? params.nowkt.toString().toBoolean() : false

        Distributions distribution = Distributions.createCriteria().get {
            eq('spcode', id)
            eq('type', type)
        } as Distributions

        if (noWkt) {
            distribution.geometry = null
        }

        addImageUrl(distribution)
    }

    Distributions updateWMSUrl(Distributions distributions) {
        if (distributions?.wmsurl) {
            if (!distributions.wmsurl.startsWith("/")) {
                distributions.wmsurl = distributions.wmsurl.replace(GEOSERVER_URL_PLACEHOLDER, spatialConfig.geoserver.url)
            } else {
                distributions.wmsurl = spatialConfig.geoserver.url + distributions.wmsurl
            }

        }
        distributions
    }

    List<Distributions> queryDistributions(Map queryParams, Boolean noWkt, String type) {
        String wkt = queryParams?.wkt
        if (queryParams?.wkt && queryParams.wkt.toString().isNumber()) {
            wkt = spatialObjectsService.getObjectsGeometryById(queryParams.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        String lsids = queryParams.lsids
        Integer geomIdx = queryParams.geom_idx as Integer ?: -1 as Integer
        String[] dataResources = queryParams.data_resource_uids as String[]

        if (queryParams.pid) {
            wkt = spatialObjectsService.getObjectsGeometryById(queryParams.pid as String, 'wkt')
        }

        StringBuilder whereClause = new StringBuilder()
        Map<String, Object> params = new HashMap<String, Object>()
        constructWhereClause(geomIdx, lsids, type, dataResources, params, whereClause)

        String wktSelect = ""
        boolean intersectArea = false
        if (wkt != null && wkt.length() > 0) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ")
            }
            whereClause.append("ST_INTERSECTS(the_geom, ST_GEOMFROMTEXT( :wkt , 4326))")
            params.put("wkt", wkt)

            wktSelect = ", ST_AREA(ST_INTERSECTION(the_geom, ST_GEOMFROMTEXT( :wkt , 4326))) as intersectArea"
            intersectArea = true
        }

        if (!noWkt) {
            wktSelect += ', the_geom'
        }

        String sql = SELECT_CLAUSE + wktSelect + " from Distributions"
        if (whereClause.length() > 0) {
            sql += " WHERE " + whereClause.toString()
        }

        List result = new ArrayList()

        String[] fields = SELECT_CLAUSE.split(',')
        groovySql.query(sql, params, { ResultSet rs ->
            while (rs.next()) {
                Map map = [:]
                fields.eachWithIndex { String entry, int i ->
                    if (rs.getObject(i+1) != null) {
                        map.put(entry, rs.getObject(i+1))
                    }
                }

                Distributions d = new Distributions(map)
                if (intersectArea) {
                    d.intersectArea = rs.getObject('intersectarea')
                }

                if (!noWkt) {
                    Object o = rs.getObject('the_geom')
                    if (o instanceof net.postgis.jdbc.PGgeometry) {
                        StringBuffer sb = new StringBuffer()
                        o.geometry.outerWKT(sb)
                        WKTReader wktReader = new WKTReader()
                        d.geometry = wktReader.read(sb.toString())
                    } else {
                        d.geometry = o
                    }
                }

                result.add(d)
            }
        })

        result = addImageUrls(result)
        result
    }

    private static void constructWhereClause(Integer geomIdx, String lsids, String type, String[] dataResources, Map<String, Object> params, StringBuilder where) {
        if (geomIdx != null && geomIdx >= 0) {
            where.append(" geom_idx = :geom_idx ")
            params.put("geom_idx", geomIdx)
        }

        if (lsids != null && lsids.length() > 0) {
            if (where.length() > 0) {
                where.append(" AND ")
            }
            int count = 0
            where.append('(')
            lsids.split(',').each {
                if (count > 0) {
                    where.append(' OR ')
                }
                count++
                where.append(" lsid = :lsid${count} ".toString())
                params.put("lsid${count}".toString(), it)
            }
            where.append(')')
        }

        if (dataResources != null && dataResources.length > 0) {
            if (where.length() > 0) {
                where.append(" AND ")
            }
            int count = 0
            where.append('(')
            dataResources.each {
                if (count > 0) {
                    where.append(' OR ')
                }
                count++
                where.append(" data_resource_uid = :dataResources${count} ".toString())
                params.put("dataResources${count}".toString(), it)
            }
            where.append(')')
        }

        if (type != null) {
            if (where.length() > 0) {
                where.append(" AND ")
            }
            where.append(" type = :distribution_type ")
            params.put("distribution_type", type)
        }

    }

    public static final String GEOSERVER_URL_PLACEHOLDER = "<COMMON_GEOSERVER_URL>"

    @Deprecated
    def count(params, String type) {
        get(params, type, COUNT)
    }

    @Deprecated
    def pointRadius(params, String type) {
        get(params, type, POINT_RADIUS)
    }

    int POINT_RADIUS_COUNT = 1
    int POINT_RADIUS = 2
    int COUNT = 3

    @Deprecated
    def pointRadiusCount(params, String type) {
        get(params, type, POINT_RADIUS_COUNT)
    }

    @Deprecated
    def get(Map queryParams, String type, requestType) {
        Double latitude = queryParams.latitude as Double
        Double longitude = queryParams.longitude as Double
        Double radius = queryParams.radius as Double

        String wkt = queryParams?.wkt
        if (queryParams?.wkt && queryParams.wkt.toString().isNumber()) {
            wkt = spatialObjectsService.getObjectsGeometryById(queryParams.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        String lsids = queryParams.lsids?.toString()?.replace("https:/", "https://")
        Integer geomIdx = (queryParams.geom_idx ?: -1) as Integer
        String[] dataResources = queryParams.data_resource_uids as String[]

        if (queryParams.pid) {
            wkt = spatialObjectsService.getObjectsGeometryById(queryParams.pid as String, 'wkt')
        }

        StringBuilder whereClause = new StringBuilder()
        Map<String, Object> params = new HashMap<String, Object>()
        constructWhereClause(geomIdx, lsids, type, dataResources, params, whereClause)

        String groupBy = ""
        String wktSelect = ""
        if (wkt != null && wkt.length() > 0) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ")
            }
            whereClause.append("ST_INTERSECTS(the_geom, ST_GEOMFROMTEXT( :wkt , 4326))")
            params.put("wkt", wkt)

            wktSelect = ", ST_AREA(ST_INTERSECTION(the_geom, ST_GEOMFROMTEXT( :wkt , 4326))) as intersectArea"
        }

        if (latitude != null || longitude != null || radius != null) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ")
            }
            whereClause.append("ST_DWithin(the_geom, ST_GeomFromText(:point, 4326), :radius)")
            params.put('point', "POINT(" + longitude + " " + latitude + ")")
            params.put('radius', radius)
        }

        String sql

        if (requestType == COUNT || requestType == POINT_RADIUS_COUNT) {
            wktSelect = "SELECT family as name, count(*) as count "
            groupBy = " GROUP BY family"
            sql = wktSelect + " from Distributions"
        } else {
            sql = SELECT_CLAUSE + wktSelect + " from Distributions"
        }
        if (whereClause.length() > 0) {
            sql += " WHERE " + whereClause.toString()
        }
        sql += groupBy

        List result = new ArrayList()

        String[] fields = SELECT_CLAUSE.split(',')
        groovySql.query(sql, params) { ResultSet rs ->
            while (rs.next()) {
                Map map = [:]

                if (groupBy) {
                    map += [name: rs.getObject('name')]
                    map += [count: rs.getObject('count')]
                } else {
                    fields.eachWithIndex { String entry, int i ->
                        if (rs.getObject(i + 1) != null) {
                            map.put(entry, rs.getObject(i + 1))
                        }
                    }
                    if (wktSelect) {
                        map += [intersectArea: rs.getObject('intersectArea')]
                    }
                }

                result.add(map)
            }
        }

        result = addImageUrls(result)
        result
    }
}

