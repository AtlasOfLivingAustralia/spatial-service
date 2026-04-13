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

import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.dto.TaskWrapper
import com.opencsv.CSVReader
import grails.converters.JSON
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.http.HttpEntity
import org.apache.http.NameValuePair
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@CompileStatic
@Slf4j
class Util {

    static String getUrl(String url) {
        urlResponse("GET", url)?.text
    }

    static String postUrl(String url, List<NameValuePair> nameValues = null, Map<String, String> headers = null, String entity = null) {
        urlResponse("POST", url, nameValues, headers, entity)?.text
    }

    static PoolingHttpClientConnectionManager pool
    static {
        try {
            pool = new PoolingHttpClientConnectionManager()
            pool.setDefaultMaxPerRoute(50)
        } catch (e) {
            // this fails when running tests
            e.printStackTrace()
        }
    }

    static Map<String, Object> getStream(String url, String jwt) {
        CloseableHttpClient client = null
        CloseableHttpResponse response = null
        try {
            RequestConfig config = RequestConfig.custom()
                    .setSocketTimeout(60000)
                    .setConnectTimeout(10000)
                    .build()
            client = HttpClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .setConnectionManager(pool)
                    .setConnectionManagerShared(true)
                    .build()

            HttpGet request = new HttpGet(url)
            if (jwt) {
                request.addHeader("Authorization", "Bearer " + jwt)
            }
            response = client.execute(request)
        } catch (Exception e) {
            log.error url, e
        }

        return [client: client, response: response] as Map<String, Object>
    }

    /**
     *
     * @param type
     * @param url
     * @param nameValues passed as queryString in GET, but passed via BODY in POST
     * @param headers
     * @param entity string body (e.g. JSON), usually for POST/PUT
     * @param username
     * @param password
     * @return
     */
    static Map<String, Object> urlResponse(String type, String url, List<NameValuePair> nameValues = null,
                                           Map<String, String> headers = null, String entity = null,
                                           Boolean doAuthentication = null, String username = null, String password = null) {
        RequestConfig config = RequestConfig.custom()
                .setSocketTimeout(300000)
                .setConnectTimeout(300000)
                .build()

        HttpClientBuilder builder = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setConnectionManager(pool)
                .setConnectionManagerShared(true)

        if (username != null && password != null) {
            CredentialsProvider credProvider = new BasicCredentialsProvider()
            credProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
            builder.setDefaultCredentialsProvider(credProvider)
        }

        CloseableHttpClient client = builder.build()

        // Parse target url, decouple params in queryString and base url
        List<NameValuePair> queryParams = new ArrayList<NameValuePair>()
        def targetUriBuilder = UriComponentsBuilder.fromUriString(url).build()
        MultiValueMap<String, String> targetParams = targetUriBuilder.getQueryParams()
        String targetUrl = new java.net.URI(targetUriBuilder.getScheme(), targetUriBuilder.getUserInfo(),
                targetUriBuilder.getHost(), targetUriBuilder.getPort(), targetUriBuilder.getPath(), null, null).toString()

        for (String key : targetParams.keySet()) {
            List<String> values = targetParams.get(key)
            for (String item : values) {
                if (item) {
                    queryParams.add(new BasicNameValuePair(key, UriUtils.decode(item, "UTF-8")))
                }
            }
        }

        // nvList will be added into queryString for GET, or body for POST
        if (nameValues) {
            queryParams.addAll(nameValues)
        }

        try {
            HttpRequestBase call

            if (type == HttpGet.METHOD_NAME) {
                java.net.URI uri = new URIBuilder(targetUrl).setParameters(queryParams).build()
                call = new HttpGet(uri)
            } else if (type == "DELETE") {
                java.net.URI uri = new URIBuilder(targetUrl).setParameters(queryParams).build()
                call = new HttpDelete(uri)
            } else if (type == HttpPut.METHOD_NAME) {
                java.net.URI uri = new URIBuilder(targetUrl).setParameters(queryParams).build()
                HttpPut put = new HttpPut(uri)
                if (entity) {
                    put.setEntity(new StringEntity(entity, "UTF-8"))
                }
                call = put
            } else {
                // POST
                java.net.URI uri = new URIBuilder(targetUrl).build()
                HttpPost post = new HttpPost(uri)
                if (entity) {
                    post.setEntity(new StringEntity(entity, "UTF-8"))
                } else if (nameValues) {
                    post.setEntity(new org.apache.http.client.entity.UrlEncodedFormEntity(nameValues, "UTF-8"))
                }
                call = post
            }

            if (headers) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    call.addHeader(h.key, h.value)
                }
            }

