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

import au.org.ala.spatial.service.LayerDistancesServiceSpec
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import org.apache.commons.io.FileUtils
import spock.lang.Specification

import java.util.zip.ZipFile

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestFor(TaskService)
@TestMixin(ControllerUnitTestMixin)
class TaskServiceSpec extends Specification {

    void setup() {

    }

    void cleanup() {}

    void "getAllSpec"() {
        when:
        def allSpec = service.getAllSpec()

        then:
        allSpec.size() > 0
    }

    void "getZip"() {
        when:
        //zips only produced when service.enabled == false
        grailsApplication.config.service.enable = false

        File tmpDir = File.createTempDir()
        FileUtils.copyDirectory(new File(new File(LayerDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent() + "/dataDirTaskZip"), tmpDir)
        grailsApplication.config.data.dir = tmpDir.getPath()

        def task = [taskId: 1, id: 1, spec: [output:[files:[name:"files"]]], history: "history", private: [[a:1]],
                    output: [files:["directMatch.txt", "/layer/layer.txt", "prefixMatch"]]]

        def zip = service.getZip(task)

        def z = new ZipFile(zip)

        then:
        ["directMatch.txt", "/layer/layer.txt", "prefixMatch.1", "prefixMatch.2"].each { name ->
            assert z.getEntry(name)
        }
    }

    void "getStatus"() {
        when:
        def task = [history: [1:"a", 2:"b"], message: "test"]
        def taskFinished = [history: [1:"a", 2:"b"], message: "test", finished: true]
        def status = service.getStatus(task)
        def statusFinished = service.getStatus(taskFinished)

        then:
        assert status == task
        assert statusFinished == taskFinished
    }
}
