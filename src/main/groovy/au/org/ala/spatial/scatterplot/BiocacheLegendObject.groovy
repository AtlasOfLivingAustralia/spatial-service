/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.scatterplot


import au.org.ala.spatial.legend.Legend
import au.org.ala.spatial.legend.LegendBuilder
import au.org.ala.spatial.legend.LegendObject
import au.org.ala.spatial.legend.QueryField
import com.opencsv.CSVReader
import groovy.util.logging.Slf4j
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException

import java.awt.*
import java.util.List

/**
 * @author Adam
 */
@Slf4j
//@CompileStatic
class BiocacheLegendObject extends LegendObject {
    //[0] is colour, [1] is count

    HashMap<Float, int[]> categoriesNumeric
    String csvLegend
    String rawCsvLegend
    String colourMode

    BiocacheLegendObject(String colourMode, String legend) {
        super((Legend) null, null)

        this.colourMode = colourMode
        rawCsvLegend = legend
        categories = new HashMap<String, int[]>()
        categoriesNumeric = new HashMap<Float, int[]>()
        if (legend != null && legend.startsWith("name,red,green,blue,count")) {
            loadFromCsv(legend)
        } else {
            loadFromJson(legend)
        }

    }

    private void loadFromJson(String legend) {

        boolean isDecade = colourMode.startsWith("occurrence_year_decade") || colourMode == "decade"
        boolean isYear = colourMode.contains("occurrence_year") && !isDecade

        int count = 0
        int sum = 0
        String colour = null
        String line = null
        StringBuilder sb = new StringBuilder()
        sb.append("name,red,green,blue,count")


        JSONParser parser = new JSONParser()
        JSONArray items = null
        try {
            items = (JSONArray) parser.parse(legend)
        } catch (ParseException e) {
            throw new RuntimeException(e)
        }

        JSONObject previous = null
//        log.debug("*** time to parse legend to JSON was " + (System.currentTimeMillis() - start) + "ms");

        categoryNameOrder = new String[items.size()]

        int i = 0
        for (Object ite : items) {
            JSONObject item = (JSONObject) ite
            if (isYear && item.get("name") != null) {
                item.put("name", item.get("name").toString().replace("-01-01T00:00:00Z", ""))
                item.put("name", item.get("name").toString().replace("-12-31T00:00:00Z", ""))
            } else if (isDecade && item.get("name") != null) {
                for (int j = 0; j <= 9; j++) {
                    item.put("name", item.get("name").toString().replace(j + "-01-01T00:00:00Z", "0"))
                    item.put("name", item.get("name").toString().replace(j + "-12-31T00:00:00Z", "0"))
                }
            }


            if ((Integer) item.get("count") == 0) {
                continue
            }

            int[] value = [new Color((Integer) item.get("red"), (Integer) item.get("green"), (Integer) item.get("blue")).getRGB(), (Integer) item.get("count")]
            categories.put(item.get("name").toString(), value)
            categoryNameOrder[i] = item.get("name").toString()
            try {
                double d = Double.NaN
                d = Double.parseDouble(item.get("name").toString())
                categoriesNumeric.put((float) d, value)
            } catch (Exception ignored) {

            }


            //check for endpoint (repitition of colour)
            if (previous != null
                    && previous.get("red") == item.get("red")
                    && previous.get("green") == item.get("green")
                    && previous.get("blue") == item.get("blue")
                    && !isDecade && !isYear) {
                if (count == 0) {
                    count = 1
                    sum = (Integer) previous.get("count")
                }
                count++
                sum += (Integer) item.get("count")
            } else {
                sb.append("\n")

                colour = item.get("red") + "," + item.get("green") + "," + item.get("blue")
                line = "\"" + item.get("name").toString().replace("\"", "\"\"") + "\"," + colour + "," + item.get("count")
                sb.append(line)
            }
            previous = item
            i++
        }
        if (count > 0) { //replace last line
            csvLegend = sb.toString().replace(line, count + " more" + "," + colour + "," + sum)
        } else {
            csvLegend = sb.toString()
        }

    }

