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
import groovy.util.logging.Slf4j

@Slf4j
class TabulationCreate extends SlaveProcess {

    void start() {
        Set all = [] as Set
        List fields = getFields()
        List tabulations = getTabulations()
        task.history.put(System.currentTimeMillis(), 'Collected ' + fields.size() + ' fields.')
        fields.eachWithIndex { field1, idx1 ->
            fields.eachWithIndex { field2, idx2 ->
                if (idx1 < idx2) {
                    try {
                        if (field1.intersect && field2.intersect) {
                            boolean exists = false
                            tabulations.each { tabulation ->
                                if ((tabulation.fid1 == field1.id && tabulation.fid2 == field2.id) ||
                                        (tabulation.fid2 == field1.id && tabulation.fid1 == field2.id)) {
                                    exists = true
                                }
                            }

                            if (!exists) {
                                String domain1 = getLayer(field1.spid).domain
                                String domain2 = getLayer(field2.spid).domain

                                if (TabulationGenerator.isSameDomain(TabulationGenerator.parseDomain(domain1),
                                        TabulationGenerator.parseDomain(domain2))) {
                                    String keyA = field1.id + " " + field2.id
                                    String keyB = field2.id + " " + field1.id
                                    String key = keyA.compareTo(keyB) < 0 ? keyA : keyB

                                    all.add(key)
                                }
                            }
                        }
                    } catch (err) {
                        task.history.put(System.currentTimeMillis(), "error comparing tabulation candidates " + field1.id + " and " + field2.id)
                        log.error "error comparing tabulation candidates " + field1.id + " and " + field2.id, err
                    }
                }
            }
        }

        getTabulations().each { t ->
            String keyA = t.fid1 + " " + t.fid2
            String keyB = t.fid2 + " " + t.fid1
            String key = keyA.compareTo(keyB) < 0 ? keyA : keyB

            all.remove(key)
        }

        task.history.put(System.currentTimeMillis(), 'Processing TabulationCreateOne ..')
        all.each { s ->
            def m = [fieldId1: s.toString().split(" ")[0], fieldId2: s.toString().split(" ")[1]]
            addOutput("process", "TabulationCreateOne " + (m as JSON))
        }
    }
}
