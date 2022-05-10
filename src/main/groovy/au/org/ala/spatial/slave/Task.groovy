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

import au.org.ala.spatial.StreamGobbler

class Task {
    static transients = ['proc',"errorGobbler", "outputGobbler"]
    Long id = System.currentTimeMillis()

    // date/time created
    Date created = new Date(System.currentTimeMillis())

    String taskId

    // name
    String name

    // log
    Map history = [:] as LinkedHashMap

    // parameters
    Map input = [:]

    // output
    Map output = [:]

    // full spec
    Map spec = [:]

    // status message
    String message = "starting"

    // finished flag
    Boolean finished = false

    Process proc
    StreamGobbler errorGobbler
    StreamGobbler outputGobbler

    void addHistory(String key, String value) {
        if (history.containsKey(key)) {
            String pre = history.get(key)
            value = pre + "\r\n" + value
        }
        history.put(key, value)
    }
}
