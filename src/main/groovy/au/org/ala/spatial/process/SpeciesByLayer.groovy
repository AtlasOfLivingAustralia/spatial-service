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

import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.Util
import au.org.ala.spatial.intersect.Grid
import au.org.ala.spatial.util.SpatialUtils
import com.opencsv.CSVWriter
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.grails.web.json.JSONObject

import java.nio.ByteBuffer
import java.nio.ByteOrder

//@CompileStatic
@Slf4j
class SpeciesByLayer extends SlaveProcess {

    void start() {

        SpeciesInput species = JSON.parse(getInput('species').toString()) as SpeciesInput
        List<String> fields = getInput('layer').toString().split(',')

        HashMap<String, Integer> speciesMap = new HashMap()

        def field = getField(fields[0])
        def layer = getLayer(field.spid)

        def speciesNames = []

        speciesNames.push(species.name)

        List<String> items = new ArrayList()
        List<SpeciesByLayerCount> counts = new ArrayList<SpeciesByLayerCount>()

        if (field.type != 'e') {
            // indexed only fields - contextual or grid as contextual layers

            def fieldObjects = getObjects(fields[0])

            HashSet<String> occurrenceIds = new HashSet()

            def firstSpecies = true

            int n = 0
            for (def fieldObject : fieldObjects) {
                def areaName = fieldObject.name

                SpeciesByLayerCount count = new SpeciesByLayerCount(fieldObject.area_km)

                n = n + 1
                taskLog("Getting species for \"" + fieldObject.name + "\" (area " + n + " of " + fieldObjects.size() + ")")

                def fq = fields[0] + ":\"" + fieldObject.name + "\""

                taskLog(fq)

                count.species = facetCount('names_and_lsid', species, fq)
                count.occurrences = occurrenceCount(species, fq)

                // added encoding fix
                items.add(areaName)
                counts.add(count)
            }
        } else {
            // indexed only - environmental fields

            String url = species.bs + "/chart?x=" + fields[0] + "&q=" + joinSpeciesQ(species.q)
            String response = Util.getUrl(url)


            def fieldObjects = ((List) ((JSONObject) JSON.parse(response))["data"])[0]["data"]

            def firstSpecies = true

            def min = Double.MAX_VALUE, max = Double.MIN_VALUE
            for (Map fieldObject : (fieldObjects as List<Map>)) {
                String areaName = fieldObject.label
                if (areaName) {
                    def values = areaName.split('-')
                    values.each {
                        try {
                            def n = Double.parseDouble(it)
                            if (min > n) min = n
                            if (max < n) max = n
                        } catch (ignored) {
                        }
                    }
                }
            }

            // number of environmental bins
            double steps = 16

            double step = (max - min) / steps
            for (int n = 0; n < steps; n++) {
                // no area_km
                SpeciesByLayerCount count = new SpeciesByLayerCount(-1)

                taskWrapper.task.message = "Getting species for area " + (n + 1) + " of " + steps

                float lowerBound = (float) (min + n * step)
                float upperBound = (float) (n == steps - 1 ? max : (min + (n + 1) * step))

                // for usage 'value < upperBoundFile'
                def upperBoundFile = n == steps - 1 ? max * 1.1 : (min + (n + 1) * step)
                // for usage 'value < upperBoundQuery'. For the last value use '*' to include the upper value
                def upperBoundQuery = n == steps - 1 ? "*" : (min + (n + 1) * step)

                // SOLR range query; lowerBound <= value < upperBoundQuery
                def fq = fields[0] + ":[" + lowerBound + " TO " + upperBoundQuery + "}"
                if (n > 0) {
                    fq += " AND -" + fields[0] + ':' + lowerBound
                }
                count.species = facetCount('names_and_lsid', species, fq)
                count.occurrences = occurrenceCount(species, fq)
                count.area = envelopeArea(layer.name, lowerBound, upperBound)

                def areaName = lowerBound + " " + upperBound

                items.add(areaName)
                counts.add(count)
            }
        }

        // produce csv from counts
        File csvFile = new File(getTaskPath() + "/species_by_layer.csv")
        CSVWriter writer = new CSVWriter(new FileWriter(csvFile))

        //info
        writer.writeNext(['species', speciesNames.join(' AND ')] as String[])
        writer.writeNext(['layer', field.name] as String[])

        //header
        if (field.type != 'e') {
            writer.writeNext( ['area name', 'area (sq km)', 'number of species', 'number of occurrences'] as String[])
        } else {
            writer.writeNext( ['lower bound', 'upper bound', 'number of species', 'number of occurrences'] as String[])
        }

        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i)
            SpeciesByLayerCount count = counts.get(i)

