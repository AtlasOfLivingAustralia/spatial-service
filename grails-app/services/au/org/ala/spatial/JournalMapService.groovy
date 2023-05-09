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

import au.org.ala.spatial.SpatialConfig
import grails.converters.JSON
import org.geotools.geometry.jts.WKTReader2
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.locationtech.jts.geom.Geometry

//@CompileStatic
class JournalMapService {

    SpatialConfig spatialConfig

    List<JSONObject> journalMapArticles = new ArrayList<JSONObject>()
    List<Loc> journalMapLocations = new ArrayList<Loc>()

    def search(String wkt, int max, int offset) {
        init()

        WKTReader2 reader2 = new WKTReader2()
        Geometry g = reader2.read(wkt)

        Set found = new HashSet()
        Set count = new HashSet()

        int pos = 0
        for (Loc loc : journalMapLocations) {
            try {
                if (loc.point.intersects(g)) {
                    if (pos >= offset && found.size() < max) found.add(journalMapArticles[loc.index])
                    count.add(journalMapArticles.get(loc.index))
                    pos++
                }
            } catch (ignore) {

            }
        }

        return [article: found, count: count.size()]
    }

    def count(String wkt) {
        init()

        WKTReader2 reader2 = new WKTReader2()
        Geometry g = reader2.read(wkt)

        def count = new HashSet()

        for (Loc loc : journalMapLocations) {
            try {
                if (loc.point.intersects(g)) {
                    count.add(journalMapArticles[loc.index])
                }
            } catch (ignored) {

            }
        }

        return count.size()
    }

    def init() {
        if (journalMapArticles != null && journalMapArticles.size() > 0) {
            return
        }

        //try disk cache
        File jaFile = new File("${spatialConfig.data.dir}/journalmap.json")

        if (jaFile.exists()) {
             JSON.parse(jaFile.text).eachWithIndex { it, index ->
                 journalMapArticles.add((JSONObject) it)
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
                        longitude = Double.parseDouble(((Map) l.get("coords")).get('lon').toString())
                        latitude = Double.parseDouble(((Map) l.get("coords")).get('lat').toString())
                    } else {
                        longitude = Double.parseDouble(l.get("longitude").toString())
                        latitude = Double.parseDouble(l.get("latitude").toString())
                    }

                    journalMapLocations.add(new Loc(reader2.read("POINT (" + longitude + " " + latitude + ")"), i))
                } catch (ignore) {
                    // failed to load this point
                }
            }
        }
    }

    class Loc {
        Geometry point
        Integer index

        Loc(Geometry point, Integer index) {
            this.point = point
            this.index = index
        }
    }
}
