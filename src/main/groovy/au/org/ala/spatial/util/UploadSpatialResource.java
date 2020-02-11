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
package au.org.ala.spatial.util;

import au.org.ala.spatial.Util;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Map;

/**
 * UploadSpatialResource helps with loading any dynamically generated spatial
 * data into geoserver.
 * <p/>
 * Main code from:
 * http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/src/examples/BasicAuthenticationExample.java?view=log
 *
 * @author ajay
 */
public class UploadSpatialResource {

    private static final Logger logger = Logger.getLogger(UploadSpatialResource.class);

    /**
     * HTTP request type PUT
     */
    public static final int PUT = 0;
    /**
     * HTTP request type POST
     */
    public static final int POST = 1;

    /**
     * Contructor for UploadSpatialResource
     */
    public UploadSpatialResource() {
        super();
    }

    public static String loadResource(String url, String extra, String username, String password, String resourcepath) {
        File input = new File(resourcepath);

        // Request content will be retrieved directly 
        // from the input stream 
        RequestEntity entity = new FileRequestEntity(input, "application/zip");

        // Execute the request 
        return processResponse(Util.urlResponse("PUT", url, null, null, entity,
                true, username, password));
    }

    public static String loadSld(String url, String extra, String username, String password, String resourcepath) {
        File input = new File(resourcepath);

        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = new FileRequestEntity(input, "application/vnd.ogc.sld+xml");

        // Execute the request
        return processResponse(Util.urlResponse("PUT", url, null, null, entity,
                true, username, password));
    }

    private static String processResponse(Map<String, Object> response) {
        String output;
        if (response != null) {
            int statuscode = ((Integer)response.get("statusCode")).intValue();
            if (statuscode == 401)
                output = "401: Unauthorised ";
            output = response.get("statusCode") + ": " + response.get("text");
        } else {
            output = "0: failed";
        }
        return output;
    }

    public static String loadCreateStyle(String url, String extra, String username, String password, String name) {
        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = null;
        try {
            File file = File.createTempFile("sld", "xml");
            FileUtils.writeStringToFile(file, "<style><name>" + name + "</name><filename>" + name + ".sld</filename></style>", "UTF-8");
            entity = new FileRequestEntity(file, "text/xml");
        } catch (Exception e) {
            logger.error(name, e);
        }

        // Execute the request
        return processResponse(Util.urlResponse("POST", url, null, null, entity,
                true, username, password));
    }

    public static String assignSld(String url, String extra, String username, String password, String data) {
        RequestEntity entity = null;
        try {
            // Request content will be retrieved directly
            // from the input stream
            File file = File.createTempFile("sld", "xml");
            FileUtils.writeStringToFile(file, data, "UTF-8");
            entity = new FileRequestEntity(file, "text/xml");
        } catch (Exception e) {
            logger.error(data, e);
        }

        // When adding a style to a layer use POST. When assigning a default style use PUT.
        String method = url.endsWith("/styles") ? "POST" : "PUT";

        // Execute the request
        return processResponse(Util.urlResponse(method, url, null, null, entity,
                true, username, password));
    }

    public static String sld(String geoserverUrl, String geoserverUsername, String geoserverPassword, String name, String pathToSldFile) {
        String extra = "";

        // Create sld
        UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, name);

        // Upload sld
        UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + name,
                extra, geoserverUsername, geoserverPassword, pathToSldFile);

        // Apply style
        String data = "<layer><enabled>true</enabled><defaultStyle><name>" + name +
                "</name></defaultStyle></layer>";

        return UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + name, extra,
                geoserverUsername, geoserverPassword, data);

    }
}
