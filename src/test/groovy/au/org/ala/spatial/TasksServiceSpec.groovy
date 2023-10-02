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


import grails.converters.JSON
import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class TasksServiceSpec extends Specification implements ServiceUnitTest<TasksService> {

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

    def specUnique = JSON.parse('{ "task1": { "private": { "unique": true}, "input":{ "input1":{"constraints":{"mandatory":true, "default":"defaultValue", "type":"double"}  }} } }')

    def setup() {

        //String pid = objectDao.createUserUploadedObject(a.wkt.toString(), a.name.toString(), '', null);
        //Objects object = objectDao.getObjectByPid(pid);

        //def spec = masterService.spec(true).get(name)
    }

    def cleanup() {
    }

//    void "task lifecycle"() {
//        when:
//
//        def taskInput = []
//        def updateInfo = []
//
//        def task = create("testTask", "1", taskInput, "2", "3", "4")
//
//        def status1 = service.getStatus(task)
//
//        service.update(task.id, updateInfo)
//
//        def status2 = service.getStatus(task)
//
//        service.cancel(task)
//
//        def status3 = service.getStatus(task)
//
//        then:
//        true
//    }
//
//    void "validation"() {
//        when:
//
//        //invalid task name
//        def invalidTaskName = service.validateInput("invalid", null)
//
//        //missing mandatory
//
//        //default is set
//
//        //restrictions (double, integer, string, layers, areas, species) min/max
//
//        //area gridasshape, envelope, valid pid, valid wkt, minArea, maxArea
//
//        //process - valid task id
//
//        //upload - valid upload id
//
//        //layers - valid layers, environmental, contextual, indb, analysis
//
//        //species - min/maxSpecies, min/maxOccurrences
//
//        //stringList - valid values
//
//
//        then:
//        true
//    }
//
//    void "after publish"() {
//        //create task
//
//        service.afterPublish(task.id, spec)
//
//        //gettask
//
//        //test formattedOutput
//    }

    void "getAllSpec"() {
        when:
        def allSpec = service.getAllSpec()

        then:
        allSpec.size() > 0
    }
}
