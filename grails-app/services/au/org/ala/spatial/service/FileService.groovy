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

import org.apache.commons.io.IOUtils

import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileService {

    def grailsApplication

    /**
     * Write all files matching 'path' and 'path.ext' to a zip file
     *
     * Files must not have full file system path.
     *
     * taskDir prefix is added to file names that do not begin with '/'
     * data.dir prefix is added to files names that do begin with '/' as long as they do not already exist in the data.dir
     *
     * When the file is a directory, contents are added recursively.
     *
     * @param outputStream
     * @param path file name reference. Must not be a full file path
     * @param taskDir prefix to use when path does not start with '/'
     * @param exact only look for an exact match
     * @return
     */
    def write(OutputStream outputStream, String path, String taskDir = null) {
        zip(outputStream, getFilesFromBase(path, taskDir), taskDir, false)

        outputStream.close()
    }

    def info(String name) {
        def map = [[path: '', exists: false, lastModified: System.currentTimeMillis(), size: 0]]

        List<File> list = getFilesFromBase(name)
        if (list != null && list.size() > 0) {
            map = []
            list.each { file ->
                if (file.isDirectory()) {
                    String relativePath = file.getPath().substring(grailsApplication.config.data.dir.toString().length(), file.getPath().length())
                    map.addAll(info(relativePath))
                } else {
                    map.add([path  : file.getPath().replace(grailsApplication.config.data.dir.toString(), ''),
                             exists: file.exists(), lastModified: file.lastModified(), size: file.length()])
                }
            }
        }

        map
    }

    /**
     * List files matching a file name.
     *
     * Files must not have full file system path.
     *
     * taskDir prefix is added to file names that do not begin with '/'
     * data.dir prefix is added to files names that do begin with '/' as long as they do not already exist in the data.dir
     *
     * When the file is a directory, contents are added recursively.
     *
     * @param name file name reference. Must not be a full file path
     * @param taskDir prefix to use when name does not start with '/'
     * @param exact only look for an exact match
     * @return
     */
    def getFilesFromBase(name, targetDir = null, exact = false) {
        boolean dataDir = name.startsWith('/')
        def e = dataDir ? grailsApplication.config.data.dir + name : "$targetDir/${name}"

        //only include path once
        def file = new File(e)

        def files = []

        if (file.exists() && !file.isDirectory()) {
            //get this named file
            files.add(file)
        } else {
            //Return everything that starts with 'file.' if file already ends with '.' the extra '.' is not included
            def search = file.getName()
            def subfiles = []
            files = file.getParentFile().listFiles(new FilenameFilter() {
                boolean accept(File dir, String filename) {

                    boolean valid = filename == search ||
                            (!exact && filename.startsWith(search + (search.endsWith('.') ? '' : '.')))

                    File f = new File(dir.getPath() + File.separator + filename)
                    if (f.isDirectory() && valid) {
                        if (!Files.isSymbolicLink(f.toPath())) {
                            f.listFiles().each { sf ->
                                //look for exact subdir matches to avoid duplication
                                subfiles.addAll(getFilesFromBase(name + File.separator + sf.getName(), targetDir, exact))
                            }
                        }
                        return false
                    }

                    return valid
                }
            })
            if (files == null) files = []
            else files = files.toList()
            files.addAll(subfiles)
        }

        files
    }

    /**
     * Unzip all files using the zip entry name to determine location to save.
     *
     * taskDir prefix is added to names that do not begin with '/'
     * data.dir prefix is added to names that do begin with '/' as long as they do not already exist in the data.dir
     *
     * @param zip filename
     * @param path prefix to use when zip entry name does not start with '/'
     * @param upload force use of path prefix for unzip destination
     * @return
     */
    def unzip(String zip, String path, boolean upload = false) {
        def zf = new ZipInputStream(new FileInputStream(zip))

        def entry
        while ((entry = zf.getNextEntry()) != null) {
            def e = !upload && entry.getName().startsWith('/') ?
                    grailsApplication.config.data.dir + entry.getName() : "$path/${entry.getName()}"
            if (e.endsWith('/')) {
                new File(e).mkdirs()
            } else {
                new File(e).getParentFile().mkdirs()

                def bos = new BufferedOutputStream(new FileOutputStream(e.toString()))
                IOUtils.copy(zf, bos)
                bos.flush()
                bos.close()
            }
        }

        zf.closeEntry()
        zf.close()
    }

    /**
     * Add specific files to a new zip file.
     *
     * Files must not have full file system path.
     *
     * taskDir prefix is added to file names that do not begin with '/'
     * data.dir prefix is added to files names that do begin with '/'
     *
     * When the file is a directory, contents are added recursively.
     *
     * @param outFilename
     * @param taskDir
     * @param filenames
     * @return
     */
    def zip(String outFilename, String taskDir, filenames) {
        if (filenames) {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(outFilename))

            def listOfFiles = []
            filenames.each { f ->
                listOfFiles.addAll(getFilesFromBase(f, taskDir))
            }
            zip(os, listOfFiles, taskDir, true)

            os.flush()
            os.close()
        }
    }

    /**
     * Add specific files to a new zip file.
     *
     * Files must have full file system path.
     *
     * @param outFilename
     * @param taskDir
     * @param files list of strings or Files
     * @param compress true || false
     * @return
     */
    def zip(OutputStream outputStream, files, String prefix, compress) {
        ZipOutputStream zos = new ZipOutputStream(outputStream)
        if (!compress) {
            zos.setLevel(0)
        }
        try {
            files.each { file ->
                File f = file
                if (f.exists() && !f.isDirectory() && f.getName() != 'download.zip') {
                    //prefix is always the location of the relative dir
                    String name = zipEntryName(f.getPath(), prefix)
                    ZipEntry ze = new ZipEntry(name)
                    zos.putNextEntry(ze)
                    def is = new BufferedInputStream(new FileInputStream(f))
                    IOUtils.copy(is, zos)
                    is.close()
                    zos.closeEntry()
                } else {
                    log.warn("cannot find file to add: ${file.getPath()}")
                }
            }
        } catch (err) {
            log.error "failed to zip download", err
        } finally {
            try {
                zos.flush()
                zos.close()
            } catch (err) {
                log.error "failed to close download zip", err
            }
        }
    }

    def addZipEntriesFromDir(ZipOutputStream zos, File dir, String prefix) {
        for (File fs : dir.listFiles()) {
            if (fs.isDirectory()) {
                if (!Files.isSymbolicLink(fs.toPath())) {
                    addZipEntriesFromDir(zos, fs, prefix)
                } else {
                    log.warn("not including files in symbolic link: ${fs.getPath()}")
                }
            } else {
                //use relative file name
                String name = zipEntryName(fs.getPath(), prefix)
                ZipEntry ze = new ZipEntry(name)
                zos.putNextEntry(ze)
                def is = new BufferedInputStream(new FileInputStream(fs))
                IOUtils.copy(is, zos)
                is.close()
                zos.closeEntry()
            }
        }
    }

    def zipEntryName(String path, String prefix) {
        //default to data.dir location
        int length = grailsApplication.config.data.dir.toString().length()

        if (path.startsWith(prefix)) {
            //location is in prefix dir
            length = prefix.toString().length()

            //prefix may or nay not be supplied with '/'
            if (!prefix.endsWith('/')) {
                length++
            }
        }

        return path.substring(length)

    }
}
