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

package au.org.ala.layers

import grails.util.Holders
import org.apache.commons.io.FileUtils

class LayerDistancesService {


    def fieldDao
    def layerDao

    /*
     * Produces layer distances CSV with type= 'name' or 'displayname' layer table column labels
     */
    def makeCSV(String type) {
        def map = loadDistances()

        def layerSet = [] as Set
        for (String k : map.keySet()) {
            def ks = k.split(" ")
            layerSet.add(ks[0])
            layerSet.add(ks[1])
        }
        def layersOrdered = layerSet.toList()
        def layerNames = new String[layersOrdered.size()]

        //match against layers
        def layerMatch = (0..layerSet.size()).collect { null }

        def fields = fieldDao.getFields()
        def layers = layerDao.getLayers()
        layersOrdered.eachWithIndex { l, idx ->
            fields.each { f ->
                if (f.getId().equalsIgnoreCase(l)) {
                    layers.each { la ->
                        if (String.valueOf(f.getSpid()).equals(String.valueOf(la.getId()))) {
                            layerMatch.set(idx, la)
                            if (type.equals("name")) {
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
                    def key = (l.compareTo(layersOrdered[j]) < 0) ? l + " " + layersOrdered[j] : layersOrdered[j] + " " + l
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

        return sb.toString()
    }

    def loadDistances() {
        def map = [:]

        def br = null
        def path = Holders.config.data.dir + "/public/layerDistances.properties"
        try {
            def file = new File(path)
            if (!file.exists()) {
                file.getParentFile().mkdirs()

                //create empty layerDistances.properties file
                FileUtils.writeStringToFile(file, "")
            }

            br = new BufferedReader(new FileReader(file))

            def line
            while ((line = br.readLine()) != null) {
                if (line.length() > 0) {
                    String[] keyvalue = line.split("=")

                    try {
                        map.put(keyvalue[0], Double.parseDouble(keyvalue[1]))
                    } catch (err) {
                        log.error 'failed parsing double from public/layerDistances.properties'
                    }
                }
            }
        } catch (err) {
            log.error 'failed import of public/layerDistances.properties', err
        } finally {
            if (br != null) {
                try {
                    br.close()
                } catch (err) {
                    log.error 'failed closing layerDistances.properties'
                }
            }

        }

        return map
    }

}
