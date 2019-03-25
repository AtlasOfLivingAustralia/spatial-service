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

import au.org.ala.layers.grid.GridClassBuilder
import au.org.ala.layers.legend.GridLegend
import au.org.ala.layers.util.Bil2diva
import au.org.ala.layers.util.Diva2bil
import au.org.ala.spatial.slave.SpatialUtils
import au.org.ala.spatial.util.GeomMakeValid
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

@Slf4j
class LayerCreation extends SlaveProcess {

    void start() {
        String uploadId = task.input.uploadId
        String layerId = task.input.layerId

        // get layer info
        Map layer = getLayer(layerId)

        if (layer == null) {
            task.err.put(String.valueOf(System.currentTimeMillis()), "layer not found for id: " + layerId)
            return
        }

        //upload shp into layersdb in a table with name layer.id
        String dir = grailsApplication.config.data.dir
        File shpUploaded = new File(dir + "/uploads/" + uploadId + "/" + uploadId + ".shp")
        File bilUploaded = new File(dir + "/uploads/" + uploadId + "/" + uploadId + ".bil")
        File txtUploaded = new File(dir + "/uploads/" + uploadId + "/" + uploadId + ".txt")
        File diva = new File(dir + "/layer/" + layer.name + ".grd")

        if (bilUploaded.exists()) {
            String srcPath = bilUploaded.getPath()
            String outPath = dir + "/layer/" + layer.name
            FileUtils.forceMkdir(new File(outPath).getParentFile())

            //reproject to 4326
            String[] cmd = [
                    grailsApplication.config.gdal.dir + "/gdalwarp",
                    "-t_srs", "EPSG:4326"
                    , srcPath
                    , outPath + "_tmp.bil"]
            task.message = 'reprojecting shp'
            try {
                runCmd(cmd, true)
            } catch (Exception e) {
                log.error("error running gdalwarp (1)", e)
            }
            cmd = [grailsApplication.config.gdal.dir + "/gdalinfo",
                   "-hist"
                   , outPath + "_tmp.bil"]
            try {
                runCmd(cmd, true)
            } catch (Exception e) {
                log.error("error running gdalwarp (2)", e)
            }
            // make .hdr
            cmd = [grailsApplication.config.gdal.dir + "/gdal_translate",
                   "-of", "Ehdr"
                   , outPath + "_tmp.bil"
                   , outPath + ".bil"]
            try {
                runCmd(cmd, true)
            } catch (Exception e) {
                log.error("error running gdalwarp (3)", e)
            }
            // delete tmp 
            new File(outPath + "_tmp.bil").delete()

            task.message = 'bil > diva'
            Bil2diva.bil2diva(outPath, outPath, layer.environmentalvalueunits.toString())

            if ("Contextual".equalsIgnoreCase(layer.type.toString())) {
                task.message = "process grid file to shapes"

                FileUtils.copyFile(txtUploaded, new File(dir + "/layer/" + layer.name + ".txt"))
                GridClassBuilder.buildFromGrid(dir + "/layer/" + layer.name)

                //replace grd/gri with polygon grid
                if (new File(dir + "/layer/" + layer.name + "/polygon.grd").exists()) {
                    if (new File(dir + "/layer/" + layer.name + ".grd").exists()) new File(dir + "/layer/" + layer.name + ".grd").delete()
                    FileUtils.moveFile(new File(dir + "/layer/" + layer.name + "/polygon.grd"), new File(dir + "/layer/" + layer.name + ".grd"))
                    if (new File(dir + "/layer/" + layer.name + ".gri").exists()) new File(dir + "/layer/" + layer.name + ".gri").delete()
                    FileUtils.moveFile(new File(dir + "/layer/" + layer.name + "/polygon.gri"), new File(dir + "/layer/" + layer.name + ".gri"))
                }
                if (new File(dir + "/layer/" + layer.name + "/polygons.sld").exists()) {
                    if (new File(dir + "/layer/" + layer.name + ".sld").exists()) new File(dir + "/layer/" + layer.name + ".sld").delete()
                    FileUtils.moveFile(new File(dir + "/layer/" + layer.name + "/polygons.sld"), new File(dir + "/layer/" + layer.name + ".sld"))
                    if (new File(dir + "/layer/" + layer.name + ".prj").exists()) new File(dir + "/layer/" + layer.name + ".prj").delete()
                    FileUtils.moveFile(new File(dir + "/layer/" + layer.name + "/polygons.prj"), new File(dir + "/layer/" + layer.name + ".prj"))

                    //delete the now empty directory
                    new File(dir + "/layer/" + layer.name).delete()
                }

                addOutput('layers', "/layer/" + layer.name + '.grd')
                addOutput('layers', "/layer/" + layer.name + '.gri')
                addOutput('layers', "/layer/" + layer.name + '.bil')
                addOutput('layers', "/layer/" + layer.name + '.hdr')

                //bil may have changed
                Diva2bil.diva2bil(outPath, outPath)

                task.message = ""
            } else {
                addOutput('layers', "/layer/" + layer.name + '.grd')
                addOutput('layers', "/layer/" + layer.name + '.gri')
                GridLegend.generateGridLegend(diva.getPath().replace('.grd', ''), outPath, 1, false)
            }

            addOutput('layers', "/layer/" + layer.name + '.sld')

            //bil 2 geotiff (?)
            task.message = 'bil > geotiff'
            try {
                SpatialUtils.toGeotiff(grailsApplication.config.gdal.dir, outPath + ".bil")
            } catch (Exception e) {
                log.error("error making geotiff", e)
            }

            addOutput('layers', "/layer/" + layer.name + '.tif')
            addOutput('layers', "/layer/" + layer.name + '.prj')

        }

        if (!bilUploaded.exists() && "Contextual".equalsIgnoreCase(layer.type.toString())) {
            if (uploadId != null && shpUploaded.exists()) {
                String newName = '/layer/' + layer.name
                File dst = new File(dir + newName)
                dst.getParentFile().mkdirs()

                task.message = 'ensuring shapefile is valid'
                GeomMakeValid.makeValidShapefile(shpUploaded.getPath(), dst.getPath() + ".shp")

                task.message = 'moving files'
                String[] cmd = [grailsApplication.config.gdal.dir + '/ogrinfo',
                                dst.getPath() + ".shp", "-sql", "CREATE SPATIAL INDEX ON " + layer.name]
                task.message = 'shp spatial index'
                try {
                    runCmd(cmd, true)
                } catch (Exception e) {
                    log.error("error running shp spatial index", e)
                }

                addOutput('layers', newName)
            }
        }

        //delete from uploads dir if master service is remote
        if (!grailsApplication.config.service.enable.toBoolean()) {
            FileUtils.deleteDirectory(new File(dir + "/uploads/" + uploadId + "/"))
        }
        addOutput("process", "Thumbnails " + ([] as JSON))

    }
}