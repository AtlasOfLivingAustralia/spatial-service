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

import au.org.ala.layers.UserDataService
import au.org.ala.layers.dao.UserDataDAO
import au.org.ala.layers.legend.QueryField
import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

import javax.sql.DataSource

class UserDataServiceSpec extends Specification implements ServiceUnitTest<UserDataService> {

    @Override
    Closure doWithSpring() {{ ->
        dataSource(InstanceFactoryBean, Stub(DataSource), DataSource)
    }}

    @Override
    Set<String> getIncludePlugins() {
        ['core', 'eventBus', "converters"].toSet()
    }

    def setup() {
        service.userDataDao = Mock(UserDataDAO)
    }

    def cleanup() {
    }

    void "importCSV"() {
        when:
        service.importCSV("test", "1", data)

        then:
        1 * service.userDataDao.setDoubleArray(_, _, _) >> { args ->
            assert doubleArray == args[2]
            true
        }
        columns * service.userDataDao.setQueryField(_, _, _) >> { args ->
            assert ((QueryField) args[2]).getName() == 'id' || ((QueryField) args[2]).getLegend().getNumericLegend().cutoffs != null
            true
        }
        1 * service.userDataDao.setMetadata(_, _) >> { args ->
            assert args[1].title == 'User uploaded points'
            assert args[1].bbox.length() > 0
            assert args[1].name == 'test'
            assert args[1].number_of_records == rows
            assert args[1].date <= System.currentTimeMillis()
            assert args[1].date > 0
            true
        }

        where:
        data || columns || rows || doubleArray
        'id,longitude,latitude\n1,2,3\n3,4,5\n6,7,8'  || 3 || 3 || [2.0,3.0,4.0,5.0,7.0,8.0].toArray(double[])
        '1,2,3\n3,4,5' || 3 || 2 || [2.0,3.0,4.0,5.0].toArray(double[])
        '1,2\n3,4' || 3 || 2 || [1.0,2.0,3.0,4.0].toArray(double[])
        'longitude,latitude\n1,2\n3,4\n6,7' || 3 || 3 || [1.0,2.0,3.0,4.0,6.0,7.0].toArray(double[])
    }
}
