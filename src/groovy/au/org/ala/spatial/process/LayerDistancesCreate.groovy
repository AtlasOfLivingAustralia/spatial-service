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

import au.org.ala.layers.tabulation.TabulationGenerator
import grails.converters.JSON
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils

@Commons
class LayerDistancesCreate extends SlaveProcess {

    void start() {
        Set all = [] as Set
        List fields = getFields()
        List layers = getLayers()

        String[] grdResolutions = task.input.grdResolutions

        //get highest resolution standardized layer files
        //only want valid fields
        task.message = 'find suitable layers'
        List validFields = []
        fields.each { field ->
            for (int i = grdResolutions.length - 1; i >= 0; i--) {
                def path = '/standard_layer/' + grdResolutions[i] + '/' + field.id + '.grd'
                def peek = slaveService.peekFile(path)
                if (peek.exists) {
                    validFields.add(field)
                    break;
                }
            }
        }

        task.message = 'getting layer distances'
        slaveService.getFile('/public/layerDistances.properties')

        File f = new File(grailsApplication.config.data.dir.toString() + '/public/layerDistances.properties')
        if (!f.exists()) FileUtils.writeStringToFile(f, '')
        Map distances = [:]
        FileReader fr = new FileReader(f)
        for (String line : fr.readLines()) {
            String[] split = line.split('=')
            if (split.length == 2) {
                distances.put(split[0], split[1])
            }
        }

        task.message = 'identify missing layer distances'
        validFields.eachWithIndex { field1, idx1 ->
            Map layer1 = findLayer(layers, field1.spid.toString())
            if (layer1 != null && layer1.type == 'Environmental') {
                validFields.eachWithIndex { field2, idx2 ->
                    Map layer2 = findLayer(layers, field2.spid.toString())
                    if (layer2 != null && idx1 < idx2 && layer2.type == 'Environmental') {
                        try {
                            String domain1 = layer1.domain
                            String domain2 = layer2.domain

                            if (TabulationGenerator.isSameDomain(TabulationGenerator.parseDomain(domain1),
                                    TabulationGenerator.parseDomain(domain2))) {
                                String key = (field1.id.compareTo(field2.id) < 0) ? field1.id + ' ' + field2.id : field2.id + ' ' + field1.id

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

        task.message = distances.size() + ' missing distances'
        task.message = 'preparing LayerDistancesCreateOne tasks'

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

            int half = batchSize / 2
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

    Map findLayer(List layers, String id) {
        Map found = null
        layers.each { layer ->
            if (String.valueOf(layer.id).equals(id)) {
                found = layer as Map
            }
        }
        return found
    }
}
