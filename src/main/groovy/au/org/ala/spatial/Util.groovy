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
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.log4j.Logger
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Util {
    static final Logger log = Logger.getLogger(Util.toString())

    static String getUrl(String url) {
        urlResponse("GET", url)?.text
    }

    static String postUrl(String url, NameValuePair[] nameValues = null, Map<String, String> headers = null,RequestEntity entity = null) {
        urlResponse("POST", url, nameValues, headers, entity)?.text
    }

    static PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager()
    static {
        try {
            pool.setMaxPerRoute(100)
            pool.setDefaultMaxPerRoute(50)
        } catch (e) {
            // this fails when running tests
        }
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

    static MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
    {
        mgr.setMaxConnectionsPerHost(50)
        mgr.setMaxTotalConnections(100)
    }

    /**
     *
     * @param type
     * @param url
     * @param nameValues passed as queryString in GET , but pass via BODY in POST
     * @param headers
     * @param entity  usually only used for binary data
     * @param doAuthentication
     * @param username
     * @param password
     * @return
     */
    static Map<String, Object> urlResponse(String type, String url, NameValuePair[] nameValues = null,
                           Map<String, String> headers = null, RequestEntity entity = null,
                           Boolean doAuthentication = null, String username = null, String password = null) {
        HttpClient client
        try {
            client = new HttpClient(new HttpClientParams(), mgr)

            HttpClientParams httpParams = client.getParams()
            httpParams.setSoTimeout(300000)
            httpParams.setConnectionManagerTimeout(300000)

            if (username != null && password != null) {
                client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
            }

            //nvList will be added into queryString in GET
            //but in body when POST
            List<BasicNameValuePair> nvList = new ArrayList();
            if (nameValues) {
                nameValues.each {
                    nvList.add(new BasicNameValuePair(it.getName(), it.getValue()));
                }
            }

            //Parse target url, decouple params in queryString and base url
            List<BasicNameValuePair> queryParams = new ArrayList();
            def targetUriBuilder = UriComponentsBuilder.fromUriString(url).build()
            MultiValueMap<String, String> targetParams = targetUriBuilder.getQueryParams()
            //remove requestQuery from url
            String targetUrl = new java.net.URI(targetUriBuilder.getScheme() ,targetUriBuilder.getUserInfo(), targetUriBuilder.getHost(), targetUriBuilder.getPort(),targetUriBuilder.getPath(),null, null).toString()
            Iterator<String> it = targetParams.keySet().iterator()
            while(it.hasNext()){
                String key = (String)it.next()
                //list always
                //Support: fq=a&fq=b etc
                def value = targetParams.get(key)

                value.each { i ->
                    String item = String.valueOf(i)
                    if (item) {
                        queryParams.add(new BasicNameValuePair(key, URLDecoder.decode(item, "UTF-8")));
                    }
                }
            }

            HttpMethodBase call = null

            try {

                if (type == HttpGet.METHOD_NAME) {
                    HttpGet httpGet = new HttpGet(targetUrl);
                    queryParams.addAll(nvList) //Combine name: value
                    java.net.URI uri = new URIBuilder(httpGet.getURI())
                            .setParameters(queryParams)
                            .build();
                    call = new GetMethod(uri.toString())
                }else if (type == "DELETE") {
                    HttpGet httpGet = new HttpGet(targetUrl);
                    queryParams.addAll(nvList) //Combine name: value
                    java.net.URI uri = new URIBuilder(httpGet.getURI())
                            .setParameters(queryParams)
                            .build();
                    call = new DeleteMethod(uri.toString())
                } else {
                    HttpPut httpGet = new HttpPut(targetUrl);
                    java.net.URI uri = new URIBuilder(httpGet.getURI())
                            .setParameters(queryParams)
                            .build();
                    if (type == HttpPut.METHOD_NAME) {
                        call = new PutMethod(uri.toString())
                    } else if (type == HttpPost.METHOD_NAME) {
                        call = new PostMethod(uri.toString())
                        if (nameValues) {
                            ((PostMethod)call).addParameters(nameValues)
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

                BufferedInputStream bis = new BufferedInputStream(call.getResponseBodyAsStream())
                return [statusCode: call.statusCode, text:  IOUtils.toString(bis), headers: call.responseHeaders]
            } catch (Exception e) {
                log.error url, e
            } finally {
                if (call) {
                    call.releaseConnection()
                }
            }
        } catch (Exception e) {
            log.error url, e
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
                String intersectArea = jo.containsKey('intersectArea') ? String.valueOf(Math.round(jo.get('intersectArea') / 1000000)) : ""

                lines[i + 1] = spcode + "," + wrap(scientific) + "," + wrap(auth) + "," + wrap(common) + "," +
                    wrap(family) + "," + wrap(genus) + "," + wrap(name) + "," + min + "," + max +
                    "," + wrap(md) + "," + wrap(lsid) + "," + wrap(areaName) + "," + wrap(areaKm) +
                        "," + wrap(dataResourceUid) + "," + wrap(intersectArea)
            }

            return lines
        }
    }

    static String[] getDistributionsOrChecklistsRollup(JSONArray ja) {
        if (ja == null || ja.isEmpty()) {
            return new String[0]
        } else {
            Map<String, List<String>> likely = new HashMap()
            Map<String, List<String>> maybe = new HashMap()
            Set<String> keys = new HashSet()

            for (int i = 0; i < ja.size(); i++) {
                JSONObject jo = (JSONObject) ja.get(i)
                String scientific = jo.containsKey('scientific') ? jo.get('scientific').toString() : ""
                String common = jo.containsKey('common_nam') ? jo.get('common_nam').toString() : ""
                String lsid = jo.containsKey('lsid') ? jo.get('lsid').toString() : ""
                String family = jo.containsKey('family') ? jo.get('family').toString() : ""

                String areaName = jo.containsKey('area_name') ? jo.get('area_name').toString() : ""
                String intersectArea = jo.containsKey('intersectArea') ? String.valueOf(Math.round(jo.get('intersectArea') / 1000000)) : ""

                String key = wrap(family) + "," + wrap(scientific) + "," + wrap(common) + "," + wrap(lsid)

                if (areaName.contains("likely")) likely.put(key, intersectArea)
                if (areaName.contains("maybe")) maybe.put(key, intersectArea)
                keys.add(key)
            }

            String[] lines = new String[keys.size() + 1]
            lines[0] = "FAMILY,SCIENTIFIC_NAME,COMMON_NAME,LSID,LIKELY_AREA,MAYBE_AREA"
            int i = 1
            for (String key : keys) {
                lines[i] = key + "," + wrap(likely.get(key)) + "," + wrap(maybe.get(key))
                i++
            }

            return lines
        }
    }

    static JSONArray getDistributionsOrChecklistsData(String type, String wkt, String lsids, String geomIdx, String layersUrl, List<String> familyLsids, dataResourceId) throws Exception {
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
        if (dataResourceId != null) {
            params.add(new NameValuePair('dataResourceUid', dataResourceId))
        }
        if (familyLsids != null) {
            for (String f : familyLsids) {
                params.add(new NameValuePair('familyLsid', f))
            }
        }

        String response = postUrl(layersUrl + sbProcessUrl.toString(), (NameValuePair[]) params.toArray(new NameValuePair[0]),
                [Accept: "application/json, text/javascript, */*"])

        if (response) {
            try {
                JSONParser jp = new JSONParser()
                JSONArray ja = (JSONArray) jp.parse(response)

                for (Object o : ja) {
                    JSONObject jo = (JSONObject) o;
                    if (familyLsids != null && !familyLsids.contains(jo.getOrDefault("family_lsid", null))) {
                        jo.remove(o);
                    }
                }

                return ja
            } catch (Exception e) {
                log.error(layersUrl + sbProcessUrl.toString() + " failed", e)
            }
        }

        return null
    }

    static String wrap(String s) {
        if (s == null) {
            return ""
        }
        return "\"" + s.replace("\"", "\"\"").replace("\\", "\\\\") + "\""
    }

    static int runCmd(String[] cmd, Long timeout) {
        return runCmd(cmd, false, null, timeout)
    }

    static int runCmd(String[] cmd, Boolean logToTask, Task task, Long timeout) {
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

            // add cmd object to task so it can be cancelled
            if (task) {
                task.proc = proc;
                task.errorGobbler = errorGobbler
                task.outputGobbler = outputGobbler
            }

            proc.waitForOrKill(timeout)

            errorGobbler.interrupt()
            outputGobbler.interrupt()

        } catch (Exception e) {
            log.error(e.getMessage(), e)
            exitValue = 1
        } finally {
            // remove cmd object from task
            if (task) {
                task.proc = null
                task.errorGobbler = null
                task.outputGobbler = null
            }

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

    static void readReplaceAfter(String fname, String start, String oldPattern, String replPattern) {
        String line
        StringBuffer sb = new StringBuffer()
        try {
            FileInputStream fis = new FileInputStream(fname)
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis))
            int afterPos = -1
            while ((line = reader.readLine()) != null) {
                if (afterPos < 0 && (afterPos = line.indexOf(start)) >= 0) {
                    line = line.substring(0, afterPos + start.length()) + line.substring(afterPos + start.length()).replaceAll(oldPattern, replPattern)
                } else if (afterPos > 0) {
                    line = line.replaceAll(oldPattern, replPattern)
                }
                sb.append(line + "\n")
            }
            reader.close()
            BufferedWriter out = new BufferedWriter(new FileWriter(fname))
            out.write(sb.toString())
            out.close()
        } catch (Throwable e) {
            e.printStackTrace(System.out)
        }
    }

    static void readReplaceBetween(String fname, String startOldText, String endOldText, String replText) {
        String line
        StringBuffer sb = new StringBuffer()
        try {
            FileInputStream fis = new FileInputStream(fname)
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis))
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n")
            }
            int start, end
            start = sb.indexOf(startOldText)
            if (start >= 0) {
                end = sb.indexOf(endOldText, start + 1)
                sb.replace(start, end + endOldText.length(), replText)
            }
            reader.close()
            BufferedWriter out = new BufferedWriter(new FileWriter(fname))
            out.write(sb.toString())
            out.close()
        } catch (Throwable e) {
            System.err.println("*** exception ***")
            e.printStackTrace(System.out)
        }
    }
}
