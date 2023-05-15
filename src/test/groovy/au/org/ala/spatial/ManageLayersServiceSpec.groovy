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

import grails.testing.gorm.DomainUnitTest
import grails.testing.services.ServiceUnitTest
import org.apache.commons.io.FileUtils
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class ManageLayersServiceSpec extends Specification implements ServiceUnitTest<ManageLayersService>, DomainUnitTest<Task> {

    def gdalInstalled = false
    SpatialConfig spatialConfig

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)

            publishService(InstanceFactoryBean, Mock(PublishService), PublishService)
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }


    def setup() {
        service.fieldService = Mock(FieldService)

        service.layerService = Mock(LayerService)
        service.layerService.getLayerByName(_, _) >> null

        service.spatialObjectsService = Mock(SpatialObjectsService)
        service.tasksService = Mock(TasksService)

    }

    def cleanup() {
    }

    void setupConfig() {
        this.spatialConfig = new SpatialConfig()
        this.spatialConfig.with {
            data = new SpatialConfig.DotDir()
            data.dir = new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent() + "/dataDir"
            task = new SpatialConfig.DotTask()
            task.general = new SpatialConfig.DotThreads()
            task.general.threads = 1
            task.admin = new SpatialConfig.DotThreads()
            task.admin.threads = 1
            geoserver = new SpatialConfig.DotGeoserver()
            geoserver.url = 'http://geoserver'
            geoserver.canDeploy = false
        }
        service.spatialConfig = spatialConfig
    }

    void "listUploadedFiles"() {
        when:

        setupConfig()

        if (taskStatus > 0) new Task(name: "LayerCreation", tag: uploadId, status: taskStatus).save()

        service.fieldService.getFieldById(_, _) >> {
            field
        }

        def list = service.getUpload(uploadId)
        list.created = null // do not test for timestamp

        then:
        assert list.raw_id == expect.raw_id
        assert list.name == expect.name
        assert list.displayname == expect.displayname
        assert list.layer_id == expect.layer_id
        assert list.filename == expect.filename

        where:
        uploadId || taskStatus || field       || expect
        "1"      || 1          || [id: "cl1"] || [raw_id: "1", layer_id: "1", filename: "1", name: "1", displayname: "1", layer_creation: "running"]
        "2"      || 2          || [:]         || [raw_id: "2", filename: "2", name: "2", displayname: "2", layer_creation: "cancelled"]
        "3"      || -1         || [:]         || [:]
        "4"      || 3          || [:]         || [raw_id: "4", filename: "4", name: "4", displayname: "4", data_resource_uid: "4", layer_creation: "error"]
        "5"      || -1         || [:]         || [raw_id: "5", filename: "5", name: "5", displayname: "5", checklist: "5"]
    }

    void "processUpload"() {

        when:
        def tmpDir
        def result
        if (gdalInstalled) {
            tmpDir = File.createTempDir()
            FileUtils.copyDirectory(new File(new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent() + "/dataDirLayerCreation"), tmpDir)
            spatialConfig.data.dir = tmpDir.getPath()

            result = service.processUpload(new File(tmpDir.getPath() + "/uploads/" + dir), dir)
        }

        then:

        if (gdalInstalled) {
            !files || files.each { file ->
                assert new File(tmpDir.getPath() + "/uploads/" + dir + "/" + file).exists()
            }

            if (error) {
                assert result.containsKey("error")
            } else {
                assert result.raw_id == raw_id
                assert result.columns == columns
                assert result.test_url.contains("ALA:" + dir + "&")
            }
        }

        where:
        dir || error || raw_id       || columns            || files
        "1" || false || "relief_ave" || []                 || ["flatten.txt", "original.name", "1.grd", "1.gri", "1.bil", "1.hdr", "1.tif"]
        "2" || false || "aus1"       || ["NAME_1", "TYPE"] || ["original.name", "2.dbf", "2.shp", "2.shx", "2.prj"]
        "4" || true  || ""           || []                 || []
    }

    void "fieldMapDefault"() {
        when:
        setupConfig()

        service.fieldService.getFieldsByDB() >> [[id: "cl1", spid: "cl1"]]
        service.layerService.getLayerById(_, _) >> [id: "1", name: "name1", displayname: "name1", type: "contextual"]

        def map = service.fieldMapDefault("1").findAll { k, v -> v != null }
        //do not test creation time or test_ur
        map.remove('created')
        map.remove('test_url')

        def expect = [name     : "name1", desc: "name1", raw_id: "1", layer_id: "1", displayname: "name1", indb: true,
                      intersect: false, analysis: true, addtomap: true, enabled: true, requestedId: "cl1", type: 'c',
                      filetype : "shp", columns: ["NAME_1", "TYPE"], fields: [], has_layer: true,
                      filename : "1", classifications: [], sid: "NAME_1", sname: "NAME_1"]

        then:

        expect.each { k, v ->
            assert map.get(k) == v
            map.remove(k)
        }

        assert map.size() == 0
    }

    void "getShapeFileColumns"() {
        when:
        def columns = service.getShapeFileColumns(new File(new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent() + "/dataDirLayerCreation/uploads/2/aus1.shp"))

        then:
        columns == ["NAME_1", "TYPE"]
    }

}
