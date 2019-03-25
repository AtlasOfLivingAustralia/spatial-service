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

import au.org.ala.spatial.Util
import au.org.ala.spatial.util.UploadSpatialResource
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.converters.Converter
import com.thoughtworks.xstream.converters.DataHolder
import com.thoughtworks.xstream.converters.MarshallingContext
import com.thoughtworks.xstream.converters.UnmarshallingContext
import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.HierarchicalStreamWriter
import com.thoughtworks.xstream.io.xml.DomDriver
import com.thoughtworks.xstream.mapper.MapperWrapper
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.ArrayUtils

import java.nio.file.Files

class LegacyService {

    def grailsApplication
    def manageLayersService
    def layerDao

    def apply() {
        if (!grailsApplication.config.legacy.enabled.toBoolean()) return

        //copy layer distances file
        File f1 = new File(grailsApplication.config.legacy.ALASPATIAL_OUTPUT_PATH + '/layerDistances.properties')
        File f2 = new File(grailsApplication.config.legacy.workingdir + '/layerDistances.properties')
        File nf = new File(grailsApplication.config.data.dir + '/public/layerDistances.properties')
        if (!nf.exists()) {
            if (f1.exists()) legacySet(f1, nf)
            else if (f2.exists()) legacySet(f2, nf)
        }

        //link layer files
        def legacyDir = grailsApplication.config.legacy.LAYER_FILES_PATH
        def legacyLayerDirs = ['/diva', '/shape', '/geotiff', '/shape_diva']

        for (String dir : legacyLayerDirs) {
            def files = new File(legacyDir + dir).listFiles()
            for (File f : files) {
                nf = new File(grailsApplication.config.data.dir + '/layer/' + f.getName())
                legacySet(f, nf)
            }
        }

        //link standard grid files
        def oldDir = new File(grailsApplication.config.legacy.ANALYSIS_LAYER_FILES_PATH.toString())
        def newDir = new File(grailsApplication.config.data.dir.toString() + '/standard_layer')
        newDir.mkdirs()
        legacySet(oldDir, newDir)

        //link analysis files
        legacyDir = grailsApplication.config.legacy.ALASPATIAL_OUTPUT_PATH + '/'
        legacyLayerDirs = ['aloc', 'gdm', 'layerthumbs', 'maxent', 'scatterplot', 'session', 'sitesbyspecies']

        for (String dir : legacyLayerDirs) {
            def files = new File(legacyDir + dir).listFiles()
            def srichness = false
            def odensity = false
            def sitesbyspecies = false
            for (File f : files) {
                if ('layerthumbs'.equalsIgnoreCase(dir)) {
                    File out = new File(grailsApplication.config.data.dir + '/public/thumbnail')
                    out.mkdirs()
                    nf = new File(out.getPath() + '/' + f.getName().replace("ALA:", ""))
                    if (!nf.exists()) {
                        legacySet(f, nf)
                    }
                } else if ("session".equalsIgnoreCase(dir)) {
                    convertSessionFiles(f.listFiles(), f.getName())
                } else if ('scatterplot'.equalsIgnoreCase(dir)) {
                    File data = new File(grailsApplication.config.legacy.workingdir + '/' + f.getName())
                    File out = new File(grailsApplication.config.data.dir + "/public/" + f.getName())
                    File img = new File(f.getPath() + '/' + f.getName() + '/' + f.getName() + '.png')
                    if (data.exists() && img.exists() && !out.exists()) {
                        out.mkdirs()

                        legacySet(img, out.getPath() + '/' + img.getName())

                        //TODO: convertScatterplotFiles(data, out)

                        String time = String.valueOf(System.currentTimeMillis())
                        String imgurl = grailsApplication.config.grails.serverURL + '/tasks/output/' + f.getName() + '/' + f.getName() + ".png"
                        String spec = "{\"history\":{${time} : \"Imported from previous spatial portal\"},\n" +
                                "                                    \"input\":{},\n" +
                                "                                    \"isBackground\":false,\n" +
                                "                                    \"description\":\"List of scatterplots.\",\n" +
                                "                                    \"name\":\"ScatterplotCreate\",\n" +
                                "                                    \"output\": { \"files\" : [{\n" +
                                "                                        \"species\":{\"scatterplotUrl\":\"${imgurl}\"}}]},\n" +
                                "                                    \"description\":\"Scatterplot species with style, scatterplot image url and task id for input to ScatterplotDraw.\"},\n" +
                                "                                    \"download\":{\"description\":\"Files in the download zip.\"}},\n" +
                                "                                    \"version\":1.0}"

                        FileUtils.writeStringToFile(new File(grailsApplication.config.data.dir + "/public/" + f.getName() + '/spec.json'), spec)
                    }
                } else if (f.isDirectory()) {
                    //link layer files
                    def analysisFiles = f.listFiles()
                    File taskDir = new File(grailsApplication.config.data.dir + '/public/' + f.getName())
                    if (!taskDir.exists()) taskDir.mkdirs()
                    for (File af : analysisFiles) {
                        if (af.getName().endsWith(".grd") || af.getName().endsWith(".gri")) {
                            nf = new File(grailsApplication.config.data.dir + '/layer/' + f.getName() + af.getName())
                            if (!nf.exists()) {
                                legacySet(af, nf)
                            }
                        } else if (af.getName().endsWith(".csv")) {
                            if (af.getName().equalsIgnoreCase("SitesBySpecies.csv")) {
                                sitesbyspecies = true
                            }
                        } else if (af.getName().endsWith(".asc")) {
                            String newName = f.getName() + '_' + af.getName().replaceAll("\\..*", ".tif")
                            nf = new File("${grailsApplication.config.data.dir}/layer/${newName}.tif")
                            if (!nf.exists()) {
                                if ("move".equalsIgnoreCase(grailsApplication.config.legacy.type.toString())) {

                                    //copy as tiff
                                    String[] cmd = [grailsApplication.config.gdal.dir + "/gdal_translate", "-of", "GTiff",
                                                    "-a_srs", "EPSG:4326", "-co", "COMPRESS=DEFLATE", "-co", "TILED=YES",
                                                    "-co", "BIGTIFF=IF_SAFER", af.getPath(),
                                                    grailsApplication.config.data.dir + '/layer/' + newName + '.tif']
                                    Util.runCmd(cmd)

                                    //copy sld
                                    File sld = new File(taskDir.getPath() + '/' + newName + '.sld')
                                    String oldLayerName = ''
                                    String sldName = ''
                                    if ('aloc'.equalsIgnoreCase(dir)) {
                                        sldName = 'aloc_' + f.getName()
                                        oldLayerName = "aloc_" + f.getName()
                                    } else if ('gdm'.equalsIgnoreCase(dir)) {
                                        sldName = 'alastyles' //default sld for gdm
                                        oldLayerName = 'gdm_' + af.getName().replace('.asc', '_' + f.getName())
                                    } else if ('maxent'.equalsIgnoreCase(dir)) {
                                        oldLayerName = 'species_' + f.getName()
                                    } else if ('sitesbyspecies'.equalsIgnoreCase(dir)) {
                                        if (af.getName().contains("occurrence")) {
                                            odensity = true
                                            sldName = 'odensity_' + f.getName()
                                            oldLayerName = sldName
                                        } else if (af.getName().contains("species")) {
                                            srichness = true
                                            sldName = 'srichness_' + f.getName()
                                            oldLayerName = sldName
                                        }
                                    }
                                    if (!sldName.isEmpty()) {
                                        FileUtils.writeStringToFile(sld, getSld(sldName))
                                        FileUtils.copyFile(sld, new File(grailsApplication.config.data.dir + '/layer/' + newName + '.sld'))
                                    }

                                    //delete layer
                                    deleteLayer(oldLayerName)

                                    //add new layer
                                    createLayer(af.getParent() + '/' + newName + '.tif', oldLayerName)
                                } else if ("copy".equalsIgnoreCase(grailsApplication.config.legacy.type.toString())) {
                                    //add new layer, it has the same name so skip creation
                                }
                            }
                        }
                        nf = new File(taskDir.getPath() + '/' + af.getName())
                        if (!nf.exists()) {
                            legacySet(af, nf)
                        }
                    }

                    //zip task only when requested from TaskController.output

                    //create spec.json for usage
                    String time = String.valueOf(System.currentTimeMillis())
                    String name = f.getName()
                    String spec = ""

                    if ('aloc'.equalsIgnoreCase(dir)) {
                        spec = "{\"history\":{${time} : \"Imported from previous spatial portal\"},\n" +
                                "                        \"input\":{},\n" +
                                "                        \"isBackground\":false,\n" +
                                "                        \"description\":\"Classification of environmental layers in an area.\",\n" +
                                "                        \"name\":\"Classification\",\n" +
                                "                        \"output\":{\n" +
                                "                            \"files\":{\n" +
                                "                                \"files\":[\"aloc.log\",\"extents.txt\",\"classification_means.csv\",\"aloc.asc\",\"aloc.png\"],\n" +
                                "                                \"description\":\"Group means.\"\n" +
                                "                            },\n" +
                                "                            \"layers\":{\n" +
                                "                                \"files\":[\"/layer/aloc_${name}.sld\",\"/layer/aloc_${name}.tif\",\"/layer/aloc.grd\",\"/layer/aloc.gri\"],\n" +
                                "                            \"description\":\"Output layer.\"\n" +
                                "                        },\n" +
                                "                        \"download\":{\n" +
                                "                            \"files\":[\"aloc.log\",\"classification_means.csv\",\"aloc.asc\",\"aloc.png\",\"classification.html\"],\n" +
                                "                            \"description\":\"Files in the download zip.\"},\n" +
                                "                        \"metadata\":{\"files\":[\"classification.html\"],\"description\":\"Classification metadata.\"}},\n" +
                                "                        \"version\": 0}"

                    } else if ('gdm'.equalsIgnoreCase(dir)) {
                        spec = ""
                    } else if ('maxent'.equalsIgnoreCase(dir)) {
                        spec = "{\"history\":{${time} : \"Imported from previous spatial portal\"},\n" +
                                "                            \"input\":{},\n" +
                                "                            \"isBackground\":false,\n" +
                                "                            \"description\":\"Maxent prediction.\",\n" +
                                "                            \"name\":\"Maxent\",\n" +
                                "                            \"output\":{\"files\":{\"files\":[\"species.asc\",\"plots/\",\"removedSpecies.txt\",\"maxentResults.csv\",\"maxent.log\"],\n" +
                                "                                \"description\":\"Output files.\"},\n" +
                                "                            \"layers\":{\"files\":[\"/layer/species_${name}.sld\",\"/layer/species_${name}.tif\"],\"description\":\"Output layers.\"},\n" +
                                "                            \"download\":{\"files\":[\"species.asc\",\"plots/\",\"species.html\",\"removedSpecies.txt\",\"maxentResults.csv\",\"maxent.log\"],\n" +
                                "                                \"description\":\"Files in the download zip.\"},\n" +
                                "                            \"metadata\":{\"files\":[\"species.html\"],\"description\":\"Prediction metadata.\"}},\n" +
                                "                            \"version\":0}"
                    } else if ("sitesbyspecies".equalsIgnoreCase(dir)) {
                        spec = "{\"history\":{${time} : \"Imported from previous spatial portal\"},\n" +
                                "                        \"input\":{},\n" +
                                "                        \"isBackground\":false,\n" +
                                "                        \"description\":\"Points to grid.\",\n" +
                                "                        \"name\":\"PointsToGrid\",\n" +
                                "                        \"output\":{\n" +
                                "                            \"files\":{ \"files\":[" + (sitesbyspecies ? "\"/SitesBySpecies.csv\"" : "") + "]},\n" +
                                "                            \"layers\":{\n" +
                                (srichness && odensity ? "\"files\":[\"/layer/srichness_${name}.sld\",\"/layer/srichness_${name}.tif\",\"/layer/odensity_${name}.sld\",\"/layer/odensity_${name}.tif\"]" : "") +
                                (srichness ? "\"files\":[\"/layer/srichness_${name}.sld\",\"/layer/srichness_${name}.tif\"]" : "") +
                                (odensity ? "\"files\":[\"/layer/odensity_${name}.sld\",\"/layer/odensity_${name}.tif\"]" : "") +
                                "                            \"description\":\"Output layer.\"\n" +
                                "                        },\n" +
                                "                        \"download\":{\n" +
                                "                            \"files\":[],\n" +
                                "                            \"description\":\"Files in the download zip.\"},\n" +
                                "                        \"metadata\":{\"files\":[\"sxs_metadata.html\"],\"description\":\"Points To Grid metadata.\"}},\n" +
                                "                        \"version\": 0}"
                    }
                    FileUtils.writeStringToFile(new File(grailsApplication.config.data.dir + "/public/" + f.getName() + '/spec.json'), spec)
                }
            }
        }
    }

