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

import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestFor(JournalMapService)
@TestMixin(GrailsUnitTestMixin)
class JournalMapServiceSpec extends Specification {

    static String wktWorld = "POLYGON((-180 -90,-180 90,180 90,180 -90,-180 -90))"
    static String nowhere = "POLYGON((0 0,0 1,1 1,1 0,0 0))"
    static String southern = "POLYGON((-180 -90,-180 0,180 0,180 -90,-180 -90))"

    def setup() {
        grailsApplication.config.data.dir = new File(LayerDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
    }

    def cleanup() {
    }

    void "search"() {
        when:
        def result = service.search(wkt, 10)

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
