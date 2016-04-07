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

import org.apache.commons.io.FileUtils

class Utils {
    static void replaceTextInFile(String path, Map map) {
        def s = FileUtils.readFileToString(new File(path))
        map.each { String k, String v ->
            s = s.replaceAll(k, v)
        }
        FileUtils.writeStringToFile(new File(path), s)
    }

    static def runCmd(String[] cmd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(cmd)
        builder.environment().putAll(System.getenv())
        builder.redirectErrorStream(true)
        Process proc = builder.start()
        proc.waitFor()
    }
}
