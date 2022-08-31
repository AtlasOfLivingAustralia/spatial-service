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
import groovy.util.logging.Slf4j
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import org.json.simple.JSONObject

@Slf4j
class DistributionRematchLsid extends SlaveProcess {

    void start() {
        String updateAll = 'true'.equalsIgnoreCase(String.valueOf(task.input.updateAll))

        List distributions = getDistributions()
        distributions.addAll(getChecklists())

        //unique by spcode
        int sqlCount = 0
        int count = 0
        distributions.each { d ->
            count = count + 1
            task.message = "Processing ${count} of ${distributions.size()}"

            String spcode = d.spcode

            String familyLsid = d.optString('family_lsid', '')
            String genusLsid = d.optString('genus_lsid', '')
            String taxonLsid = d.optString('lsid', '')

            if (updateAll || (familyLsid + genusLsid + taxonLsid).isEmpty()) {

                String sql = ''
                def match = processRecord([family: d.optString('family', ''), genus: d.optString('genus_name', ''), scientificName: d.optString('scientific', '')])

                if (!familyLsid.equals(match.familyID)) {
                    sql += "UPDATE distributions SET family_lsid = '" + match.familyID + "' WHERE spcode='" + spcode + "';"
                }
                if (!genusLsid.equals(match.genusID)) {
                    sql += "UPDATE distributions SET genus_lsid = '" + match.genusID + "' WHERE spcode='" + spcode + "';"
                }
                if (!taxonLsid.equals(match.taxonConceptID)) {
                    sql += "UPDATE distributions SET lsid = '" + match.taxonConceptID + "' WHERE spcode='" + spcode + "';"
                }

                if (sql.length() > 0) {
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
    }

    public def processRecord(def data) {
        def input = net.sf.json.JSONObject.fromObject(data)
        StringRequestEntity requestEntity = new StringRequestEntity(input.toString(), 'application/json', 'UTF-8')

        def url = task.input.namematchingUrl

        def response = Util.urlResponse("POST", url + "/api/searchByClassification", null, null, requestEntity)

        def output = net.sf.json.JSONObject.fromObject(response.text)

        def taxonConceptID = ''
        def familyID = ''
        def genusID = ''
        def ignoreTaxonMatch = false

        def value = output.get('taxonConceptID')
        if (StringUtils.isNotBlank(value)) {
            taxonConceptID = value
        }
        value = output.get('familyID')
        if (StringUtils.isNotBlank(value)) {
            familyID = value
        }
        value = output.get('genusID')
        if (StringUtils.isNotBlank(value)) {
            genusID = value
        }

        value = output.get('issues')
        ignoreTaxonMatch = ('' + value).indexOf('excluded') >= 0

        if (ignoreTaxonMatch) {
            taxonConceptID = ''
            familyID = ''
            genusID = ''
        }

        [taxonConceptID: taxonConceptID, familyID: familyID, genusID: genusID]
    }
}
