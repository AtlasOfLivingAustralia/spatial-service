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
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class InitGeoserver extends SlaveProcess {
    String geoserverUrl
    String username
    String password
    String postgresqlPath
    String postgresqlUser
    String postgresqlPassword

    void start() {
        geoserverUrl = getInput('geoserverUrl') ?: spatialConfig.geoserver.url
        username = getInput('geoserverUser') ?: spatialConfig.geoserver.username
        password = getInput('geoserverPassword') ?: spatialConfig.geoserver.password

        // default url is jdbc:postgres://localhost/layersdb, get the path
        postgresqlPath = getInput('postgresqlPath') ?: spatialConfig.dataSource.url.split("/")[2]
        postgresqlUser = getInput('postgresqlUser') ?: spatialConfig.dataSource.username
        postgresqlPassword = getInput('postgresqlPassword') ?: spatialConfig.dataSource.password

        changeGeoserverPassword()

        uploadLayoutFiles()
        setupWorkspace()
        linkToPostgresql()
        createWorldLayer()

        if (spatialConfig.geoserver.spatialservice.colocated) {
            whitelistColocatedUploads();
        }
    }

    Map restCall(String description, String type, String url, String entity) {
        taskLog(description + "...")
        Map response = Util.urlResponse(type, geoserverUrl + url, null, null, entity, true, username, password)
        taskLog("statusCode: " + response.statusCode + ", " + response.text)

        return response
    }

    void changeGeoserverPassword() {
        String defaultUser = 'admin'
        String defaultPassword = 'geoserver'

        Map response

        if (defaultPassword == password) {
            log.error("Geoserver is configured with the default password")
            taskLog("ERROR: Geoserver is configured with the default password")
        } else {
            // attempt to change the password from the default to the password in the config
            taskLog("Change the default password...")
            response = Util.urlResponse("PUT", geoserverUrl + "/rest/security/self/password", null,
                    ['Content-Type': 'application/json'],
                    "{ \"newPassword\":\"${password}\" }",
                    true, defaultUser, defaultPassword)
            taskLog("statusCode: " + response.statusCode + ", " + response.text)

            // attempt to change the master password with the supplied
            restCall("Change the default password", "PUT", "/rest/security/masterpw",
                    "{ \"oldMasterPassword\":\"${defaultPassword}\", \"newMasterPassword\":\"${password}\" }")
        }
    }

    void createWorldLayer() {
        Map response

        response = restCall("Search for layer 'world'", "GET", "/rest/layers/ALA:world.xml", null)
        if (response.statusCode != 200) {
            // encode bytes as Base64 string to pass as entity — GeoServer accepts zip uploads as raw bytes
            // use UploadSpatialResource for binary file uploads
            URL resource = InitGeoserver.class.getResource("/geoserver/world.zip")
            au.org.ala.spatial.util.UploadSpatialResource.loadResource(
                    geoserverUrl + "/rest/workspaces/ALA/datastores/world/file.shp",
                    "", username, password,
                    File.createTempFile("world", ".zip").tap { it.bytes = resource.bytes }.path)
        }
    }

    void uploadLayoutFiles() {
        URL resource = InitGeoserver.class.getResource("/geoserver/scale.xml")
        restCall("Create the 'scale' layout that is used by biocache-service", "PUT",
                "/rest/resource/layout/scale.xml", resource.text)
    }

    void setupWorkspace() {
        Map response

        taskLog("Delete default workspaces and create ALA workspace")
        for (String workspace : ["nurc", "cite", "it.geosolutions.html", "sde", "sf", "tiger", "topp"]) {
            restCall("Delete workspace " + workspace, "DELETE", "/rest/workspaces/" + workspace + "?recurse=true", null)
        }
        response = restCall("Search for workspace ALA", "GET", "/rest/workspaces/ALA", null)
        if (response.statusCode != 200) {
            restCall("Creating workspace ALA", "POST", "/rest/workspaces",
                    "<workspace><name>ALA</name></workspace>")
        }
    }

    void linkToPostgresql() {
        Map response

        // create store
        String storeXml = "<dataStore><name>LayersDB</name><connectionParameters>" +
                "<host>" + postgresqlPath + "</host>" +
                "<port>5432</port>" +
                "<database>layersdb</database>" +
                "<schema>public</schema>" +
                "<user>" + postgresqlUser + "</user>" +
                "<passwd>" + postgresqlPassword + "</passwd>" +
                "<dbtype>postgis</dbtype>" +
                "</connectionParameters></dataStore>"
        response = restCall("Search for store LayersDB", "GET", "/rest/workspaces/ALA/datastores/LayersDB", null)
        if (response.statusCode != 200) {
            restCall("Creating layersDB store", "POST", "/rest/workspaces/ALA/datastores", storeXml)
        }

        // upload marker.png as binary via UploadSpatialResource
        URL markerResource = InitGeoserver.class.getResource("/geoserver/marker.png")
        File markerTmp = File.createTempFile("marker", ".png")
        markerTmp.bytes = markerResource.bytes
        au.org.ala.spatial.util.UploadSpatialResource.loadResource(
                geoserverUrl + "/rest/resource/styles/marker.png", "", username, password, markerTmp.path)

        taskLog("Creating and uploading styles")
        for (String style : ["envelope_style", "distributions_style", "alastyles", "points_style"]) {
            response = restCall("Search for style " + style, "GET", "/rest/styles/" + style + ".xml", null)
            if (response.statusCode != 200) {
                restCall("Creating style " + style, "POST", "/rest/styles",
                        "<style><name>" + style + "</name><filename>" + style + ".sld</filename></style>")
            }
            URL styleResource = InitGeoserver.class.getResource("/geoserver/" + style + ".sld")
            restCall("Upload style " + style, "PUT", "/rest/styles/" + style, styleResource.text)
        }

        // create layers
        taskLog("Creating layers and assigning styles")
        for (String layer : ["Objects", "Distributions", "Points"]) {
            URL layerResource = InitGeoserver.class.getResource("/geoserver/" + layer + ".xml")
            restCall("Creating layer " + layer, "POST",
                    "/rest/workspaces/ALA/datastores/LayersDB/featuretypes", layerResource.text)

            String styleXml
            if (layer == "Points") {
                styleXml = "<layer><defaultStyle><name>points_style</name><workspace>ALA</workspace></defaultStyle></layer>"
            } else {
                styleXml = "<layer><defaultStyle><name>distributions_style</name><workspace>ALA</workspace></defaultStyle></layer>"
            }
            restCall("Assign style to layer " + layer, "PUT", "/rest/layers/ALA:" + layer, styleXml)
        }
    }

    void whitelistColocatedUploads() {
        String request = '<regexUrlCheck><name>colocated_uploads</name><description></description>' +
                '<enabled>true</enabled><regex>^file://' + spatialConfig.data.dir + '/layer/(?!.*\\.\\./).*$</regex></regexUrlCheck>'

        restCall("Whitelist colocated uploads", "POST", "/rest/urlchecks", request)
    }
}
