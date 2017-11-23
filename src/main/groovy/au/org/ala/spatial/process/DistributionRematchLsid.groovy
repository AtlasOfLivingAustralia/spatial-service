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
import org.apache.commons.io.FileUtils
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

@Slf4j
class DistributionRematchLsid extends SlaveProcess {

    void start() {
        String updateAll = task.input.updateAll

        List distributions = getDistributions()
        distributions.addAll(getChecklists())

        //unique by spcode
        int sqlCount = 0
        distributions.each { d ->
            String spcode = d.spcode

            boolean hasFamilyLsid = d.containsKey('family_lsid') && d.family_lsid != null && d.family_lsid.length() > 0
            boolean hasGenusLsid = d.containsKey('genus_lsid') && d.genus_lsid != null && d.genus_lsid.length() > 0
            boolean hasLsid = d.containsKey('lsid') && d.lsid != null && d.lsid.length() > 0

            String newFamilyLsid = (updateAll || !hasFamilyLsid) && d.family_lsid != d.containsKey('family') && d.family != null && d.family.length() > 0 ? lookupSpeciesOrFamilyLsid(d.family) : null
            String newGenusLsid = (updateAll || !hasGenusLsid) && d.containsKey('genus_name') && d.genus_name != null && d.genus_name.length() > 0 ? lookupGenusLsid(d.genus_name) : null
            String newLsid = (updateAll || !hasLsid) && d.containsKey('scientific') && d.scientific != null && d.scientific.length() > 0 ? lookupSpeciesOrFamilyLsid(d.scientific) : null

            String sqlf = "UPDATE distributions SET family_lsid = '" + newFamilyLsid + "' WHERE spcode='" + spcode + "';"
            String sqlg = "UPDATE distributions SET genus_lsid = '" + newGenusLsid + "' WHERE spcode='" + spcode + "';"
            String sqll = "UPDATE distributions SET lsid = '" + newLsid + "' WHERE spcode='" + spcode + "';"

            String sql = ''
            if (newFamilyLsid != null && (!hasFamilyLsid || !newFamilyLsid.equals(d.family_lsid))) {
                sql += sqlf
            }
            if (newGenusLsid != null && (!hasGenusLsid || !newGenusLsid.equals(d.genus_lsid))) {
                sql += sqlg
            }
            if (newLsid != null && (!hasLsid || !newLsid.equals(d.lsid))) {
                sql += sqll
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

    String lookupSpeciesOrFamilyLsid(id) {
        String lsid = null

        try {
            URL wsUrl = new URL(task.input.bieUrl.toString() + "/ws/guid/" + id)
            URI uri = new URI(wsUrl.getProtocol(), wsUrl.getAuthority(), wsUrl.getPath(), wsUrl.getQuery(), wsUrl.getRef())

            def response = Util.urlResponse("GET", uri.toURL().toString())

            if (!response || !response?.text || response?.statusCode != 200) {
                log.error("Fetching of species or family LSID failed :" + response + ", " + id)
                return null
            }

            JSONArray jsonArr = (JSONArray) new JSONParser().parse(response.text)
            if (jsonArr.size() > 0) {
                JSONObject jsonObj = (JSONObject) jsonArr.get(0)

                lsid = (String) jsonObj.get("acceptedIdentifier")
            }
        } catch (e) {
            log.error 'failed to search lsid', e
        } finally {
            if (get != null) {
                get.releaseConnection()
            }
        }

        lsid
    }

    // Need to use a different web service to lookup genus lsids
    String lookupGenusLsid(id) {
        String genusLsid = null

        try {
            URL wsUrl = new URL(task.input.bieUrl.toString() + "/search.json?fq=rank:genus&q=" + id)
            URI uri = new URI(wsUrl.getProtocol(), wsUrl.getAuthority(), wsUrl.getPath(), wsUrl.getQuery(), wsUrl.getRef())

            def response = Util.urlResponse("GET", uri.toURL().toString())

            if (!response || response?.statusCode != 200) {
                throw new IllegalStateException("Fetching of species or family LSID failed")
            }

            String responseContent = response.text

            JSONObject jsonObj = (JSONObject) new JSONParser().parse(responseContent)

            if (jsonObj.containsKey("searchResults")) {
                JSONObject searchResultsObj = (JSONObject) jsonObj.get("searchResults")
                if (searchResultsObj.containsKey("results")) {
                    JSONArray resultsArray = (JSONArray) searchResultsObj.get("results")
                    if (resultsArray.size() > 0) {
                        JSONObject firstResult = (JSONObject) resultsArray.get(0)
                        if (firstResult.containsKey("guid")) {
                            genusLsid = (String) firstResult.get("guid")
                        }
                    }
                }
            }
        } catch (err) {
            log.error 'failed to search lsid', err
        }

        genusLsid
    }
}
