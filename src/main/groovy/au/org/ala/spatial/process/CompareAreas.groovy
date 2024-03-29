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

import au.org.ala.spatial.SpatialObjects
import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.dto.SpeciesInput
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import grails.converters.JSON
import groovy.util.logging.Slf4j

//@CompileStatic
@Slf4j
class CompareAreas extends SlaveProcess {

    void start() {

        List<AreaInput> areas = JSON.parse(getInput('area').toString()).collect { it as AreaInput } as List<AreaInput>
        SpeciesInput species = JSON.parse(getInput('species').toString()) as SpeciesInput

        // replace 'all areas in a layer' with the actual areas
        List<AreaInput> filteredAreas = []
        List<SpatialObjects> list = []
        String fid = null
        for (area in areas) {
            if (area.q?.matches('cl[0-9]+:\\*')) {
                fid = area.q.substring(0, area.q.indexOf(':'))
                list = getObjects(fid)
            } else {
                filteredAreas.add(area)
            }
        }
        for (SpatialObjects so : list) {
            AreaInput ai = new AreaInput()
            ai.name = so.name
            ai.q = fid + ":\"" + ai.name + '"'
            filteredAreas.add(ai)
        }

        //get info for each area
        for (area in filteredAreas) {
            taskLog(area.name + ": define area")
            area.speciesArea = getSpeciesArea(species, [area])
            taskLog(area.name + ": count occurrences")
            area.numberOfOccurrences = occurrenceCount(area.speciesArea)
            taskLog(area.name + ": list species")
            area.speciesList = new CSVReader(new StringReader(getSpeciesList(area.speciesArea))).readAll()
        }

        // TODO: this should compare more than 2 areas.
        makeFiles(species, areas[0], areas[1])
    }

    def makeFiles(SpeciesInput species, AreaInput area1, AreaInput area2) {
        def speciesOnly1 = []
        def speciesOnly2 = []
        def speciesBoth = []

        taskLog("sort species lists")
        if (!area1.speciesList?.size()) {
            taskLog("no species in area: " + area1.name)
            return
        }
        if (!area2.speciesList?.size()) {
            taskLog("no species in area: " + area2.name)
            return
        }
        def header = appendStrings(area1.speciesList[0], "Number of occurrences - " + area1.name, "Number of occurrences - " + area2.name)
        def sorted1 = area1.speciesList.subList(1, area1.speciesList.size())
        sorted1.sort(new StringArrayComparator())
        def sorted2 = area2.speciesList.subList(1, area2.speciesList.size())
        sorted2.sort(new StringArrayComparator())

        def i1 = 0
        def i2 = 0

        taskLog("compare species")
        File tmpFile = new File(taskPath + '/comparison.tmp.csv')
        def csv = new CSVWriter(new FileWriter(tmpFile))
        csv.writeNext(header)
        while (i1 < sorted1.size() && i2 < sorted2.size()) {
            if (i1 >= sorted1.size() || sorted1[i1][0] > sorted2[i2][0]) {
                speciesOnly2.push(sorted2[i2][0])
                csv.writeNext(appendStrings(sorted2[i2], "0", sorted2[i2][11]))
                i2++
            } else if (i2 >= sorted2.size() || sorted1[i1][0] < sorted2[i2][0]) {
                speciesOnly1.push(sorted1[i1][0])
                csv.writeNext(appendStrings(sorted1[i1], sorted1[i1][11], "0"))
                i1++
            } else if (sorted1[i1][0] == sorted2[i2][0]) {
                speciesBoth.push(sorted1[i1][0])
                csv.writeNext(appendStrings(sorted2[i2], sorted1[i1][11], sorted2[i2][11]))
                i1++
                i2++
            }
        }
        csv.close()

        File csvFile = new File(taskPath + '/comparison.csv')
        def csvTop = new CSVWriter(new FileWriter(csvFile))
        csvTop.writeNext(["Species", "Area name", "Sq km", "Occurrences", "Species"] as String[])
        csvTop.writeNext([species.name, area1.name, area1.area_km, area1.numberOfOccurrences, sorted1.size()].collect {
            it.toString()
        } as String[])// adjust speciesList.size for header row
        csvTop.writeNext([species.name, area2.name, area2.area_km, area2.numberOfOccurrences, sorted2.size()].collect {
            it.toString()
        } as String[])
        csvTop.writeNext([""] as String[])
        csvTop.writeNext(["Species found only in ${area1.name}", sorted1.size()].collect {
            it.toString()
        } as String[])
        csvTop.writeNext(["Species found only in ${area2.name}", sorted2.size()].collect {
            it.toString()
        } as String[])
        csvTop.writeNext(["Species found in both areas", speciesBoth.size()].collect {
            it.toString()
        } as String[])
        csvTop.writeNext([""] as String[])
        csvTop.writeNext([""] as String[])
        csvTop.close()

        // insert comparison summary at the beginning of comparison.csv
        csvFile.append(tmpFile.text)
        addOutput("csv", "comparison.csv", true)

        taskLog("build summary")
        def html = "<div><div>Report for: ${species.name}<br>${area1.name}<br>${area2.name}</div><br>" +
                "<table name='areaComparisonResult'><tbody><tr><td>Area name</td><td>Sq km</td><td>Occurrences</td><td>Species</td></tr>" +
                "<tr><td>${area1.name}</td><td>${area1.area_km}</td><td>${area1.numberOfOccurrences}</td><td>${sorted1.size()}</td></tr>" +
                "<tr><td>${area2.name}</td><td>${area2.area_km}</td><td>${area2.numberOfOccurrences}</td><td>${sorted2.size()}</td></tr>" +
                "<tr><td>&nbsp;</td></tr><tr><td>Species found only in ${area1.name}</td><td>${speciesOnly1.size()}</td></tr>" +
                "<tr><td>Species found only in ${area2.name}</td><td>${speciesOnly2.size()}</td></tr>" +
                "<tr><td>Species found in both areas</td><td>${speciesBoth.size()}</td></tr></tbody></table></div>"

        new File(taskPath + '/comparison.html').write(html)
        addOutput("metadata", "comparison.html", true)
    }

    private static String[] appendStrings(String[] list, String append1, String append2) {
        // replace the last column in list (number of records) with append1 (number of records in area 1)

        String[] join = new String[list.length + 1]
        System.arraycopy(list, 0, join, 0, list.length)

        join[list.length - 1] = append1
        join[list.length] = append2
        return join
    }

//@CompileStatic
class StringArrayComparator implements Comparator<String[]> {
        @Override
        int compare(String[] o1, String[] o2) {
            if ((o1.length == 0 || o1[0] == null) && (o2.length == 0 || o2[0] == null)) {
                return 0
            } else if (o1.length == 0 || o1[0] == null) {
                return 1
            } else if (o2.length == 0 || o2[0] == null) {
                return -1
            } else {
                return o1[0] <=> o2[0]
            }
        }
    }
}
