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
//@CompileStatic
class Task {

    // date/time created
    Date created = new Date(System.currentTimeMillis())

    // name identifier
    String name

    // log
    Map history = [:]

    // tag
    String tag

    // 0 = in_queue, 1 = running, 2 = cancelled, 3 = error, 4 = finished
    Integer status = 0

    // status message
    String message

    // status base url
    String url

    // running slave
    String slave

    // email
    String email

    // userId
    String userId

    // sessionId
    String sessionId

    static hasOne = [parent: Task]
    static hasMany = [children: Task, input: InputParameter, output: OutputParameter]

    static constraints = {
        message nullable: true
        url nullable: true
        slave nullable: true
        email nullable: true
        tag nullable: true
        userId nullable: true
        sessionId nullable: true
    }

    static mapping = {
        tag type: 'text'
        url type: 'text'
        email type: 'text'
        slave type: 'text'
        message type: 'text'

        userId index: 'task_userId_idx'
        sessionId index: 'task_sessionId_idx'
        name index: 'task_name_idx'

        history lazy: true, fetch: 'select'
        input lazy: true, fetch: 'select'
        output lazy: true, fetch: 'select'

        version false
    }
}
