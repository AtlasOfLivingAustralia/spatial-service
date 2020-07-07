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

import au.com.bytecode.opencsv.CSVWriter
import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

@Slf4j
class SpeciesByLayer extends SlaveProcess {

    void start() {

        def species = JSON.parse(task.input.species.toString())
        def fields = JSON.parse(task.input.layer.toString())

        HashMap<String, Integer> speciesMap = new HashMap()

        def field = getField(fields[0])
        def layer = getLayer(field.spid)

        def speciesNames = []

        speciesNames.push(species.name)

        HashMap<String, SpeciesByLayerCount> counts = new HashMap();

        if (field.type != 'e') {
            // indexed only fields - contextual or grid as contextual layers

            def fieldObjects = getObjects(fields[0])

            HashSet<String> occurrenceIds = new HashSet();

            def firstSpecies = true;

            int n = 0
            for (def fieldObject : fieldObjects) {
                def areaName = new String(fieldObject.name.getBytes("US-ASCII"), "UTF-8")

                def count = new SpeciesByLayerCount(fieldObject.area_km)

                n = n + 1
                taskLog("Getting species for \"" + fieldObject.name + "\" (area " + n + " of " + fieldObjects.size() + ")")

                def fq = fields[0] + ":" + fieldObject.name

                taskLog(fq)

                count.species = facetCount('names_and_lsid', species, fq)
                count.occurrences = occurrenceCount(species, fq)

                // added encoding fix
                counts.put(areaName, count)
            }
        } else {
            // indexed only - environmental fields

            String url = species.bs + "/chart?x=" + fields[0] + "&q=" + species.q
            String response = Util.getUrl(url)

            JSONParser jp = new JSONParser()
            def fieldObjects = ((JSONObject) jp.parse(response)).getAt("data").get(0).getAt("data")

            def firstSpecies = true

            def min = Double.MAX_VALUE, max = Double.MIN_VALUE
            for (def fieldObject : fieldObjects) {
                def areaName = fieldObject.label
                if (areaName) {
                    def values = areaName.split('-')
                    values.each {
                        try {
                            def n = Double.parseDouble(it)
                            if (min > n) min = n
                            if (max < n) max = n
                        } catch (err) {
                        }
                    }
                }
            }

            double steps = 30
            double step = (max - min) / steps
            for (int n = 0; n < steps; n++) {
                // no area_km
                def count = new SpeciesByLayerCount(-1)

                task.message = "Getting species for area " + (n + 1) + " of " + steps

                def lowerBound = (min + n * step)
                def upperBound = n == steps - 1 ? max : (min + (n + 1) * step)

                def fq = fields[0] + ":[" + lowerBound + " TO " + upperBound + "]"
                if (n > 0) {
                    fq += " AND -" + fields[0] + ":" + lowerBound
                }
                count.species = facetCount('names_and_lsid', species, fq)
                count.occurrences = occurrenceCount(species, fq)

                def areaName = lowerBound + " " + upperBound

                counts.put(areaName, count)
            }
        }

        // produce csv from counts
        File csvFile = new File(getTaskPath() + "/species_by_layer.csv")
        CSVWriter writer = new CSVWriter(new FileWriter(csvFile))

        //info
        writer.writeNext((String[]) ['species', speciesNames.join(' AND ')])
        writer.writeNext((String[]) ['layer', field.name])

        //header
        if (field.type != 'e') {
            writer.writeNext((String[]) ['area name', 'area (sq km)', 'number of species', 'number of occurrences'])
        } else {
            writer.writeNext((String[]) ['lower bound', 'upper bound', 'number of species', 'number of occurrences'])
        }

        for (def entry : counts.entrySet()) {
            if (field.type != 'e') {
                writer.writeNext([entry.key, entry.value.area, entry.value.species, entry.value.occurrences] as String[])
            } else {
                def bounds = entry.key.split(' ')
                writer.writeNext([bounds[0], bounds[1], entry.value.species, entry.value.occurrences] as String[])
            }
        }

        writer.flush()
        writer.close()

        addOutput("csv", csvFile.name, true)
    }

    private class SpeciesByLayerCount {
        int species = 0
        int occurrences = 0
        double area = 0

        SpeciesByLayerCount(double area) {
            this.area = area
        }
    }
}
