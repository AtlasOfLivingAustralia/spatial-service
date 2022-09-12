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

import au.com.bytecode.opencsv.CSVReader
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils

import java.text.MessageFormat

@Slf4j
class TrackHarvest extends SlaveProcess {

    void start() {

        try {

            String biocacheServiceUrl = taskWrapper.input.biocacheServiceUrl.toString()
            String[] dataProviderIds = taskWrapper.input.dataProviders.toString().split(",")

            StringBuilder sb = new StringBuilder()
            sb.append("DELETE FROM distributions WHERE type = 't';\n")

            //biocache-store to distributions mapping
            Map mapping = [
                    'genus_guid'       : 'genus_lsid',
                    'species_guid'     : 'lsid',
                    'collectionID'     : 'metadata_u',
                    'family'           : 'family',
                    'genus'            : 'genus_name',
                    'scientificName'   : 'scientific',
                    'raw_taxon_name'   : 'specific_n',
                    'vernacularName'   : 'common_nam',
                    'family_id'        : 'family_lsid',
                    'data_resource_uid': 'data_resource_uid',
                    'footprintWKT'     : 'the_geom',
                    'recordID'         : 'group_name',
                    'eventID'          : 'area_name'
            ]
            //get
            String records = new URL("${biocacheServiceUrl}/occurrences/index/download?facet=off&q=data_provider_uid:${dataProviderIds.join(" OR data_provider_uid:")}&fields=${mapping.keySet().join(",")}&reasonTypeId=10&zip=false&qa=none&dwcHeaders=true").text

            CSVReader csv = new CSVReader(new StringReader(records))

            //skip to data.csv header
            csv.readNext()
            csv.readNext()

            List header = csv.readNext().toList()
            def columns = []
            def scientificAdded = false
            for (String s : header) {
                if (s.equals("scientificName")) {
                    if (!scientificAdded) {
                        columns.add("scientific")
                        scientificAdded = true
                    } else {
                        columns.add("specific_n")
                    }
                } else {
                    String h = mapping.get(s)
                    if (h == null && s.endsWith("_raw")) {
                        h = mapping.get(s.substring(0, s.length() - 4))
                    }
                    if (h == null) {
                        h = mapping.get(s + ".p")
                    }
                    if (h != null) {
                        columns.add(h)
                    } else {
                        log.error("cannot map column " + s)
                    }
                }
            }

            String[] row
            //start of default range for track spcodes
            int spcode = 5000000
            while ((row = csv.readNext()) != null) {
                if (row.length <= 1) {
                    break
                }
                def values = []

                for (int i = 0; i < header.size() && i < row.length; i++) {
                    if (StringUtils.isNotEmpty(row[i])) {
                        if (header[i].equals("footprintWKT")) {
                            values.add("ST_GEOMFROMTEXT(''" + row[i] + "'', 4326)")
                        } else {
                            values.add("\'\'" + sqlEscapeString(row[i]).replace("\'", "\'\'") + "\'\'")
                        }
                    }
                }

                if (columns.size() > 0) {
                    String sql = MessageFormat.format("INSERT INTO distributions (spcode," + columns.join(",") + ", type)" +
                            " VALUES ({0}," + values.join(",") + ", ''t'');\n", spcode.toString())

                    sb.append(sql)
                }

                spcode++
            }

            String wmsurl = "<COMMON_GEOSERVER_URL>/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:Distributions&format=image/png&viewparams=s:"
            sb.append("UPDATE distributions SET bounding_box = st_envelope(the_geom), geom_idx = spcode, wmsurl = '" + wmsurl + "' || spcode WHERE type = 't';")

            FileUtils.writeStringToFile(new File(getTaskPath() + "tracks.sql"), sb.toString())

            addOutput('sql', 'tracks.sql')

        } catch (Exception e) {
            log.error("error initialising journalmap data", e)
        }
    }
}
