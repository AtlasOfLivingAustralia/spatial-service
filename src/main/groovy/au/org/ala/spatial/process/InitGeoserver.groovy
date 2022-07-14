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
import org.apache.http.HttpEntity
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.StringEntity

@Slf4j
class InitGeoserver extends SlaveProcess {
    String geoserverUrl
    String username
    String password
    String postgresqlPath
    String postgresqlUser
    String postgresqlPassword

    void start() {
        geoserverUrl = task.input.geoserverUrl ?: grailsApplication.config.geoserver.url
        username = task.input.geoserverUser ?: grailsApplication.config.geoserver.username
        password = task.input.geoserverPassword ?: grailsApplication.config.geoserver.password

        // default url is jdbc:postgres://localhost/layersdb, get the path
        postgresqlPath = task.input.postgresqlPath ?: grailsApplication.config.dataSource.url.split("/")[2]
        postgresqlUser = task.input.postgresqlUser ?: grailsApplication.config.dataSource.username
        postgresqlPassword = task.input.postgresqlPassword ?: grailsApplication.config.dataSource.password

        changeGeoserverPassword()

        uploadLayoutFiles()
        setupWorkspace()
        linkToPostgresql()
        createWorldLayer()
    }

    Map restCall(String description, String type, String url, HttpEntity entity) {
        taskLog(description + "...")
        Map response = Util.urlResponse(type, geoserverUrl + url, null, null, entity, true, username, password)
        taskLog("statusCode: " + response.statusCode + ", " + response.text)

        return response
    }

    void changeGeoserverPassword() {
        String defaultUser = 'admin'
        String defaultPassword = 'geoserver'

        HttpEntity entity
        Map response
        def resource

        if (defaultPassword == password) {
            log.error("Geoserver is configured with the default password")
            taskLog("ERROR: Geoserver is configured with the default password")
        } else {
            // attempt to change the password from the default to the password in the config
            taskLog("Change the default password...")
            entity = new StringEntity("{ \"newPassword\":\"${password}\" }", "application/json", "UTF-8")
            response = Util.urlResponse("PUT", geoserverUrl + "/rest/security/self/password", null, null, entity, true, defaultUser, defaultPassword)
            taskLog("statusCode: " + response.statusCode + ", " + response.text)

            // attempt to change the master password with the supplied
            entity = new StringEntity("{ \"oldMasterPassword\":\"${defaultPassword}\", \"newMasterPassword\":\"${password}\" }", "application/json", "UTF-8")
            restCall("Change the default password", "PUT", "/rest/security/masterpw", entity)
        }
    }

    void createWorldLayer() {
        HttpEntity entity
        Map response
        def resource

        response = restCall("Search for layer 'world'", "GET", "/rest/layers/ALA:world.xml", null)
        if (response.statusCode != 200) {
            resource = InitGeoserver.class.getResource("/geoserver/world.zip")
            entity = new ByteArrayEntity(resource.bytes, "application/zip")
            restCall("Upload the shapefile", "PUT", "/rest/workspaces/ALA/datastores/world/file.shp", entity)
        }
    }

    void uploadLayoutFiles() {
        HttpEntity entity
        Map response
        def resource

        resource = InitGeoserver.class.getResource("/geoserver/scale.xml")
        entity = new StringEntity(resource.text, "application/json", "UTF-8")
        restCall("Create the 'scale' layout that is used by biocache-service", "PUT", "/rest/resource/layout/scale.xml", entity)
    }

    void setupWorkspace() {
        HttpEntity entity
        Map response
        def resource

        taskLog("Delete default workspaces and create ALA workspace")
        for (String workspace : ["nurc", "cite", "it.geosolutions.html", "sde", "sf", "tiger", "topp"]) {
            restCall("Delete workspace " + workspace, "DELETE", "/rest/workspaces/" + workspace + "?recurse=true", null)
        }
        response = restCall("Search for workspace ALA", "GET", "/rest/workspaces/ALA", null)
        if (response.statusCode != 200) {
            restCall("Creating workspace ALA", "POST", "/rest/workspaces", new StringEntity("<workspace><name>ALA</name></workspace>", "text/xml", "UTF-8"))
        }
    }

    void linkToPostgresql() {
        HttpEntity entity
        Map response
        def resource

        // create store
        entity = new StringEntity("<dataStore><name>LayersDB</name><connectionParameters>" +
                "<host>" + postgresqlPath + "</host>" +
                "<port>5432</port>" +
                "<database>layersdb</database>" +
                "<schema>public</schema>" +
                "<user>" + postgresqlUser + "</user>" +
                "<passwd>" + postgresqlPassword + "</passwd>" +
                "<dbtype>postgis</dbtype>" +
                "</connectionParameters></dataStore>", "text/xml", "UTF-8")
        response = restCall("Search for store LayersDB", "GET", "/rest/workspaces/ALA/datastores/LayersDB", null)
        if (response.statusCode != 200) {
            restCall("Creating layersDB store", "POST", "/rest/workspaces/ALA/datastores", entity)
        }

        // create styles
        resource = InitGeoserver.class.getResource("/geoserver/marker.png")
        entity = new ByteArrayEntity(resource.bytes, "image/png")
        restCall("Upload marker.png for the points_style", "PUT", "/rest/resource/styles/marker.png", entity)

        taskLog("Creating and uploading styles")
        for (String style : ["envelope_style", "distributions_style", "alastyles", "points_style"]) {
            response = restCall("Search for style " + style, "GET", "/rest/styles/" + style + ".xml", null)
            if (response.statusCode != 200) {
                entity = new StringEntity("<style><name>" + style + "</name><filename>" + style + ".sld</filename></style>", "text/xml", "UTF-8")
                restCall("Creating style " + style, "POST", "/rest/styles", entity)
            }
            resource = InitGeoserver.class.getResource("/geoserver/" + style + ".sld")
            entity = new StringEntity(resource.text, "application/vnd.ogc.sld+xml", "UTF-8")
            restCall("Upload style " + style, "PUT", "/rest/styles/" + style, entity)
        }

        // create layers
        taskLog("Creating layers and assigning styles")
        for (String layer : ["Objects", "Distributions", "Points"]) {
            resource = InitGeoserver.class.getResource("/geoserver/" + layer + ".xml")
            entity = new StringEntity(resource.text, "text/xml", "UTF-8")
            restCall("Creating layer " + layer, "POST", "/rest/workspaces/ALA/datastores/LayersDB/featuretypes", entity)

            if (layer.equals("Points")) {
                entity = new StringEntity("<layer><defaultStyle><name>points_style</name><workspace>ALA</workspace></defaultStyle></layer>", "text/xml", "UTF-8")
            } else {
                entity = new StringEntity("<layer><defaultStyle><name>distributions_style</name><workspace>ALA</workspace></defaultStyle></layer>", "text/xml", "UTF-8")
            }

            restCall("Assign style to layer " + layer, "PUT", "/rest/layers/ALA:" + layer, entity)
        }

    }
}
