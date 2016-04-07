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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

import java.io.File;
import java.io.FileWriter;

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
        String output = "";

        HttpClient client = new HttpClient();
        client.setConnectionTimeout(10000);
        client.setTimeout(60000);

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        File input = new File(resourcepath);

        PutMethod put = new PutMethod(url);
        put.setDoAuthentication(true);

        //put.addRequestHeader("Content-type", "application/zip");

        // Request content will be retrieved directly 
        // from the input stream 
        RequestEntity entity = new FileRequestEntity(input, "application/zip");
        put.setRequestEntity(entity);

        // Execute the request 
        try {
            int result = client.executeMethod(put);

            output = result + ": " + put.getResponseBodyAsString();

        } catch (Exception e) {
            e.printStackTrace(System.out);
            output = "0: " + e.getMessage();
        } finally {
            // Release current connection to the connection pool once you are done 
            put.releaseConnection();
        }

        return output;


    }

    public static String loadSld(String url, String extra, String username, String password, String resourcepath) {
        System.out.println("loadSld url:" + url);
        System.out.println("path:" + resourcepath);

        String output = "";

        HttpClient client = new HttpClient();
        client.setConnectionTimeout(10000);
        client.setTimeout(60000);

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        client.getParams().setAuthenticationPreemptive(true);

        File input = new File(resourcepath);

        PutMethod put = new PutMethod(url);
        put.setDoAuthentication(true);

        // Request content will be retrieved directly
        // from the input stream
        RequestEntity entity = new FileRequestEntity(input, "application/vnd.ogc.sld+xml");
        put.setRequestEntity(entity);

        // Execute the request
        try {
            int result = client.executeMethod(put);

            output = result + ": " + put.getResponseBodyAsString();

        } catch (Exception e) {
            e.printStackTrace(System.out);
            output = "0: " + e.getMessage();
        } finally {
            // Release current connection to the connection pool once you are done
            put.releaseConnection();
        }

        return output;
    }

    public static String loadCreateStyle(String url, String extra, String username, String password, String name) {
        System.out.println("loadCreateStyle url:" + url);
        System.out.println("name:" + name);

        String output = "";

        HttpClient client = new HttpClient();
        client.setConnectionTimeout(10000);
        client.setTimeout(60000);

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        PostMethod post = new PostMethod(url);
        post.setDoAuthentication(true);

        // Execute the request
        try {
            // Request content will be retrieved directly
            // from the input stream
            File file = File.createTempFile("sld", "xml");
            FileWriter fw = new FileWriter(file);
            fw.append("<style><name>" + name + "</name><filename>" + name + ".sld</filename></style>");
            fw.close();
            RequestEntity entity = new FileRequestEntity(file, "text/xml");
            post.setRequestEntity(entity);

            int result = client.executeMethod(post);

            output = result + ": " + post.getResponseBodyAsString();

        } catch (Exception e) {
            e.printStackTrace(System.out);
            output = "0: " + e.getMessage();
        } finally {
            // Release current connection to the connection pool once you are done
            post.releaseConnection();
        }

        return output;
    }

    public static String assignSld(String url, String extra, String username, String password, String data) {
        System.out.println("assignSld url:" + url);
        System.out.println("data:" + data);

        String output = "";

        HttpClient client = new HttpClient();
        client.setConnectionTimeout(10000);
        client.setTimeout(60000);

        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        PutMethod put = new PutMethod(url);
        put.setDoAuthentication(true);

        // Execute the request
        try {
            // Request content will be retrieved directly
            // from the input stream
            File file = File.createTempFile("sld", "xml");
            System.out.println("file:" + file.getPath());
            FileWriter fw = new FileWriter(file);
            fw.append(data);
            fw.close();
            RequestEntity entity = new FileRequestEntity(file, "text/xml");
            put.setRequestEntity(entity);

            int result = client.executeMethod(put);

            output = result + ": " + put.getResponseBodyAsString();

        } catch (Exception e) {
            output = "0: " + e.getMessage();
            e.printStackTrace(System.out);
        } finally {
            // Release current connection to the connection pool once you are done
            put.releaseConnection();
        }

        return output;
    }

    /**
     * sends a PUT or POST call to a URL using authentication and including a
     * file upload
     *
     * @param type         one of UploadSpatialResource.PUT for a PUT call or
     *                     UploadSpatialResource.POST for a POST call
     * @param url          URL for PUT/POST call
     * @param username     account username for authentication
     * @param password     account password for authentication
     * @param resourcepath local path to file to upload, null for no file to
     *                     upload
     * @param contenttype  file MIME content type
     * @return server response status code as String or empty String if
     * unsuccessful
     */
    public static String httpCall(int type, String url, String username, String password, String resourcepath, String contenttype) {
        String output = "";

        HttpClient client = new HttpClient();
        client.setConnectionTimeout(10000);
        client.setTimeout(60000);
        client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));


        RequestEntity entity = null;
        if (resourcepath != null) {
            File input = new File(resourcepath);
            entity = new FileRequestEntity(input, contenttype);
        }

        HttpMethod call = null;
        ;
        if (type == PUT) {
            PutMethod put = new PutMethod(url);
            put.setDoAuthentication(true);
            if (entity != null) {
                put.setRequestEntity(entity);
            }
            call = put;
        } else if (type == POST) {
            PostMethod post = new PostMethod(url);
            if (entity != null) {
                post.setRequestEntity(entity);
            }
            call = post;
        } else {
            //SpatialLogger.log("UploadSpatialResource", "invalid type: " + type);
            return output;
        }

        // Execute the request 
        try {
            int result = client.executeMethod(call);

            output = result + ": " + call.getResponseBodyAsString();
        } catch (Exception e) {
            //SpatialLogger.log("UploadSpatialResource", "failed upload to: " + url);
            output = "0: " + e.getMessage();
            e.printStackTrace(System.out);
        } finally {
            // Release current connection to the connection pool once you are done 
            call.releaseConnection();
        }

        return output;
    }
}
