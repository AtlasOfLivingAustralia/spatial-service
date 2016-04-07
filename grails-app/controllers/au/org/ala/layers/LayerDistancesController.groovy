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

import au.org.ala.spatial.analysis.layers.LayerDistanceIndex
import grails.converters.JSON

class LayerDistancesController {

    def layerDistancesService

    def layerdistancesJSON() {
        render LayerDistanceIndex.loadDistances() as JSON
    }

    def csvRawnames() {
        render file: layerDistancesService.makeCSV("name"), contentType: 'text/csv'
    }

    def csv() {
        render file: layerDistancesService.makeCSV("displayname"), contentType: 'text/csv'
    }

}
