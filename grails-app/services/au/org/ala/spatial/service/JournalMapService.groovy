package au.org.ala.spatial.service

import org.apache.commons.io.FileUtils
import org.geotools.geometry.text.WKTParser
import org.geotools.referencing.crs.DefaultGeographicCRS
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.opengis.geometry.Geometry

class JournalMapService {

    def grailsApplication

    def journalMapArticles = new ArrayList<JSONObject>()
    def journalMapLocations = new ArrayList<Map>()

    def search(String wkt, int max) {
        init();

        WKTParser wktParser = new WKTParser(DefaultGeographicCRS.WGS84)
        Geometry g = wktParser.parse(wkt)

        def found = []
        def count = 0

        for (Map loc : journalMapLocations) {
            if (loc.point.intersects(g)) {
                if (found.size() < max) found.add(journalMapArticles.get(loc.index))
                count++
            }
        }

        return [article: found, count: count]
    }

    def count(String wkt) {
        init();

        WKTParser wktParser = new WKTParser(DefaultGeographicCRS.WGS84)
        Geometry g = wktParser.parse(wkt)

        def count = 0

        for (Map loc : journalMapLocations) {
            if (loc.point.intersects(g)) {
                count++
            }
        }

        return count
    }

    def init() {
        if (journalMapArticles != null && journalMapArticles.size() > 0) {
            return;
        }

        //try disk cache
        File jaFile = new File(grailsApplication.config.data.dir + '/journalmap.json')

        if (jaFile.exists()) {
            JSONParser jp = new JSONParser();
            JSONArray ja = (JSONArray) jp.parse(FileUtils.readFileToString(jaFile));

            for (int i = 0; i < ja.size(); i++) {
                journalMapArticles.add((JSONObject) ja.get(i));
            }
        }

        //construct locations list
        for (int i = 0; i < journalMapArticles.size(); i++) {
            JSONArray locations = (JSONArray) journalMapArticles.get(i).get("locations");

            WKTParser wktParser = new WKTParser(DefaultGeographicCRS.WGS84)

            for (int j = 0; j < locations.size(); j++) {
                JSONObject l = (JSONObject) locations.get(j);
                double longitude = Double.parseDouble(l.get("longitude").toString());
                double latitude = Double.parseDouble(l.get("latitude").toString());
                journalMapLocations.add([point: wktParser.parse("POINT (" + longitude + " " + latitude + ")"), index: i])
            }
        }
    }
}
