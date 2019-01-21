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

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import grails.converters.JSON
import groovy.util.logging.Slf4j

@Slf4j
class SpeciesByLayer extends SlaveProcess {

    void start() {

        def speciesList = JSON.parse(task.input.species.toString())
        def fields = JSON.parse(task.input.layer.toString())

        HashMap<String, Integer> speciesMap = new HashMap()

        def field = getField(fields[0])

        def fieldObjects = getObjects(fields[0])
        HashMap<String, SpeciesByLayerCount> counts = new HashMap();

        // added encoding fix
        for (def fieldObject : fieldObjects) {
            counts.put(new String(fieldObject.name.getBytes("US-ASCII"), "UTF-8"), new SpeciesByLayerCount(fieldObject.area_km))
        }

        HashSet<String> occurrenceIds = new HashSet();

        def firstSpecies = true;
        def speciesNames = []
        for (def species : speciesList) {
            speciesNames.push(species.name)

            // csv contains a header and the columns [id, names_and_lsid, field value]
            def stream = getOccurrencesCsv(species.q + "&fq=" + fields[0] + ":*", species.bs, 'id,names_and_lsid,' + fields[0])
            def reader = new CSVReader(new InputStreamReader(stream))

            // ignore csv header
            def row = reader.readNext()

            while ((row = reader.readNext()) != null) {
                // only read each occurrence once
                if (row.length == 3 && (firstSpecies || !occurrenceIds.contains(row[0]))) {
                    SpeciesByLayerCount count = counts.get(new String(row[2].getBytes("US-ASCII"), "UTF-8"))

                    // ignore species without a valid field value
                    if (count != null) {
                        count.occurrences++

                        Integer speciesId = speciesMap.get(row[1]);
                        if (speciesId == null) {
                            speciesId = speciesMap.size()
                            speciesMap.put(row[1], speciesId)
                        }
                        count.species.set(speciesId)
                    }

                    occurrenceIds.add(row[0])
                }
            }

            try {
                stream.close()
            } catch (Exception e) {
                e.printStackTrace()
            }

            firstSpecies = false;
        }

        // produce csv from counts
        File csvFile = new File(getTaskPath() + "/species_by_layer.csv")
        CSVWriter writer = new CSVWriter(new FileWriter(csvFile))

        //info
        writer.writeNext((String[]) ['species', speciesNames.join(' AND ')])
        writer.writeNext((String[]) ['layer', field.name])

        //header
        writer.writeNext((String[]) ['area name', 'area (sq km)', 'number of species', 'number of occurrences'])

        for (def entry : counts.entrySet()) {
            writer.writeNext([entry.key, entry.value.area, entry.value.species.cardinality(), entry.value.occurrences] as String[])
        }

        writer.flush()
        writer.close()

        addOutput("csv", csvFile.name, true)
    }

    private class SpeciesByLayerCount {
        BitSet species = new BitSet()

        int occurrences = 0
        double area = 0

        SpeciesByLayerCount(double area) {
            this.area = area
        }
    }
}
