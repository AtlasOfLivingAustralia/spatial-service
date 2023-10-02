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

import au.org.ala.spatial.Fields
import au.org.ala.spatial.Layers
import au.org.ala.spatial.TabulationGeneratorService
import grails.converters.JSON
import groovy.util.logging.Slf4j

//@CompileStatic
@Slf4j
class LayerDistancesCreate extends SlaveProcess {

    void start() {
        Set all = [] as Set
        List<Fields> fields = getFields()
        List<Layers> layers = getLayers()

        Double[] grdResolutions = spatialConfig.grdResolutions

        //get highest resolution standardized layer files
        //only want valid fields
        taskWrapper.task.message = 'find suitable layers'
        List<Fields> validFields = []
        fields.each { Fields field ->
            for (int i = grdResolutions.length - 1; i >= 0; i--) {
                def path = '/standard_layer/' + grdResolutions[i] + '/' + field.id + '.grd'
                if (new File(path).exists()) {
                    validFields.add(field)
                    break
                }
            }
        }

        File f = new File(spatialConfig.data.dir.toString() + '/public/layerDistances.properties')
        if (!f.exists()) {
            f.write('')
        }
        Map distances = [:]
        FileReader fr = new FileReader(f)
        for (String line : fr.readLines()) {
            String[] split = line.split('=')
            if (split.length == 2) {
                distances.put(split[0], split[1])
            }
        }

        taskWrapper.task.message = 'identify missing layer distances'
        validFields.eachWithIndex { Fields field1, Integer idx1 ->
            Layers layer1 = findLayer(layers, field1.spid.toString())
            if (layer1 != null && layer1.type == 'Environmental') {
                validFields.eachWithIndex { field2, idx2 ->
                    Layers layer2 = findLayer(layers, field2.spid.toString())
                    if (layer2 != null && idx1 < idx2 && layer2.type == 'Environmental') {
                        try {
                            String domain1 = layer1.domain
                            String domain2 = layer2.domain

                            if (TabulationGeneratorService.isSameDomain(TabulationGeneratorService.parseDomain(domain1),
                                    TabulationGeneratorService.parseDomain(domain2))) {
                                String key = field1.id < field2.id ? field1.id + ' ' + field2.id : field2.id + ' ' + field1.id

                                if (!distances.containsKey(key)) {
                                    all.add(key)
                                }
                            }
                        } catch (err) {
                            log.error "error comparing layer distance candidates " + field1.id + " and " + field2.id, err
                        }
                    }
                }
            }
        }

        taskWrapper.task.message = distances.size() + ' missing distances'
        taskWrapper.task.message = 'preparing LayerDistancesCreateOne tasks'

        Map count = [:]
        for (int i = 0; i < all.size(); i++) {
            for (String s : all[i].toString().split(' ')) {
                if (count.containsKey(s)) {
                    count.put(s, count.get(s) + 1)
                } else {
                    count.put(s, 1)
                }
            }
        }
        List lIds = new ArrayList<>(count.keySet()).sort(true, new Comparator() {
            @Override
            int compare(Object o1, Object o2) {
                return count.get(o2) - count.get(o1)
            }
        })

        log.error 'sizes: ' + all.size() + ', ' + validFields.size()
        if (all.size() <= validFields.size()) {
            //recalculate individual values
            all.each { s ->
                def m = [fieldId1: s.toString().split(' ')[0], fieldId2: s.toString().split(' ')[1]]
                addOutput("process", "LayerDistancesCreateOne " + (m as JSON))
            }
        } else {
            //recalculate all
            int batchSize = 18

            int half = (int)(batchSize / 2)
            for (int i = 0; i < lIds.size(); i += half) {
                for (int j = i + half; j < lIds.size(); j += half) {

                    List batch = []
                    for (int k = i; k < lIds.size() && k < i + half; k++) {
                        batch.add(lIds[k])
                    }
                    for (int k = j; k < lIds.size() && k < Math.min(lIds.size() - j, j + half); k++) {
                        batch.add(lIds[k])
                    }

                    //process
                    Map m = [:]
                    for (int k = 0; k < batch.size(); k++) {
                        m.put('fieldId' + (k + 1), batch[k])
                    }
                    if (m.size() >= 2) {
                        addOutput("process", "LayerDistancesCreateOne " + (m as JSON))
                    }
                }
            }
        }
    }

    static Layers findLayer(List<Layers> layers, String id) {
        Layers found = null
        layers.each { Layers layer ->
            if (String.valueOf(layer.id) == id) {
                found = layer
            }
        }
        return found
    }
}
