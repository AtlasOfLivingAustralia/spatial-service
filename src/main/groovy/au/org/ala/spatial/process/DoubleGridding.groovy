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

import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.layers.DoubleGriddingGenerator
import au.org.ala.spatial.util.Records
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.intersect.SimpleRegion
import grails.converters.JSON
import groovy.util.logging.Slf4j

import java.text.SimpleDateFormat

@Slf4j
class DoubleGridding extends SlaveProcess {

    void start() {
        //area to restrict (only interested in area.q part)
        List<AreaInput> areas = JSON.parse(getInput('area').toString()) as List<AreaInput>
        RegionEnvelope regionEnvelope = processArea(areas[0])

        //number of target species
        SpeciesInput species = JSON.parse(getInput('species').toString()) as SpeciesInput

        SpeciesInput speciesArea = getSpeciesArea(species, areas)

        new File(getTaskPath()).mkdirs()

        def primaryGridCellSize = getInput('primaryGridCellSize').toString().toDouble()
        def secondaryGridCellSize = getInput('secondaryGridCellSize').toString().toDouble()

        def yearSize = getInput('yearSize').toString().toInteger()

        double[] bbox = new double[4]
        bbox[0] = -180
        bbox[1] = -90
        bbox[2] = 180
        bbox[3] = 90

        String envelopeFile = getTaskPath() + "envelope_" + taskWrapper.id
        Grid envelopeGrid = null
        if (regionEnvelope.envelope != null) {
            String[] types = new String[regionEnvelope.envelope.length]
            String[] fieldIds = new String[regionEnvelope.envelope.length]
            for (int i = 0; i < regionEnvelope.envelope.length; i++) {
                types[i] = "e"
                fieldIds[i] = regionEnvelope.envelope.layername
            }
            gridCutterService.makeEnvelope(envelopeFile, "0.01", regionEnvelope.envelope, Long.MAX_VALUE, types, fieldIds)
            envelopeGrid = new Grid(envelopeFile)
        }


        def years = []
        if (facetOccurenceCount('year', speciesArea).size() == 0) {
            taskLog("Error: No occurrences in that area!")
            throw new Exception("Error: No occurrences in that area!")
        }
        for (def result in facetOccurenceCount('year', speciesArea)[0].fieldResult) {
            years.push(Integer.parseInt(result.label))
        }
        years.sort()
        if (years.size() == 0) {
            taskLog("ERROR: No year values found")
            return
        }
        def minYear = years[0]
        def maxYear = years[years.size() - 1]

        Records records = getRecords(speciesArea.bs.toString(), speciesArea.q.toString(), bbox, null, null)

        //update bbox with spatial extent of records
        double minx = 180, miny = 90, maxx = -180, maxy = -90
        for (int i = 0; i < records.getRecordsSize(); i++) {
            minx = Math.min(minx, records.getLongitude(i))
            maxx = Math.max(maxx, records.getLongitude(i))
            miny = Math.min(miny, records.getLatitude(i))
            maxy = Math.max(maxy, records.getLatitude(i))
        }
        minx -= secondaryGridCellSize
        miny -= secondaryGridCellSize
        maxx += secondaryGridCellSize
        maxy += secondaryGridCellSize
        bbox[0] = minx
        bbox[2] = maxx
        bbox[1] = miny
        bbox[3] = maxy

        //test restrictions
        int occurrenceCount = records.getRecordsSize()
        int boundingboxcellcount = (int) ((bbox[2] - bbox[0]) * (bbox[3] - bbox[1]) / (secondaryGridCellSize * secondaryGridCellSize))
        String error = null
        if (occurrenceCount == 0) {
            error = "No occurrences found"
        }
        if (error != null) {
            //error
            return
        }

        writeMetadata(getTaskPath() + "sxs_metadata.html", "Sites by Species", records, bbox, null, "" /*TODO: area_km*/, species.name.toString(), secondaryGridCellSize, primaryGridCellSize)
        addOutput("metadata", "sxs_metadata.html", true)

        DoubleGriddingGenerator sbs = new DoubleGriddingGenerator(secondaryGridCellSize, primaryGridCellSize, bbox, minYear as int, maxYear as int, yearSize)

        int[] counts = sbs.write(records, getTaskPath(), regionEnvelope.region, envelopeGrid)

        addOutput("files", "SitesBySpecies.csv", true)

    }

    def getRecords(String bs, String q, double[] bbox, String filename, SimpleRegion region) {
        new Records(bs, q, bbox, filename, region)
    }

    void writeMetadata(String filename, String title, Records records, double[] bbox, int[] counts, String addAreaSqKm, String speciesName, Double secondaryGridCellSize, Double primaryGridCellSize) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss")
        FileWriter fw = new FileWriter(filename)
        fw.append("<html><h1>").append(title).append("</h1>")
        fw.append("<table>")
        fw.append("<tr><td>Date/time " + sdf.format(new Date()) + "</td></tr>")
        fw.append("<tr><td>Model reference number: " + taskWrapper.id + "</td></tr>")
        fw.append("<tr><td>Species selection " + speciesName + "</td></tr>")
        fw.append("<tr><td>Primary grid size (decimal degrees) " + primaryGridCellSize + "</td></tr>")
        fw.append("<tr><td>Secondary grid size (decimal degrees) " + secondaryGridCellSize + "</td></tr>")

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


        fw.append("</table>")
        fw.append("</html>")
        fw.close()
    }

}
