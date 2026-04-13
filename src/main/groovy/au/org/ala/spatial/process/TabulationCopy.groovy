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
import au.org.ala.spatial.dto.Tabulation
import grails.converters.JSON
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.web.json.JSONObject

@CompileStatic
@Slf4j
class TabulationCopy extends SlaveProcess {

    void start() {

        String sourceUrl = getInput('sourceUrl')

        //get tabulations
        List<Tabulation> tabulations = JSON.parse(Util.getUrl(sourceUrl + "/tabulations.json")) as List<Tabulation>
        File fname = new File(getTaskPath() + 'tabulationImport.sql')
        addOutput('sql', 'tabulationImport.sql')

        int sqlCount = 0

        for (Tabulation tab : tabulations) {
            if (getField(tab.fid2) && getField(tab.fid1)) {
                List<JSONObject> data = JSON.parse(Util.getUrl("${sourceUrl}/tabulation/data/${tab.fid1}/${tab.fid2}/tabulation.json")) as List<JSONObject>

                def ids1 = [:]
                for (def obj : getObjects(tab.fid1)) {
                    ids1.put(obj.name, obj.pid)
                }
                def ids2 = [:]
                for (def obj : getObjects(tab.fid2)) {
                    ids2.put(obj.name, obj.pid)
                }

                //sql to delete existing entry
                fname.write("DELETE FROM tabulation WHERE " +
                        "(fid1='${tab.fid1}' AND fid2='${tab.fid2}') OR (fid1='${tab.fid1}' AND fid2='${tab.fid2}');", true)

                //sql to add new entries
                StringBuilder sb = new StringBuilder()
                for (JSONObject row : data) {
                    def id1 = ids1.get(row.get('name1'))
                    def id2 = ids2.get(row.get('name2'))
                    if (id1 && id2) {
                        sb.append("INSERT INTO tabulation (fid1, fid2, pid1, pid2, species, occurrences, area, speciest1, speciest2) " +
                                "VALUES ('${row.get('fid1')}','${row.get('fid2')}','${id1}','${id2}'," +
                                "${row.get('species')},${row.get('occurrences')},${row.get('area')},${row.get('speciest1')},${row.get('speciest2')});\n")
                    } else {
                        //TODO: log error
                    }
                }

                File sqlFile = new File(getTaskPath() + 'tabulation' + sqlCount + '.sql')
                boolean append = sqlFile.exists() && sqlFile.length() < 5 * 1024 * 1024
                if (!append) {
                    sqlCount++
                    sqlFile = new File(getTaskPath() + 'tabulation' + sqlCount + '.sql')
                    addOutput('sql', 'tabulation' + sqlCount + '.sql')
                    sqlFile.write(sb.toString())
                }
                if (append) {
                    sqlFile.append(sb.toString())
                }
            }
        }

    }


}
