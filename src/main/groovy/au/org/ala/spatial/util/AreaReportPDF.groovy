package au.org.ala.spatial.util

import au.org.ala.spatial.Distributions
import au.org.ala.spatial.DistributionsService
import au.org.ala.spatial.JournalMapService
import au.org.ala.spatial.SpatialObjects
import au.org.ala.spatial.SpatialObjectsService
import au.org.ala.spatial.TabulationService
import au.org.ala.spatial.Task
import au.org.ala.spatial.Util
import au.org.ala.spatial.dto.Tabulation
import com.opencsv.CSVReader
import grails.converters.JSON
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import org.springframework.util.StreamUtils
import org.yaml.snakeyaml.util.UriEncoder

import java.awt.*
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.List

@Slf4j
@CompileStatic
class AreaReportPDF {

    static final int PROGRESS_COUNT = 92

    static final String[] SPECIES_GROUPS = new String[]{}

    String area_km
    String name
    String filePath
    String query
    String bbox
    String date
    String datetime
    String geoserverUrl
    String openstreetmapUrl
    String biocacheServiceUrl
    String biocacheHubUrl
    String listsUrl
    String bieUrl

    Map<String, String> distributions = new HashMap()
    Map<String, Integer> distributionCounts = new HashMap()

    String journalMapUrl

    String journalMapData
    String journalMapCount

    Map progress
    String serverUrl
    String configPath

    String pid
    String dataDir

    List<String> excludedPages

    DistributionsService distributionsService

    JournalMapService journalMapService

    TabulationService tabulationService

    SpatialObjectsService spatialObjectsService

    AreaReportPDF(DistributionsService distributionsService, JournalMapService journalMapService,
            TabulationService tabulationService, SpatialObjectsService spatialObjectsService,
                  String geoserverUrl, String openstreetmapUrl, String biocacheServiceUrl, String biocacheHubUrl,
                  String bieUrl, String listsUrl,
                  String q, String pid,
                  String areaName,
                  String area_km,
                  Map progress, String serverUrl,
                  String outputPath,
                  String journalMapUrl, String dataDir, String configPath, List<String> excludedPages) {
        this.distributionsService = distributionsService
        this.journalMapService = journalMapService
        this.tabulationService = tabulationService
        this.spatialObjectsService = spatialObjectsService
        this.dataDir = dataDir
        this.journalMapUrl = journalMapUrl
        this.name = areaName
        this.progress = progress
        this.serverUrl = serverUrl
        this.geoserverUrl = geoserverUrl
        this.openstreetmapUrl = openstreetmapUrl
        this.biocacheServiceUrl = biocacheServiceUrl
        this.biocacheHubUrl = biocacheHubUrl
        this.bieUrl = bieUrl
        this.listsUrl = listsUrl
        this.pid = pid
        this.query = q
        this.area_km = area_km
        this.filePath = outputPath
        this.configPath = configPath
        this.excludedPages = excludedPages

        this.date = new SimpleDateFormat("dd/MM/yyyy").format(new Date())
        this.datetime = new SimpleDateFormat("dd/MM/yyyy hh:mm").format(new Date())

        this.bbox = null

        setProgress("preparing")

        init()
        build()

        setProgress("finished")
    }

    JSONArray pages

