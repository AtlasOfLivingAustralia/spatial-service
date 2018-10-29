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

import com.vividsolutions.jts.geom.Geometry
import org.apache.commons.io.FileUtils
import org.geotools.geometry.jts.WKTReader2
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

class JournalMapService {

    def grailsApplication

    def journalMapArticles = new ArrayList<JSONObject>()
    def journalMapLocations = new ArrayList<Map>()

    def search(String wkt, int max) {
        init()

        WKTReader2 reader2 = new WKTReader2()
        Geometry g = reader2.read(wkt)

        def found = new HashSet()
        def count = new HashSet()

        for (Map loc : journalMapLocations) {
            try {
                if (loc.point.intersects(g)) {
                    if (found.size() < max) found.add(journalMapArticles.get(loc.index))
                    count.add(journalMapArticles.get(loc.index))
                }
            } catch (err) {

            }
        }

        return [article: found, count: count.size()]
    }

    def count(String wkt) {
        init()

        WKTReader2 reader2 = new WKTReader2()
        Geometry g = reader2.read(wkt)

        def count = new HashSet()

        for (Map loc : journalMapLocations) {
            try {
                if (loc.point.intersects(g)) {
                    count.add(journalMapArticles.get(loc.index))
                }
            } catch (err) {

            }
        }

        return count.size()
    }

    def init() {
        if (journalMapArticles != null && journalMapArticles.size() > 0) {
            return
        }

        //try disk cache
        File jaFile = new File("${grailsApplication.config.data.dir}/journalmap.json")

        if (jaFile.exists()) {
            JSONParser jp = new JSONParser()
            JSONArray ja = (JSONArray) jp.parse(FileUtils.readFileToString(jaFile))

            for (int i = 0; i < ja.size(); i++) {
                journalMapArticles.add((JSONObject) ja.get(i))
            }
        }

        //construct locations list
        for (int i = 0; i < journalMapArticles.size(); i++) {
            JSONArray locations = (JSONArray) journalMapArticles.get(i).get("locations")

            WKTReader2 reader2 = new WKTReader2()

            for (int j = 0; j < locations.size(); j++) {
                JSONObject l = (JSONObject) locations.get(j)
                try {
                    double longitude
                    double latitude
                    if (l.containsKey('coords')) {
                        longitude = Double.parseDouble(l.get("coords").get('lon').toString())
                        latitude = Double.parseDouble(l.get("coords").get('lat').toString())
                    } else {
                        longitude = Double.parseDouble(l.get("longitude").toString())
                        latitude = Double.parseDouble(l.get("latitude").toString())
                    }

                    journalMapLocations.add([point: reader2.read("POINT (" + longitude + " " + latitude + ")"), index: i])
                } catch (err) {
                    // failed to load this point
                }
            }
        }
    }
}
