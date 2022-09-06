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

import au.org.ala.spatial.slave.TaskWrapper
import org.apache.log4j.Logger

class StreamGobbler extends Thread {

    final Logger log = Logger.getLogger(this.class)

    BufferedReader br
    String logPrefix
    TaskWrapper task

    StreamGobbler(InputStream is, String logPrefix, TaskWrapper task) {
        br = new BufferedReader(new InputStreamReader(is))
        this.logPrefix = logPrefix
        this.task = task
    }

    @Override
    void run() {
        try {
            String line
            while ((line = br.readLine()) != null) {
                if (task != null) {
                    task.history.put(System.currentTimeMillis(), logPrefix + ": " + line)
                }
                log.debug logPrefix + ": " + line
            }
        } catch (Exception e) {
            log.error 'task: ' + ', failed consuming log with prefix ' + logPrefix, e
        }
    }
}
