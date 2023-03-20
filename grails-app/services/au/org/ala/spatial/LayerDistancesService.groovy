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

package au.org.ala.spatial
//@CompileStatic
class LayerDistancesService {


    FieldService fieldService
    LayerService layerService

    SpatialConfig spatialConfig

    /*
     * Produces layer distances CSV with type= 'name' or 'displayname' layer table column labels
     */
    String makeCSV(String type) {
        def map = loadDistances()

        Set<String> layerSet = [] as Set
        for (String k : map.keySet()) {
            String [] ks = k.split(" ")
            layerSet.add(ks[0])
            layerSet.add(ks[1])
        }
        List<String> layersOrdered = layerSet as List<String>
        def layerNames = new String[layersOrdered.size()]

        //match against layers
        def layerMatch = (0..layerSet.size()).collect { null }

        List<Fields> fields = fieldService.getFields(false)
        List<Layers> layers = layerService.getLayers()
        layersOrdered.eachWithIndex { l, idx ->
            fields.each { f ->
                if (f.getId().equalsIgnoreCase(l)) {
                    layers.each { la ->
                        if (String.valueOf(f.getSpid()) == String.valueOf(la.getId())) {
                            layerMatch.set(idx, la)
                            if (type == "name") {
                                layerNames[idx] = la.getName()
                            } else {
                                layerNames[idx] = la.getDisplayname()
                            }

                        }
                    }
                }
            }
        }

        //output lower association matrix
        def sb = new StringBuilder()
        layersOrdered.eachWithIndex { l, idx ->
            if (idx > 0) {
                sb.append(layerNames[idx])
            }
            sb.append(",")
            int size = (idx == 0) ? layersOrdered.size() - 1 : idx
            for (int j = 0; j < size; j++) {
                if (idx == 0) {
                    sb.append(layerNames[j])
                } else {
                    def key = l < layersOrdered[j] ? l + " " + layersOrdered[j] : layersOrdered[j] + " " + l
                    if (key != null && map.get(key) != null) {
                        sb.append(map.get(key))
                    }
                }

                if (j < size - 1) {
                    sb.append(",")
                }
            }
            sb.append("\n")
        }

        sb.toString()
    }

    def loadDistances() {
        def map = [:]

        def br = null
        def path = spatialConfig.data.dir + "/public/layerDistances.properties"
        try {
            def file = new File(path)
            if (!file.exists()) {
                file.getParentFile().mkdirs()

                //create empty layerDistances.properties file
                file.write('')
            }

            br = new BufferedReader(new FileReader(file))

            String line
            while ((line = br.readLine()) != null) {
                if (line.length() > 0) {
                    String[] keyvalue = line.split("=")

                    try {
                        map.put(keyvalue[0], Double.parseDouble(keyvalue[1]))
                    } catch (ignored) {
                        log.error 'failed parsing double from public/layerDistances.properties'
                    }
                }
            }
        } catch (err) {
            log.error 'failed import of public/layerDistances.properties', err
        } finally {
            if (br != null) {
                br.close()
            }

        }

        return map
    }

}