    private void loadFromCsv(String legend) {
        List<String[]> csv = null
        try {
            CSVReader csvReader = new CSVReader(new StringReader(legend))
            csv = csvReader.readAll()
            csvReader.close()
        } catch (IOException ex) {
            log.error("error reading legend: ", ex)
        }

        boolean isDecade = colourMode.startsWith("occurrence_year_decade") || colourMode == "decade"
        boolean isYear = colourMode.contains("occurrence_year") && !isDecade

        int count = 0
        int sum = 0
        String colour = null
        String line = null
        StringBuilder sb = new StringBuilder()
        sb.append("name,red,green,blue,count")
        categoryNameOrder = new String[csv.size() - 1]
        for (int i = 1; i < csv.size(); i++) {
            String[] c = csv.get(i)
            String[] p = (i > 1) ? csv.get(i - 1) : null

            if (isYear) {
                c[0] = c[0].replace("-01-01T00:00:00Z", "")
                c[0] = c[0].replace("-12-31T00:00:00Z", "")
            } else if (isDecade) {
                for (int j = 0; j <= 9; j++) {
                    c[0] = c[0].replace(j + "-01-01T00:00:00Z", "0")
                    c[0] = c[0].replace(j + "-12-31T00:00:00Z", "0")
                }
            }

            int rc = Integer.parseInt(c[4])
            if (rc == 0) {
                continue
            }

            int[] value = [readColour(c[1], c[2], c[3]), rc]
            categories.put(c[0], value)
            categoryNameOrder[i - 1] = c[0]
            double d = Double.NaN
            d = Double.parseDouble(c[0])
            categoriesNumeric.put((float) d, value)

            //check for endpoint (repitition of colour)
            if (p != null && c.length > 4 && p.length > 4
                    && p[1] == c[1] && p[2] == c[2] && p[3] == c[3]
                    && !isDecade && !isYear) {
                if (count == 0) {
                    count = 1
                    sum = Integer.parseInt(p[4])
                }
                count++
                sum += Integer.parseInt(c[4])
            } else {
                sb.append("\n")

                colour = c[1] + "," + c[2] + "," + c[3]
                line = "\"" + c[0] + "\"," + colour + "," + c[4]
                sb.append(line)
            }
        }
        if (count > 0) { //replace last line
            csvLegend = sb.toString().replace(line, count + " more" + "," + colour + "," + sum)
        } else {
            csvLegend = sb.toString()
        }
    }

    /**
     * Get legend as a table.
     * <p/>
     * CSV
     * (header) name, red, green, blue, count CR
     * (records) string, 0-255, 0-255, 0-255, integer CR
     *
     * @return
     */
    @Override
    String getTable() {
        return csvLegend
    }

    @Override
    int getColour(String value) {
        int[] data = categories.get(value)

        if (data != null) {
            return data[0]
        } else {
            return DEFAULT_COLOUR
        }
    }

    @Override
    int getColour(float value) {
        int[] data = categoriesNumeric.get(value)

        if (data != null) {
            return data[0]
        } else {
            return DEFAULT_COLOUR
        }
    }

    @Override
    float[] getMinMax() {
        if (getNumericLegend() != null) {
            return super.getMinMax()
        }
        float[] minmax = new float[2]
        boolean first = true
        for (Float d : categoriesNumeric.keySet()) {
            if (!Float.isNaN(d)) {
                if (first || minmax[0] > d) {
                    minmax[0] = d
                }
                if (first || minmax[1] < d) {
                    minmax[1] = d
                }
                first = false
            }
        }
        if (!first) {
            return null
        } else {
            return minmax
        }
    }

    private static int readColour(String red, String green, String blue) {
        return new Color(Integer.parseInt(red), Integer.parseInt(green), Integer.parseInt(blue)).getRGB()
    }

    LegendObject getAsIntegerLegend() {
        if (colourMode == "decade") {
            double[] values = new double[categoriesNumeric.size()]
            int i = 0
            for (double d : categoriesNumeric.keySet()) {
                values[i++] = d
            }
            return LegendBuilder.legendForDecades(values, new QueryField(colourMode, QueryField.FieldType.INT))
        } else {
            int size = 0
            for (float f : categoriesNumeric.keySet()) {
                int[] v = categoriesNumeric.get(f)
                size += v[1]
            }
            double[] values = new double[size]
            int pos = 0
            for (float f : categoriesNumeric.keySet()) {
                int[] v = categoriesNumeric.get(f)
                for (int i = 0; i < v[1]; i++) {
                    values[pos] = f
                    pos++
                }
            }

            return LegendBuilder.legendFromDoubles(values, new QueryField(colourMode, QueryField.FieldType.INT))
        }
    }
}
