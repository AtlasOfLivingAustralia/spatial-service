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

import au.org.ala.spatial.dto.Tabulation
import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Ignore
import spock.lang.Specification

import javax.sql.DataSource

class TabulationServiceSpec extends Specification implements ServiceUnitTest<TabulationService> {

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {

        List<Tabulation> tabulations = TestUtil.getListFromJSON('tabulations.json', Tabulation.class)
        service.getTabulation(_, _, _) >> {
            tabulations
        }
    }

    def cleanup() {
    }

    //func: area | occurrences | species
    //type: csv | json
    @Ignore
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
