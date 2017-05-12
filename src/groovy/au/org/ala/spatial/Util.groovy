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

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.spatial.slave.Task
import grails.converters.JSON
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.NameValuePair
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.log4j.Logger
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

class Util {

    static final Logger log = Logger.getLogger(Util.toString())

    static String getUrl(String url) {
        DefaultHttpClient client = new DefaultHttpClient()

        HttpParams params = client.getParams()
        HttpConnectionParams.setConnectionTimeout(params, 10000)
        HttpConnectionParams.setSoTimeout(params, 600000)

        HttpGet get = new HttpGet(url)
        HttpResponse response = client.execute(get)
        String out = IOUtils.toString(response.getEntity().getContent())
        get.releaseConnection()

        out
    }

    static String postUrl(String url, NameValuePair[] params) throws Exception {

        HttpClient client = new HttpClient()
        PostMethod post = new PostMethod(url)

        post.setRequestBody(params)

        int result = client.executeMethod(post)

        // Get the response
        if (result == 200) {
            return post.getResponseBodyAsString()
        }

        return null
    }

    static makeQid(query) {
        PostMethod post = new PostMethod("${query.bs}/webportal/params")

        if (query.q instanceof List) {
            post.addParameter('q', ((List)query.q)[0].toString())
            if (query.q.size() > 1) query.q.subList(1, query.q.size()).each { post.addParameter('fq', it.toString()) }
        } else {
            post.addParameter('q', query.q.toString())
            if (query.fq) query.fq.each {
                if (it instanceof String)
                    post.addParameter('fq', it)
            }
        }

        if (query.wkt) post.addParameter('wkt', query.wkt.toString())

        post.addParameter('bbox', 'true')

        HttpClient client = new HttpClient()
        client.getHttpConnectionManager().getParams().setConnectionTimeout(10000)
        client.getHttpConnectionManager().getParams().setSoTimeout(600000)

        client.executeMethod(post)

        post.getResponseBodyAsString()
    }

    static getQid(bs, qid) {
        JSON.parse(getUrl("$bs/webportal/params/details/$qid"))
    }

    static String[] getDistributionsOrChecklists(JSONArray ja) {
        if (ja == null || ja.isEmpty()) {
            return new String[0]
        } else {
            String[] lines = new String[ja.size() + 1]
            lines[0] = "SPCODE,SCIENTIFIC_NAME,AUTHORITY_FULL,COMMON_NAME,FAMILY,GENUS_NAME,SPECIFIC_NAME,MIN_DEPTH,MAX_DEPTH,METADATA_URL,LSID,AREA_NAME,AREA_SQ_KM"
            for (int i = 0; i < ja.size(); i++) {
                JSONObject jo = (JSONObject) ja.get(i)
                String spcode = jo.containsKey('spcode') ? jo.get('spcode').toString() : ""
                String scientific = jo.containsKey('scientific') ? jo.get('scientific').toString() : ""
                String auth = jo.containsKey('authority') ? jo.get('authority').toString() : ""
                String common = jo.containsKey('common_nam') ? jo.get('common_nam').toString() : ""
                String family = jo.containsKey('family') ? jo.get('family').toString() : ""
                String genus = jo.containsKey('genus') ? jo.get('genus').toString() : ""
                String name = jo.containsKey('specific_n') ? jo.get('specific_n').toString() : ""
                String min = jo.containsKey('min_depth') ? jo.get('min_depth').toString() : ""
                String max = jo.containsKey('max_depth') ? jo.get('max_depth').toString() : ""

                String md = jo.containsKey('metadata_u') ? jo.get('metadata_u').toString() : ""
                String lsid = jo.containsKey('lsid') ? jo.get('lsid').toString() : ""
                String areaName = jo.containsKey('area_name') ? jo.get('area_name').toString() : ""
                String areaKm = jo.containsKey('area_km') ? jo.get('area_km').toString() : ""
                String dataResourceUid = jo.containsKey('data_resouce_uid') ? jo.get('data_resouce_uid').toString() : ""

                lines[i + 1] = spcode + "," + wrap(scientific) + "," + wrap(auth) + "," + wrap(common) + "," +
                    wrap(family) + "," + wrap(genus) + "," + wrap(name) + "," + min + "," + max +
                    "," + wrap(md) + "," + wrap(lsid) + "," + wrap(areaName) + "," + wrap(areaKm) +
                    "," + wrap(dataResourceUid)
            }

            return lines
        }
    }

    static JSONArray getDistributionsOrChecklistsData(String type, String wkt, String lsids, String geomIdx, String layersUrl) throws Exception {
        StringBuilder sbProcessUrl = new StringBuilder()
        sbProcessUrl.append("/").append(type)

        HttpClient client = new HttpClient()
        PostMethod post = new PostMethod(layersUrl + sbProcessUrl.toString())

        if (wkt != null) {
            post.addParameter("wkt", wkt)
        }
        if (lsids != null) {
            post.addParameter("lsids", lsids)
        }
        if (geomIdx != null) {
            post.addParameter("geom_idx", geomIdx)
        }
        post.addRequestHeader("Accept", "application/json, text/javascript, */*")
        int result = client.executeMethod(post)
        if (result == 200) {
            String txt = post.getResponseBodyAsString()
            JSONParser jp = new JSONParser()
            JSONArray ja = (JSONArray) jp.parse(txt)
            return ja
        }

        return null
    }

