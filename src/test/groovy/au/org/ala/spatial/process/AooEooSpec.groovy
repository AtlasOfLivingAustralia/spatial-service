package au.org.ala.spatial.process

import au.org.ala.spatial.dto.SpeciesInput
import au.org.ala.spatial.SpatialConfig
import au.org.ala.spatial.Util
import au.org.ala.spatial.LayersDistancesServiceSpec
import au.org.ala.spatial.TaskQueueService
import au.org.ala.spatial.TasksService
import au.org.ala.spatial.TestUtil
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

import javax.sql.DataSource

class AooEooSpec extends Specification implements GrailsUnitTest {

    TasksService tasksService
    SpatialConfig spatialConfig
    TaskQueueService taskQueueService

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)

            this.spatialConfig = new SpatialConfig()
            this.spatialConfig.with {
                data = new SpatialConfig.DotDir()
                data.dir = new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
                task = new SpatialConfig.DotTask()
                task.general = new SpatialConfig.DotThreads()
                task.general.threads = 1
                task.admin = new SpatialConfig.DotThreads()
                task.admin.threads = 1
            }
            taskQueueService(TaskQueueService) {
                spatialConfig = this.spatialConfig
            }
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def proc = new AooEoo() {
        @Override
        String streamBytes(String url, String name) {
            if (url.contains('webportal/params/details')) return '{}'
            else if (url.contains('/occurrences/search')) return '{"totalRecords": 10}'
            else if (url.contains('/occurrence/facets?facets=')) return '{"count": 10}'
            else if (url.contains('/occurrences/facets/download?facets=')) return "120,-15\n154, -25\n140, -40"
            return 'other'
        }

        @Override
        Integer occurrenceCount(SpeciesInput species, String extraFq){
            10
        }
    }

    def setup() {
        proc.tasksService = Mock(TasksService)
        tasksService = proc.tasksService

        taskQueueService = applicationContext.getBean('taskQueueService')
    }

    def cleanup() {

    }

    def "run AooEoo"() {
        when:

        def tmpDir = File.createTempDir()

        System.out.println(tmpDir.getPath())

        Util.metaClass.static.getQid = { qid -> [:] }
        Util.metaClass.static.makeQid = { query -> "qid" }

        proc.taskWrapper = [
                path: tmpDir,
                spec : tasksService.getAllSpec().find { spec -> spec.name.equalsIgnoreCase('AooEoo') },
                task: [
                        input: [
                                [ name: "area", value: "[{}]"],
                                [ name: "species", value: "{\"q\": \"\", \"name\": \"test species\"}"],
                                [ name: "resolution", value: "0.02"],
                                [ name: "coverage", value: "2"],
                                [ name: "radius", value: 5000]
                        ],
                        output: []
                ]
            ]

        proc.start()

        then:
        tmpDir.listFiles().each { file ->
            assert file.text.replaceAll("\\s", "") == TestUtil.getResourceAsString('output/aooeoo/' + file.name).replaceAll("\\s", "")
        }
    }

}
