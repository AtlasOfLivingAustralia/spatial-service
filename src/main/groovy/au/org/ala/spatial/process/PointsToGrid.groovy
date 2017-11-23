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

import au.org.ala.layers.grid.GridCutter
import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.intersect.SimpleRegion
import au.org.ala.spatial.analysis.layers.OccurrenceDensity
import au.org.ala.spatial.analysis.layers.Records
import au.org.ala.spatial.analysis.layers.SitesBySpecies
import au.org.ala.spatial.analysis.layers.SpeciesDensity
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import java.text.SimpleDateFormat

@Slf4j
class PointsToGrid extends SlaveProcess {

    void start() {

        def resolution = task.input.resolution.toString()

        //area to restrict (only interested in area.q part)
        def area = JSON.parse(task.input.area.toString())
        def (region, envelope) = processArea(area[0])

        //number of target species
        def species = JSON.parse(task.input.species.toString())

        def speciesArea = getSpeciesArea(species, area)

        new File(getTaskPath()).mkdirs()

        def gridCellSize = task.input.gridCellSize.toString().toDouble()
        def movingAverageStr = task.input.movingAverage.toString()
        def movingAverage = movingAverageStr.substring(0, movingAverageStr.indexOf('x')).toInteger()
        def occurrenceDensity = task.input.occurrenceDensity.toString().toBoolean()
        def speciesRichness = task.input.speciesRichness.toString().toBoolean()
        def sitesBySpecies = task.input.sitesBySpecies.toString().toBoolean()

        //moving average check
        if (movingAverage % 2 == 0 || movingAverage <= 0
                || movingAverage >= 16) {
            String msg = "Moving average size " + movingAverage + " is not valid.  Must be odd and between 1 and 15."

            return
        }

        double[] bbox = new double[4]
        bbox[0] = area.bbox[0][0]
        bbox[1] = area.bbox[0][1]
        bbox[2] = area.bbox[0][2]
        bbox[3] = area.bbox[0][3]

        // dump the species data to a file
        task.message = "getting species data"
        Records records = getRecords(speciesArea.bs.toString(), speciesArea.q.toString(), bbox, null, null)

        //update bbox with spatial extent of records
        double minx = 180, miny = 90, maxx = -180, maxy = -90
        for (int i = 0; i < records.getRecordsSize(); i++) {
            minx = Math.min(minx, records.getLongitude(i))
            maxx = Math.max(maxx, records.getLongitude(i))
            miny = Math.min(miny, records.getLatitude(i))
            maxy = Math.max(maxy, records.getLatitude(i))
        }
        minx -= gridCellSize
        miny -= gridCellSize
        maxx += gridCellSize
        maxy += gridCellSize
        bbox[0] = minx
        bbox[2] = maxx
        bbox[1] = miny
        bbox[3] = maxy

        //test restrictions
        int occurrenceCount = records.getRecordsSize()
        int boundingboxcellcount = (int) ((bbox[2] - bbox[0]) * (bbox[3] - bbox[1]) / (gridCellSize * gridCellSize))
        String error = null
        if (occurrenceCount == 0) {
            error = "No occurrences found"
        }
        if (error != null) {
            //error
            return
        }

        String envelopeFile = getTaskPath() + "envelope_" + task.id
        Grid envelopeGrid = null
        if (envelope != null) {
            String [] types = new String[envelope.length]
            String [] fieldIds = new String[envelope.length]
            for (int i=0;i<envelope.length;i++) {
                types[i] = "e"
                fieldIds[i] = envelope.layername
            }
            GridCutter.makeEnvelope(envelopeFile, resolution, envelope, Long.MAX_VALUE, types, fieldIds)
            envelopeGrid = new Grid(envelopeFile)
        }

        if (sitesBySpecies) {
            task.message = "building sites by species matrix for " + records.getSpeciesSize() + " species in " + records.getRecordsSize() + " occurrences"

            SitesBySpecies sbs = new SitesBySpecies(gridCellSize, bbox)
            int[] counts = sbs.write(records, getTaskPath(), region, envelopeGrid)
            writeMetadata(getTaskPath() + "sxs_metadata.html", "Sites by Species", records, bbox, false, false, counts, "" /*TODO: area_km*/, species.name.toString(), gridCellSize, movingAverageStr)
            addOutput("metadata", "sxs_metadata.html")
        }

        if (occurrenceDensity) {
            task.message = "building occurrence density layer"
            OccurrenceDensity od = new OccurrenceDensity(movingAverage, gridCellSize, bbox)
            od.write(records, getTaskPath(), "occurrence_density", 1, true, true)

            //convert .asc to .grd/.gri
            convertAsc(getTaskPath() + "occurrence_density.asc", grailsApplication.config.data.dir + '/layer/' + task.id + "_occurrence_density", true)
            try {
                FileUtils.moveFile(new File(grailsApplication.config.data.dir + '/layer/' + task.id + '_occurrence_density.png'),
                        new File(getTaskPath() + 'occurrence_density.png'))
                FileUtils.moveFile(new File(grailsApplication.config.data.dir + '/layer/' + task.id + '_occurrence_density_legend.png'),
                        new File(getTaskPath() + 'occurrence_density_legend.png'))

                addOutput("files", "occurrence_density.png", true)
                addOutput("files", "occurrence_density_legend.png", true)
            } catch (Exception e) {

            }

            addOutput("layers", "/layer/" + task.id + "_occurrence_density.sld")
            addOutput("layers", "/layer/" + task.id + "_occurrence_density.tif")
            addOutput("files", "occurrence_density.asc", true)

            writeMetadata(getTaskPath() + "odensity_metadata.html", "Occurrence Density", records, bbox, occurrenceDensity, false, null, null, species.name.toString(), gridCellSize, movingAverageStr)
            addOutput("files", "odensity_metadata.html")
            addOutput("files", "occurrence_density.png")
        }

        if (speciesRichness) {
            task.message = "building species richness layer"
            SpeciesDensity sd = new SpeciesDensity(movingAverage, gridCellSize, bbox)
            sd.write(records, getTaskPath(), "species_richness", 1, true, true)

            convertAsc(getTaskPath() + "species_richness.asc", "${grailsApplication.config.data.dir}/layer/${task.id}_species_richness", true)
            try {
                FileUtils.moveFile(new File("${grailsApplication.config.data.dir}/layer/${task.id}_species_richness.png"),
                        new File(getTaskPath() + 'species_richness.png'))
                FileUtils.moveFile(new File("${grailsApplication.config.data.dir}/layer/${task.id}_species_richness_legend.png"),
                        new File(getTaskPath() + 'species_richness_legend.png'))

                addOutput("files", "species_richness.png", true)
                addOutput("files", "species_richness_legend.png", true)
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            }

            addOutput("layers", "/layer/" + task.id + "_species_richness.sld")
            addOutput("layers", "/layer/" + task.id + "_species_richness.tif")
            addOutput("files", "species_richness.asc", true)

            writeMetadata(getTaskPath() + "srichness_metadata.html", "Species Richness", records, bbox, false, speciesRichness, null, null, species.name.toString(), gridCellSize, movingAverageStr)
            addOutput("files", "srichness_metadata.html", true)
        }
    }