            CloseableHttpResponse response = client.execute(call)
            try {
                HttpEntity responseEntity = response.getEntity()
                String text = responseEntity ? responseEntity.getContent().text : ""
                return [statusCode: response.getStatusLine().getStatusCode(), text: text,
                        headers: response.getAllHeaders()] as Map<String, Object>
            } finally {
                response.close()
            }
        } catch (Exception e) {
            log.error url, e
        } finally {
            client.close()
        }

        return null
    }

    static String makeQid(SpeciesInput query, def webService) {
        List<NameValuePair> params = new ArrayList<NameValuePair>()

        params.add(new BasicNameValuePair('q', query.q[0].toString()))
        if (query.q.size() > 1) {
            for (String fq : query.q.subList(1, query.q.size())) {
                params.add(new BasicNameValuePair('fq', fq.toString()))
            }
        }

        if (query.wkt) {
            params.add(new BasicNameValuePair('wkt', query.wkt.toString()))
        }

        // this causes the qid to fail when there are no occurrences in the area
        //params.add(new BasicNameValuePair('bbox', 'true'))

        // TODO: JWT and /ws/qid
        def qid = postUrl("${query.bs}/qid".toString(), params)

        return qid
    }

    static SpeciesInput getQid(String bs, String qid) {
        JSONObject json = (JSONObject) JSON.parse(getUrl("$bs/qid/$qid"))
        SpeciesInput s = new SpeciesInput()
        s.q = [json.get('q') as String]
        if (json.get('fq')) {
            JSONArray fq = (JSONArray) json.get('fq')
            for (int i = 0; i < fq.size(); i++) {
                s.q.add(fq.get(i) as String)
            }
        }
        s.wkt = json.get('wkt') as String

        return s
    }

    static String[] getDistributionsOrChecklists(List<Distributions> ja) {
        if (ja == null || ja.isEmpty()) {
            return new String[0]
        } else {
            String[] lines = new String[ja.size() + 1]
            lines[0] = "SPCODE,SCIENTIFIC_NAME,AUTHORITY_FULL,COMMON_NAME,FAMILY,GENUS_NAME,SPECIFIC_NAME,MIN_DEPTH,MAX_DEPTH,METADATA_URL,LSID,AREA_NAME,AREA_SQ_KM"
            ja.eachWithIndex {Distributions it, int idx ->
                String intersectArea = String.format(Locale.US, "%.2f", Math.round(it.intersectArea ?: 0) as Double / 1000000.0)

                lines[idx + 1] = it.spcode + "," + wrap(it.scientific) + "," + wrap(it.authority_) + "," + wrap(it.common_nam) + "," +
                    wrap(it.family) + "," + wrap(it.genus_name) + "," + wrap(it.specific_n) + "," + (it.min_depth ?: "") + "," + (it.max_depth ?: "") +
                        "," + wrap(it.metadata_u) + "," + wrap(it.lsid) + "," + wrap(it.area_name) + "," + String.format(Locale.US, "%.2f", (it.area_km ?: 0) as Double) +
                        "," + wrap(it.data_resource_uid) + "," + intersectArea
            }

            return lines
        }
    }

    static String[] getDistributionsOrChecklistsRollup(List<Distributions> ja) {
        if (ja == null || ja.isEmpty()) {
            return new String[0]
        } else {
            Map<String, String> likely = new HashMap<String, String>()
            Map<String, String> maybe = new HashMap<String, String>()
            Set<String> keys = new HashSet()

            ja.each {Distributions it ->
                String key = wrap(it.family) + "," + wrap(it.scientific) + "," + wrap(it.common_nam) + "," + wrap(it.lsid)

                String areaName = it.area_name
                String intersectArea = String.format(Locale.US, "%.2f", Math.round(it.intersectArea ?: 0) as Double / 1000000)

                if (areaName.toLowerCase().contains("likely")) likely.put(key, intersectArea)
                if (areaName.toLowerCase().contains("maybe")) maybe.put(key, intersectArea)

                keys.add(key)
            }

            String[] lines = new String[keys.size() + 1]
            lines[0] = "FAMILY,SCIENTIFIC_NAME,COMMON_NAME,LSID,LIKELY_AREA,MAYBE_AREA"
            int i = 1
            for (String key : keys) {
                lines[i] = key + "," + wrap(likely.get(key) as String) + "," + wrap(maybe.get(key) as String)
                i++
            }

            return lines
        }
    }

    static String wrap(String s) {
        if (s == null) {
            return ""
        }
        return "\"" + s.replace("\"", "\"\"").replace("\\", "\\\\") + "\""
    }

    static int runCmd(String[] cmd, Long timeout) {
        return runCmd(cmd, false, null, timeout, null)
    }

    static int runCmd(String[] cmd, Boolean logToTask, TaskWrapper task, Long timeout, StringBuffer stringBuffer) {
        int exitValue = 1

        ProcessBuilder builder = new ProcessBuilder(cmd)
        builder.environment().putAll(System.getenv())
        builder.redirectErrorStream(false)

        Process proc
        try {
            proc = builder.start()

            // any error message?
            StreamGobbler errorGobbler = new StreamGobbler(proc.getErrorStream(), "", logToTask ? task : null, null)

            // any output?
            StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), "", logToTask ? task : null, stringBuffer)

            // kick them off
            errorGobbler.start()
            outputGobbler.start()

            // add cmd object to task so it can be cancelled
            if (task) {
                task.proc = proc
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
                } catch (ignored) {
                }
                try {
                    proc.getOutputStream().close()
                } catch (ignored) {
                }
                try {
                    proc.getErrorStream().close()
                } catch (ignored) {

                }
            }
        }

        exitValue
    }

    static Distributions getChecklistsBySpcode(String spcode, List<Distributions> list) {
        for (int i = 0; i < list.size(); i++) {
            if (spcode == String.valueOf(list[i].spcode)) {
                return list.get(i)
            }
        }
        return null
    }

    static String[] getAreaChecklists(String[] records, List<Distributions> list) {
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

    static void replaceTextInFile(String path, Map<String, String> map) {
        String s = new File(path).text
        map.each { String k, String v ->
            s = s.replaceAll(k, v)
        }
        new File(path).write(s)
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
