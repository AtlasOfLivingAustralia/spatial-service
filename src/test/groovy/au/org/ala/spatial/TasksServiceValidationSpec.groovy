package au.org.ala.spatial

import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification
import spock.lang.Unroll

import javax.sql.DataSource

/**
 * Which processes a user can start (/tasks/capabilities, /tasks/create) and how their input is
 * validated. Processes without "isPublic": true in src/main/resources/processes are admin only
 * (layer, field, distribution and tabulation creation, layer copy, geoserver init...).
 */
class TasksServiceValidationSpec extends Specification implements ServiceUnitTest<TasksService> {

    static final List<String> ADMIN_ONLY = ['LayerCopy', 'LayerCreation', 'FieldCreation', 'InitGeoserver',
                                            'DistributionCreation', 'TabulationCreate', 'StandardizeLayers']

    static final List<String> PUBLIC = ['AooEoo', 'Classification', 'Maxent', 'ScatterplotCreate', 'TaxonFrequency']

    FieldService fieldService = Mock()
    LayerService layerService = Mock()

    @Override
    Closure doWithSpring() {
        { ->
            dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
            spatialConfig(SpatialConfig) {
                data = new SpatialConfig.DotDir()
                data.dir = new File(LayersDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
            }
        }
    }

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        service.fieldService = fieldService
        service.layerService = layerService
    }

    def "non admins only see public processes"() {
        when:
        def spec = service.getSpecification(false)

        then:
        spec.keySet().containsAll(PUBLIC)
        ADMIN_ONLY.every { !spec.containsKey(it) }
    }

    def "admins see every process"() {
        expect:
        service.getSpecification(true).keySet().containsAll(ADMIN_ONLY + PUBLIC)
    }

    @Unroll
    def "a non admin cannot start #name"() {
        when:
        def errors = service.validateInput(name, [:], false)

        then:
        errors.spec

        where:
        name << ADMIN_ONLY
    }

    def "an unknown process is rejected"() {
        expect:
        service.validateInput('NoSuchProcess', [:], true).spec
    }

    def "default values are added to the input"() {
        given:
        Map input = [layer: ['el1', 'el2']]
        fieldService.getFieldById(_, false) >> new Fields(type: 'e', name: 'env')

        when:
        service.validateInput('Classification', input, false)

        then:
        input.groups == 5
    }

    @Unroll
    def "Classification groups=#groups is #outcome"() {
        given:
        fieldService.getFieldById(_, false) >> new Fields(type: 'e', name: 'env')

        when:
        def errors = service.validateInput('Classification', [layer: ['el1', 'el2'], groups: groups], false)

        then:
        errors.containsKey('groups') == !valid

        where:
        groups | valid
        2      | true
        30     | true
        1      | false
        31     | false
        outcome = valid ? 'valid' : 'rejected'
    }

    def "the error for a value over the maximum names the maximum"() {
        given:
        fieldService.getFieldById(_, false) >> new Fields(type: 'e', name: 'env')

        when:
        def errors = service.validateInput('Classification', [layer: ['el1', 'el2'], groups: 31], false)

        then:
        errors.groups.contains('Maximum value is 30')
    }

    def "too few layers are rejected"() {
        when:
        def errors = service.validateInput('Classification', [layer: ['el1']], false)

        then:
        errors.layer
    }

    def "a contextual layer is rejected by a process that only accepts environmental layers"() {
        given:
        fieldService.getFieldById('cl22', false) >> new Fields(id: 'cl22', type: 'c', name: 'States')
        fieldService.getFieldById('el1', false) >> new Fields(id: 'el1', type: 'e', name: 'Rain')

        when:
        def errors = service.validateInput('Classification', [layer: ['cl22', 'el1']], false)

        then:
        errors.layer?.contains('cl22')
    }

    def "an unknown layer is rejected"() {
        given:
        fieldService.getFieldById(_, false) >> null

        when:
        def errors = service.validateInput('Classification', [layer: ['el1', 'nope']], false)

        then:
        errors.layer
    }

    def "an environmental layer is accepted by a process that only accepts environmental layers"() {
        given:
        fieldService.getFieldById(_, false) >> new Fields(type: 'e', name: 'Rain')

        expect:
        !service.validateInput('Classification', [layer: ['el1', 'el2']], false).layer
    }

    def "an analysis layer is rejected by a process that does not accept analysis layers"() {
        given: 'analysis layers have an intersection file but no field'
        fieldService.getFieldById('aloc_1', false) >> null
        layerService.getIntersectionFile('aloc_1') >> new au.org.ala.spatial.dto.IntersectionFile('aloc_1', '/data/aloc_1', null, 'aloc_1', 'aloc_1', 'Classification', null, 'a', null)

        when:
        def errors = service.validateInput('SpeciesByLayer', [layer: ['aloc_1']], false)

        then:
        errors.layer?.contains('aloc_1')
    }
}
