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

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class Task {

    Long id = System.currentTimeMillis()

    // date/time created
    Date created = new Date(System.currentTimeMillis())

    String taskId

    // name
    String name

    // log
    Map history = [:]

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
}
