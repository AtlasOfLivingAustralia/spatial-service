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

class UploadedObjectsMetadata {

    Date time_last_updated = new Date(System.currentTimeMillis())
    String user_id
    String pid

    static constraints = {
        time_last_updated nullable: true
        user_id nullable: true
    }

    static mapping = {
        version(false)

        id name: 'pid'

        pid index: 'uploaded_objects_metadata_pid_idx'
    }
}
