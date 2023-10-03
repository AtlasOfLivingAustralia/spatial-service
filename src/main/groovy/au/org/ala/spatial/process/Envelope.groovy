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

import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.dto.LayerFilter
import au.org.ala.spatial.util.SpatialUtils
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

@Slf4j
class Envelope extends SlaveProcess {

    void start() {
        List<String> envelope = getInput('envelope').toString().split(',') as List<String>
        String resolution = getInput('resolution')
        //String makeShapefile = Boolean.parseBoolean(getInput('shp') as String)
        String geoserverUrl = getInput('geoserverUrl')

        LayerFilter[] filter = new LayerFilter[envelope.size()]
        if (envelope) {
            // fq to LayerFilter
            for (int i = 0; i < envelope.size(); i++) {
                filter[i] = LayerFilter.parseLayerFilter(envelope[i].replace(":[", ",").replace(" TO ", ",").replace("]", ""))

                // change
                def field = getField(filter[i].getLayername())
                if (field?.spid) {
                    def layername = getLayer(field?.spid)?.name
                    if (layername) {
                        filter[i] = new LayerFilter(layername, filter[i].minimum_value, filter[i].maximum_value)
                    }
                }
                String layerFile = '/standard_layer/' + resolution + "/" + filter[i].getLayername()
            }
        }

        File dir = new File(getTaskPath())
        dir.mkdirs()

        String filename = taskWrapper.id

        File grid = new File(dir.getPath() + File.separator + filename)

        double areaSqKm
        String[] types = new String[filter.length]
        String[] fieldIds = new String[filter.length]
        for (int i = 0; i < filter.length; i++) {
            types[i] = filter[i].contextual ? "c" : "e"
            fieldIds[i] = filter[i].layername
        }
        taskLog("Making envelope.....")
        try {
            if ((areaSqKm = gridCutterService.makeEnvelope(grid.getPath(), resolution, filter, Integer.MAX_VALUE, types, fieldIds)) >= 0) {

                SpatialUtils.divaToAsc(dir.getPath() + File.separator + filename)
                SpatialUtils.toGeotiff(spatialConfig.gdal.dir, dir.getPath() + File.separator + filename + ".asc")
                SpatialUtils.save4326prj(dir.getPath() + File.separator + filename + ".prj")

                writeEnvelopeStyle(dir.getPath() + File.separator + filename + ".sld")

                addOutput("files", filename + ".asc")
                addOutput("files", filename + ".prj")
                addOutput("files", filename + ".tif", true)
                addOutput("files", filename + ".sld")

                Grid g = new Grid(grid.getPath())

                def values = [name       : "Environmental envelope",
                              description: "",
                              q          : envelope,
                              bbox       : "POLYGON((" + g.xmin + " " + g.ymin + "," + g.xmax + " " + g.ymin + "," +
                                      g.xmax + " " + g.ymax + "," + g.xmin + " " + g.ymax + "," +
                                      g.xmin + " " + g.ymin + "))",
                              area_km    : areaSqKm,
                              type       : "envelope",
                              file       : filename + ".tif",
                              id         : taskWrapper.id,
                              wmsurl     : geoserverUrl + "/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + taskWrapper.id]
                addOutput("envelopes", (values as JSON).toString())

                String metadata = "<html><body>Extents: " + g.xmin + "," + g.ymin + "," + g.xmax + "," + g.ymax + "<br>Area (sq km): " + areaSqKm + "</body></html>"
                new File(dir.getPath() + File.separator + "envelope.html").write(metadata)
                addOutput("metadata", "envelope.html", true)

                SpatialUtils.grid2shp(grid.getPath(), [1])

                for (String ext : [".shp", ".shx", ".fix", ".dbf"]) {
                    File newFile = new File(dir.getPath() + File.separator + "envelope" + ext)
                    if (newFile.exists()) newFile.delete()
                    FileUtils.moveFile(new File(grid.getPath() + ext), newFile)
                    addOutput("files", "envelope" + ext, true)
                }
            } else {
                taskLog("ERROR: Area of the envelope is 0 sq km.")
            }
        } catch (err) {
            taskLog("ERROR: Area of the envelope is 0 sq km for the resolution " + resolution + " decimal degrees.")
        }
    }

    def writeEnvelopeStyle(String file) {
        new File(file).write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sld:StyledLayerDescriptor xmlns=\"http://www.opengis.net/sld\" xmlns:sld=\"http://www.opengis.net/sld\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.0.0\">\n" +
                "  <sld:NamedLayer>\n" +
                "    <sld:Name>raster</sld:Name>\n" +
                "    <sld:UserStyle>\n" +
                "      <sld:Name>raster</sld:Name>\n" +
                "      <sld:Title>A very simple color map</sld:Title>\n" +
                "      <sld:Abstract>A very basic color map</sld:Abstract>\n" +
                "      <sld:FeatureTypeStyle>\n" +
                "        <sld:Name>name</sld:Name>\n" +
                "        <sld:FeatureTypeName>Feature</sld:FeatureTypeName>\n" +
                "        <sld:Rule>\n" +
                "          <sld:RasterSymbolizer>\n" +
                "            <sld:Geometry>\n" +
                "              <ogc:PropertyName>geom</ogc:PropertyName>\n" +
                "            </sld:Geometry>\n" +
                "            <sld:ChannelSelection>\n" +
                "              <sld:GrayChannel>\n" +
                "                <sld:SourceChannelName>1</sld:SourceChannelName>\n" +
                "                <sld:ContrastEnhancement>\n" +
                "                  <sld:GammaValue>1.0</sld:GammaValue>\n" +
                "                </sld:ContrastEnhancement>\n" +
                "              </sld:GrayChannel>\n" +
                "            </sld:ChannelSelection>\n" +
                "            <sld:ColorMap>\n" +
                "              <sld:ColorMapEntry color=\"#ffffff\" opacity=\"0\" quantity=\"0\"/>\n" +
                "              <sld:ColorMapEntry color=\"#ff0000\" quantity=\"1\" label=\"presence\"/>\n" +
                "            </sld:ColorMap>\n" +
                "            <sld:ContrastEnhancement/>\n" +
                "          </sld:RasterSymbolizer>\n" +
                "        </sld:Rule>\n" +
                "      </sld:FeatureTypeStyle>\n" +
                "    </sld:UserStyle>\n" +
                "  </sld:NamedLayer>\n" +
                "</sld:StyledLayerDescriptor>")
    }
}
