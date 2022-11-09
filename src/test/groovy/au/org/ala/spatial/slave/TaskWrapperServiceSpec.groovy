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

package au.org.ala.spatial.slave

import au.org.ala.spatial.service.TaskQueueService
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class TaskWrapperServiceSpec extends Specification implements ServiceUnitTest<TaskQueueService>, DataTest {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    void setup() {

    }

    void cleanup() {}



}
