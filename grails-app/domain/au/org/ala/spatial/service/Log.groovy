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

class Log {

    // date/time created
    Date created = new Date(System.currentTimeMillis())

    // category 1
    String category1

    // category 2
    String category2

    // data
    String data

    // userId
    String userId

    // sessionId
    String sessionId

    static constraints = {
        data nullable: true
        userId nullable: true
        sessionId nullable: true
        category1 nullable: true
        category2 nullable: true
    }

    static mapping = {
        data type: 'text'

        userId index: 'log_userId_idx'
        sessionId index: 'log_sessionId_idx'
        category1 index: 'log_category1_idx'
        category2 index: 'log_category1_idx'

        version false
    }
}
