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

import au.org.ala.spatial.util.GeomMakeValid
import org.locationtech.jts.geom.Geometry
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils
import org.geotools.data.FeatureReader
import org.geotools.data.shapefile.ShapefileDataStore

import java.text.MessageFormat

@Commons
class DistributionCreation extends SlaveProcess {

    void start() {
        String uploadId = task.input.uploadId
        String data_resource_uid = task.input.data_resource_uid

        //upload shp into layersdb in a table with name layer.id
        String dir = grailsApplication.config.data.dir

        //open shapefile
        File file = new File(dir + "/uploads/" + uploadId + "/" + uploadId + ".shp")

        ShapefileDataStore sds = new ShapefileDataStore(file.toURI().toURL())
        FeatureReader reader = sds.featureReader

        //mapping between shp file field names and distributions table column names
        //shp name : [column name, column type (i=integer, s=string, b=boolean)]
        Map distributionFields = [gid           : ['gid', 'i'],
                                  //spcode: ['spcode', 'i'],
                                  scientific    : ['scientific', 's'],
                                  authority_    : ['authority_', 's'],
                                  common_nam    : ['common_nam', 's'],
                                  family        : ['family', 's'],
                                  genus_name    : ['genus_name', 's'],
                                  specific_n    : ['specific_n', 's'],
                                  min_depth     : ['min_depth', 'i'],
                                  max_depth     : ['max_depth', 'i'],
                                  pelagic_fl    : ['pelagic_fl', 'i'],
                                  coastal_fl    : ['coastal_fl', 'b'],
                                  desmersal_    : ['desmersal_fl', 'b'],
                                  estuarine_    : ['estuarine_fl', 'b'],
                                  family_lsi    : ['family_lsid', 's'],
                                  genus_lsid    : ['genus_lsid', 's'],
                                  caab_speci    : ['caab_species_number', 's'],
                                  caab_famil    : ['caab_family_number', 's'],
                                  group_name    : ['group_name', 's'],
                                  metadata_u    : ['metadata_u', 's'],
                                  lsid          : ['lsid', 's'],
                                  area_name     : ['area_name', 's'],
                                  pid           : ['pid', 's'],
                                  checklist_name: ['checklist_', 's'],
                                  area_km       : ['area_km', 'i'],
                                  notes         : ['notes', 's'],
                                  endemic       : ['endemic', 'b'],
                                  genus_exem    : ['genus_exemplar', 'b'],
                                  family_exe    : ['family_exemplar', 'b'],
                                  imageUrl      : ['family_exemplar', 'b'],
                                  image_qual    : ['image_quality', 's']]

        task.message = "reading shapefile"
        int sqlCount = 0
        while (reader.hasNext()) {
            def f = reader.next()

            //get record
            StringBuilder values = new StringBuilder()
            StringBuilder columns = new StringBuilder()
            String spcode = null
            f.getProperties().each { p ->
                if (p.getName().toString().equalsIgnoreCase('spcode')) {
                    spcode = (int) p.getValue()
                }
                distributionFields.each { String k, List v ->
                    if (p.getName().toString().equalsIgnoreCase(k)) {
                        if (columns.length() > 0) columns.append(",")
                        columns.append("\"").append(v[0]).append("\"")

                        if (values.length() > 0) values.append(",")
                        if (v[1].equalsIgnoreCase('s')) {
                            //string
                            values.append("\'\'").append(sqlEscapeString(p.getValue()).replace("\'", "\'\'")).append("\'\'")
                        } else if (v[1].equalsIgnoreCase('b')) {
                            //boolean
                            values.append(sqlEscapeString(p.getValue().toString()))
                        } else {
                            //number
                            values.append(sqlEscapeString('' + p.getValue()))
                        }
                    }
                }
            }

            //insert sql
            if (spcode != null) {

                Geometry g = (Geometry) f.getDefaultGeometry()
                if (!g.isValid()) {
                    try {
                        g = GeomMakeValid.makeValid(g)
                    } catch (err) {
                        log.error 'task: ' + task.id + ' failed validating wkt', err
                    }
                }

                if (g != null) {
                    String sql = MessageFormat.format("DELETE FROM distributions WHERE spcode = {0};" +
                            "INSERT INTO distributions (data_resource_uid,spcode," + columns.toString() + ", the_geom, type)" +
                            " VALUES (''{1}'', {2}," + values.toString() + ", ST_GEOMFROMTEXT(''{3}'', 4326), ''e'');\n",
                            '' + spcode,
                            sqlEscapeString(data_resource_uid),
                            '' + spcode,
                            sqlEscapeString(g.toString()))

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
        }

        String wmsurl = "<COMMON_GEOSERVER_URL>/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:Distributions&format=image/png&viewparams=s:"
        String sql = "UPDATE distributions SET bounding_box = st_envelope(the_geom), geom_idx = spcode, wmsurl = '" + wmsurl + "' || spcode;"

        // put distributions into objects table
        sql += "\nINSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch, area_km, bbox) " +
                "(select nextval('objects_id_seq'), geom_idx, geom_idx, '', '" +
                grailsApplication.config.userObjectsField + "', the_geom, false, " +
                "(st_area(ST_GeogFromWKB(st_asbinary(the_geom)), true)/1000000), ST_ASTEXT(ST_EXTENT(the_geom)) " +
                "from distributions where pid is null group by geom_idx, the_geom);"
        sql += "\nupdate distributions set pid = o.pid from objects o where distributions.geom_idx || '' = o.id and " +
                "distributions.geom_idx || '' = o.name and o.fid='cl1083' and distributions.the_geom = o.the_geom and " +
                "distributions.pid is null;"

        FileUtils.writeStringToFile(new File(getTaskPath() + 'finish.sql'), sql)
        addOutput('sql', 'finish.sql')

        //delete from uploads dir if master service is remote
        if (!grailsApplication.config.service.enable.toBoolean()) {
            FileUtils.deleteDirectory(new File(dir + "/uploads/" + uploadId + "/"))
        }

        addOutput("process", "DistributionRematchLsid")

        reader.close()
    }
}
