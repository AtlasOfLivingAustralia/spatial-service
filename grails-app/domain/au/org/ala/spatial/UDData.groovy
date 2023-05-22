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

class UDData implements Serializable {
    Integer ud_header_id
    String ref
    String data_type
    Byte[] data

    static constraints = {
        ud_header_id nullable: true
        ref nullable: true
        data_type nullable: true
        data nullable: true
    }

    static mapping = {
        table name: 'ud_data_x'
        version(false)

        ud_header_id index: 'ud_data_x_header_idx'
        ref sqlType: "character varying(20)"
    }
}
