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

package au.org.ala.spatial.service

import au.org.ala.layers.LayerDistancesService
import au.org.ala.layers.dao.FieldDAO
import au.org.ala.layers.dao.LayerDAO
import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification


/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestFor(LayerDistancesService)
@TestMixin(GrailsUnitTestMixin)
class LayerDistancesServiceSpec extends Specification {

    def setup() {
        service.fieldDao = Mock(FieldDAO)
        service.layerDao = Mock(LayerDAO)

        List<Field> fields = TestUtil.getListFromJSON('fields.json', Field.class)
        service.fieldDao.getFields() >> fields

        List<Layer> layers = TestUtil.getListFromJSON('layers.json', Layer.class)
        service.layerDao.getLayers() >> layers

        grailsApplication.config.data.dir = new File(LayerDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
    }

    def cleanup() {
    }

    void "makeCSV name"() {
        when:
        def csv = service.makeCSV('name')

        then:
        csv.trim().equals(TestUtil.getResourceAsString('output/LayerDistancesServiceSpec_makeCSV_name').trim())
    }
}

