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
import org.apache.commons.httpclient.Header
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.json.simple.JSONValue

//@CompileStatic
@Slf4j
class JournalMapHarvest extends SlaveProcess {

    void start() {

        try {

            String journalMapUrl = spatialConfig.journalmap.url
            String journalMapKey = spatialConfig.journalmap.api_key

            List<JSONObject> journalMapArticles = new ArrayList()

            int page = 1
            int maxpage = 0
            List<String> publicationsIds = new ArrayList<String>()
            while (page == 1 || page <= maxpage) {
                taskWrapper.task.message = "fetching publications page: " + page

                String url = journalMapUrl + "api/publications.json?version=1.0&key=" + journalMapKey + "&page=" + page
                page = page + 1

                Map response = Util.urlResponse("GET", url)

                if (response && response?.statusCode >= 200 && response?.statusCode < 300) {
                    //update maxpage
                    maxpage = Integer.parseInt(getHeader(response.headers as Iterable<Header>, "X-Pages")?.toString())

                    //cache

                    JSONArray jcollection = (JSONArray) JSON.parse(response?.text as String)
                    for (int i = 0; i < jcollection.size(); i++) {
                        if (((JSONObject) jcollection.get(i)).containsKey("id")) {
                            publicationsIds.add(((JSONObject) jcollection.get(i)).get("id").toString())
                        }
                    }
                } else {
                    //failed
                    break
                }
            }

            for (String publicationsId : publicationsIds) {
                //allow for collection failure
                try {
                    page = 1
                    maxpage = 0
                    while (page == 1 || page <= maxpage) {
                        taskWrapper.task.message = "fetching articles for publication: " + publicationsId + " page: " + page

                        String url = journalMapUrl + "api/articles.json?version=1.0&key=" + journalMapKey + "&page=" + page + "&publication_id=" + publicationsId
                        page = page + 1

                        Map response = Util.urlResponse("GET", url)

                        if (response && response?.statusCode >= 200 && response?.statusCode < 300) {
                            //update maxpage
                            maxpage = Integer.parseInt(getHeader(response.headers as Iterable<Header>, "X-Pages")?.toString())

                            //cache

                            JSONArray jarticles = (JSONArray) JSON.parse(response?.text as String)
                            for (int j = 0; j < jarticles.size(); j++) {
                                JSONObject o = (JSONObject) jarticles.get(j)
                                if (o.containsKey("locations")) {
                                    journalMapArticles.add(o)
                                }
                            }
                        } else {
                            //failed
                            break
                        }
                    }
                } catch (Exception e) {
                    log.error("journalmap - failure to get articles from publicationsId: " + publicationsId, e)
                }
            }

            //save to disk cache
            def jaFile = new File("${spatialConfig.data.dir}/journalmap.json")
            FileWriter fw = new FileWriter(jaFile)
            JSONValue.writeJSONString(journalMapArticles, fw)
            fw.flush()
            fw.close()

            addOutput('files', '/journalmap.json')

        } catch (Exception e) {
            log.error("error initialising journalmap data", e)
        }
    }

    String getHeader(Iterable<Header> headers, key) {
        for (Header h : headers) {
            if (h.name == key) {
                return h.value
            }
        }

        return ''
    }
}
