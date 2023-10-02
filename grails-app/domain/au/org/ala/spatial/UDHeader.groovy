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

class UDHeader {

    Date upload_dt
    Date lastuse_dt
    String user_id
    String analysis_id
    String metadata

    String description
    Integer ud_header_id
    Integer data_size
    String record_type
    Date mark_for_deletion_dt
    String data_path

    static constraints = {
        upload_dt nullable: true
        lastuse_dt nullable: true
        user_id nullable: true
        analysis_id nullable: true
        metadata nullable: true
        description nullable: true
        data_size nullable: true
        record_type nullable: true
        mark_for_deletion_dt nullable: true
        data_path nullable: true
    }

    static mapping = {
        table 'ud_header'
        id name: "ud_header_id"
        version(false)

        metadata sqlType: "text"
    }
}
