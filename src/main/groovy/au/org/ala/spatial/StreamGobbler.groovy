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

package au.org.ala.spatial

import au.org.ala.spatial.dto.TaskWrapper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class StreamGobbler extends Thread {

    BufferedReader br
    String logPrefix
    TaskWrapper taskWrapper
    StringBuffer stringBuffer

    StreamGobbler(InputStream is, String logPrefix, TaskWrapper task, StringBuffer stringBuffer) {
        br = new BufferedReader(new InputStreamReader(is))
        this.logPrefix = logPrefix
        this.taskWrapper = task
        this.stringBuffer = stringBuffer
    }

    @Override
    void run() {
        try {
            String line
            while ((line = br.readLine()) != null) {
                if (taskWrapper != null) {
                    // surpress some JDK logging
                    if (!line.startsWith('INFO:') && !line.startsWith('NOTE:') && !line.contains('java.')) {
                        String str = logPrefix + ": " + line

                        // trim str to max 250 characters
                        if (str.length() > 250) {
                            str = str.substring(0, 250)
                        }

                        taskWrapper.task.history.put(System.currentTimeMillis() as String, str)
                    }
                }
                if (stringBuffer != null) {
                    stringBuffer.append(line).append('\n');
                }
                log.debug logPrefix + ": " + line
            }
        } catch (Exception e) {
            log.error 'task: ' + ', failed consuming log with prefix ' + logPrefix, e
        }
    }
}