            if (field.type != 'e') {
                writer.writeNext([item, count.area, count.species, count.occurrences] as String[])
            } else {
                def bounds = item.split(' ')
                writer.writeNext([bounds[0], bounds[1], count.species, count.occurrences] as String[])
            }
        }

        writer.flush()
        writer.close()

        addOutput("csv", csvFile.name, true)
    }

    double envelopeArea(String layerName, float minBound, float maxBound) {
        Grid grid = new Grid(spatialConfig.data.dir.toString() + '/layer/' + layerName)

        int bufferSize = 1024 * 1024
        float areaSqKm = 0

        Grid g = Grid.getLoadedGrid(grid.filename)
        if (g != null && g.grid_data != null) {
            for (int i = 0; i < g.grid_data.length; i++) {
                areaSqKm += areaOf(g, g.grid_data[i], i, minBound, maxBound)
            }
        } else {
            int length = grid.nrows * grid.ncols
            RandomAccessFile afile = null
            File f2 = new File(grid.filename + ".GRI")

            try {
                if (!f2.exists()) {
                    afile = new RandomAccessFile(grid.filename + ".gri", "r")
                } else {
                    afile = new RandomAccessFile(grid.filename + ".GRI", "r")
                }

                byte[] b = new byte[bufferSize]
                int i = 0
                int max = 0

                int len
                while ((len = afile.read(b)) > 0) {
                    ByteBuffer bb = ByteBuffer.wrap(b)
                    if (grid.byteorderLSB) {
                        bb.order(ByteOrder.LITTLE_ENDIAN)
                    }

                    if (grid.datatype.equalsIgnoreCase("UBYTE")) {
                        max += len

                        for (max = Math.min(max, length); i < max; ++i) {
                            float value = (float) bb.get()
                            if (value < 0.0F) {
                                value += 256.0F
                            }
                            areaSqKm += areaOf(grid, value, i, minBound, maxBound)
                        }
                    } else if (grid.datatype.equalsIgnoreCase("BYTE")) {
                        max += len

                        for (max = Math.min(max, length); i < max; ++i) {
                            areaSqKm += areaOf(grid, (float) bb.get(), i, minBound, maxBound)
                        }
                    } else if (grid.datatype.equalsIgnoreCase("SHORT")) {
                        max += len / 2

                        for (max = Math.min(max, length); i < max; ++i) {
                            areaSqKm += areaOf(grid, (float) bb.getShort(), i, minBound, maxBound)
                        }
                    } else if (grid.datatype.equalsIgnoreCase("INT")) {
                        max += len / 4

                        for (max = Math.min(max, length); i < max; ++i) {
                            areaSqKm += areaOf(grid, (float) bb.getInt(), i, minBound, maxBound)
                        }
                    } else if (grid.datatype.equalsIgnoreCase("LONG")) {
                        max += len / 8

                        for (max = Math.min(max, length); i < max; ++i) {
                            areaSqKm += areaOf(grid, (float) bb.getLong(), i, minBound, maxBound)
                        }
                    } else if (grid.datatype.equalsIgnoreCase("FLOAT")) {
                        max += len / 4

                        for (max = Math.min(max, length); i < max; ++i) {
                            areaSqKm += areaOf(grid, (float) bb.getFloat(), i, minBound, maxBound)
                        }
                    } else if (grid.datatype.equalsIgnoreCase("DOUBLE")) {
                        max += len / 8

                        for (max = Math.min(max, length); i < max; ++i) {
                            areaSqKm += areaOf(grid, (float) bb.getDouble(), i, minBound, maxBound )
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        return areaSqKm
    }

    int cellsInRow = 0

    double areaOf(Grid grid, float value, int cellIdx, float minValue, float maxValue) {
        if (value >= minValue && value < maxValue && value != grid.nodatavalue) {
            cellsInRow++
        }

        // end of row; return area value
        if ((cellIdx + 1) % grid.ncols == 0) {
            // latitude at the middle of the current row
            double latitude = Math.floor(cellIdx / grid.ncols as double) * grid.yres + grid.yres / 2.0 + grid.ymax

            // area of a cell at this grid resolution and latitude * number of cells to count
            double area = SpatialUtils.cellArea(grid.yres, latitude) * cellsInRow

            // reset number of cells
            cellsInRow = 0

            return area
        } else {
            return 0
        }
    }
}

class SpeciesByLayerCount {
    int species = 0
    int occurrences = 0
    double area = 0

    SpeciesByLayerCount(double area) {
        this.area = area
    }
}
