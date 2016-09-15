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

import org.apache.maven.artifact.ant.shaded.IOUtil

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileService {

    def grailsApplication

    // put resources (area WKT and layer files) as zip to a stream
    // an area: 'cache/ENVELOPE(...', requires the associated layer files to produce the envelope
    // an area: 'cache/object pid', will provide a .wkt, getting the layer from a layers-service
    // an existing resource: 'path'
    def write(OutputStream outputStream, String name) {

        def files = []
        def stringName = null
        def string = null

        if (name.contains("/")) {
            //look for an internal file

        }

        if (name.startsWith('cache/ENVELOPE')) {
            //parse layer names
            name.replace('cache/ENVELOPE(', '').replace(')', '').split(',').each { layerName ->
                files.addAll(getLayerStandardisedFiles(split[0], null))
            }
        } else if (name.startsWith('cache/pid')) { //TODO: use correct pid identification
            //parse pid
            def pid = name
            def url = grailsApplication.config.layersService.url + '/object/wkt/' + pid
            stringName = name + '.wkt'
            string = new URL(url).text
        } else {
            def list = getFilesFromBase(name)
            if (list != null) {
                files.addAll(list)
            }
        }

        if (files.size() > 0) {
            //open zip for writing
            ZipOutputStream zos = new ZipOutputStream(outputStream)
            zos.setLevel(0)

            files.each { file ->
                //add to zip
                ZipEntry ze = new ZipEntry(file.getPath().replace(grailsApplication.config.data.dir, ''))
                zos.putNextEntry(ze)

                //open and stream
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))
                byte[] bytes = new byte[1024]
                int size
                while ((size = bis.read(bytes)) > 0) {
                    zos.write(bytes, 0, size)
                }

                zos.flush()

                //close file and entry
                zos.closeEntry()

                bis.close()
            }

            // add a zip entry containing 'string'
            /*if (stringName != null) {
                ZipEntry ze = new ZipEntry(URLEncoder.encode(stringName, 'UTF-8'))
                zos.putNextEntry(ze)
                zos.write(string.bytes)
                zos.closeEntry()
            }*/

            //close
            zos.flush()
            zos.close()
        } else {
            log.error "cannot find file: " + name
        }

        outputStream.close()
    }

    def info(String name) {
        def map = [[path: '', exists: false, lastModified: System.currentTimeMillis()]]

        def list = getFilesFromBase(name)
        if (list != null && list.size() > 0) {
            map = []
            list.each { file ->
                map.add([path  : file.getPath().replace(grailsApplication.config.data.dir, ''),
                         exists: file.exists(), lastModified: file.lastModified()])
            }
        }

        map
    }

    // get the shapefile from the layers_dir/analysis
    def getLayerStandardisedFiles(name, resolution) {
        //

        return []
    }

    def getFilesFromBase(name) {
        def path = grailsApplication.config.data.dir
        def file = new File(path + '/' + name)

        def files = []

        if (file.exists() && file.isDirectory()) {
            //get everything from this dir
            files = file.listFiles()
        } else if (file.exists()) {
            //get this named file
            files.add(file)
        } else {
            //add * and return everything that matches
            def search = file.getName()
            files = file.getParentFile().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    return filename.equals(search) || filename.startsWith(search + '.')
                }
            });
        }

        files
    }

    def unzip(zip, path, boolean upload) {
        def zf = new ZipInputStream(new FileInputStream(zip))

        byte[] buffer = new byte[1024]

        def entry
        while ((entry = zf.getNextEntry()) != null) {
            def e = !upload && entry.getName().startsWith('/') ?
                    grailsApplication.config.data.dir + entry.getName() :
                    path + '/' + entry.getName()
            if (e.endsWith('/')) {
                new File(e).mkdirs()
            } else {
                new File(e).getParentFile().mkdirs()

                def bos = new BufferedOutputStream(new FileOutputStream(e))

                int len
                while ((len = zf.read(buffer)) >= 0) {
                    bos.write(buffer, 0, len)
                }
                bos.flush()
                bos.close()
            }
        }

        zf.closeEntry()
        zf.close()
    }

    def zip(outFilename, taskDir, files) {
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFilename)))
        try {
            files.each { file ->
                def e = file.startsWith('/') ?
                        grailsApplication.config.data.dir + file :
                        taskDir + '/' + file
                def f = new File(e)

                if (f.isDirectory()) {
                    for (File fs : f.listFiles()) {
                        ZipEntry ze = new ZipEntry(f.getName() + File.separator + fs.getName())
                        zos.putNextEntry(ze)
                        def is = new BufferedInputStream(new FileInputStream(fs))
                        IOUtil.copy(is, zos)
                        is.close()
                        zos.closeEntry()
                    }
                } else {
                    ZipEntry ze = new ZipEntry(f.getName())
                    zos.putNextEntry(ze)
                    def is = new BufferedInputStream(new FileInputStream(f))
                    IOUtil.copy(is, zos)
                    is.close()
                    zos.closeEntry()
                }
            }
        } catch (err) {
            log.error 'failed to zip download for : ' + taskDir, err
        } finally {
            try {
                zos.flush()
                zos.close()
            } catch (err) {
                log.error 'failed to close download zip for : ' + taskDir, err
            }
        }
    }
}
