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
import spock.lang.Specification

import javax.sql.DataSource

class JournalMapServiceSpec extends Specification implements ServiceUnitTest<JournalMapService> {

    static String wktWorld = "POLYGON((-180 -90,-180 90,180 90,180 -90,-180 -90))"
    static String nowhere = "POLYGON((0 0,0 1,1 1,1 0,0 0))"
    static String southern = "POLYGON((-180 -90,-180 0,180 0,180 -90,-180 -90))"

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
    }

    def cleanup() {
    }

    void "search"() {
        when:
        def result = service.search(wkt, 10, 0)

        then:
        result.count == expect

        where:
        wkt << [wktWorld, nowhere, southern]
        expect << [3, 0, 2]
    }

    void "count"() {
        when:
        def result = service.count(wkt)

        then:
        result == expect

        where:
        wkt << [wktWorld, nowhere, southern]
        expect << [3, 0, 2]
    }
}