    def convertSessionFiles(File[] files, String name) {
        if (files.length == 0) return

        def dir = files[0].getParent()

        Map m = new HashMap()

        Scanner scanner = null
        try {
            scanner = new Scanner(new File(dir + "/details.txt"))
            // first grab the zoom level and bounding box
            String[] mapdetails = scanner.nextLine().split(",")

            float[] bb = [Float.parseFloat(mapdetails[1]), Float.parseFloat(mapdetails[2]),
                          Float.parseFloat(mapdetails[3]), Float.parseFloat(mapdetails[4])]

            m.put("basemap", "google_roadmaps")
            m.put("extents", bb)

            String[] scatterplotNames = null
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine()
                if (line.startsWith("scatterplotNames")) {
                    scatterplotNames = line.substring(17).split("___")
                }
            }
            ArrayUtils.reverse(scatterplotNames)

            XStream xstream = new XStream(new DomDriver()) {
                protected MapperWrapper wrapMapper(MapperWrapper next) {
                    return new MapperWrapper(next) {
                        public boolean shouldSerializeMember(Class definedIn, String fieldName) {
                            if (definedIn == Object.class || !super.shouldSerializeMember(definedIn, fieldName))
                                System.out.println("faled to read: " + definedIn + ", " + fieldName)

                            return definedIn != Object.class ? super.shouldSerializeMember(definedIn, fieldName) : false
                        }
                    }
                }

                @Override
                public Object unmarshal(HierarchicalStreamReader reader) {
                    Object o = super.unmarshal(reader)
                    return o
                }

                @Override
                public Object unmarshal(HierarchicalStreamReader reader, Object root) {
                    Object o = super.unmarshal(reader, root)
                    return o
                }

                @Override
                public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder) {
                    Object o = super.unmarshal(reader, root, dataHolder)
                    return o
                }
            }
            xstream.registerConverter(new MapConverter())
            xstream.alias("au.org.emii.portal.menu.MapLayer", HashMap.class)

