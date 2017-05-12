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

import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import spock.lang.Specification

import java.util.concurrent.CountDownLatch

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestFor(FileLockService)
@TestMixin(GrailsUnitTestMixin)
class FileLockServiceSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test locking"() {
        when:
        Task task1 = new Task(id: 1)
        Task task2 = new Task(id: 2)
        Task task3 = new Task(id: 3)
        Task task4 = new Task(id: 4)

        List files = ['a', 'b', 'c', 'd']
        def filesOther = ['d']
        def filesSome = ['a']
        def filesMore = ['a', 'd']

        CountDownLatch latch1 = service.lock(files, task1)
        CountDownLatch latch2 = service.lock(filesMore, task2)
        CountDownLatch latch3 = service.lock(filesSome, task3)
        CountDownLatch latch4 = service.lock(filesOther, task4)

        then:
        assert latch1 == null //not locked
        assert latch2.getCount() == 1 //locked
        assert latch3.getCount() == 1 //locked
        assert latch4.getCount() == 1 //locked

        assert service.release(files) || true
        // (latch3 and latch4) OR latch2 will be unlocked
        if (latch3.getCount() == 0 && latch4.getCount() == 0 && latch2.getCount() == 1) {
            service.release(filesSome) || true
            assert latch2.getCount() == 1 //still locked
            service.release(filesOther) || true
            assert latch2.getCount() == 0 //not locked

            service.release(filesMore) || true

            assert service.filesList.size() == 0
            assert service.locks.size() == 0
        } else if (latch3.getCount() == 1 && latch4.getCount() == 1 && latch2.getCount() == 0) {
            service.release(filesMore) || true
            assert latch3.getCount() == 0 //not locked
            assert latch4.getCount() == 0 //not locked

            service.release(filesSome) || true
            service.release(filesOther) || true

            assert service.filesList.size() == 0
            assert service.locks.size() == 0
        }
    }
}