    void init() {

        try {
            FileUtils.forceMkdir(new File(filePath + "/"))

            pages = (JSONArray) JSON.parse(new String(getFileAsBytes("AreaReportDetails.json"), StandardCharsets.UTF_8))

            File styleFile = new File(filePath + "/areaReport.css")
            FileUtils.writeByteArrayToFile(styleFile, getFileAsBytes("areaReport.css"))

            File footer = new File(filePath + "/footer.html")
            FileUtils.writeByteArrayToFile(footer, getFileAsBytes("footer.html"))

            File toc = new File(filePath + "/tableOfContents.html")
            FileUtils.writeByteArrayToFile(toc, getFileAsBytes("tableOfContents.html"))

            mlArea = addObjectByPid(pid)
            DecimalFormat df = new DecimalFormat("###,###.##")
            area_km = df.format(Double.parseDouble(area_km))

        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    void build() {

        int pageNumber = 1
        for (Object o : pages) {
            try {
                exportPage(new File(filePath + "/report." + pageNumber + ".html"), (JSONObject) o)
            } catch (Exception e) {
                e.printStackTrace()
            }

            pageNumber = pageNumber + 1
        }
    }

    void exportPage(File outputFile, JSONObject pageDefinition) throws Exception {
        StringBuilder sb = new StringBuilder()

        if ("title" == pageDefinition.get("type")) {
            sb.append(makeTitlePage(pageDefinition))
        } else if ("file" == pageDefinition.get("type")) {
            sb.append(makeFilePage(pageDefinition))
        } else if ("general" == pageDefinition.get("type")) {
            sb.append(makeGeneralPage(pageDefinition))
        } else if ("page-header" == pageDefinition.get("type")) {
            sb.append(makeHeader(pageDefinition))
        }

        outputFile.write(sb.toString())
    }

    byte[] getFileAsBytes(String file) throws Exception {
        File overrideFile = new File(this.configPath + "/" + file)
        byte[] bytes = null
        if (overrideFile.exists()) {
            bytes = FileUtils.readFileToByteArray(overrideFile)
        } else {
            bytes = StreamUtils.copyToByteArray(AreaReportPDF.class.getResourceAsStream("/areareport/" + file))
        }

        return bytes
    }

    int reportItem = 0
    String reportItemLabel = ""

    String makeHeader(JSONObject pageDefinition) throws Exception {
        return makePage(pageDefinition, true)
    }

    String makeGeneralPage(JSONObject pageDefinition) throws Exception {
        return makePage(pageDefinition, false)
    }

    String makePage(JSONObject pageDefinition, boolean pageFragment) throws Exception {
        StringBuilder sb = new StringBuilder()

        JSONArray pageItems = (JSONArray) pageDefinition.get("items")
        Long children = 1L

        if (pageDefinition.containsKey("subpages"))
            children = (Long) pageDefinition.get("subpages")

        for (int i = 0; i < children; i++) {
            StringBuilder currentPage = new StringBuilder()
            currentPage.append("<div class='content'><table>")

            int itemIdx = 0
            String title = null

            boolean excludePage = false

            for (Object o : pageItems) {
                if (excludePage) {
                    continue
                }

                setProgress("report item: " + reportItemLabel + " (" + reportItem + ")")
                reportItem++

                JSONObject jo = (JSONObject) o

                String value = getValue(jo, itemIdx, pageDefinition, i, title)

                // title
                if (itemIdx == 0 && !pageFragment) {
                    title = value
                    if (!value.contains("<")) {
                        value = "<h1 class='title' id='" + value + "'>" + value + "</h1>"
                    } else {
                        value = "<h1 class='title'>" + value + "</h1>"
                    }

                    excludePage = excludedPages.contains(title)
                }

                reportItemLabel = title

                if (value != null && "null" != value) {
                    value = "<tr class='" + jo.get("type") + " " + jo.getOrDefault("class", "") + "'><td>" + value + "</td></tr>"

                    currentPage.append(value)
                }

                itemIdx++
            }

            currentPage.append("</table></div>")

            if (!excludePage) {
                sb.append(currentPage)
            }
        }

        return sb.toString()
    }

    String makeFilePage(JSONObject pageDefinition) throws Exception {
        String pageFile = (String) pageDefinition.get("file")
        File overrideFile = new File(configPath + "/" + pageFile)
        if (overrideFile.exists()) {
            overrideFile.text
        } else {
            this.class.getResourceAsStream("/areareport/" + pageFile).text
        }
    }

    String makeTitlePage(JSONObject pageDefinition) throws Exception {
        StringBuilder sb = new StringBuilder()

        String imageFile = (String) pageDefinition.get("image")
        byte[] imageBytes = getFileAsBytes(imageFile)
        File headerImgFile = new File(filePath + "/header.jpg")
        FileUtils.writeByteArrayToFile(headerImgFile, imageBytes)

        sb.append("<div>")
        sb.append("<img class='imgHeader' src='header.jpg' width='100%' ></img>")
        sb.append("<table class='dashboard' >")

        int idx = 0
        JSONArray counts = (JSONArray) pageDefinition.get("counts")
        for (Object o : counts) {
            JSONObject jo = (JSONObject) o

            String value = getValue(jo, 0, null, 0, null)

            if (idx % 3 == 0) {
                sb.append("<tr>")
            }
            sb.append("<td>" + value + "</td>")
            if (idx % 3 == 2) {
                sb.append("</tr>")
            }

            idx++
        }

        sb.append("</table></div>")

        return sb.toString()
    }

    int figureNumber = 1

    static String getValueForKey(String key, JSONObject item, Integer itemIdx, JSONObject parent, Integer parentIdx) {
        String value = null

        if (item.containsKey(key)) {
            // value for item(key)
            value = (String) item.get(key)
        } else if (parent != null && parent.containsKey(key + itemIdx)) {
            // parent value for this itemIdx
            value = (String) ((JSONArray) parent.get(key + itemIdx)).get(parentIdx)
        } else if (parent != null && parent.containsKey(key + "s")) {
            // parent value for any itemIdx
            value = (String) ((JSONArray) parent.get(key + "s")).get(parentIdx)
        }

        return value
    }

    String queryAndFq(String fq) throws UnsupportedEncodingException {
        if (fq == null) {
            return query
        } else {
            return query + "&fq=" + UriEncoder.encode(fq)
        }
    }

    String getValue(JSONObject item, Integer itemIdx, JSONObject parent, Integer parentIdx, String title) throws Exception {
        try {
            String type = (String) item.get("type")

            String text = getValueForKey("text", item, itemIdx, parent, parentIdx)
            String label = getValueForKey("label", item, itemIdx, parent, parentIdx)
            String fq = getValueForKey("fq", item, itemIdx, parent, parentIdx)
            String conservationListsString = getValueForKey("conservationLists", item, itemIdx, parent, parentIdx)
            String kingdom = getValueForKey("kingdom", item, itemIdx, parent, parentIdx)
            String area = getValueForKey("area", item, itemIdx, parent, parentIdx)
            String dataResourceId = getValueForKey("dataResourceId", item, itemIdx, parent, parentIdx)

            String[] conservationLists = null
            if (conservationListsString != null) {
                conservationLists = conservationListsString.split(",")
            }

            String value = ""
            if (type == "species") {
                if (item.containsKey("endemic") && "true".equalsIgnoreCase(item.get("endemic").toString())) {
                    // TODO: support fq term in getEndemicSpeciesCount, and confirm that this will give the expected result
                    value = getEndemicSpeciesCount(query)
                } else {
                    value = getSpeciesCount(queryAndFq(fq))
                }
            } else if (type == "occurrences") {
                value = getOccurrenceCount(queryAndFq(fq))
            } else if (type == "checklists") {
                initDistributionsCsv("checklists", pid, null, null)
                value = distributionCounts.get("checklists" + pid + null + null).toString()
            } else if (type == "expertdistributions") {
                initDistributionsCsv("expertdistributions", pid, null, null)
                value = distributionCounts.get("expertdistributions" + pid + null + null).toString()
            } else if (type == "attribute") {
                value = this.getClass().getDeclaredField((String) item.get("name")).get(this).toString()
            } else if (type == "journalmap") {
                initJournalmapCsv()
                value = journalMapCount
            } else if (type == "image") {
                value = "<img src='" + text + "' width='100%' ></img>"
            } else if (type == "text") {
                value = text
            } else if (type == "table") {
                // table types: species, tabulation, journalmap, distributions, checklists
                String table = (String) item.getOrDefault("table", null)
                String tableValue = (String) item.getOrDefault("value", null)
                String endemic = String.valueOf(item.getOrDefault("endemic", null))

                value = makeTable(fq, table, title, tableValue, endemic, conservationLists, area, kingdom, dataResourceId)
            } else if (type == "map") {
                Double buffer = (Double) item.getOrDefault("buffer", null)

                String layer = (String) item.getOrDefault("layer", null)
                String layerStyle = (String) item.getOrDefault("layerStyle", null)

                Integer red = (Integer) item.getOrDefault("red", null)
                Integer green = (Integer) item.getOrDefault("green", null)
                Integer blue = (Integer) item.getOrDefault("blue", null)
                Boolean grid = (Boolean) item.getOrDefault("grid", null)
                Boolean uncertainty = (Boolean) item.getOrDefault("uncertainty", null)
                Integer size = (Integer) item.getOrDefault("size", null)
                Double opacity = (Double) item.getOrDefault("opacity", null)

                String legendUrl = (String) item.getOrDefault("legendUrl", null)

                String map = makeMap(fq, buffer, layer, layerStyle, red, green, blue, opacity, grid, size, uncertainty)
                if (legendUrl != null) {
                    value = "<img class='imgWithLegend' src='" + map + "'></img>"
                    value += "<img class='legend' src='" + StringEscapeUtils.escapeHtml(legendUrl) + "'></img>"
                } else {
                    value = "<img src='" + map + "'></img>"
                }
            } else if (type == "figure") {
                String figureTitleHtml = new String(getFileAsBytes("figureTitle.html"))
                value += figureTitleHtml.replaceAll("<figureNumber/>", String.valueOf(figureNumber)).replaceAll("<title/>", title)
                figureNumber++
            }

            if (label != null) {
                value = String.format(label, value)
            }

            return value
        } catch (Exception e) {
            e.printStackTrace()
        }
        return null
    }

    int imageNumber = 0

    String makeMap(String fq, Double buffer, String layer, String layerStyle, Integer red, Integer green, Integer blue,
                   Double opacity, Boolean grid, Integer size, Boolean uncertainty) throws UnsupportedEncodingException {
        if (buffer == null) buffer = 0.05d
        if (red == null) red = 0
        if (green == null) green = 0
        if (blue == null) blue = 255
        if (opacity == null) opacity = 0.6d
        if (grid == null) grid = false
        if (size == null) size = 9
        if (uncertainty == null) uncertainty = false

        String imageFile = imageNumber + ".png"
        imageNumber++

        String basemap = "Minimal"
        String type = "png"
        int resolution = 0

        //convert POLYGON box to bounds
        List<Double> bbox = new ArrayList<>()
        String[] split = this.bbox.split(",")
        String[] p1 = split[1].split(" ")
        String[] p2 = split[3].split(" ")

        bbox.add(Math.min(Double.parseDouble(p1[0]), Double.parseDouble(p2[0])))
        bbox.add(Math.min(Double.parseDouble(p1[1]), Double.parseDouble(p2[1])))

        bbox.add(Math.max(Double.parseDouble(p1[0]), Double.parseDouble(p2[0])))
        bbox.add(Math.max(Double.parseDouble(p1[1]), Double.parseDouble(p2[1])))

        double stepx = (bbox.get(2) - bbox.get(0)) * buffer
        double stepy = (bbox.get(3) - bbox.get(1)) * buffer
        double[] extents = new double[]{
                Math.max(-180, bbox.get(0) - stepx),
                Math.max(-85, bbox.get(1) - stepy),
                Math.min(180, bbox.get(2) + stepx),
                Math.min(85, bbox.get(3) + stepy)}

        // increase extents to match EPSG:3857
        int[] windowSize = new int[2]
        windowSize[0] = SpatialUtils.convertLngToPixel(extents[2]) - SpatialUtils.convertLngToPixel(extents[0])
        windowSize[1] = SpatialUtils.convertLatToPixel(extents[1]) - SpatialUtils.convertLatToPixel(extents[3])

        double aspectRatio = 1.6

        if (windowSize[0] / (double) windowSize[1] < aspectRatio) {
            // increase width
            int width = (int) (windowSize[1] * aspectRatio)
            extents[0] = SpatialUtils.convertPixelToLng(SpatialUtils.convertLngToPixel(extents[0]) - (width - windowSize[0]) / 2 as int)
            extents[2] = SpatialUtils.convertPixelToLng(SpatialUtils.convertLngToPixel(extents[2]) + (width - windowSize[0]) / 2 as int)
        } else if (windowSize[0] / (double) windowSize[1] > aspectRatio) {
            // increase height
            int height = (int) (windowSize[0] / aspectRatio)
            extents[1] = SpatialUtils.convertPixelToLat(SpatialUtils.convertLatToPixel(extents[1]) - (height - windowSize[1]) / 2 as int)
            extents[3] = SpatialUtils.convertPixelToLat(SpatialUtils.convertLatToPixel(extents[3]) + (height - windowSize[1]) / 2 as int)
        }

        String ml = null
        if (layer != null) {
            ml = createLayer(layer, 1.0f)
            if (layerStyle != null) {
                ml += "&styles=" + layerStyle
            }
            ml += "&format_options=dpi:600"
        } else if (fq != null) {
            ml = createSpeciesLayer(query + "&fq=" + fq, red, green, blue, opacity.floatValue(), grid, size, uncertainty)
        }

        saveImage(imageFile, new PrintMapComposer(geoserverUrl, openstreetmapUrl, extents, basemap, new String[]{mlArea, ml}, aspectRatio, "", type, resolution, dataDir, null).get())

        return imageFile
    }

    private boolean isCancelled() {
        return progress != null && progress.containsKey("cancel")
    }

    private void setProgress(String label) {
        if (progress != null) {
            progress.put(String.valueOf(System.currentTimeMillis()), label)
        } else {
            log.error(label)
        }
    }

    private FileWriter startHtmlOut(int fileNumber, String filename) throws Exception {
        FileWriter fw = new FileWriter(filename.replace(".", "." + fileNumber + "."))
        fw.write("<html>")
        fw.write("<head><link rel='stylesheet' type='text/css' href='" + serverUrl + "/static/area-report/areaReport.css'></link></head>")
        fw.write("<body>")
        return fw
    }

    int tableNumber = 1

    String makeTable(String fq, String table, String title, String value, String endemic, String[] conservationLists, String area, String kingdom, String dataResourceId) throws Exception {
        StringBuilder str = new StringBuilder()

        // table title
        String tableLink = ""
        if (fq != null) {
            tableLink = new String(getFileAsBytes("tableLink.html"))
            tableLink = tableLink.replaceAll("<url/>", biocacheHubUrl + "/occurrences/search?q=" + queryAndFq(fq))
        }

        String tableTitle = new String(getFileAsBytes("tableTitle.html"))
        tableTitle = tableTitle.replaceAll("<tableNumber/>", String.valueOf(tableNumber))
                .replaceAll("<title/>", StringEscapeUtils.escapeHtml(title))
                .replaceAll("<tableLink/>", tableLink)
        str.append(tableTitle)
        tableNumber++

        // get column order for the table
        str.append("<table>")

        int[] columnOrder = null
        int[] columnOrder2 = new int[0]
        String csv = null
        boolean header = false
        if ("species" == table) {
            if ("true".equalsIgnoreCase(endemic)) {
                columnOrder = new int[]{0, 1, 2, 3}
                String endemicTableHeader = new String(getFileAsBytes("endemicTableHeader.html"))
                str.append(endemicTableHeader)
                csv = getEndemicSpecies(queryAndFq(fq))
            } else {
                columnOrder = new int[]{8, 1, 10, 11}
                String speciesTableHeader = new String(getFileAsBytes("speciesTableHeader.html"))
                str.append(speciesTableHeader)
                csv = speciesList(queryAndFq(fq))
            }
            header = true
        } else if ("journalmap" == table) {
            //authors (last_name, first_name), publish_year, title, publication.name, doi, JournalMap URL
            columnOrder = new int[]{0, 1, 2, 3, 4, 5}
            String journalMapTableHeader = new String(getFileAsBytes("journalMapTableHeader.html"))
            str.append(journalMapTableHeader)
            initJournalmapCsv()
            csv = journalMapData
        } else if ("expertdistributions" == table) {
            // expert distributions
            columnOrder = new int[]{4, 1, 3, 7, 8, 11, 12}
            String expertDistributionsTableHeader = new String(getFileAsBytes("expertDistributionsTableHeader.html"))
            str.append(expertDistributionsTableHeader)
            csv = initDistributionsCsv(table, pid, null, null)
            header = true
        } else if ("checklists" == table) {
            // checklist
            columnOrder = new int[]{4, 1, 3, 11, 12}
            String checklistsTableHeader = new String(getFileAsBytes("checklistsTableHeader.html"))
            str.append(checklistsTableHeader)
            csv = initDistributionsCsv(table, pid, null, null)
            header = true
        } else if ("tabulation" == table) {
            // checklist
            columnOrder = new int[]{0, 1, 2}
            String tabulationTableHeader = new String(getFileAsBytes("tabulationTableHeader.html"))
            str.append(tabulationTableHeader)
            csv = getTabulationCSV(value)
        } else if ("distributions" == table) {
            columnOrder = new int[]{1, 2}
            columnOrder2 = new int[]{4, 5}
            String distributionsTableHeader = new String(getFileAsBytes("distributionsTableHeader.html"))
            StringBuilder sb = new StringBuilder()
            if (conservationLists != null) {
                for (String conservationList : conservationLists) {
                    sb.append("<td>").append(getSpeciesListName(conservationList)).append("</td>")
                }
            }
            str.append(distributionsTableHeader.replaceAll("<contents/>", sb.toString()))

            String wkt = pid
            if (StringUtils.trimToNull(area) != null) {
                WKTReader reader = new WKTReader()
                Geometry g1 = reader.read(getWkt(pid))
                Geometry g2 = reader.read(getWkt(area))
                Geometry g3 = g1.intersection(g2)
                if (g3.getArea() > 0) {
                    wkt = g3.toString()
                    csv = initDistributionsCsv(table, wkt, dataResourceId, StringUtils.trimToNull(kingdom))
                } else {
                    csv = ""
                }
            } else {
                csv = initDistributionsCsv(table, wkt, dataResourceId, StringUtils.trimToNull(kingdom))
            }

            header = true
        }

        CSVReader r = new CSVReader(new StringReader(csv))
        if (header) r.readNext()

        String[] line
        int row = 0
        while ((line = r.readNext()) != null) {
            if (row % 2 == 0) {
                str.append("<tr class='odd'>")
            } else {
                str.append("<tr class='even'>")
            }
            if (conservationLists != null) {
                str.append("<td><div>").append(StringEscapeUtils.escapeHtml(familyToClass.get(line[0].toLowerCase()))).append("</div></td>")
            }
            for (int i = 0; i < columnOrder.length && columnOrder[i] < line.length; i++) {
                // permit unencoded <a>
                if (line[columnOrder[i]] != null && line[columnOrder[i]].startsWith("<a ")) {
                    str.append("<td><div>").append((line[columnOrder[i]])).append("</div></td>")
                } else {
                    str.append("<td><div>").append(StringEscapeUtils.escapeHtml(line[columnOrder[i]])).append("</div></td>")
                }
            }
            if (conservationLists != null) {
                for (String conservationList : conservationLists) {
                    String v = getSpeciesListValue(conservationList, line[3], "status")
                    str.append("<td><div>").append(StringEscapeUtils.escapeHtml(v)).append("</div></td>")
                }
            }
            for (int i = 0; i < columnOrder2.length && columnOrder2[i] < line.length; i++) {
                // permit unencoded <a>
                if (line[columnOrder2[i]] != null && line[columnOrder2[i]].startsWith("<a ")) {
                    str.append("<td><div>").append((line[columnOrder2[i]])).append("</div></td>")
                } else {
                    str.append("<td><div>").append(StringEscapeUtils.escapeHtml(line[columnOrder2[i]])).append("</div></td>")
                }
            }
            str.append("</tr>")

            row++
        }

        str.append("</table>")

        return str.toString()
    }

    private String getSpeciesListName(String dr) {
        try {
            String txt = (String) Util.urlResponse("GET", listsUrl + "/ws/speciesList/" + dr).get("text")

            JSONObject jo = (JSONObject) JSON.parse(txt)

            return (String) jo.get("listName")
        } catch (Exception e) {
            e.printStackTrace()
        }
        return ""
    }

    private final Map<String, JSONArray> speciesListValues = new HashMap()

    private String getSpeciesListValue(String dr, String lsid, String key) {
        JSONArray values = speciesListValues.get(dr)

        try {
            if (values == null) {
                boolean hasAnotherPage = true
                int max = 400
                int offset = 0

                while (hasAnotherPage) {
                    String txt = (String) Util.urlResponse("GET", listsUrl + "/speciesListItems/" + dr + "?includeKVP=true&max=" + max + "&offset=" + offset).get("text")

                    JSONArray newValues = (JSONArray) JSON.parse(txt)
                    values.addAll(newValues)

                    hasAnotherPage = newValues.size() == max
                    offset += max
                }

                speciesListValues.put(dr, values)
            }
        } catch (Exception e) {
            e.printStackTrace()
        }

        for (Object o : values) {
            JSONObject jo = (JSONObject) o
            if (lsid.equalsIgnoreCase((String) jo.getOrDefault("lsid", null))) {
                JSONArray kvp = (JSONArray) jo.get("kvpValues")
                for (Object o2 : kvp) {
                    JSONObject jo2 = (JSONObject) o2
                    if (key == jo2.getOrDefault("key", null)) {
                        String s = (String) jo2.getOrDefault("value", null)

                        // keep initials only
                        return s.replaceAll("[a-z]", "")
                    }
                }
            }
        }

        return ""
    }

    String mlArea

    private String getTabulationCSV(String fieldId)  {
        try {
            List<Tabulation> list = tabulationService.getTabulationSingle(fieldId, pid)

            //make csv
            StringBuilder sb = new StringBuilder()

            double totalArea = 0
            list.each {
                totalArea += it.area / 1000000.0
            }

            if (totalArea > 0) {
                int row = 0
                list.each {
                    if (row > 0) {
                        sb.append("\n")
                    }

                    sb.append(StringEscapeUtils.escapeCsv(it.name1))
                    sb.append(",")
                    sb.append(String.format("%.2f", it.area / 1000000.0))
                    sb.append(",")
                    sb.append(String.format("%.2f", it.area / 1000000.0 / totalArea * 100))
                    row++
                }
            }

            return sb.toString()
        } catch (Exception e) {
            e.printStackTrace()
            throw e
        }
    }

    private final Map<String, List<String>> kingdomFamilies = new HashMap()
    private final Map<String, String> familyToClass = new HashMap()

    private List<String> getFamilyLsids(String kingdom) {
        if (kingdom == null) {
            return null
        } else if (kingdomFamilies.size() == 0) {
            // init kingdomFamilies
            try {
                String text = (String) Util.urlResponse("GET", bieUrl + "ws/search.json?fq=rank:family&pageSize=100000").get("text")
                JSONObject jo = (JSONObject) JSON.parse(text)
                JSONArray ja = (JSONArray) ((JSONObject) jo.get("searchResults")).get("results")

                for (Object o : ja) {
                    JSONObject jo1 = (JSONObject) o
                    String k = (String) jo1.getOrDefault("kingdom", null)
                    String f = (String) jo1.getOrDefault("familyGuid", null)

                    if (k != null && f != null) {
                        List<String> l = kingdomFamilies.get(k.toLowerCase())
                        if (l == null) {
                            l = new ArrayList()
                            kingdomFamilies.put(k.toLowerCase(), l)
                        }
                        l.add(f)
                    }

                    String fn = (String) jo1.getOrDefault("family", null)
                    String cn = (String) jo1.getOrDefault("class", null)
                    if (fn != null && cn != null) {
                        familyToClass.put(fn.toLowerCase(), cn)
                    }
                }
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        return kingdomFamilies.get(kingdom.toLowerCase())
    }

    private String initDistributionsCsv(String type, String pid, String dataResourceId, String kingdom) throws Exception {
        String key = type + pid + dataResourceId + kingdom
        String result = distributions.get(key)
        if (result == null) {
            String[] csv = null

            List<String> familyLsids = getFamilyLsids(kingdom)

            List<Distributions> data = distributionsService.queryDistributions([wkt: pid, dataResourceUid: dataResourceId, familyLsid: familyLsids], true,
                    type == "expertdistributions" ? Distributions.EXPERT_DISTRIBUTION : Distributions.SPECIES_CHECKLIST)

            if (dataResourceId != null) {
                csv = Util.getDistributionsOrChecklistsRollup(data)
            } else {
                csv = Util.getDistributionsOrChecklists(data)
            }
            int count = 0

            if (csv.length <= 0) {
                // 0 found
                count = 0
            } else {
                if (type == "checklist") {
                    csv = Util.getAreaChecklists(csv, data)
                }
                count = csv.length - 1
            }

            distributions.put(key, StringUtils.join(csv, "\n"))
            distributionCounts.put(key, count)

            result = distributions.get(key)
        }

        return result
    }

    private String getWkt(String wkt) {
        if (StringUtils.isNumeric(wkt)) {
            return spatialObjectsService.getObjectsGeometryById(wkt, 'wkt')
        } else {
            return wkt
        }
    }

    private void initJournalmapCsv() throws Exception {
        if (journalMapData == null) {
            if (isCancelled()) return
            StringBuilder sb = new StringBuilder()
            def list = journalMapService.search(getWkt(pid), 10000, 0).article
            //empty header
            sb.append("\n")

            int size = 0
            for (JSONObject jo : (list as List<JSONObject>)) {
                size++
                if (sb.length() > 0) {
                    sb.append("\n")
                }
                //authors (last_name, first_name), publish_year, title, publication.name, doi, JournalMap URL
                if (jo.containsKey("authors")) {
                    String author = ""
                    JSONArray ja = (JSONArray) jo.get("authors")
                    for (int i = 0; i < ja.size(); i++) {
                        if (i > 0) author += ", "
                        JSONObject o = (JSONObject) ja.get(i)
                        if (o.containsKey("last_name")) {
                            author += o.get("last_name") as String + ", "
                        }
                        if (o.containsKey("first_name")) {
                            author += o.get("first_name")
                        }
                    }
                    sb.append("\"").append(author.replace("\"", "\"\"")).append("\".")
                }
                sb.append(",")
                if (jo.containsKey("publish_year")) {
                    sb.append("\"").append(jo.get("publish_year").toString().replace("\"", "\"\"")).append(".\"")
                }
                sb.append(",")
                if (jo.containsKey("title")) {
                    sb.append("\"").append(jo.get("title").toString().replace("\"", "\"\"")).append(".\"")
                }
                sb.append(",")
                if (jo.containsKey("publication")) {
                    JSONObject o = (JSONObject) jo.get("publication")
                    if (o.containsKey("name")) {
                        sb.append("\"").append(o.get("name").toString().replace("\"", "\"\"")).append(".\"")
                    }
                } else if (jo.containsKey("publication_name")) {
                    sb.append("\"").append(jo.get("publication_name").toString().replace("\"", "\"\"")).append(".\"")
                }
                sb.append(",")
                if (jo.containsKey("doi")) {
                    sb.append("\"").append(jo.get("doi").toString().replace("\"", "\"\"")).append(".\"")
                }
                sb.append(",")
                if (jo.containsKey("id")) {
                    String journalmapUrl = journalMapUrl
                    String articleUrl = journalmapUrl + "articles/" + jo.get("id").toString()
                    sb.append("<a href='" + StringEscapeUtils.escapeHtml(articleUrl) + "'>" + StringEscapeUtils.escapeHtml(articleUrl) + "</a>")
                }
            }

            if (size <= 0) {
                journalMapCount = "0"
            } else {
                journalMapCount = String.valueOf(size)
            }

            journalMapData = sb.toString()
        }
    }

    JSONArray endemicData = null

    private String getEndemicSpeciesCount(String q) {
        if (endemicData == null) {

            JSONArray ja = null
            try {
                ja = (JSONArray) JSON.parse(Util.getUrl(biocacheServiceUrl + "/explore/endemic/species/" + q.replace("qid:", "") + "?facets=names_and_lsid"))

                endemicData = ja
            } catch (Exception e) {
                log.error("failed to parse endemic species for " + query, e)
            }
        }

        if (endemicData != null) {
            return String.valueOf(endemicData.size())
        } else {
            return "0"
        }
    }

    private String getEndemicSpecies(String q) {
        getEndemicSpeciesCount(q)

        StringBuilder sb = new StringBuilder()

        if (endemicData != null) {
            try {
                for (Object o : endemicData) {
                    JSONObject jo = (JSONObject) o

                    if (jo.containsKey("label")) {
                        String label = (String) jo.get("label")
                        if (label != null) {
                            String[] names_and_lsid = label.split("\\|")

                            String scientificName = names_and_lsid[0]
                            String commonName = names_and_lsid.length > 2 ? names_and_lsid[2] : ""
                            String kingdom = names_and_lsid.length > 3 ? names_and_lsid[3] : ""
                            String family = names_and_lsid.length > 4 ? names_and_lsid[4] : ""

                            Long count = (Long) jo.get("count")

                            if (sb.length() > 0) {
                                sb.append("\n")
                            }
                            sb.append(family).append(",").append(scientificName).append(",").append(commonName).append(",").append(count)
                        }
                    }
                }
            } catch (Exception e) {
                log.error("failed to parse endemic species for " + query, e)
            }
        }

        return sb.toString()
    }

    void saveImage(String name, byte[] bytes) {
        try {
            new File(filePath + File.separator).mkdirs()
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath + File.separator + name))
            bos.write(bytes)
            bos.close()
        } catch (Exception e) {
            log.error("failed to write image to: " + filePath, e)
        }
    }

    String createLayer(String layerName, float opacity) {
        return addWMSLayer(layerName + "&opacity=" + opacity)
    }

    private static SpatialObjects getEnvelope(String envelopeTaskId) {
        def task = Task.get(envelopeTaskId)

        for (def output : task.output) {
            if ("area" == output.name) {
                return JSON.parse(output.file) as SpatialObjects
            }
        }
        return null
    }

    String addObjectByPid(String pid) {
        SpatialObjects obj
        if (pid.startsWith("ENVELOPE")) {
            obj = getEnvelope(pid.replace("ENVELOPE", ""))
        } else {
            obj = spatialObjectsService.getObjectByPid(pid)
        }

        //add feature to the map as a new layer
        String mapLayer = ""
        try {
            if (pid.startsWith("ENVELOPE")) {
                mapLayer = addWMSLayer(obj.wmsurl + "&opacity=0.6")
            } else {
                String filter = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><StyledLayerDescriptor version=\"1.0.0\" xmlns=\"http://www.opengis.net/sld\">"+"<NamedLayer><Name>ALA:Objects</Name>"+"<UserStyle><FeatureTypeStyle><Rule><Title>Polygon</Title><PolygonSymbolizer>"+"<Fill>"+"<GraphicFill><Graphic><Mark><WellKnownName>shape://times</WellKnownName><Stroke>"+"<CssParameter name=\"stroke\">#FF0000</CssParameter>"+"<CssParameter name=\"stroke-width\">1</CssParameter>"+"</Stroke></Mark></Graphic></GraphicFill>"+"</Fill>"+"<Stroke>"+"<CssParameter name=\"stroke\">#FF0000</CssParameter>"+"<CssParameter name=\"stroke-width\">4</CssParameter>"+"</Stroke>"+"</PolygonSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer></StyledLayerDescriptor>"
                mapLayer = addWMSLayer("Objects&viewparams=s:" + pid + "&sld_body=" + filter + "&opacity=0.6")
            }
        } catch (Exception ignored) {
        }
        //if the layer is a point create a radius
        bbox = obj.bbox.toString()
        area_km = obj.area_km.toString()

        return mapLayer
    }

    String createSpeciesLayer(String q, int red, int green, int blue, float opacity, boolean grid, int size, boolean uncertainty) {
        Color c = new Color(red, green, blue)
        String hexColour = Integer.toHexString(c.getRGB() & 0x00ffffff)
        String envString = ""
        if (grid) {
            //colour mode is in 'filter' but need to move it to envString
            envString += "colormode:grid"
        } else {
            envString = "color:" + hexColour
        }
        envString += ";name:circle;size:" + size + ";opacity:1"
        if (uncertainty) {
            envString += ";uncertainty:1"
        }

        String uri = biocacheServiceUrl + "/webportal/wms/reflect?"
        uri += "service=WMS&version=1.1.0&request=GetMap&format=image/png"
        uri += "&layers=ALA:occurrences"
        uri += "&transparent=true"
        uri += "&opacity=" + opacity
        uri += "&CQL_FILTER="


        String ml = uri + q

        try {
            ml += "&ENV=" + UriEncoder.encode(envString.replace("'", "\\'"))
        } catch (Exception ignored) {
        }

        return ml
    }

    String addWMSLayer(String name) {
        String mapLayer = geoserverUrl + "/wms/reflect?REQUEST=GetMap&VERSION=1.1.0&FORMAT=image/png&layers=ALA:" + name

        return mapLayer
    }

    String speciesList(String q) {
        String list = Util.getUrl(biocacheServiceUrl + "/occurrences/facets/download?facets=names_and_lsid&lookup=true&count=true&q=" + q)

        return list
    }

    String getSpeciesCount(String q) {
        String list = Util.getUrl(biocacheServiceUrl + "/occurrence/facets?facets=names_and_lsid&flimit=0&q=" + q)



        String count = "0"
        try {
            count = ((JSONObject) ((JSONArray) JSON.parse(list)).get(0)).get("count").toString()
        } catch (Exception ignored) {

        }

        return count
    }

    String getOccurrenceCount(String q) {
        String list = Util.getUrl(biocacheServiceUrl + "/occurrences/search?pageSize=0&facet=false&q=" + q)



        String count = "0"
        try {
            count = ((JSONObject) JSON.parse(list)).get("totalRecords").toString()
        } catch (Exception ignored) {

        }

        return count
    }
}
