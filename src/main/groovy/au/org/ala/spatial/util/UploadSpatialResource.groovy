/**
 * ************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia All Rights Reserved.
 * <p/>
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * <p/>
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * *************************************************************************
 */
package au.org.ala.spatial.util

import au.org.ala.spatial.Util
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder

@Slf4j
@CompileStatic
class UploadSpatialResource {
    /**
     * HTTP request type PUT
     */
    public static final int PUT = 0
    /**
     * HTTP request type POST
     */
    public static final int POST = 1

    /**
     * Contructor for UploadSpatialResource
     */
    UploadSpatialResource() {
        super()
    }

    static String loadResource(String url, String extra, String username, String password, String resourcepath) {
        return putFile(url, new File(resourcepath), "application/zip", username, password)
    }

    static String loadSld(String url, String extra, String username, String password, String resourcepath) {
        return putFile(url, new File(resourcepath), "application/vnd.ogc.sld+xml", username, password)
    }

    private static String putFile(String url, File file, String contentType, String username, String password) {
        CloseableHttpClient client = buildClient(username, password)
        try {
            HttpPut put = new HttpPut(url)
            put.setEntity(new FileEntity(file, ContentType.create(contentType)))
            CloseableHttpResponse response = client.execute(put)
            try {
                return response.getStatusLine().getStatusCode() + ": " + response.getEntity()?.getContent()?.text
            } finally {
                response.close()
            }
        } catch (Exception e) {
            log.error(url, e)
            return "0: failed"
        } finally {
            client.close()
        }
    }

    private static String postFile(String url, File file, String contentType, String username, String password) {
        CloseableHttpClient client = buildClient(username, password)
        try {
            HttpPost post = new HttpPost(url)
            post.setEntity(new FileEntity(file, ContentType.create(contentType)))
            CloseableHttpResponse response = client.execute(post)
            try {
                return response.getStatusLine().getStatusCode() + ": " + response.getEntity()?.getContent()?.text
            } finally {
                response.close()
            }
        } catch (Exception e) {
            log.error(url, e)
            return "0: failed"
        } finally {
            client.close()
        }
    }

    private static CloseableHttpClient buildClient(String username, String password) {
        RequestConfig config = RequestConfig.custom()
                .setSocketTimeout(300000)
                .setConnectTimeout(300000)
                .build()
        HttpClientBuilder builder = HttpClientBuilder.create().setDefaultRequestConfig(config)
        if (username && password) {
            CredentialsProvider creds = new BasicCredentialsProvider()
            creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
            builder.setDefaultCredentialsProvider(creds)
        }
        return builder.build()
    }

    private static String processResponse(Map<String, Object> response) {
        if (response != null) {
            return response.get("statusCode") as String + ": " + response.get("text") as String
        }
        return "0: failed"
    }

    static String loadCreateStyle(String url, String extra, String username, String password, String name) {
        try {
            File file = File.createTempFile("sld", "xml")
            file.write("<style><name>" + name + "</name><filename>" + name + ".sld</filename></style>")
            return postFile(url, file, "text/xml", username, password)
        } catch (Exception e) {
            log.error(name, e)
            return "0: failed"
        }
    }

    static String assignSld(String url, String extra, String username, String password, String data) {
        try {
            File file = File.createTempFile("sld", "xml")
            file.write(data)
            String method = url.endsWith("/styles") ? "POST" : "PUT"
            if (method == "POST") {
                return postFile(url, file, "text/xml", username, password)
            } else {
                return putFile(url, file, "text/xml", username, password)
            }
        } catch (Exception e) {
            log.error(data, e)
            return "0: failed"
        }
    }

    static String sld(String geoserverUrl, String geoserverUsername, String geoserverPassword, String layerName, String styleName, String pathToSldFile) {
        String extra = ""

        loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, styleName)

        loadSld(geoserverUrl + "/rest/styles/" + styleName,
                extra, geoserverUsername, geoserverPassword, pathToSldFile)

        String data = "<layer><enabled>true</enabled><defaultStyle><name>" + styleName +
                "</name></defaultStyle></layer>"

        String resp = assignSld(geoserverUrl + "/rest/layers/ALA:" + layerName, extra,
                geoserverUsername, geoserverPassword, data)

        addGwcStyle(geoserverUrl, layerName, styleName, geoserverUsername, geoserverPassword)

        return resp
    }

    static String addGwcStyle(String geoserverUrl, String layerName, String styleName, String username, String password) {
        String url = geoserverUrl + "/gwc/rest/layers/ALA:" + layerName + ".xml"
        Map<String, Object> response = Util.urlResponse("GET", url, null, null, null, true, username, password)

        String conf = (String) response?.get("text")
        if (conf != null && !conf.contains("<string>" + styleName + "</string>")) {
            conf = conf.replace("</values>", "<string>" + styleName + "</string></values>")

            try {
                File file = File.createTempFile("tmp", "xml")
                file.write(conf)
                return postFile(url, file, "text/xml", username, password)
            } catch (Exception e) {
                log.error(conf, e)
            }
        }

        return ""
    }
}
