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
import groovy.util.logging.Slf4j
import org.apache.commons.httpclient.methods.FileRequestEntity
import org.apache.commons.httpclient.methods.RequestEntity

import groovy.transform.CompileStatic


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
        File input = new File(resourcepath)

        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = new FileRequestEntity(input, "application/zip")

        // Execute the request
        return processResponse(Util.urlResponse("PUT", url, null, null, entity,
                true, username, password))
    }

    static String loadSld(String url, String extra, String username, String password, String resourcepath) {
        File input = new File(resourcepath)

        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = new FileRequestEntity(input, "application/vnd.ogc.sld+xml")

        // Execute the request
        return processResponse(Util.urlResponse("PUT", url, null, null, entity,
                true, username, password))
    }

    private static String processResponse(Map<String, Object> response) {
        String output
        if (response != null) {
            int statuscode = ((Integer) response.get("statusCode")).intValue()
            output = response.get("statusCode") as String + ": " + response.get("text") as String
        } else {
            output = "0: failed"
        }
        return output
    }

    static String loadCreateStyle(String url, String extra, String username, String password, String name) {
        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = null
        try {
            File file = File.createTempFile("sld", "xml")
            file.write("<style><name>" + name + "</name><filename>" + name + ".sld</filename></style>")
            entity = new FileRequestEntity(file, "text/xml")
        } catch (Exception e) {
            log.error(name, e)
        }

        // Execute the request
        return processResponse(Util.urlResponse("POST", url, null, null, entity,
                true, username, password))
    }

    static String assignSld(String url, String extra, String username, String password, String data) {
        RequestEntity entity = null
        try {
            // Request content will be retrieved directly
            // from the input stream
            File file = File.createTempFile("sld", "xml")
            file.write(data)
            entity = new FileRequestEntity(file, "text/xml")
        } catch (Exception e) {
            log.error(data, e)
        }

        // When adding a style to a layer use POST. When assigning a default style use PUT.
        String method = url.endsWith("/styles") ? "POST" : "PUT"

        // Execute the request
        return processResponse(Util.urlResponse(method, url, null, null, entity,
                true, username, password))
    }

    static String sld(String geoserverUrl, String geoserverUsername, String geoserverPassword, String layerName, String styleName, String pathToSldFile) {
        String extra = ""

        // Create sld
        loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, styleName)

        // Upload sld
        loadSld(geoserverUrl + "/rest/styles/" + styleName,
                extra, geoserverUsername, geoserverPassword, pathToSldFile)

        // Apply style
        String data = "<layer><enabled>true</enabled><defaultStyle><name>" + styleName +
                "</name></defaultStyle></layer>"

        String resp = assignSld(geoserverUrl + "/rest/layers/ALA:" + layerName, extra,
                geoserverUsername, geoserverPassword, data)

        addGwcStyle(geoserverUrl, layerName, styleName, geoserverUsername, geoserverPassword)


        return resp
    }

    static String addGwcStyle(String geoserverUrl, String layerName, String styleName, String username, String password) {
        String url = geoserverUrl + "/gwc/rest/layers/ALA:" + layerName + ".xml"
        Map<String, Object> response = Util.urlResponse("GET", url, null,
                null, null, true, username, password)

        //add Style to layer GWC styles
        String conf = (String) response.get("text")
        if (conf != null && !conf.contains("<string>" + styleName + "</string>")) {
            conf = conf.replace("</values>", "<string>" + styleName + "</string></values>")

            RequestEntity entity = null
            try {
                // Request content will be retrieved directly
                // from the input stream
                File file = File.createTempFile("tmp", "xml")
                file.write(conf)
                entity = new FileRequestEntity(file, "text/xml")

                return processResponse(Util.urlResponse("POST", url, null, null, entity,
                        true, username, password))
            } catch (Exception e) {
                log.error(conf, e)
            }
        }

        return ""
    }
}
