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

import au.org.ala.layers.intersect.SimpleShapeFile
import au.org.ala.layers.util.LayerFilter
import au.org.ala.spatial.slave.SpatialUtils
import au.org.ala.spatial.slave.Utils
import org.apache.commons.io.FileUtils
import org.json.simple.parser.JSONParser

class Classification extends SlaveProcess {

    void start() {
        //update ALOC
        slaveService.getFile('/modelling/aloc/aloc.jar')

        //list of layers
        JSONParser jp = new JSONParser()
        def layers = jp.parse(task.input.layer.toString())
        def envnameslist = new String[layers.size()]
        layers.eachWithIndex { l, idx ->
            envnameslist[idx] = l
        }

        //area to restrict
        String area = jp.parse(task.input.area.toString())
        String region = null
        String envelope = null
        if (area != null && area.startsWith("ENVELOPE")) {
            envelope = LayerFilter.parseLayerFilters(area)
        } else {
            region = SimpleShapeFile.parseWKT(area)
        }

        //target resolution
        def resolution = task.input.resolution

        //number of target groups
        def groups = task.input.groups

        //make a shapefile
        def makeShapefile = task.input.shp

        new File(getTaskPath()).mkdirs()

        def cutDataPath = cutGrid(envnameslist, resolution, region, envelope, null);

        String[] cmd = ["java", "-Xmx" + String.valueOf(grailsApplication.config.aloc.xmx),
                        "-jar", grailsApplication.config.data.dir + '/modelling/aloc/aloc.jar',
                        cutDataPath, String.valueOf(groups), String.valueOf(grailsApplication.config.aloc.threads), getTaskPath()]

        runCmd(cmd, true)

        def replaceMap = [:] as LinkedHashMap
        envnameslist.each {
            replaceMap.put(it + '.grd', getLayer(getField(it).spid).displayname)
            replaceMap.put(it, getLayer(getField(it).spid).displayname)
        }

        cmd = [grailsApplication.config.gdal.dir + "/gdal_translate", "-of", "GTiff", "-a_srs", "EPSG:4326",
               "-co", "COMPRESS=DEFLATE", "-co", "TILED=YES", "-co", "BIGTIFF=IF_SAFER",
               getTaskPath() + "aloc.asc", getTaskPath() + task.id + "_aloc.tif"]
        task.message = "asc > tif"
        runCmd(cmd, false)

        if (new File(getTaskPath() + task.id + "_aloc.sld").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_aloc.sld")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + task.id + "_aloc.sld"), target)
            addOutput("layers", "/layer/" + task.id + "_aloc.sld")
        }
        if (new File(getTaskPath() + "aloc.sld").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_aloc.sld")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + "aloc.sld"), target)
            addOutput("layers", "/layer/" + task.id + "_aloc.sld")
        }
        if (new File(getTaskPath() + task.id + "_aloc.tif").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_aloc.tif")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + task.id + "_aloc.tif"), target)
            addOutput("layers", "/layer/" + task.id + "_aloc.tif")
        }

        if (new File(getTaskPath() + "aloc.grd").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_aloc.grd")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + "aloc.grd"), target)
            addOutput("layers", "/layer/" + task.id + "_aloc.grd")
        }
        if (new File(getTaskPath() + "aloc.gri").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_aloc.gri")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + "aloc.gri"), target)
            addOutput("layers", "/layer/" + task.id + "_aloc.gri")
        }

        if (new File(getTaskPath() + "aloc.log").exists()) addOutput("files", "aloc.log", true)
        if (new File(getTaskPath() + "extents.txt").exists()) addOutput("files", "extents.txt")
        if (new File(getTaskPath() + "classification_means.csv").exists()) {
            //translate fieldId to layerName
            Utils.replaceTextInFile(getTaskPath() + "classification_means.csv", replaceMap)
            addOutput("files", "classification_means.csv", true)
        }

        if (new File(getTaskPath() + "aloc.asc").exists()) addOutput("files", "aloc.asc", true)
        if (new File(getTaskPath() + "aloc.png").exists()) addOutput("files", "aloc.png", true)

        if (new File(getTaskPath() + "classification.html").exists()) {
            //translate fieldId to layerName
            Utils.replaceTextInFile(getTaskPath() + "classification.html", replaceMap)
            addOutput("metadata", "classification.html", true)
        }

        if (makeShapefile.toString().toBoolean()) {
            SpatialUtils.grid2shp(getTaskPath() + "aloc")
            addOutput("files", "aloc.shp", true)
            addOutput("files", "aloc.shx", true)
            addOutput("files", "aloc.dbf", true)
            addOutput("files", "aloc.prj", true)
            addOutput("files", "aloc.fix", true)
        }
    }
}
