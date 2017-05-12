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

package au.org.ala.layers

import au.org.ala.layers.dao.ObjectDAO
import grails.converters.JSON

class JournalMapController {

    def journalMapService
    ObjectDAO objectDao

    def search() {
        String wkt = params?.pid ? objectDao.getObjectsGeometryById(params.pid.toString(), "wkt") : params?.wkt
        render journalMapService.search(wkt, params?.max ?: 10) as JSON
    }

    def count() {
        String wkt = params?.pid ? objectDao.getObjectsGeometryById(params.pid.toString(), "wkt") : params?.wkt
        def map = [count: journalMapService.count(wkt)]
        render map as JSON
    }
}
