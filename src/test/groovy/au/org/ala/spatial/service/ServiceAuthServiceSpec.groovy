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

import au.org.ala.spatial.Util
import grails.testing.services.ServiceUnitTest
import grails.util.Holders
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class ServiceAuthServiceSpec extends Specification implements ServiceUnitTest<ServiceAuthService> {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        ExpandoMetaClass.enableGlobally()
        Holders.config.serviceKey = 'localKey'
        Holders.config.apiKeyCheckUrlTemplate = "{0}"
        Util.metaClass.static.getUrl = { String url ->
            if (url.contains('test valid key')) return '"valid":true'
            return 'is not valid'
        }
    }

    def cleanup() {
    }

    void "valididate key"() {
        when:
        def result = service.isValid(key)

        then:
        result == isValid

        where:
        key || isValid
        'localKey' || true
        'not valid' || false
        'test valid key' || true
    }
}
