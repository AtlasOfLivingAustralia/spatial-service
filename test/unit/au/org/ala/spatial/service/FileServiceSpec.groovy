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

import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import org.apache.maven.artifact.ant.shaded.FileUtils
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
@TestFor(FileService)
@TestMixin(GrailsUnitTestMixin)
class FileServiceSpec extends Specification {

    def setup() {
        grailsApplication.config.data.dir = new File(LayerDistancesServiceSpec.class.getResource("/resources/layers.json").getFile()).getParent()
    }

    def cleanup() {
    }

    void "info"() {
        when:
        def info = service.info(path)

        then:
        info.size() == length
        info[0].exists == exists
        info.each { i ->
            assert i.path != null
            assert i.exists != null
            assert i.lastModified != null
            assert i.size != null
        }

        where:
        path || length || exists
        '/layers.json' || 1 || true
        '/testdir' || 4 || true
        '/testdir/testfile' || 2 || true
        '/does not exist' || 1 || false
    }

    void 'zip unzip'() {
        when:

        def origDir = grailsApplication.config.data.dir

        //setup output directories
        def tmpfile = File.createTempFile('test', '.zip')
        def rootDirLocal = File.createTempDir()
        def taskDirLocal = File.createTempDir()
        def rootDirRemote = File.createTempDir()
        def taskDirRemote = File.createTempDir()

        FileUtils.copyDirectoryStructure(new File(grailsApplication.config.data.dir), rootDirLocal)
        FileUtils.copyDirectoryStructure(new File(grailsApplication.config.data.dir), taskDirLocal)

        def srcFiles = service.getFilesFromBase(path, grailsApplication.config.data.dir)

        //zip
        grailsApplication.config.data.dir = rootDirLocal.getPath()
        service.zip(tmpfile.getPath(), taskDirLocal.getPath(), [path])

        //unzip
        grailsApplication.config.data.dir = rootDirRemote.getPath()
        service.unzip(tmpfile.getPath(), taskDirRemote.getPath(), false)

        //wait a little for the unzip
        Thread.sleep(1000)

        then:
        //same number of files in each location
        assert srcFiles.size() == count
        assert srcFiles.size() == service.getFilesFromBase(path, taskDirRemote.getPath()).size()

        //all source files are copied
        srcFiles.each { s ->
            if (path.startsWith("/")) {
                assert new File(s.getPath().replace(origDir, rootDirRemote.getPath())).exists()
            } else {
                assert new File(s.getPath().replace(origDir, taskDirRemote.getPath())).exists()
            }
        }

        //test
        //- specific file in data.dir
        //- whole directory in taskDir
        //- matched file name in data.dir
        where:
        path || count
        '/layers.json' || 1
        'testdir/subdir' || 2
        '/testdir/testfile' || 2
    }
}
