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
import org.apache.commons.httpclient.*
import org.apache.commons.httpclient.auth.AuthScope
import org.apache.commons.httpclient.methods.*
import org.apache.commons.httpclient.params.HttpClientParams
import org.apache.commons.io.FileUtils
import org.apache.log4j.Logger
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Util {

    static final Logger log = Logger.getLogger(Util.toString())

    static String getUrl(String url) {
        urlResponse("GET", url)?.text
    }

    static String postUrl(String url, NameValuePair[] nameValues = null, Map<String, String> headers = null) {
        urlResponse("POST", url, nameValues, headers)?.text
    }

    static Map<String, Object> getStream(url) {
        HttpClient client = null
        HttpMethodBase call = null
        try {
            client = new HttpClient()

            HttpClientParams httpParams = client.getParams()
            httpParams.setSoTimeout(60000)
            httpParams.setConnectionManagerTimeout(10000)

            try {
                call = new GetMethod(url)

                client.executeMethod(call)
            } catch (Exception e) {
                log.error url, e
            }
        } catch (Exception e) {
            log.error url, e
        }

        return [client: client, call: call]
    }

    static void closeStream(streamObj) {
        try {
            if (streamObj?.call) {
                streamObj.call.releaseConnection()
            }
        } catch (Exception e) {
            log.error e.getMessage(), e
        }
        try {
            if (streamObj?.client &&
                    ((SimpleHttpConnectionManager) streamObj?.client.getHttpConnectionManager()) instanceof SimpleHttpConnectionManager) {
                ((SimpleHttpConnectionManager) streamObj?.client.getHttpConnectionManager()).shutdown()
            }
        } catch (Exception e) {
            log.error e.getMessage(), e
        }
    }

    static Map<String, Object> urlResponse(String type, String url, NameValuePair[] nameValues = null,
                           Map<String, String> headers = null, RequestEntity entity = null,
                           Boolean doAuthentication = null, String username = null, String password = null) {
        HttpClient client
        try {
            client = new HttpClient()

            HttpClientParams httpParams = client.getParams()
            httpParams.setSoTimeout(60000)
            httpParams.setConnectionManagerTimeout(10000)

            if (username != null && password != null) {
                client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
            }

            HttpMethodBase call = null

            try {
                if (type == "GET") {
                    call = new GetMethod(url)
                } else if (type == "DELETE") {
                    call = new DeleteMethod(url)
                } else {
                    if (type == "PUT") {
                        call = new PutMethod(url)
                    } else if (type == "POST") {
                        call = new PostMethod(url)

                        if (nameValues) {
                            nameValues.each { nv ->
                                if (nv.value instanceof List) {
                                    nv.value.each { i ->
                                        ((PostMethod) call).addParameter(String.valueOf(nv.name), String.valueOf(i))
                                    }
                                } else {
                                    ((PostMethod) call).addParameter(String.valueOf(nv.name), String.valueOf(nv.value))
                                }

                            }
                        }
                    }
                    if (entity) {
                        ((EntityEnclosingMethod) call).setRequestEntity(entity)
                    }
                }

                if (doAuthentication != null) {
                    call.setDoAuthentication(doAuthentication)
                }

                if (headers) {
                    headers.each { k, v ->
                        call.addRequestHeader(k, v)
                    }
                }

                client.executeMethod(call)

                return [statusCode: call.statusCode, text: call.responseBodyAsString, headers: call.responseHeaders]
            } catch (Exception e) {
                log.error url, e
            } finally {
                if (call) {
                    call.releaseConnection()
                }
            }
        } catch (Exception e) {
            log.error url, e
        } finally {
            if (client && client instanceof SimpleHttpConnectionManager && client.getHttpConnectionManager()) {
                ((SimpleHttpConnectionManager) client.getHttpConnectionManager()).shutdown()
            }
        }

        return null
    }

    static makeQid(query) {
        List<NameValuePair> params = new ArrayList<>()

        if (query.q instanceof List) {
            params.add(new NameValuePair('q', ((List)query.q)[0].toString()))
            if (query.q.size() > 1) query.q.subList(1, query.q.size()).each {
                params.add(new NameValuePair('fq', it.toString()))
            }
        } else {
            params.add(new NameValuePair('q', query.q.toString()))
            if (query.fq) query.fq.each {
                if (it instanceof String) {
                    params.add(new NameValuePair('fq', it))
                }
            }
        }

        if (query.wkt) {
            params.add(new NameValuePair('wkt', query.wkt.toString()))
        }

        params.add(new NameValuePair('bbox', 'true'))

        return postUrl("${query.bs}/webportal/params", (NameValuePair[]) params.toArray(new NameValuePair[0]))
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

        List<NameValuePair> params = new ArrayList<>()

        if (wkt != null) {
            params.add(new NameValuePair('wkt', wkt))
        }
        if (lsids != null) {
            params.add(new NameValuePair('lsids', lsids))
        }
        if (geomIdx != null) {
            params.add(new NameValuePair('geom_idx', geomIdx))
        }

        String response = postUrl(layersUrl + sbProcessUrl.toString(), (NameValuePair[]) params.toArray(new NameValuePair[0]),
                [Accept: "application/json, text/javascript, */*"])

        if (response) {
            try {
                JSONParser jp = new JSONParser()
                JSONArray ja = (JSONArray) jp.parse(response)
                return ja
            } catch (Exception e) {
                log.error(layersUrl + sbProcessUrl.toString() + " failed", e)
            }
        }

        return null
    }

    static String wrap(String s) {
        return "\"" + s.replace("\"", "\"\"").replace("\\", "\\\\") + "\""
    }

    static int runCmd(String[] cmd) {
        return runCmd(cmd, false, null)
    }

    static int runCmd(String[] cmd, Boolean logToTask, Task task) {
        int exitValue = 1

        ProcessBuilder builder = new ProcessBuilder(cmd)
        builder.environment().putAll(System.getenv())
        builder.redirectErrorStream(false)

        Process proc
        try {
            proc = builder.start()

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
            log.error(e.getMessage(), e)
        } finally {
            if (proc) {
                try {
                    proc.getInputStream().close()
                } catch (err) {
                }
                try {
                    proc.getOutputStream().close()
                } catch (err) {
                }
                try {
                    proc.getErrorStream().close()
                } catch (err) {

                }
            }
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

    static void replaceTextInFile(String path, Map map) {
        def s = FileUtils.readFileToString(new File(path))
        map.each { String k, String v ->
            s = s.replaceAll(k, v)
        }
        FileUtils.writeStringToFile(new File(path), s)
    }

    static void zip(String zipFilename, String[] filenames, String[] archFilenames) throws IOException {
        ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFilename)))
        try {
            byte[] data = new byte[512]

            for (int i = 0; i < filenames.length; ++i) {
                InputStream fin = new BufferedInputStream(new FileInputStream(filenames[i]))
                try {
                    ZipEntry entry = new ZipEntry((new File(archFilenames[i])).getName())
                    zout.putNextEntry(entry)

                    int bc
                    while ((bc = fin.read(data, 0, 512)) != -1) {
                        zout.write(data, 0, bc)
                    }
                } finally {
                    fin.close()
                }
            }
        } finally {
            zout.flush()
            zout.close()
        }
    }
}
