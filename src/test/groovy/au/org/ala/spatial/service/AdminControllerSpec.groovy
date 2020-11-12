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

import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class AdminControllerSpec extends Specification implements ControllerUnitTest<AdminController>, DomainUnitTest<Task> {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        new Task(name: "test1", status: -1).save()
        new Task(name: "test2", status: 0).save()
        new Task(name: "test3", status: 1).save()

        grailsApplication.config.serverName = ""
        grailsApplication.config.cas.server.LoginUrl = ""
        grailsApplication.config.auth.admin_role = ""

        controller.serviceAuthService = Mock(ServiceAuthService)
        controller.serviceAuthService.isValid(_) >> true
        controller.serviceAuthService.isAdmin(_) >> true
        controller.serviceAuthService.isLoggedIn(_) >> true

        controller.authService = Mock(AuthService)
        controller.authService.userInRole(_) >> true

        controller.masterService = Mock(MasterService)
        controller.masterService.spec(_) >> { return [key1: "test1"] }
        controller.masterService.slaves >> { return [key1: "test1"] }

    }

    def cleanup() {
    }

    void "list all tasks"() {
        when:
        params.all = ""
        params.api_key = "valid"
        controller.tasks()

        then:
        (JSON.parse(response.text) as Map).size() == 3
    }

    void "list open tasks"() {
        when:
        params.api_key = "valid"
        controller.tasks()

        then:
        (JSON.parse(response.text) as Map).size() == 2
    }

    void "get capabilites"() {
        when:
        setup()
        controller.capabilities()

        then:
        (JSON.parse(response.text) as Map).size() > 0
    }

    void "get slaves"() {
        when:
        setup()
        params.api_key = "valid"
        controller.slaves()

        then:
        (JSON.parse(response.text) as Map).size() > 0
    }
}
