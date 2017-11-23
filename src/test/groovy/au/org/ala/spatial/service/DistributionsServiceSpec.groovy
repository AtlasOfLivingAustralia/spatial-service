package au.org.ala.spatial.service

import au.org.ala.layers.dao.DistributionDAO
import au.org.ala.layers.dto.Distribution
import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class DistributionsServiceSpec extends Specification implements ServiceUnitTest<DistributionsService> {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        service.distributionDao = Mock(DistributionDAO)

        List<Distribution> distributions = TestUtil.getListFromJSON('distributions.json', Distribution.class)
        List<Distribution> checklists = TestUtil.getListFromJSON('checklists.json', Distribution.class)

        service.distributionDao.queryDistributions(_, _, _, _, _, _, _, _, _, _, _, _, _,
                _, _ as String, _, _) >> { v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, type, v16, v17 ->
            type == Distribution.EXPERT_DISTRIBUTION ? distributions : checklists
        }

        service.init()
    }

    def cleanup() {
    }

    void "getDistributionsBySpcode"() {
        when:
        def distribution = service.getDistributionsBySpcode(spcode)

        then:
        distribution.size() == size

        for (int i = 0;i < size;i++) {
            distribution.get(i).spcode == Long.parseLong(spcode)
        }

        where:
        spcode || size
        "1" || 1
        "2" || 1
        "-1" || 0
    }

    void "getChecklistsBySpcode"() {
        when:
        def checklist = service.getChecklistsBySpcode(spcode)

        then:
        checklist.size() == size

        for (int i = 0;i < size;i++) {
            checklist.get(i).spcode == Long.parseLong(spcode)
        }

        where:
        spcode || size
        "1" || 1
        "2" || 1
        "-1" || 0
    }

    void "getDistributionsByLsid"() {
        when:
        def distribution = service.getDistributionsByLsid(lsid)

        then:
        distribution.size() == size

        for (int i = 0;i < size;i++) {
            distribution.get(i).spcode == Long.parseLong(lsid)
        }

        where:
        lsid || size
        "1" || 1
        "2" || 2
        "-1" || 0
    }

    void "getChecklistsByLsid"() {
        when:
        def checklist = service.getChecklistsByLsid(lsid)

        then:
        checklist.size() == size

        for (int i = 0;i < size;i++) {
            checklist.get(i).lsid == lsid
        }

        where:
        lsid || size
        "1" || 1
        "2" || 2
        "-1" || 0
    }

    void "getSpeciesChecklistCountByWMS"() {
        when:
        def count = service.getSpeciesChecklistCountByWMS(wms)

        then:
        count == expect

        where:
        wms || expect
        "wms1" || 1
        "wms2" || 2
        "-1" || 0
    }

    void "csv generation"() {
        when:
        def checklists = service.getDistributionsOrChecklists(Distribution.SPECIES_CHECKLIST, null)
        def areas = service.getAreaChecklists(checklists)

        then:
        checklists.join("\n").trim().equals(TestUtil.getResourceAsString('output/DistributionsServiceSpec_getAreaChecklists_checklists').trim())
        areas.join("\n").trim().equals(TestUtil.getResourceAsString('output/DistributionsServiceSpec_getAreaChecklists_areas').trim())
    }
}