    def getRecords(String bs, String q, double[] bbox, String filename, SimpleRegion region) {
        new Records(bs, q, bbox, filename, region)
    }

    void writeMetadata(String filename, String title, Records records, double[] bbox, boolean odensity, boolean sdensity, int[] counts, String addAreaSqKm, String speciesName, Double gridCellSize, String movingAverage) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss")
        FileWriter fw = new FileWriter(filename)
        fw.append("<html><h1>").append(title).append("</h1>")
        fw.append("<table>")
        fw.append("<tr><td>Date/time " + sdf.format(new Date()) + "</td></tr>")
        fw.append("<tr><td>Model reference number: " + task.id + "</td></tr>")
        fw.append("<tr><td>Species selection " + speciesName + "</td></tr>")
        if (!odensity && !sdensity) {
            fw.append("<tr><td>Grid: " + 1 + "x" + 1 + " moving average, resolution " + gridCellSize + " degrees</td></tr>")
        } else {
            fw.append("<tr><td>Grid: " + movingAverage + " moving average, resolution " + gridCellSize + " degrees</td></tr>")
        }
        fw.append("<tr><td>" + records.getSpeciesSize() + " species</td></tr>")
        fw.append("<tr><td>" + records.getRecordsSize() + " occurrences</td></tr>")
        if (counts != null) {
            fw.append("<tr><td>" + counts[0] + " grid cells with an occurrence</td></tr>")
            fw.append("<tr><td>" + counts[1] + " grid cells in the area (both marine and terrestrial)</td></tr>")
        }
        if (addAreaSqKm != null) {
            fw.append("<tr><td>Selected area " + addAreaSqKm + " sqkm</td></tr>")
        }
        fw.append("<tr><td>bounding box of the selected area " + bbox[0] + "," + bbox[1] + "," + bbox[2] + "," + bbox[3] + "</td></tr>")
        if (odensity) {
            fw.append("<tr><td><br>Occurrence Density</td></tr>")
            fw.append("<tr><td><img src='occurrence_density.png' width='300px' height='300px'><img src='occurrence_density_legend.png'></td></tr>")
        }
        if (sdensity) {
            fw.append("<tr><td><br>Species Richness</td></tr>")
            fw.append("<tr><td><img src='species_richness.png' width='300px' height='300px'><img src='species_richness_legend.png'></td></tr>")
        }
        fw.append("</table>")
        fw.append("</html>")
        fw.close()
    }

}
