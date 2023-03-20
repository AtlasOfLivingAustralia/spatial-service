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


import au.org.ala.web.AuthService
import grails.converters.JSON
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class TasksControllerSpec extends Specification implements ControllerUnitTest<TasksController>, DomainUnitTest<Task> {

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
            spatialConfig(SpatialConfig){
                data = new SpatialConfig.DotDir()
                data.dir = new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
                auth = new SpatialConfig.DotRole()
                auth.admin_role = 'ADMIN_ROLE'
            }
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        new Task(name: "test1", status: -1).save()
        new Task(name: "test2", status: 0).save()
        new Task(name: "test3", status: 1).save()

//        spatialConfig.serverName = ""
//        spatialConfig.cas.server.LoginUrl = ""
//        spatialConfig.auth.admin_role = ""

        controller.authService = Mock(AuthService)
        controller.authService.userInRole(_) >> true

        controller.tasksService = Mock(TasksService)
        controller.tasksService.spec(_) >> { return [key1: "test1"] }

    }

    def cleanup() {
    }

    void "getCapabilites"() {
        when:
        setup()
        controller.capabilities()

        then:
        (JSON.parse(response.text) as Map).size() > 0
    }

}
