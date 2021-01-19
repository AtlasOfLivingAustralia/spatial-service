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

package au.org.ala.spatial.service

import au.org.ala.layers.intersect.Grid
import au.org.ala.spatial.util.UploadSpatialResource
import grails.converters.JSON
import org.apache.commons.io.FileUtils

class AdminController {

    def masterService
    def serviceAuthService
    def fieldDao
    def authService

    def index() {

    }

    /**
     * get collated capabilities specs from all registered slaves
     * @return
     */
    def capabilities() {
        render masterService.spec(serviceAuthService.isAdmin(params)) as JSON
    }

    /**
     * information about all registered slaves
     *
     * admin only
     *
     * @return
     */
    def slaves() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        render masterService.slaves as JSON
    }

    /**
     * information about all tasks waiting and running
     *
     * admin only
     *
     * @return
     */
    def tasks() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        params.max = params?.max ?: 10

        List list
        if (params.containsKey('all')) {
            list = Task.list(params)
        } else {
            list = Task.findAllByStatusOrStatus(0, 1)
        }
        render list as JSON
    }

    /**
     * trigger slave re-register
     *
     * admin only
     *
     * @return
     */
    def reRegisterSlaves() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        int count = 0
        masterService.slaves.each { url, slave ->
            if (masterService.reRegister(slave)) {
                count++
            }
        }

        Map map = [slavesReRegistered: count]
        render map as JSON
    }

    /**
     * Return true when logged in, CAS is disabled or api_key is valid.
     *
     * Otherwise redirect to CAS for login.
     *
     * @param params
     * @return
     */
    private boolean doLogin() {
        if (!serviceAuthService.isLoggedIn(params)) {
            redirect(url: grailsApplication.config.security.cas.loginUrl + "?service=" +
                    grailsApplication.config.security.cas.appServerName + request.forwardURI + (request.queryString ? '?' + request.queryString : ''))
            return false
        }

        return true
    }


    /**
     * admin only
     *
     * @return
     */
    def defaultGeoserverStyles() {
        if (!doLogin() || !serviceAuthService.isAdmin(params)) {
            return
        }

        def geoserverUrl = grailsApplication.config.geoserver.url
        def geoserverUsername = grailsApplication.config.geoserver.username
        def geoserverPassword = grailsApplication.config.geoserver.password

        // create outline style
        def data = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" +
                "<StyledLayerDescriptor version=\"1.0.0\"\n" +
                "  xsi:schemaLocation=\"http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd\"\n" +
                "  xmlns=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\"\n" +
                "  xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\n" +
                "  <NamedLayer>\n" +
                "    <Name></Name>\n" +
                "    <UserStyle>\n" +
                "      <Title>An outline polygon style</Title>\n" +
                "      <FeatureTypeStyle>\n" +
                "        <Rule>\n" +
                "          <Title>outline polygon</Title>\n" +
                "          <PolygonSymbolizer>\n" +
                "            <Stroke>\n" +
                "              <CssParameter name=\"stroke\">#000000</CssParameter>\n" +
                "              <CssParameter name=\"stroke-width\">0.5</CssParameter>\n" +
                "            </Stroke>\n" +
                "          </PolygonSymbolizer>\n" +
                "\n" +
                "        </Rule>\n" +
                "\n" +
                "      </FeatureTypeStyle>\n" +
                "    </UserStyle>\n" +
                "  </NamedLayer>\n" +
                "</StyledLayerDescriptor>\n"

        def extra = ''

        UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, "outline")
        File tmpFile = File.createTempFile("sld", "xml")
        FileUtils.writeStringToFile(tmpFile, data, "UTF-8");
        UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + "outline",
                extra, geoserverUsername, geoserverPassword, tmpFile.path);

        def fields = fieldDao.getFieldsByCriteria('')
        fields.each { field ->
            def styleName = field.id
            def layerName = field.layer.name

            def stylesRequired = []
            if ('c'.equals(field.type)) {
                stylesRequired.push(styleName)
                stylesRequired.push('outline')
                stylesRequired.push('polygon')
            } else {
                stylesRequired.push(layerName)

                def linear = layerName + "_linear"
                stylesRequired.push(linear)

                // add layerName_linear sld if required
                UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                        extra, geoserverUsername, geoserverPassword, "outline")
                tmpFile = File.createTempFile("sld", "xml")
                FileUtils.writeStringToFile(tmpFile, getLinearStyle(layerName), "UTF-8");
                UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + linear,
                        extra, geoserverUsername, geoserverPassword, tmpFile.path)
            }

            stylesRequired.each { style ->
                data = "<style><name>" + style + "</name></style>"
                UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + layerName + "/styles", "",
                        geoserverUsername, geoserverPassword, data)
                UploadSpatialResource.addGwcStyle(geoserverUrl, layerName, style, geoserverUsername, geoserverPassword)
            }
        }
    }

    private def getLinearStyle(String name, boolean reversed) {

        if (l != null) {
            String dir = grailsApplication.config.data.dir
            def diva = new Grid(dir + "/layer/" + layer.name)

            def min = diva.minval
            def max = diva.maxval
            def nodatavalue = diva.nodatavalue

            def colour1
            def colour2

            if (reversed) {
                colour1 = '0xffffff'
                colour2 = '0x000000'
            } else {
                colour1 = '0x000000'
                colour2 = '0xffffff'
            }

            String classSld = '<?xml version="1.0" encoding="UTF-8"?><StyledLayerDescriptor xmlns="http://www.opengis.net/sld">' +
                    '<NamedLayer><Name>ALA:' + name + '</Name>' +
                    '<UserStyle><FeatureTypeStyle><Rule><RasterSymbolizer><Geometry></Geometry><ColorMap>' +
                    (nodatavalue < min ? '<ColorMapEntry color="0x000000" opacity="0" quantity="' + nodatavalue + '"/>' : '') +
                    '<ColorMapEntry color="0x000000" opacity="1" quantity="' + min + '"/>' +
                    '<ColorMapEntry color="0xffffff" opacity="1" quantity="' + max + '"/>' +
                    (nodatavalue > max ? '<ColorMapEntry color="0xffffff" opacity="0" quantity="' + nodatavalue + '"/>' : '') +
                    '</ColorMap></RasterSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>'
        }
    }
}