            int i = 0
            for (File f : files) {
                if (!f.getName().equals("details.txt")) {
                    Map layer = (Map) xstream.fromXML(files[1])

                    Map ml = new HashMap()
                    ml.put("uid", i++)
                    ml.put("colorType", layer.get("colourMode"))
                    ml.put("facet", layer.get("colourMode"))
                    ml.put("visible", layer.get("displayed"))

                    Map q = ((Map) layer.get("data")).get("query")
                    Map metadata = (Map) layer.get("mapLayerMetadata")

                    ml.put("name", layer.get("displayName"))
                    ml.put("opacity", Float.parseFloat(layer.get("opacity")) * 100.0)
                    ml.put("uncertainty", layer.get("sizeUncertain"))
                    ml.put("qid", q.get("paramId")) //species layer
                    ml.put("pid", q.get("pid")) //area layer
                    ml.put("wkt", layer.get("geometryWKT")) //area layer
                    ml.put("facets", layer.get("facets")) //q filter for area
                    ml.put("uri", layer.get("uri")) //for wms layers
                    ml.put("spcode", layer.get("spcode")) //distributions and checklists
                    ml.put("legendUrl", ((Map) ((List) layer.get("styles")).get(0)).get("legendUri")) //wms legend
                    ml.put("metadata", metadata.get("moreInfo")) //more info link or display
                    ml.put("displaypath", layer.get("uri"))
                    ml.put("bs", q.get("biocacheServer"))
                    ml.put("ws", q.get("biocacheWebServer"))
                    ml.put("red", layer.get("redVal"))
                    ml.put("green", layer.get("greenVal"))
                    ml.put("blue", layer.get("blueVal"))
                    ml.put("size", layer.get("size"))
                    String type = layer.get("subType").toString()

                    //TODO: update spatial-hub to fill in the blanks
                    //TODO: support scatterplot layers
                    if (ml.get("metadata") != null && ml.get("metadata").toString().contains("/layers/view/more/")) {
                        //extract layer id
                        String id = ml.get("metadata").toString().replaceAll(".*/layers/view/more/", "")
                        ml.put("id", id)
                        if ("environmental".equalsIgnoreCase(layerDao.getLayerById(id)?.getType())) {
                            ml.put("layertype", layer.get("environmental"))
                        } else {
                            ml.put("layertype", layer.get("contextual"))
                        }
                    } else if ("21" == type) ml.put("layertype", layer.get("species"))
                    else if ("true" == layer.get("polygonLayer")?.toString()) ml.put("layertype", layer.get("area"))

                }
            }

        } catch (Exception e) {
            e.printStackTrace()

        } finally {
            if (scanner != null) {
                scanner.close()
            }
        }
    }

    def getSld(String name) {
        def geoserverUrl = grailsApplication.config.geoserver.url
        def geoserverUsername = grailsApplication.config.geoserver.username
        def geoserverPassword = grailsApplication.config.geoserver.password

        def url = geoserverUrl + "/rest/styles/" + name
        def result = manageLayersService.httpCall("GET", url, geoserverUsername, geoserverPassword, null, null, "text/plain")

        result[1]
    }

    def legacySet(File src, File target, boolean force = false) {
        boolean symbolic = Files.isSymbolicLink(target.toPath())
        boolean move = "move".equalsIgnoreCase(grailsApplication.config.legacy.type.toString())
        boolean copy = "copy".equalsIgnoreCase(grailsApplication.config.legacy.type.toString())

        if (src.exists() && (!target.exists() || force || (symbolic && (move || copy)))) {
            if (!target.getParentFile().exists())
                target.getParentFile().mkdirs()

            if (move) {
                if (src.isDirectory()) {
                    if (target.isDirectory()) {
                        FileUtils.moveDirectoryToDirectory(src, target, true)
                    } else {
                        FileUtils.moveDirectory(src, target)
                    }
                } else {
                    if (target.isDirectory()) {
                        FileUtils.moveFileToDirectory(src, target, true)
                    } else {
                        FileUtils.moveFile(src, target)
                    }
                }
            } else if (copy) {
                if (src.isDirectory()) {
                    if (target.isDirectory()) {
                        FileUtils.copyDirectoryToDirectory(src, target, true)
                    } else {
                        FileUtils.copyDirectory(src, target)
                    }
                } else {
                    if (target.isDirectory()) {
                        FileUtils.copyFileToDirectory(src, target, true)
                    } else {
                        FileUtils.copyFile(src, target)
                    }
                }
            } else {
                Files.createSymbolicLink(target.toPath(), src.toPath())
            }
        }
    }

    def deleteLayer(String layername) {
        def geoserverUrl = grailsApplication.config.geoserver.url
        def geoserverUsername = grailsApplication.config.geoserver.username
        def geoserverPassword = grailsApplication.config.geoserver.password

        try {
            //attempt to delete
            manageLayersService.httpCall("DELETE",
                    geoserverUrl + "/rest/workspaces/ALA/coveragestores/" + layername,
                    geoserverUsername, geoserverPassword,
                    null, null, "text/plain")
        } catch (Exception e) {
            log.error("failed to delete geoserver layer: " + layername + ", " + e.getMessage(), e)
        }
    }

    def createLayer(String tiff, String layername) {
        def geotiff = new File(tiff)
        def sld = new File(tiff.replace(".tif", ".sld"))
        def name = layername

        def geoserverUrl = grailsApplication.config.geoserver.url
        def geoserverUsername = grailsApplication.config.geoserver.username
        def geoserverPassword = grailsApplication.config.geoserver.password

        if (geotiff.exists()) {
            try {

                //TODO: Why is.prj interfering with Geoserver discovering .tif is EPSG:4326?
                def oldPrj = new File(tiff.replace('.tif', '.prj'))
                def tmpPrj = new File(tiff.replace('.tif', '.prj.tmp'))
                if (oldPrj.exists()) FileUtils.moveFile(oldPrj, tmpPrj)

                //attempt to delete
                manageLayersService.httpCall("DELETE",
                        geoserverUrl + "/rest/workspaces/ALA/coveragestores/" + name,
                        geoserverUsername, geoserverPassword,
                        null, null, "text/plain")

                // no need to handle remote geoserver

                String[] result = manageLayersService.httpCall("PUT",
                        geoserverUrl + "/rest/workspaces/ALA/coveragestores/" +
                                name + "/external.geotiff?configure=first",
                        geoserverUsername, geoserverPassword,
                        null,
                        "file://" + geotiff.getPath(),
                        "text/plain")
                if (result[0] != "200" && result[0] != "201") {
                    log.error("failed to PUT tiff: " + geotiff.getPath() + ", " + result[0] + ": " + result[1])
                }

                //return prj
                if (tmpPrj.exists()) FileUtils.moveFile(tmpPrj, oldPrj)

                if (sld.exists()) {
                    //Create style
                    def out = UploadSpatialResource.sld(geoserverUrl, geoserverUsername, geoserverPassword, name, sld.getPath())
                    if (!out.startsWith("200") && !out.startsWith("201")) {
                        log.error("failed to apply style: " + name)
                    }
                }
            } catch (err) {
                log.error 'failed to upload geotiff to geoserver: ' + geotiff.getPath(), err
            }
        }
    }

    class MapConverter implements Converter {

        boolean canConvert(Class clazz) {
            return true
        }

        void marshal(Object value, HierarchicalStreamWriter writer,
                            MarshallingContext context) {
        }

        Object unmarshal(HierarchicalStreamReader reader,
                                UnmarshallingContext context) {
            return unmarshal(reader, context, false)
        }

        Object unmarshal(HierarchicalStreamReader reader,
                         UnmarshallingContext context, boolean isList) {
            Map<String, Object> map = null
            List<String> list = null

            while (reader.hasMoreChildren()) {
                reader.moveDown()

                String key = reader.getNodeName() // nodeName aka element's name
                String value = reader.getValue()

                if (key.startsWith("occurrence_year,1942"))
                    key = key

                if (value.equals("7"))
                    key = key

                if ("entry".equals(key)) {
                    while (reader.hasMoreChildren()) {
                        reader.moveDown()
                        String key1 = reader.getNodeName() // nodeName aka element's name
                        String value1 = reader.getValue()
                        if (key1.startsWith("occurrence_year,1942"))
                            key = key
                        if (value1.startsWith("occurrence_year,1942"))
                            key = key
                        if (reader.hasMoreChildren()) {
                            value1 = unmarshal(reader, context, key1.endsWith("s") || key1.endsWith("List"))
                        }
                        reader.moveUp()
                        reader.moveDown()
                        String key2 = reader.getNodeName() // nodeName aka element's name
                        if (key2.startsWith("occurrence_year,1942"))
                            key = key
                        Object value2 = reader.getValue()
                        if (reader.hasMoreChildren()) {
                            value2 = unmarshal(reader, context, key2.endsWith("s") || key2.endsWith("List"))
                        }
                        reader.moveUp()

                        if (map == null) map = new HashMap<String, Object>()
                        map.put(value1, value2)
                    }
                } else if (reader.hasMoreChildren()) {
                    Object o = unmarshal(reader, context, key.endsWith("s") || key.endsWith("List"))
                    if (!isList) {
                        if (map == null) map = new HashMap<String, Object>()
                        map.put(key, o)
                    } else {
                        if (list == null) list = new ArrayList<>()
                        list.add(o)
                    }
                } else if ("string".equals(key) || "double".equals(key) || "float".equals(key) ||
                        "long".equals(key) || "int".equals(key)) {
                    if (list == null) list = new ArrayList<>()
                    list.add(value)
                } else {
                    if (map == null) map = new HashMap<String, Object>()

                    if (map.containsKey(key) && map.size() == 1) {
                        //change to list
                        if (list == null) list = new ArrayList<>()
                        list.add(map.get(key))
                        list.add(value)
                        map.remove(key)
                    } else {
                        if (!isList) {
                            if (map == null) map = new HashMap<String, Object>()
                            map.put(key, value)
                        } else {
                            if (list == null) list = new ArrayList<>()
                            list.add(value)
                        }
                    }
                }

                reader.moveUp()
            }

            return list != null ? list : map
        }

    }
}