    static String wrap(String s) {
        return "\"" + s.replace("\"", "\"\"").replace("\\", "\\\\") + "\""
    }

    static int runCmd(String[] cmd, boolean logToTask, Task task) {
        int exitValue = 1

        ProcessBuilder builder = new ProcessBuilder(cmd)
        builder.environment().putAll(System.getenv())
        builder.redirectErrorStream(false)

        try {
            Process proc = builder.start()

            // any error message?
            StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "", logToTask ? task : null)

            // any output?
            StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "", logToTask ? task : null)

            // kick them off
            errorGobbler.start()
            outputGobbler.start()

            int exitVal = proc.waitFor()

            errorGobbler.interrupt()
            outputGobbler.interrupt()

            // any error???
            exitValue = exitVal
        } catch (Exception e) {
            log.debug(e.getMessage())
        }

        exitValue
    }

    static int occurrenceCount(query) {
        int count = 0
        String q
        try {
            if (query.q instanceof List) {
                q = ((List) query.q)[0]
                if (query.q.size() > 1) query.q.subList(1, query.q.size()).each { q += "&fq=$it"}
            } else {
                q = query.q
                if (query.fq) query.fq.each {
                    if (it instanceof String) q += "&fq=$it"
                }
            }
            def json = JSON.parse(getUrl("${query.bs}/occurrences/search?q=${q}&facet=off&pageSize=0"))
            if (json != null) count = json.totalRecords
        } catch (Exception e) {
            log.debug(e.getMessage())

            //retry with a qid
            if (q != null && !q.contains("qid:")) {
                count = occurrenceCount({ q:  "qid:" + makeQid(query) ;bs: query.bs; ws: query.ws})
            }
        }
        return count
    }

    static int speciesCount(query) {
        int count = 0
        String q
        try {
            if (query.q instanceof List) {
                q = ((List) query.q)[0]
                if (query.q.size() > 1) query.q.subList(1, query.q.size()).each { q += "&fq=$it" }
            } else {
                q = query.q
                if (query.fq) query.fq.each {
                    if (it instanceof String) q += '&fq=' + it
                }
            }
            def json = JSON.parse(getUrl("${query.bs}/occurrences/facets/download?facets=names_and_lsid&flimit=0&q=$q"))
            if (json != null) count = ((List) json.data)[0].count
        } catch (Exception e) {
            log.debug(e.getMessage())

            //retry with a qid
            if (q != null && !q.contains("qid:")) {
                count = occurrenceCount({ q:  "qid:" + makeQid(query); bs: query.bs; ws: query.ws})
            }
        }
        return count
    }

    static JSONObject getChecklistsBySpcode(String spcode, JSONArray list) {
        for (int i=0;i<list.size();i++){
            if (spcode == String.valueOf(list.get(i).get("spcode"))) {
                return (JSONObject) list.get(i)
            }
        }
        return null
    }

    static String[] getAreaChecklists(String[] records, JSONArray list) {
        String[] lines = null
        try {
            if (records != null && records.length > 0) {
                String[][] data = new String[records.length - 1][]
                // header
                for (int i = 1; i < records.length; i++) {
                    CSVReader csv = new CSVReader(new StringReader(records[i]))
                    data[i - 1] = csv.readNext()
                    csv.close()
                }
                Arrays.sort(data, new Comparator<String[]>() {
                    @Override
                    int compare(String[] o1, String[] o2) {
                        // compare WMS urls
                        String s1 = getChecklistsBySpcode(o1[0], list).get("wmsurl")
                        String s2 = getChecklistsBySpcode(o2[0], list).get("wmsurl")
                        if (s1 == null && s2 == null) {
                            return 0
                        } else if (s1 != null && s2 != null) {
                            return s1 <=> s2
                        } else if (s1 == null) {
                            return -1
                        } else {
                            return 1
                        }
                    }
                })

                lines = new String[records.length]
                lines[0] = lines[0] = "SPCODE,SCIENTIFIC_NAME,AUTHORITY_FULL,COMMON_NAME,FAMILY,GENUS_NAME,SPECIFIC_NAME,MIN_DEPTH,MAX_DEPTH,METADATA_URL,LSID,AREA_NAME,AREA_SQ_KM,SPECIES_COUNT"
                int len = 1
                int thisCount = 0
                for (int i = 0; i < data.length; i++) {
                    thisCount++
                    String s1 = getChecklistsBySpcode(data[i][0], list).get("wmsurl")
                    String s2 = i + 1 < data.length ? getChecklistsBySpcode(data[i + 1][0], list).get("wmsurl") : null
                    if (i == data.length - 1 || (s1 == null && s2 != null) || (s1 != null && s2 == null) ||
                            (s1 != null && s2 != null && s1 != s2)) {
                        StringBuilder sb = new StringBuilder()
                        for (int j = 0; j < data[i].length; j++) {
                            if (j > 0) {
                                sb.append(",")
                            }
                            if (j == 0 || (j >= 9 && j != 10)) {
                                sb.append(wrap(data[i][j]))
                            }
                        }
                        sb.append(",").append(thisCount)
                        lines[len] = sb.toString()
                        len++
                        thisCount = 0
                    }
                }
                lines = Arrays.copyOf(lines, len)
            }
        } catch (Exception e) {
            log.error("error building species checklist", e)
            lines = null
        }
        return lines
    }
}
