/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License") you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.layers

import au.org.ala.layers.dao.LayerIntersectDAO
import au.org.ala.layers.dao.ObjectDAO
import au.org.ala.spatial.util.BatchConsumer
import au.org.ala.spatial.util.BatchProducer
import com.vividsolutions.jts.geom.Geometry
import grails.converters.JSON
import grails.core.GrailsApplication
import org.geotools.geojson.geom.GeometryJSON

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

//TODO: replace batch intersections with a slave process
class IntersectController {

    ObjectDAO objectDao
    LayerIntersectDAO layerIntersectDao
    GrailsApplication grailsApplication

    def intersect(String ids, Double lat, Double lng) {
        render layerIntersectDao.samplingFull(ids, lng, lat) as JSON
    }

    def batch() {
        File dir = new File((grailsApplication.config.data.dir + '/intersect/batch/') as String)
        dir.mkdirs()
        BatchConsumer.start(layerIntersectDao, dir.getPath(), grailsApplication.config.sampling.threads.toInteger())

        //help get params when they don't pick up automatically from a POST
        String fids = params.containsKey('fids') ? params.fids : ''
        String points = params.containsKey('points') ? params.points : ''
        String gridcache = params.containsKey('gridcache') ? params.gridcache : '0'
        try {
            if ("POST".equalsIgnoreCase(request.getMethod()) && !params.containsKey('fids') && !params.containsKey('points')) {
                for (String param : request.reader.text.split("&")) {
                    if (param.startsWith("fids=")) {
                        fids = param.substring(5)
                    } else if (param.startsWith("points=")) {
                        points = param.substring(7)
                    }
                }
            }
        } catch (err) {
            log.error 'failed to read POST body for batch intersect', err
        }

        if (points.isEmpty() || fids.isEmpty()) {
            render null as JSON
        }

        Map map = new HashMap()
        String batchId
        try {

            // get limits
            int pointsLimit, fieldsLimit

            String[] passwords = grailsApplication.config.batch_sampling_passwords.toString().split(',')
            pointsLimit = grailsApplication.config.batch_sampling_points_limit.toInteger()
            fieldsLimit = grailsApplication.config.batch_sampling_fields_limit.toInteger()

            String password = params.containsKey('pw') ? params.pw : null
            for (int i = 0; password != null && i < passwords.length; i++) {
                if (passwords[i] == password) {
                    pointsLimit = Integer.MAX_VALUE
                    fieldsLimit = Integer.MAX_VALUE
                }
            }

            // count fields
            int countFields = 1
            int p = 0
            while ((p = fids.indexOf(',', p + 1)) > 0)
                countFields++

            // count points
            int countPoints = 1
            p = 0
            while ((p = points.indexOf(',', p + 1)) > 0)
                countPoints++

            if (countPoints / 2 > pointsLimit) {
                map.put("error", "Too many points.  Maximum is " + pointsLimit)
            } else if (countFields > fieldsLimit) {
                map.put("error", "Too many fields.  Maximum is " + fieldsLimit)
            } else {
                batchId = BatchProducer.produceBatch(dir.getPath(), "request address:" + request.getRemoteAddr(), fids, points, gridcache)

                map.put("batchId", batchId)
                BatchProducer.addInfoToMap(dir.getPath(), batchId, map)
                map.put("statusUrl", grailsApplication.config.grails.serverURL + '/intersect/batch/' + batchId)
            }

            render map as JSON
            return
        } catch (Exception e) {
            e.printStackTrace()
        }

        map.put("error", "failed to create new batch")
        render map as JSON
    }

    def batchStatus(String id) {
        File dir = new File((grailsApplication.config.data.dir + '/intersect/batch/') as String)
        dir.mkdirs()
        BatchConsumer.start(layerIntersectDao, dir.getPath(), grailsApplication.config.sampling.threads.toInteger())

        Map map = new HashMap()
        try {
            BatchProducer.addInfoToMap(dir.getPath(), id, map)
            if (map.get("finished") != null) {
                map.put("downloadUrl", grailsApplication.config.grails.serverURL + '/intersect/batch/download/' + id)
            }
        } catch (err) {
            log.error 'failed to get batch status: ' + id, err
        }

        render map as JSON
    }

