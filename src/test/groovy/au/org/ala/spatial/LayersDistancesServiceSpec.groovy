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


import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Ignore
import spock.lang.Specification

import javax.sql.DataSource

class LayersDistancesServiceSpec extends Specification implements ServiceUnitTest<LayerDistancesService> {

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
            spatialConfig(SpatialConfig){
                data = new SpatialConfig.DotDir()
                data.dir = new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
            }
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        service.fieldService = Mock(FieldService)
        service.layerService = Mock(LayerService)

        List<Fields> fields = TestUtil.getListFromJSON('fields.json', Fields.class)
        service.fieldService.getFields(false) >> fields

        List<Layers> layers = TestUtil.getListFromJSON('layers.json', Layers.class)
        service.layerService.getLayers() >> layers
    }

    def cleanup() {
    }

    @Ignore
    void "makeCSV name"() {
        when:
        def csv = service.makeCSV('name')

        then:
        csv.trim() == TestUtil.getResourceAsString('output/LayerDistancesServiceSpec_makeCSV_name').trim()
    }
}

