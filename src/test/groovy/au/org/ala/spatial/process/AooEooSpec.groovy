package au.org.ala.spatial.process

import au.org.ala.spatial.Util
import au.org.ala.spatial.service.TestUtil

import au.org.ala.spatial.service.TaskQueueService
import org.apache.commons.io.FileUtils
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

import javax.sql.DataSource

class AooEooSpec extends Specification implements GrailsUnitTest {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def proc = new AooEoo()

    def setup() {
        proc.taskService = Mock(TaskQueueService)
        proc.slaveService = Mock(SlaveService)
        proc.grailsApplication = grailsApplication
    }

    def cleanup() {

    }

    def "run AooEoo"() {
        when:

        def tmpDir = File.createTempDir()
        proc.taskService.getBasePath(_) >> tmpDir.getPath() + '/'

        System.out.println(tmpDir.getPath())

        Util.metaClass.static.getQid = { qid -> [:] }
        Util.metaClass.static.makeQid = { query -> "qid" }

        Util.metaClass.static.getUrl = { String url ->
            if (url.contains('webportal/params/details')) return '{}'
            else if (url.contains('/occurrences/search')) return '{"totalRecords": 10}'
            else if (url.contains('/occurrence/facets?facets=')) return '{"count": 10}'
            else if (url.contains('/occurrences/facets/download?facets=')) return "120,-15\n154, -25\n140, -40"
            return 'other'
        }

        proc.task = [spec : Mock(TaskQueueService).getAllSpec().find { spec -> spec.name.equalsIgnoreCase('AooEoo') },
                     input: [area: "[{}]", species: "{\"q\": \"\", \"name\": \"test species\"}", resolution: "0.02", coverage: "2", radius: 5000]]

        proc.start()

        then:
        tmpDir.listFiles().each { file ->
            assert FileUtils.readFileToString(file).replaceAll("\\s","") == TestUtil.getResourceAsString('output/aooeoo/' + file.name).replaceAll("\\s","")
        }
    }

}
