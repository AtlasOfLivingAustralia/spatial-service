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
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

@Slf4j
class TabulationCopy extends SlaveProcess {

    void start() {

        def sourceUrl = taskWrapper.input.sourceUrl

        //get tabulations
        def tabulations = JSON.parse(Util.getUrl(sourceUrl + "/tabulations.json"))
        File fname = new File(getTaskPath() + 'tabulationImport.sql')
        addOutput('sql', 'tabulationImport.sql')

        int sqlCount = 0

        for (def tab : tabulations) {
            if (getField(tab.fid2) && getField(tab.fid1)) {
                def data = JSON.parse(Util.getUrl("${sourceUrl}/tabulation/data/${tab.fid1}/${tab.fid2}/tabulation.json"))

                def ids1 = [:]
                for (def obj : getObjects(tab.fid1)) {
                    ids1.put(obj.name, obj.pid)
                }
                def ids2 = [:]
                for (def obj : getObjects(tab.fid2)) {
                    ids2.put(obj.name, obj.pid)
                }

                //sql to delete existing entry
                FileUtils.writeStringToFile(fname, "DELETE FROM tabulation WHERE " +
                        "(fid1='${tab.fid1}' AND fid2='${tab.fid2}') OR (fid1='${tab.fid1}' AND fid2='${tab.fid2}');", true)

                //sql to add new entries
                StringBuilder sb = new StringBuilder()
                for (def row : data) {
                    def id1 = ids1.get(row.name1)
                    def id2 = ids2.get(row.name2)
                    if (id1 && id2) {
                        sb.append("INSERT INTO tabulation (fid1, fid2, pid1, pid2, species, occurrences, area, speciest1, speciest2) " +
                                "VALUES ('${row.fid1}','${row.fid2}','${id1}','${id2}'," +
                                "${row.species},${row.occurrences},${row.area},${row.speciest1},${row.speciest2});\n")
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
                }
                FileUtils.writeStringToFile(sqlFile, sb.toString(), append)
            }
        }

    }


}