    def batchDownload(String id) {
        Boolean csv = params.containsKey('csv') ? params.csv.toString().toBoolean() : false

        File dir = new File((grailsApplication.config.data.dir + '/intersect/batch/') as String)
        dir.mkdirs()
        BatchConsumer.start(layerIntersectDao, dir.getPath(), grailsApplication.config.sampling.threads.toInteger())

        OutputStream os = null
        BufferedInputStream bis = null
        ZipOutputStream zip = null
        try {
            Map map = new HashMap()
            BatchProducer.addInfoToMap(dir.getPath(), String.valueOf(id), map)
            if (map.get("finished") != null) {
                os = response.getOutputStream()

                bis = new BufferedInputStream(new FileInputStream(dir.getPath() + File.separator + id + File.separator + "sample.csv"))

                if (!csv) {
                    zip = new ZipOutputStream(os)
                    zip.putNextEntry(new ZipEntry("sample.csv"))

                    os = zip
                }
                byte[] buffer = new byte[4096]
                int size
                while ((size = bis.read(buffer)) > 0) {
                    os.write(buffer, 0, size)
                }
                os.flush()
            }
        } catch (err) {
            log.error 'failed to download batch', err
        } finally {
            if (os != null) {
                try {
                    os.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
            if (bis != null) {
                try {
                    bis.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
            if (zip != null) {
                try {
                    zip.close()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                }
            }
        }
    }

    def reloadConfig() {
        Map map = new HashMap()
        layerIntersectDao.reload()
        map.put("layerIntersectDao", "successful")

        render map as JSON
    }

    def pointRadius(String fid, Double lat, Double lng, Double radius) {
        render objectDao.getObjectsWithinRadius(fid, lat, lng, radius) as JSON
    }

    def wktGeometryIntersect(String fid) {
        render objectDao.getObjectsIntersectingWithGeometry(fid, request.reader.text) as JSON
    }

    def geojsonGeometryIntersect(String fid) {
        String wkt = geoJsonToWkt(request.reader.text)
        render objectDao.getObjectsIntersectingWithGeometry(fid, wkt) as JSON
    }

    String geoJsonToWkt(String geoJson) {
        GeometryJSON gJson = new GeometryJSON()
        Geometry geometry = gJson.read(new StringReader(geoJson))

        if (!geometry.isValid()) {
            return null
        }

        String wkt = geometry.toText()
        return wkt
    }

    def objectIntersect(String fid, String pid) {
        render objectDao.getObjectsIntersectingWithObject(fid, pid) as JSON
    }

    def poiPointRadiusIntersect(Double lat, Double lng, Double radius) {
        render objectDao.getPointsOfInterestWithinRadius(lat, lng, radius) as JSON
    }

    def wktPoiIntersectGet() {
        if (params.containsKey('wkt')) {
            render objectDao.pointsOfInterestGeometryIntersect(params.wkt.toString()) as JSON
        } else {
            render objectDao.pointsOfInterestGeometryIntersect(objectDao.getObjectsGeometryById(params.pid.toString(), 'wkt')) as JSON
        }
    }

    def geojsonPoiIntersectGet() {
        String wkt = geoJsonToWkt(params.geojson.toString())
        render objectDao.pointsOfInterestGeometryIntersect(wkt) as JSON

    }

    def objectPoiIntersect(Integer pid) {
        render objectDao.pointsOfInterestObjectIntersect(Integer.toString(pid)) as JSON
    }

    def poiPointRadiusIntersectCount(Double lat, Double lng, Double radiusKm) {
        Map map = new HashMap()
        map.put("count", objectDao.getPointsOfInterestWithinRadiusCount(lat, lng, radiusKm))

        render map as JSON
    }

    def wktPoiIntersectGetCount() {
        Map map = new HashMap()
        map.put("count", objectDao.pointsOfInterestGeometryIntersectCount(params.wkt.toString()))

        render map as JSON
    }

    def geojsonPoiIntersectGetCount() {
        String wkt = geoJsonToWkt(params.geojson.toString())
        Map map = new HashMap()
        map.put("count", objectDao.pointsOfInterestGeometryIntersectCount(wkt))

        render map as JSON
    }

    def objectPoiIntersectCount(Integer pid) {
        Map map = new HashMap()
        map.put("count", objectDao.pointsOfInterestObjectIntersectCount(Integer.toString(pid)))

        render map as JSON
    }
}
