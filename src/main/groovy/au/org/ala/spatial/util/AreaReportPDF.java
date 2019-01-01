package au.org.ala.spatial.util;

import au.com.bytecode.opencsv.CSVReader;
import au.org.ala.spatial.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.util.StreamUtils;

import java.awt.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AreaReportPDF {
    private static final Logger LOGGER = Logger.getLogger(AreaReportPDF.class);

    private static final int PROGRESS_COUNT = 92;

    private static final String[] SPECIES_GROUPS = new String[]{};

    private String area_km;
    private String name;
    private String filePath;
    private String query;
    private String bbox;
    String geoserverUrl;
    String openstreetmapUrl;
    String biocacheServiceUrl;
    String biocacheHubUrl;

    private Map<String, String> distributions = new HashMap();
    private Map<String, Integer> distributionCounts = new HashMap();

    List<JSONObject> documents;

    String journalMapUrl;

    String journalMapData;
    String journalMapCount;

    private Map progress;
    private String serverUrl;

    String pid;
    String dataDir;

    public AreaReportPDF(String geoserverUrl, String openstreetmapUrl, String biocacheServiceUrl, String biocacheHubUrl,
                         String q, String pid,
                         String areaName,
                         String area_km,
                         Map progress, String serverUrl,
                         String outputPath,
                         String journalMapUrl, String dataDir) {
        this.dataDir = dataDir;
        this.journalMapUrl = journalMapUrl;
        this.name = areaName;
        this.progress = progress;
        this.serverUrl = serverUrl;
        this.geoserverUrl = geoserverUrl;
        this.openstreetmapUrl = openstreetmapUrl;
        this.biocacheServiceUrl = biocacheServiceUrl;
        this.biocacheHubUrl = biocacheHubUrl;
        this.pid = pid;
        this.query = q;
        this.area_km = area_km;
        filePath = outputPath;

        setProgress("preparing");

        init();
        build();

        setProgress("finished");
    }

    JSONArray pages;

    void init() {

        try {
            FileUtils.forceMkdir(new File(filePath + "/"));

            DecimalFormat df = new DecimalFormat("###,###.##");
            area_km = df.format(Double.parseDouble(area_km));

            JSONParser jp = new JSONParser();
            pages = (JSONArray) jp.parse(new String(getFileAsBytes("AreaReportDetails.json"), "UTF-8"));

            File styleFile = new File(filePath + "/areaReport.css");
            FileUtils.writeByteArrayToFile(styleFile, getFileAsBytes("areaReport.css"));

            mlArea = addObjectByPid(pid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void build() {

        int pageNumber = 1;
        for (Object o : pages) {
            try {
                exportPage(new File(filePath + "/report." + pageNumber + ".html"), (JSONObject) o);
            } catch (Exception e) {
                e.printStackTrace();
            }

            pageNumber = pageNumber + 1;
        }
    }

    void exportPage(File outputFile, JSONObject pageDefinition) throws Exception {
        StringBuilder sb = new StringBuilder();

        if ("title".equals(pageDefinition.get("type"))) {
            sb.append(makeTitlePage(pageDefinition));
        } else if ("file".equals(pageDefinition.get("type"))) {
            sb.append(makeFilePage(pageDefinition));
        } else if ("general".equals(pageDefinition.get("type"))) {
            sb.append(makeGeneralPage(pageDefinition));
        }

        FileUtils.writeStringToFile(outputFile, sb.toString());
    }

    byte[] getFileAsBytes(String file) throws Exception {
        File overrideFile = new File("/data/spatial-service/config/" + file);
        byte[] bytes = null;
        if (overrideFile.exists()) {
            FileUtils.readFileToByteArray(overrideFile);
        } else {
            bytes = StreamUtils.copyToByteArray(AreaReportPDF.class.getResourceAsStream("/areareport/" + file));
        }

        return bytes;
    }

    int reportItem = 0;
    String reportItemLabel = "";

    String makeGeneralPage(JSONObject pageDefinition) throws Exception {
        StringBuilder sb = new StringBuilder();

        JSONArray pageItems = (JSONArray) pageDefinition.get("items");
        Long children = 1L;

        if (pageDefinition.containsKey("subpages"))
            children = (Long) pageDefinition.get("subpages");

        for (int i = 0; i < children; i++) {
            sb.append("<div class='content'><table>");

            int itemIdx = 0;
            String title = null;

            for (Object o : pageItems) {
                setProgress("report item: " + reportItemLabel + " (" + reportItem + ")");
                reportItem++;

                JSONObject jo = (JSONObject) o;

                String value = getValue(jo, itemIdx, pageDefinition, i, title);

                // title
                if (itemIdx == 0) {
                    title = value;
                    value = "<h1 class='title' id='" + value + "'>" + value + "</h1>";
                }

                reportItemLabel = title;

                value = "<tr class='" + jo.get("type") + "'><td>" + value + "</td></tr>";

                sb.append(value);

                itemIdx++;
            }

            sb.append("</table></div>");
        }

        return sb.toString();
    }

    String makeFilePage(JSONObject pageDefinition) throws Exception {
        String pageFile = (String) pageDefinition.get("file");
        File overrideFile = new File("/data/spatial-service/config/" + pageFile);
        if (overrideFile.exists()) {
            return FileUtils.readFileToString(overrideFile);
        } else {
            return StreamUtils.copyToString(AreaReportPDF.class.getResourceAsStream("/areareport/" + pageFile), Charset.forName("UTF-8"));
        }
    }

    String makeTitlePage(JSONObject pageDefinition) throws Exception {
        StringBuilder sb = new StringBuilder();

        String imageFile = (String) pageDefinition.get("image");
        byte[] imageBytes = getFileAsBytes(imageFile);
        File headerImgFile = new File(filePath + "/header.jpg");
        FileUtils.writeByteArrayToFile(headerImgFile, imageBytes);

        sb.append("<div>");
        sb.append("<img class='imgHeader' src='header.jpg' width='100%' ></img>");
        sb.append("<table class='dashboard' >");

        int idx = 0;
        JSONArray counts = (JSONArray) pageDefinition.get("counts");
        for (Object o : counts) {
            JSONObject jo = (JSONObject) o;

            String value = getValue(jo, 0, null, 0, null);

            if (idx % 3 == 0) {
                sb.append("<tr>");
            }
            sb.append("<td>" + value + "</td>");
            if (idx % 3 == 2) {
                sb.append("</tr>");
            }

            idx++;
        }

        sb.append("</table></div>");

        return sb.toString();
    }

    int figureNumber = 1;

    String getValueForKey(String key, JSONObject item, Integer itemIdx, JSONObject parent, Integer parentIdx) {
        String value = null;

        if (item.containsKey(key)) {
            // value for item(key)
            value = (String) item.get(key);
        } else if (parent != null && parent.containsKey(key + itemIdx)) {
            // parent value for this itemIdx
            value = (String) ((JSONArray) parent.get(key + itemIdx)).get(parentIdx);
        } else if (parent != null && parent.containsKey(key + "s")) {
            // parent value for any itemIdx
            value = (String) ((JSONArray) parent.get(key + "s")).get(parentIdx);
        }

        return value;
    }

    String queryAndFq(String fq) throws UnsupportedEncodingException {
        if (fq == null) {
            return query;
        } else {
            return query + "&fq=" + URLEncoder.encode(fq, "UTF-8");
        }
    }

    String getValue(JSONObject item, Integer itemIdx, JSONObject parent, Integer parentIdx, String title) throws Exception {
        try {
            String type = (String) item.get("type");

            String text = getValueForKey("text", item, itemIdx, parent, parentIdx);
            String label = getValueForKey("label", item, itemIdx, parent, parentIdx);
            String fq = getValueForKey("fq", item, itemIdx, parent, parentIdx);

            String value = "";
            if (type.equals("species")) {
                if (item.containsKey("endemic") && "true".equalsIgnoreCase(item.get("endemic").toString())) {
                    // TODO: support fq term in getEndemicSpeciesCount, and confirm that this will give the expected result
                    value = getEndemicSpeciesCount(query);
                } else {
                    value = getSpeciesCount(queryAndFq(fq));
                }
            } else if (type.equals("occurrences")) {
                value = getOccurrenceCount(queryAndFq(fq));
            } else if (type.equals("checklists")) {
                initDistributionsCsv("checklists");
                value = distributionCounts.get("checklists").toString();
            } else if (type.equals("expertdistributions")) {
                initDistributionsCsv("expertdistributions");
                value = distributionCounts.get("expertdistributions").toString();
            } else if (type.equals("attribute")) {
                value = this.getClass().getDeclaredField((String) item.get("name")).get(this).toString();
            } else if (type.equals("journalmap")) {
                initJournalmapCsv();
                value = journalMapCount;
            } else if (type.equals("text")) {
                value = text;
            } else if (type.equals("table")) {
                // table types: species, tabulation, journalmap, distributions, checklists
                String table = (String) item.get("table");
                String tableValue = (String) item.get("value");
                String endemic = String.valueOf(item.get("endemic"));

                value = makeTable(fq, table, title, tableValue, endemic);
            } else if (type.equals("map")) {
                Double buffer = (Double) item.get("buffer");

                String layer = (String) item.get("layer");
                String layerStyle = (String) item.get("layerStyle");

                Integer red = (Integer) item.get("red");
                Integer green = (Integer) item.get("green");
                Integer blue = (Integer) item.get("blue");
                Boolean grid = (Boolean) item.get("grid");
                Boolean uncertainty = (Boolean) item.get("uncertainty");
                Integer size = (Integer) item.get("size");
                Double opacity = (Double) item.get("opacity");

                String legendUrl = (String) item.get("legendUrl");

                String map = makeMap(fq, buffer, layer, layerStyle, red, green, blue, opacity, grid, size, uncertainty);
                if (legendUrl != null) {
                    value = "<img class='imgWithLegend' src='" + map + "'></img>";
                    value += "<img class='legend' src='" + StringEscapeUtils.escapeHtml(legendUrl) + "'></img>";
                } else {
                    value = "<img src='" + map + "'></img>";
                }
            } else if (type.equals("figure")) {
                value += "<b class='figure" + figureNumber + "'>Figure " + figureNumber + ":</b> Map of " + title;
                figureNumber++;
            }

            if (label != null) {
                value = String.format(label, value);
            }

            return value;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    int imageNumber = 0;

    String makeMap(String fq, Double buffer, String layer, String layerStyle, Integer red, Integer green, Integer blue,
                   Double opacity, Boolean grid, Integer size, Boolean uncertainty) throws UnsupportedEncodingException {
        if (buffer == null) buffer = 0.05;
        if (red == null) red = 0;
        if (green == null) green = 0;
        if (blue == null) blue = 255;
        if (opacity == null) opacity = 0.6;
        if (grid == null) grid = false;
        if (size == null) size = 9;
        if (uncertainty == null) uncertainty = false;

        String imageFile = imageNumber + ".png";
        imageNumber++;

        String basemap = "Minimal";
        String type = "png";
        int resolution = 0;

        //convert POLYGON box to bounds
        List<Double> bbox = new ArrayList<>();
        String[] split = this.bbox.split(",");
        String[] p1 = split[1].split(" ");
        String[] p2 = split[3].split(" ");

        bbox.add(Math.min(Double.parseDouble(p1[0]), Double.parseDouble(p2[0])));
        bbox.add(Math.min(Double.parseDouble(p1[1]), Double.parseDouble(p2[1])));

        bbox.add(Math.max(Double.parseDouble(p1[0]), Double.parseDouble(p2[0])));
        bbox.add(Math.max(Double.parseDouble(p1[1]), Double.parseDouble(p2[1])));

        double step = (bbox.get(2) - bbox.get(0)) * buffer;
        double[] extents = new double[]{
                Math.max(-180, bbox.get(0) - step),
                Math.max(-85, bbox.get(1) - step),
                Math.min(180, bbox.get(2) + step),
                Math.min(85, bbox.get(3) + step)};

        double aspectRatio = 1.6;

        String ml = null;
        if (layer != null) {
            ml = createLayer(layer, 1.0f);
            if (layerStyle != null) {
                ml += "&styles=" + layerStyle;
            }
            ml += "&format_options=dpi:600";
        } else if (fq != null) {
            ml = createSpeciesLayer(query + "&fq=" + fq, red, green, blue, opacity.floatValue(), grid, size, uncertainty);
        }

        saveImage(imageFile, new PrintMapComposer(geoserverUrl, openstreetmapUrl, extents, basemap, new String[]{mlArea, ml}, aspectRatio, "", type, resolution, dataDir, null).get());

        return imageFile;
    }

    private boolean isCancelled() {
        return progress != null && progress.containsKey("cancel");
    }

    private void setProgress(String label) {
        progress.put(String.valueOf(System.currentTimeMillis()), label);
    }

    private FileWriter startHtmlOut(int fileNumber, String filename) throws Exception {
        FileWriter fw = new FileWriter(filename.replace(".", "." + fileNumber + "."));
        fw.write("<html>");
        fw.write("<head><link rel='stylesheet' type='text/css' href='" + serverUrl + "/static/area-report/areaReport.css'></link></head>");
        fw.write("<body>");
        return fw;
    }

    int tableNumber = 1;

    String makeTable(String fq, String table, String title, String value, String endemic) throws Exception {
        StringBuilder str = new StringBuilder();

        // table title
        str.append("<span class='tableNumber' id='").append(tableNumber).append("'><br></br><b>Table ").append(tableNumber).append(":</b> ").append(StringEscapeUtils.escapeHtml(title));
        tableNumber++;
        if (fq != null) {
            str.append("<a href='").append(biocacheHubUrl).append("/occurrences/search?q=").append(queryAndFq(fq)).append("'>(Link to full list)</a>");
        }
        str.append("</span>");

        // get column order for the table
        str.append("<table>");

        int[] columnOrder = null;
        String csv = null;
        boolean header = false;
        if ("species".equals(table)) {
            if ("true".equalsIgnoreCase(endemic)) {
                columnOrder = new int[]{0, 1, 2, 3};
                str.append("<tr><td>Family</td><td class='scientificName'>Scientific Name</td><td>Common Name</td><td>No. Occurrences</td></tr>");
                csv = getEndemicSpecies(queryAndFq(fq));
            } else {
                columnOrder = new int[]{8, 1, 10, 11};
                str.append("<tr><td>Family</td><td class='scientificName'>Scientific Name</td><td>Common Name</td><td>No. Occurrences</td></tr>");
                csv = speciesList(queryAndFq(fq));
            }
            header = true;
        } else if ("journalmap".equals(table)) {
            //authors (last_name, first_name), publish_year, title, publication.name, doi, JournalMap URL
            columnOrder = new int[]{0, 1, 2, 3, 4, 5};
            str.append("<tr><td>Author/s</td><td>Year</td><td>Title</td><td>Publication</td><td>DOI</td><td>URL</td></tr>");
            initJournalmapCsv();
            csv = journalMapData;
        } else if ("expertdistributions".equals(table)) {
            // expert distributions
            columnOrder = new int[]{4, 1, 3, 7, 8, 11, 12};
            str.append("<tr><td>Family</td><td class='scientificName' >Scientific Name</td><td>Common Name</td><td>Min Depth</td><td>Max Depth</td><td>Area Name</td><td>Area sq km</td></tr>");
            initDistributionsCsv(table);
            csv = distributions.get(table);
            header = true;
        } else if ("checklists".equals(table)) {
            // checklist
            columnOrder = new int[]{4, 1, 3, 11, 12};
            str.append("<tr><td>Family</td><td class='scientificName' >Scientific Name</td><td>Common Name</td><td>Area Name</td><td>Area sq km</td></tr>");
            initDistributionsCsv(table);
            csv = distributions.get(table);
            header = true;
        } else if ("tabulation".equals(table)) {
            // checklist
            columnOrder = new int[]{0, 1, 2};
            str.append("<tr><td>Class/Region</td><td>Area (sq km)</td><td>% of total area</td></tr>");
            csv = getTabulationCSV(value);
        }

        CSVReader r = new CSVReader(new StringReader(csv));
        if (header) r.readNext();

        String[] line;
        int row = 0;
        while ((line = r.readNext()) != null) {
            if (row % 2 == 0) {
                str.append("<tr class='odd'>");
            } else {
                str.append("<tr class='even'>");
            }
            for (int i = 0; i < columnOrder.length && columnOrder[i] < line.length; i++) {
                // permit unencoded <a>
                if (line[columnOrder[i]] != null && line[columnOrder[i]].startsWith("<a ")) {
                    str.append("<td><div>").append((line[columnOrder[i]])).append("</div></td>");
                } else {
                    str.append("<td><div>").append(StringEscapeUtils.escapeHtml(line[columnOrder[i]])).append("</div></td>");
                }
            }
            str.append("</tr>");

            row++;
        }

        str.append("</table>");

        return str.toString();
    }

    String mlArea;

    private String getTabulationCSV(String fieldId) throws ParseException {
        JSONParser jp = new JSONParser();

        String url = serverUrl + "/tabulation/" + fieldId + "/" + 5724983 + ".json";

        try {
            JSONArray tabulation = (JSONArray) jp.parse(Util.getUrl(url));

            //make csv
            StringBuilder sb = new StringBuilder();

            double totalArea = 0;
            for (Object o : tabulation) {
                JSONObject jo = (JSONObject) o;
                totalArea += Double.parseDouble(jo.get("area").toString()) / 1000000.0;
            }

            if (totalArea > 0) {
                int row = 0;
                for (Object o : tabulation) {
                    if (row > 0) {
                        sb.append("\n");
                    }

                    JSONObject jo = (JSONObject) o;
                    sb.append(StringEscapeUtils.escapeCsv(jo.get("name1").toString()));
                    sb.append(",");
                    sb.append(String.format("%.2f", Double.parseDouble(jo.get("area").toString()) / 1000000.0));
                    sb.append(",");
                    sb.append(String.format("%.2f", Double.parseDouble(jo.get("area").toString()) / 1000000.0 / totalArea * 100));
                    row++;
                }
            }

            return sb.toString();
        } catch (Exception e) {
            System.out.println(url);
            e.printStackTrace();
            throw e;
        }
    }

    private void initDistributionsCsv(String type) throws Exception {
        if (distributions.get(type) == null) {
            StringBuilder sb = new StringBuilder();
            String[] list = new String[0];

            String[] csv = null;

            JSONArray data = Util.getDistributionsOrChecklistsData(type.equals("checklists") ? "checklists" : "distributions", "5724983", null, null, serverUrl);
            csv = Util.getDistributionsOrChecklists(data);
            int count = 0;

            if (csv.length <= 0) {
                // 0 found
                count = 0;
            } else {
                if (type.equals("checklist")) {
                    csv = Util.getAreaChecklists(csv, data);
                }
                count = csv.length - 1;
            }

            distributions.put(type, StringUtils.join(csv, "\n"));
            distributionCounts.put(type, count);
        }
    }

    private void initJournalmapCsv() throws Exception {
        if (journalMapData == null) {
            JSONParser jp = new JSONParser();

            if (isCancelled()) return;
            StringBuilder sb = new StringBuilder();
            List<JSONObject> list = (JSONArray) ((JSONObject) jp.parse(Util.getUrl(serverUrl + "/journalMap/search?pid=" + pid))).get("article");
            //empty header
            sb.append("\n");

            for (JSONObject jo : list) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                //authors (last_name, first_name), publish_year, title, publication.name, doi, JournalMap URL
                if (jo.containsKey("authors")) {
                    String author = "";
                    JSONArray ja = (JSONArray) jo.get("authors");
                    for (int i = 0; i < ja.size(); i++) {
                        if (i > 0) author += ", ";
                        JSONObject o = (JSONObject) ja.get(i);
                        if (o.containsKey("last_name")) {
                            author += o.get("last_name") + ", ";
                        }
                        if (o.containsKey("first_name")) {
                            author += o.get("first_name");
                        }
                    }
                    sb.append("\"").append(author.replace("\"", "\"\"")).append("\".");
                }
                sb.append(",");
                if (jo.containsKey("publish_year")) {
                    sb.append("\"").append(jo.get("publish_year").toString().replace("\"", "\"\"")).append(".\"");
                }
                sb.append(",");
                if (jo.containsKey("title")) {
                    sb.append("\"").append(jo.get("title").toString().replace("\"", "\"\"")).append(".\"");
                }
                sb.append(",");
                if (jo.containsKey("publication")) {
                    JSONObject o = (JSONObject) jo.get("publication");
                    if (o.containsKey("name")) {
                        sb.append("\"").append(o.get("name").toString().replace("\"", "\"\"")).append(".\"");
                    }
                } else if (jo.containsKey("publication_name")) {
                    sb.append("\"").append(jo.get("publication_name").toString().replace("\"", "\"\"")).append(".\"");
                }
                sb.append(",");
                if (jo.containsKey("doi")) {
                    sb.append("\"").append(jo.get("doi").toString().replace("\"", "\"\"")).append(".\"");
                }
                sb.append(",");
                if (jo.containsKey("id")) {
                    String journalmapUrl = journalMapUrl;
                    String articleUrl = journalmapUrl + "articles/" + jo.get("id").toString();
                    sb.append("<a href='" + StringEscapeUtils.escapeHtml(articleUrl) + "'>" + StringEscapeUtils.escapeHtml(articleUrl) + "</a>");
                }
            }

            documents = list;

            if (documents.size() <= 0) {
                journalMapCount = "0";
            } else {
                journalMapCount = String.valueOf(documents.size());
            }

            journalMapData = sb.toString();
        }
    }

    JSONArray endemicData = null;

    private String getEndemicSpeciesCount(String q) {
        if (endemicData == null) {
            JSONParser jp = new JSONParser();
            JSONArray ja = null;
            try {
                ja = (JSONArray) jp.parse(Util.getUrl(biocacheServiceUrl + "/explore/endemic/species/" + q.replace("qid:", "") + "?facets=names_and_lsid"));

                endemicData = ja;
            } catch (Exception e) {
                LOGGER.error("failed to parse endemic species for " + query, e);
            }
        }

        if (endemicData != null) {
            return String.valueOf(endemicData.size());
        } else {
            return "0";
        }
    }

    private String getEndemicSpecies(String q) {
        getEndemicSpeciesCount(q);

        StringBuilder sb = new StringBuilder();

        if (endemicData != null) {
            try {
                for (Object o : endemicData) {
                    JSONObject jo = (JSONObject) o;

                    if (jo.containsKey("label")) {
                        String label = (String) jo.get("label");
                        if (label != null) {
                            String[] names_and_lsid = label.split("\\|");

                            String scientificName = names_and_lsid[0];
                            String commonName = names_and_lsid.length > 2 ? names_and_lsid[2] : "";
                            String kingdom = names_and_lsid.length > 3 ? names_and_lsid[3] : "";
                            String family = names_and_lsid.length > 4 ? names_and_lsid[4] : "";

                            Long count = (Long) jo.get("count");

                            if (sb.length() > 0) {
                                sb.append("\n");
                            }
                            sb.append(family).append(",").append(scientificName).append(",").append(commonName).append(",").append(count);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("failed to parse endemic species for " + query, e);
            }
        }

        return sb.toString();
    }

    void saveImage(String name, byte[] bytes) {
        try {
            new File(filePath + File.separator).mkdirs();
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath + File.separator + name));
            bos.write(bytes);
            bos.close();
        } catch (Exception e) {
            LOGGER.error("failed to write image to: " + filePath, e);
        }
    }

    String createLayer(String layerName, float opacity) {
        return addWMSLayer(layerName + "&opacity=" + opacity);
    }

    public String addObjectByPid(String pid) {

        JSONParser jp = new JSONParser();
        JSONObject obj = null;
        try {
            obj = (JSONObject) jp.parse(Util.getUrl(serverUrl + "/object/" + pid));
        } catch (ParseException e) {
            LOGGER.error("failed to parse for object: " + pid);
        }

        //add feature to the map as a new layer
        String mapLayer = "";
        try {
            if (pid.startsWith("ENVELOPE")) {
                mapLayer = addWMSLayer(obj.get("wmsurl") + "&opacity=0.6");
            } else {
                String filter = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><StyledLayerDescriptor version=\"1.0.0\" xmlns=\"http://www.opengis.net/sld\">"
                        + "<NamedLayer><Name>ALA:Objects</Name>"
                        + "<UserStyle><FeatureTypeStyle><Rule><Title>Polygon</Title><PolygonSymbolizer>"
                        + "<Stroke>"
                        + "<CssParameter name=\"stroke\">#FF0000</CssParameter>"
                        + "<CssParameter name=\"stroke-width\">4</CssParameter>"
                        + "</Stroke>"
                        + "<Fill>"
                        + "<GraphicFill><Graphic><Mark><WellKnownName>shape://times</WellKnownName><Stroke>"
                        + "<CssParameter name=\"stroke\">#FF0000</CssParameter>"
                        + "<CssParameter name=\"stroke-width\">1</CssParameter>"
                        + "</Stroke></Mark></Graphic></GraphicFill>"
                        + "</Fill>"
                        + "</PolygonSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>";
                mapLayer = addWMSLayer("Objects&viewparams=s:" + pid + "&sld_body=" + filter + "&opacity=0.6");
            }
        } catch (Exception e) {
        }
        //if the layer is a point create a radius
        bbox = obj.get("bbox").toString();

        return mapLayer;
    }

    String createSpeciesLayer(String q, int red, int green, int blue, float opacity, boolean grid, int size, boolean uncertainty) {
        Color c = new Color(red, green, blue);
        String hexColour = Integer.toHexString(c.getRGB() & 0x00ffffff);
        String envString = "";
        if (grid) {
            //colour mode is in 'filter' but need to move it to envString
            envString += "colormode:grid";
        } else {
            envString = "color:" + hexColour;
        }
        envString += ";name:circle;size:" + size + ";opacity:1";
        if (uncertainty) {
            envString += ";uncertainty:1";
        }

        String uri = biocacheServiceUrl + "/webportal/wms/reflect?";
        uri += "service=WMS&version=1.1.0&request=GetMap&format=image/png";
        uri += "&layers=ALA:occurrences";
        uri += "&transparent=true";
        uri += "&opacity=" + opacity;
        uri += "&CQL_FILTER=";


        String ml = uri + q;

        try {
            ml += "&ENV=" + URLEncoder.encode(envString.replace("'", "\\'"), "UTF-8");
        } catch (Exception e) {
        }

        return ml;
    }

    public String addWMSLayer(String name) {
        String mapLayer = geoserverUrl + "/wms/reflect?REQUEST=GetMap&VERSION=1.1.0&FORMAT=image/png&layers=ALA:" + name;

        return mapLayer;
    }

    String speciesList(String q) {
        String list = Util.getUrl(biocacheServiceUrl + "/occurrences/facets/download?facets=names_and_lsid&lookup=true&count=true&q=" + q);

        return list;
    }

    String getSpeciesCount(String q) {
        String list = Util.getUrl(biocacheServiceUrl + "/occurrence/facets?facets=names_and_lsid&flimit=0&q=" + q);

        JSONParser jp = new JSONParser();

        String count = "0";
        try {
            count = ((JSONObject) ((JSONArray) jp.parse(list)).get(0)).get("count").toString();
        } catch (Exception e) {

        }

        return count;
    }

    String getOccurrenceCount(String q) {
        String list = Util.getUrl(biocacheServiceUrl + "/occurrences/search?pageSize=0&facet=false&q=" + q);

        JSONParser jp = new JSONParser();

        String count = "0";
        try {
            count = ((JSONObject) jp.parse(list)).get("totalRecords").toString();
        } catch (Exception e) {

        }

        return count;
    }
}
