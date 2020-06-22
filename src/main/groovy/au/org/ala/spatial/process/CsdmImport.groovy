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

package au.org.ala.spatial.process


import au.org.ala.spatial.Util
import au.org.ala.spatial.util.GeomMakeValid
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.geom.Polygon
import grails.converters.JSON
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils
import org.geotools.feature.FeatureCollection
import org.geotools.feature.FeatureIterator
import org.geotools.geojson.feature.FeatureJSON
import org.json.simple.JSONObject

import java.text.MessageFormat

@Commons
class CsdmImport extends SlaveProcess {

    void start() {
        try {
            String csdmUrl = task.input.csdmUrl
            String data_resource_uid = task.input.data_resource_uid
            Long startIdx = task.input.start_id

            //get dataset ids from CKAN
            JSONObject datasets = JSON.parse(Util.getUrl(csdmUrl + "/api/3/action/package_list"))
            List datasetIds = (List) datasets.get("result")

            //remove dataset ids already loaded
            getDistributions().each { d ->
                if (data_resource_uid.equals(d.data_resource_uid)) {
                    if (datasetIds.contains(d.group_name)) {
                        datasetIds.remove(d.group_name)
                    } else {
                        startIdx++
                    }
                }
            }

            File tmpDir = File.createTempDir()

            Geometry aggregated = null
            String organisation
            String url
            JSONObject metadata
            String doi
            String scientific

            int sqlCount = 0

            //load each dataset
            datasetIds.each { id ->
                // resource list
                JSONObject dataset = JSON.parse(Util.getUrl(csdmUrl + "/api/3/action/package_show?id=" + URLEncoder.encode(id)))

                organisation = dataset.result.organization.name
                url = csdmUrl + "/dataset/" + URLEncoder.encode(id)

                dataset.get("result").get("resources").each { resource ->
                    if (resource.name.startsWith("equal")) {
                        scientific = resource.name.replace('equal.sens.spec_', '').replace('_maxent_cloglog.tif', '')
                        // download geotiff
                        File raster = new File(tmpDir, "raster.tif")
                        File reprojected = new File(tmpDir, "reprojected.tif")
                        File geojson = new File(tmpDir, "geojson")

                        if (raster.exists()) raster.delete()
                        if (reprojected.exists()) reprojected.delete()
                        if (geojson.exists()) geojson.delete()

                        FileUtils.copyURLToFile(new URL(resource.url), raster)

                        String[] cmd = [
                                grailsApplication.config.gdal.dir + "gdalwarp",
                                "-t_srs", "EPSG:4326"
                                , raster.path
                                , reprojected.path]

                        try {
                            runCmd(cmd, false)
                        } catch (Exception e) {
                            log.error("error running gdalwarp (1)", e)
                        }
                        cmd = [grailsApplication.config.gdal.dir + "gdal_polygonize.py",
                               "-f", "GeoJSON"
                               , reprojected.path
                               , geojson.path]
                        try {
                            runCmd(cmd, false)
                        } catch (Exception e) {
                            log.error("error running gdal_polygonize (2)", e)
                        }

                        FeatureJSON features = new FeatureJSON()

                        String geojsonString = FileUtils.readFileToString(geojson)

                        // remove CRS (to fix bug)
                        geojsonString = geojsonString.replaceAll('"crs"', '"ignore"')

                        FeatureCollection fc = features.readFeatureCollection(new StringReader(geojsonString))

                        FeatureIterator fi = fc.features()

                        while (fi.hasNext()) {
                            org.opengis.feature.Feature f = fi.next()

                            // only use presence features
                            if (f.getProperty("DN").value == 1 && f.defaultGeometryProperty.value instanceof Polygon) {
                                Geometry g = f.defaultGeometryProperty.value

                                if (g != null) {
                                    if (!g.isValid()) {
                                        try {
                                            g = GeomMakeValid.makeValid(g)
                                        } catch (err) {
                                            log.error 'task: ' + task.id + ' failed validating wkt', err
                                        }
                                    }

                                    if (g == null) {
                                        aggregated = aggregated
                                    } else if (aggregated == null) {
                                        aggregated = g
                                    } else {
                                        Geometry a = aggregated.union(g)
                                        aggregated = a
                                    }
                                }
                            }
                        }
                    } else if (resource.name.equals("experiment_input_metadata.json")) {
                        // download metadata
                        metadata = JSON.parse(Util.getUrl(resource.url))

                        doi = metadata.data.doiSpeciesData
                    }
                }

                // build db insert
                if (aggregated != null) {
                    String spcode = startIdx;

                    String sql = MessageFormat.format("INSERT INTO distributions (data_resource_uid,spcode,group_name, the_geom, type, metadata_u, gid, scientific, specific_n, area_name)" +
                            " VALUES (''{1}'', ''{2}'', ''{3}'', ST_GEOMFROMTEXT(''{4}'', 4326), ''e'', ''{5}'', ''{6}'', ''{7}'', ''{8}'', ''{9}'');\n",
                            sqlEscapeString(data_resource_uid),
                            '' + spcode,
                            organisation,
                            sqlEscapeString(aggregated.toString()),
                            sqlEscapeString(url),
                            '' + spcode,
                            scientific,
                            scientific,
                            id)

                    File sqlFile = new File(getTaskPath() + 'objects' + sqlCount + '.sql')
                    boolean append = sqlFile.exists() && sqlFile.length() < 5 * 1024 * 1024
                    if (!append) {
                        sqlCount++
                        sqlFile = new File(getTaskPath() + 'objects' + sqlCount + '.sql')
                        addOutput('sql', 'objects' + sqlCount + '.sql')
                    }
                    FileUtils.writeStringToFile(sqlFile, sql, append)
                }
            }

            String wmsurl = "<COMMON_GEOSERVER_URL>/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:Distributions&format=image/png&viewparams=s:"
            String sql = "UPDATE distributions SET bounding_box = st_envelope(the_geom), geom_idx = spcode, wmsurl = '" + wmsurl + "' || spcode;"

            // put distributions into objects table
            sql += "\nupdate distributions set pid = o.pid from objects o where distributions.the_geom = o.the_geom and distributions.pid is null;"
            sql += "\nINSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch, area_km, bbox) " +
                    "(select nextval('objects_id_seq'), max(spcode), max(area_name), '', '" +
                    grailsApplication.config.userObjectsField + "', the_geom, false, " +
                    "(st_area(ST_GeogFromWKB(st_asbinary(the_geom)), true)/1000000), ST_ASTEXT(ST_EXTENT(the_geom)) " +
                    "from distributions where pid is null group by the_geom);"
            sql += "\nupdate distributions set pid = o.pid from objects o where distributions.the_geom = o.the_geom and " +
                    "distributions.pid is null and fid = '" + grailsApplication.config.userObjectsField + "' and " +
                    "o.id = '' || distributions.spcode;"
            sql += "\nupdate distributions set pid = o.pid from objects o where distributions.the_geom = o.the_geom and " +
                    "distributions.pid is null and o.fid = '" + grailsApplication.config.userObjectsField + "';"

            FileUtils.writeStringToFile(new File(getTaskPath() + 'finish.sql'), sql)
            addOutput('sql', 'finish.sql')

            addOutput("process", "DistributionRematchLsid")
        } catch (err) {
            err.printStackTrace()
        }
    }
}
