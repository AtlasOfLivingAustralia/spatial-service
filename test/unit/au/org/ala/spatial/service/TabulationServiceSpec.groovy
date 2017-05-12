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

import au.org.ala.layers.TabulationService
import au.org.ala.layers.dao.TabulationDAO
import au.org.ala.layers.dto.Tabulation
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestFor(TabulationService)
@TestMixin(GrailsUnitTestMixin)
class TabulationServiceSpec extends Specification {

    def setup() {
        service.tabulationDao = Mock(TabulationDAO)

        List<Tabulation> tabulations = TestUtil.getListFromJSON('tabulations.json', Tabulation.class)
        service.tabulationDao.getTabulation( _, _, _) >> tabulations
    }

    def cleanup() {
    }

    //func: area | occurrences | species
    //type: csv | json
    void "generateTabulationCSVHTML"() {
        when:
        def area = service.generateTabulationCSVHTML("cl1", "cl2", null, "area", "csv")
        def occurrences = service.generateTabulationCSVHTML("cl2", "cl1", null, "occurrences", "csv")
        def species = service.generateTabulationCSVHTML("cl1", "cl2", null, "species", "json")

        then:
        assert area.trim() == TestUtil.getResourceAsString('output/generateTabulationCSVHTML_csv_area').trim()
        assert occurrences.trim() == TestUtil.getResourceAsString('output/generateTabulationCSVHTML_html_occurrences').trim()
        assert species.trim() == TestUtil.getResourceAsString('output/generateTabulationCSVHTML_json_species').trim()
    }
}
