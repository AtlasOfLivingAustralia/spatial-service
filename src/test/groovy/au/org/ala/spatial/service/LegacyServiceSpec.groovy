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

import grails.config.Config
import grails.testing.services.ServiceUnitTest
import org.apache.tools.ant.taskdefs.GUnzip
import org.apache.tools.ant.taskdefs.Untar
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class LegacyServiceSpec extends Specification implements ServiceUnitTest<LegacyService> {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def dataDir
    def legacyDir

    def setup() {
        dataDir = File.createTempDir()
        grailsApplication.config.data.dir = dataDir.getPath()

        legacyDir = File.createTempDir()
        def gz = new GUnzip()
        gz.setDest(legacyDir)
        gz.setSrc(new File(TestUtil.getResourcePath('legacy.structure.tar.gz')))
        gz.execute()

        def tarFile = new File(legacyDir.getPath() + '/legacy.structure.tar')
        def tar = new Untar()
        tar.setDest(legacyDir)
        tar.setSrc(tarFile)
        tar.execute()

        tarFile.delete()
    }

    def cleanup() {
    }

    void "link"() {
        when:

        grailsApplication.config.legacy.ALASPATIAL_OUTPUT_PATH = legacyDir.getPath() + "/ala/data/alaspatial"
        grailsApplication.config.legacy.workingdir = legacyDir.getPath()
        grailsApplication.config.legacy.LAYER_FILES_PATH = legacyDir.getPath() + "/ala/data/layers/ready"
        grailsApplication.config.legacy.type = "link"
        grailsApplication.config.legacy.enabled = true

        grailsApplication.config.data.dir = dataDir.getPath()

        service.apply()

        then:
        //TODO: add more legacy files to test
        ["layer/slope_2.grd", "layer/slope_2.gri", "layer/slope_2.tif", "layer/aus1.shp", "layer/aus1.dbf",
         "layer/aus1.shx", "layer/aus1.prj", "public/layerDistances.properties"].each { name ->
            assert new File(dataDir.getPath() + "/" + name).exists()
        }
    }
}
