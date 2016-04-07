package au.org.ala.spatial.util;

import au.com.bytecode.opencsv.CSVReader;
import au.org.ala.layers.util.SpatialUtil;
import au.org.ala.spatial.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AreaReportPDF {
    private static final Logger LOGGER = Logger.getLogger(AreaReportPDF.class);

    private static final int PROGRESS_COUNT = 92;

    private static final String[] SPECIES_GROUPS = new String[]{"Algae", "Amphibians", "Angiosperms", "Animals", "Arthropods", "Bacteria"
            , "Birds", "Bryophytes", "Chromista", "Crustaceans", "Dicots", "FernsAndAllies", "Fish", "Fungi"
            , "Gymnosperms", "Insects", "Mammals", "Molluscs", "Monocots", "Plants", "Protozoa", "Reptiles"};

    private String wkt;
    //private MapLayer mlArea;
    private String areaPid;
    private String areaName;
    private Map<String, String> counts;
    private Map<String, String> csvs;
    private Map<String, String> speciesLinks;
    //private BiocacheQuery query;
    private String[] checklists;
    private String[] distributions;
    List<JSONObject> documents;
    private Map<String, byte[]> imageMap;
    //private RemoteMap remoteMap;
    private Map tabulation;
    private int fileNumber;

    private String filePath;
    private String query;

    private String wkhtmltopdfPath;
    private String bbox;
    String geoserverUrl;
    String biocacheServiceUrl;

    String speciesListThreatened;
    String speciesListInvasive;

    private Map progress;
    private String serverUrl;
    private String[][] reportLayers = {
            {
                    "cl1918",
                    "National Dynamic Land Cover",
                    "http://spatial.ala.org.au/geoserver/wms?REQUEST=GetLegendGraphic&VERSION=1.0.0&FORMAT=image/png&WIDTH=20&HEIGHT=9&LAYER=dlcmv1",
                    "N",
                    "<br /><br />The Dynamic Land Cover Dataset is the first nationally consistent and thematically comprehensive land cover reference for Australia. It provides a base-line for reporting on change and trends in vegetation cover and extent. Information about land cover dynamics is essential to understanding and addressing a range of national challenges such as drought, salinity, water availability and ecosystem health. The data is a synopsis of land cover information for every 250m by 250m area of the country from April 2000 to April 2008. The classification scheme used to describe land cover categories in the Dataset conforms to the 2007 International Standards Organisation (ISO) land cover standard (19144-2). The Dataset shows Australian land covers clustered into 34 ISO classes. These reflect the structural character of vegetation, ranging from cultivated and managed land covers (crops and pastures) to natural land covers such as closed forest and open grasslands. [Ref1]<br /><br />Australia's Dynamic Land Cover: <a href='http://www.ga.gov.au/earth-observation/landcover.html'>http://www.ga.gov.au/earth-observation/landcover.html</a><br /><br />National Dynamic Land Cover layer: Classification: Vegetation; Type: Contextual (polygonal); Metadata contact organisation: Geoscience Australia (GA). <a href='http://spatial.ala.org.au/ws/layers/view/more/dlcmv1'>http://spatial.ala.org.au/ws/layers/view/more/dlcmv1</a>"
            },
            {
                    "cl1053",
                    "Global Context Ecoregions",
                    "",
                    "Y",
                    "<br /><br />Terrestrial Ecoregions of the World (TEOW)<br /><br />Terrestrial Ecoregions of the World (TEOW) is a biogeographic regionalisation of the Earth's terrestrial biodiversity. Our biogeographic units are ecoregions, which are defined as relatively large units of land or water containing a distinct assemblage of natural communities sharing a large majority of species, dynamics, and environmental conditions. There are 867 terrestrial ecoregions, classified into 14 different biomes such as forests, grasslands, or deserts. Ecoregions represent the original distribution of distinct assemblages of species and communities. [Ref2]<br /><br />TEOW: <a href='http://worldwildlife.org/biome-categories/terrestrial-ecoregions'>http://worldwildlife.org/biome-categories/terrestrial-ecoregions</a><br /><br />Terrestrial Ecoregional Boundaries layer: Classification: Biodiversity - Region; Type: Contextual (polygonal); Metadata contact organisation: The Nature Conservancy (TNC).  <a href='http://spatial.ala.org.au/ws/layers/view/more/1053'>http://spatial.ala.org.au/ws/layers/view/more/1053</a>"
            },
            {
                    "cl1052",
                    "Freshwater Ecoregions of the World (FEOW)",
                    "",
                    "Y",
                    "<br /><br />Freshwater Ecoregions of the World (FEOW) is a collaborative project providing the first global biogeographic regionalization of the Earth's freshwater biodiversity, and synthesizing biodiversity and threat data for the resulting ecoregions. We define a freshwater ecoregion as a large area encompassing one or more freshwater systems that contains a distinct assemblage of natural freshwater communities and species. The freshwater species, dynamics, and environmental conditions within a given ecoregion are more similar to each other than to those of surrounding ecoregions and together form a conservation unit. [Ref5]<br /><br />FEOW: <a href='http://worldwildlife.org/biome-categories/freshwater-ecoregions'>http://worldwildlife.org/biome-categories/freshwater-ecoregions</a><br /><br />Freshwater Ecoregions of the World layer: Classification: Biodiversity - Region; Type: Contextual (polygonal); Metadata contact organisation: TNC. <a href='http://spatial.ala.org.au/ws/layers/view/more/1052'>http://spatial.ala.org.au/ws/layers/view/more/1052</a>"
            }
    };

    String pid;

    public AreaReportPDF(String geoserverUrl, String biocacheServiceUrl, String q, String pid, String areaName,
                         List<String> facets, Map progress, String serverUrl,
                         String[][] reportLayers, String wkhtmltopdfPath, String outputPath) {

        try {
            speciesListThreatened = URLEncoder.encode("species_list_uid:dr1782 OR species_list_uid:dr967 OR species_list_uid:dr656 OR species_list_uid:dr649 OR species_list_uid:dr650 OR species_list_uid:dr651 OR species_list_uid:dr492 OR species_list_uid:dr1770 OR species_list_uid:dr493 OR species_list_uid:dr653 OR species_list_uid:dr884 OR species_list_uid:dr654 OR species_list_uid:dr655 OR species_list_uid:dr490 OR species_list_uid:dr2201", "UTF-8");
            speciesListInvasive = URLEncoder.encode("species_list_uid:dr947 OR species_list_uid:dr707 OR species_list_uid:dr945 OR species_list_uid:dr873 OR species_list_uid:dr872 OR species_list_uid:dr1105 OR species_list_uid:dr1787 OR species_list_uid:dr943 OR species_list_uid:dr877 OR species_list_uid:dr878 OR species_list_uid:dr1013 OR species_list_uid:dr879 OR species_list_uid:dr880 OR species_list_uid:dr881 OR species_list_uid:dr882 OR species_list_uid:dr883 OR species_list_uid:dr927 OR species_list_uid:dr823", "UTF-8");
        } catch (Exception e) {
        }

        this.areaName = areaName;
        this.progress = progress;
        this.serverUrl = serverUrl;
        this.geoserverUrl = geoserverUrl;
        this.biocacheServiceUrl = biocacheServiceUrl;

        this.pid = pid;
        this.query = q;
        this.wkhtmltopdfPath = wkhtmltopdfPath;

        if (reportLayers != null) this.reportLayers = reportLayers;

//        remoteMap = new RemoteMapImpl();
//        ((RemoteMapImpl) remoteMap).setLayerUtilities(new LayerUtilitiesImpl());

        filePath = outputPath;

        try {
            FileUtils.forceMkdir(new File(filePath + "/"));
        } catch (Exception e) {
            LOGGER.error("failed to create directory for PDF: " + filePath, e);
        }

        //query for images and data
        setProgress("Getting information", 0);
        if (!isCancelled()) init();

        //transform data into html
        setProgress("Formatting", 0);
        if (!isCancelled()) makeHTML();

        //transform html into pdf
        setProgress("Producing PDF", 0);
        if (!isCancelled()) savePDF();

        setProgress("Finished", 1);
    }

    public static void main(String[] args) {
        Properties p = new Properties();
        try {
//            p.load(new FileInputStream("/data/webportal/config/webportal-config.properties"));
//            CommonData.init(p);
        } catch (Exception e) {
            LOGGER.error("failed to load properties", e);
        }

        //String wkt = "POLYGON((112.0 -44.0,112.0 -11.0,154.0 -11.0,154.0 -44.0,112.0 -44.0))";
        String wkt = "POLYGON((149.26687622068 -35.258741390775,149.35579681395 -35.298540090399,149.33657073973 -35.320673151768,149.28404235838 -35.336638814392,149.24559020995 -35.322914136746,149.26687622068 -35.258741390775))";

        try {
            new AreaReportPDF("http://local.ala.org.au:8079/geoserver",
                    "http://biocache.ala.org.au/ws",
                    "*%3A*&wkt=" + URLEncoder.encode("POLYGON((151.144554432 -33.8900501635,151.144554432 -33.8455629915,151.196584992 -33.8455629915,151.196584992 -33.8900501635,151.144554432 -33.8900501635))", "UTF-8"),
                    "685950",
                    "test",
                    null, null,
                    "http://local.ala.org.au:8080/spatial-service",
                    null, "/data/ala/utils/wkhtmltoimage/wkhtmltoimage-amd64", "/data/webportal/data/");
        } catch (Exception e) {
            e.printStackTrace();
            ;
        }
    }

    private boolean isCancelled() {
        return progress != null && progress.containsKey("cancel");
    }

    private void setProgress(String label, double percent) {
        System.out.println("PROGRESS: " + label + " " + percent);
        if (progress != null) {
            progress.put(String.valueOf(System.currentTimeMillis()), label);

            if (percent == 0) {
                Double currentPercent = (Double) progress.get("percent");
                if (currentPercent == null) {
                    currentPercent = 0.0;
                } else {
                    currentPercent *= 100;
                }

                // progress.put("percent", Math.min((currentPercent + 1) / 100, 1.0));
            } else {
                // progress.put("percent", percent);
            }
        }
    }

    public byte[] getPDF() {
        try {
            return FileUtils.readFileToByteArray(new File(filePath + "/output.pdf"));
        } catch (Exception e) {
            LOGGER.error("failed to get PDF from: " + filePath + "/output.pdf", e);
        }

        return null;
    }

    private void savePDF() {
        try {
            String[] inputHtmls = new String[fileNumber - 2];
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><link rel='stylesheet' type='text/css' href='" + serverUrl + "/area-report/areaReport.css'></link></head><body><script>function changePage(page) {contents.src = page;}</script><table style=\"width:100%;height:100%\"><tr><td style=\"width:300px;vertical-align:top\"><div><h2>Index</h2><ul>");
            for (int i = 1; i < fileNumber; i++) {
                if (i >= 2) inputHtmls[i - 2] = filePath + "/report." + i + ".html";

                //get title
                String s = FileUtils.readFileToString(new File(filePath + "/report." + i + ".html"));
                String title = "Part " + i;
                if (i == 1) title = "Cover";
                try {
                    title = s.substring(s.indexOf("<h1>") + 4, s.indexOf("</h1>"));
                } catch (Exception e) {
                }
                sb.append("<li><a href=\"javascript:changePage('report." + i + ".html')\">" + title + "</a></li>");
            }
            //last page
            sb.append("<li><a href=\"javascript:changePage('http://local.ala.org.au:8080/spatial-service/area-report/furtherLinks.html')\">Further Links</a></li>");

            sb.append("</ul></td><td><iframe id=\"contents\" src=\"report.1.html\" style=\"width:100%;height:100%;border:0px\" ></td></tr></table></body></html>");

            FileUtils.writeStringToFile(new File(filePath + "/index.html"), sb.toString());

            //5min timeout
            makePDF(filePath + "/report.1.html", inputHtmls, filePath + "/output.pdf", 5 * 60 * 1000);

            //make paths relative
            for (int i = 1; i < fileNumber; i++) {
                File f = new File(filePath + "/report." + i + ".html");
                String s = FileUtils.readFileToString(f);
                s = s.replace(filePath + "/", "");
                f.delete();
                FileUtils.writeStringToFile(f, s);
            }

        } catch (Exception e) {
            LOGGER.error("failed to produce PDF", e);
        }
    }

    private void makeHTML() {
        //make report
        fileNumber = 1;
        try {
            //read data

            JSONParser jp = new JSONParser();
            JSONObject tabulations = (JSONObject) jp.parse(FileUtils.readFileToString(new File(filePath + "/tabulations.json"), "UTF-8"));
            JSONObject csvs = (JSONObject) jp.parse(FileUtils.readFileToString(new File(filePath + "/csvs.json")));
            JSONObject counts = (JSONObject) jp.parse(FileUtils.readFileToString(new File(filePath + "/counts.json"), "UTF-8"));

            //header
            String filename = filePath + "/report.html";
            FileWriter fw = startHtmlOut(fileNumber, filename);

            //box summary
            fw.write("<img  id='imgHeader' src='" + serverUrl + "/image/header.jpg' width='100%' />");
            //fw.write("<div>AREA REPORT</div>");
            fw.write("<table id='dashboard' >");
            fw.write("<tr>");
            fw.write("<td>");
            fw.write("Area: " + String.format("%s", (counts.get("Area (sq km)"))) + " sq km");
            fw.write("</td>");
            fw.write("<td>");
            fw.write("Species: " + String.format("%s", (counts.get("Species"))));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("Occurrences: " + String.format("%s", counts.get("Occurrences")));
            fw.write("</td>");
            fw.write("</tr>");
            fw.write("<tr>");
            fw.write("<td>");
            fw.write("Endemic species: " + String.format("%s", counts.get("Endemic Species")));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("All threatened species: " + counts.get("Threatened_Species"));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("Migratory species: " + counts.get("Migratory_Species"));
            fw.write("</td>");
            fw.write("</tr>");
            fw.write("<tr>");
            fw.write("<td>");
            fw.write("All invasive species: " + counts.get("Invasive_Species"));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("Iconic species: " + counts.get("Iconic_Species"));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("JournalMap Articles: " + counts.get("Journalmap"));
            fw.write("</td>");
            fw.write("</tr>");
            fw.write("<tr>");
            fw.write("<td>");
            fw.write("Animals: " + String.format("%s", counts.get("Animals")));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("Plants: " + String.format("%s", counts.get("Plants")));
            fw.write("</td>");
            fw.write("<td>");
            fw.write("Birds: " + String.format("%s", counts.get("Birds")));
            fw.write("</td>");
            fw.write("</tr>");

            fw.write("</table>");

            fw.write("</body></html>");
            fw.close();
            fileNumber++;
            fw = startHtmlOut(fileNumber, filename);
            //index page


            //map pages
            int figureNumber = 1;
            int tableNumber = 1;
            mapPage(fw, areaName, figureNumber, tableNumber, "base_area.png",
                    "Area: <b>" + String.format("%s", counts.get("Area (sq km)")) + " sq km</b>",
                    null, null);
            fw.write("</body></html>");
            fw.close();

            for (String[] split : reportLayers) {
                String shortname = split[0];
                String displayname = split[1];
                String geoserver_url = split[2];
                String canSetColourMode = split[3];
                String description = split[4];

                fileNumber++;
                fw = startHtmlOut(fileNumber, filename);
                figureNumber++;
                mapPage(fw, displayname, figureNumber, tableNumber, shortname + ".png",
                        description,
                        (JSONObject) tabulations.get(shortname)
                        , geoserver_url.isEmpty() ? null : geoserver_url);
                fw.write("</body></html>");
                fw.close();
            }
//            fw = startHtmlOut(fileNumber, filename);
//            figureNumber++;
//            mapPage(fw, "Integrated Marine and Coastal Regionalisation of Australia (IMCRA)", figureNumber, "imcra4_pb.png",
//                    "<br /><br />The Integrated Marine and Coastal Regionalisation of Australia (IMCRA v4.0) is a spatial framework for classifying Australia's marine environment into bioregions that make sense ecologically and are at a scale useful for regional planning. These bioregions are the basis for the development of a National Representative System of Marine Protected Areas (NRSMPA). [Ref3]\n" +
//                            "<br /><br />IMCRA: <a href='http://www.environment.gov.au/node/18075'>http://www.environment.gov.au/node/18075</a>" +
//                            "<br /><br />IMCRA Regions layer: Classification: Biodiversity - Region; Type: Contextual (polygonal); Metadata contact organisation: Environmental Resources Information Network (ERIN).  <a href='http://spatial.ala.org.au/ws/layers/view/more/imcra4_pb'>http://spatial.ala.org.au/ws/layers/view/more/imcra4_pb</a>" +
//                            "<br /><br />Goals and Principles for the Establishment of the NRSMPA: <a href='http://www.environment.gov.au/resource/goals-and-principles-establishment-national-representative-system-marine-protected-areas'>http://www.environment.gov.au/resource/goals-and-principles-establishment-national-representative-system-marine-protected-areas</a>",
//                    tabulations.getJSONObject(CommonData.getLayerFacetName("imcra4_pb")));
//            fw.write("</body></html>");fw.close();
//            fileNumber++;

            fileNumber++;
            fw = startHtmlOut(fileNumber, filename);
            figureNumber++;
            tableNumber++;

            //occurrences page
            int count = Integer.parseInt(counts.get("Occurrences").toString());
            int countKosher = Integer.parseInt(counts.get("Occurrences (spatially valid only)").toString());
            String imageUrl = "occurrences.png";
            String notes = "Spatially valid records are considered those that do not have any type of flag questioning their location, for example a terrestrial species being recorded in the ocean. [Ref6]";
            speciesPage(true, fw, "My Area", "Occurrences", notes, tableNumber, count, countKosher, figureNumber, imageUrl,
                    null);
            figureNumber++;
            fw.write("</body></html>");
            fw.close();
            fileNumber++;
            fw = startHtmlOut(fileNumber, filename);

            //species pages
            count = Integer.parseInt(counts.get("Species").toString());
            countKosher = Integer.parseInt(counts.get("Species (spatially valid only)").toString());
            imageUrl = null;
            notes = "Spatially valid records are considered those that do not have any type of flag questioning their location, for example a terrestrial species being recorded in the ocean. [Ref6]";
            speciesPage(true, fw, "My Area", "Species", notes, tableNumber, count, countKosher, figureNumber, imageUrl,
                    csvs.get("Species").toString());
            tableNumber++;
            fw.write("</body></html>");
            fw.close();
            fileNumber++;
            fw = startHtmlOut(fileNumber, filename);

            //threatened species page
            try {
                count = Integer.parseInt(counts.get("Threatened_Species").toString());
                imageUrl = "Threatened_Species" + ".png";
                notes = "";
                speciesPage(true, fw, "My Area", "All threatened species", notes, tableNumber, count, -1, figureNumber, imageUrl,
                        csvs.get("Threatened_Species").toString());
                figureNumber++;
                fw.write("</body></html>");
                fw.close();
                fileNumber++;
                fw = startHtmlOut(fileNumber, filename);
            } catch (Exception e) {
                e.printStackTrace();
                fileNumber--;
            }

            //invasive species page
            try {
                count = Integer.parseInt(counts.get("Invasive_Species").toString());
                imageUrl = "Invasive_Species" + ".png";
                notes = "";
                speciesPage(true, fw, "My Area", "All invasive species", notes, tableNumber, count, -1, figureNumber, imageUrl,
                        csvs.get("Invasive_Species").toString());
                figureNumber++;
                fw.write("</body></html>");
                fw.close();
                fileNumber++;
                fw = startHtmlOut(fileNumber, filename);
            } catch (Exception e) {
                e.printStackTrace();
                fileNumber--;
            }

            //iconic species page

            try {
                count = Integer.parseInt(counts.get("Iconic_Species").toString());
                imageUrl = "Iconic_Species" + ".png";
                notes = "";
                speciesPage(true, fw, "My Area", "Iconic species", notes, tableNumber, count, -1, figureNumber, imageUrl,
                        csvs.get("Iconic_Species").toString());
                figureNumber++;
                fw.write("</body></html>");
                fw.close();
                fileNumber++;
                fw = startHtmlOut(fileNumber, filename);
            } catch (Exception e) {
                e.printStackTrace();
                fileNumber--;
            }
            try {        //migratory species page
                count = Integer.parseInt(counts.get("Migratory_Species").toString());
                imageUrl = "Migratory_Species" + ".png";
                notes = "";
                speciesPage(true, fw, "My Area", "Migratory species", notes, tableNumber, count, -1, figureNumber, imageUrl,
                        csvs.get("Migratory_Species").toString());
                figureNumber++;
                fw.write("</body></html>");
                fw.close();
                fileNumber++;
                fw = startHtmlOut(fileNumber, filename);
            } catch (Exception e) {
                e.printStackTrace();
                fileNumber--;
            }

            try {
                for (int i = 0; i < SPECIES_GROUPS.length; i++) {
                    String s = SPECIES_GROUPS[i];
                    count = Integer.parseInt(counts.get(s).toString());
                    countKosher = Integer.parseInt(counts.get(s + " (spatially valid only)").toString());
                    speciesPage(true, fw, "My Area", "lifeform - " + s, notes, tableNumber, count, countKosher, figureNumber,
                            "lifeform - " + s + ".png", csvs.get(s).toString());
                    tableNumber++;
                    figureNumber++;
                    fw.write("</body></html>");
                    fw.close();
                    fileNumber++;
                    fw = startHtmlOut(fileNumber, filename);
                }
            } catch (Exception e) {
                fileNumber--;
                e.printStackTrace();
            }
//            //expert distributions
//            count = Integer.parseInt(counts.get("Distribution Areas").toString());
//            speciesPage(false, fw, "My Area", "Expert Distributions", notes, tableNumber,
//                    count, -1, figureNumber, null, csvs.get("distributions").toString());
//            fw.write("</body></html>");
//            fw.close();
//            fileNumber++;
//            fw = startHtmlOut(fileNumber, filename);
//            count = Integer.parseInt(counts.get("Checklist Areas").toString());
//            speciesPage(false, fw, "My Area", "Checklist Areas", notes, tableNumber,
//                    count, -1, figureNumber, null, csvs.get("checklists").toString());
//            fw.write("</body></html>");
//            fw.close();
//            fileNumber++;
//            fw = startHtmlOut(fileNumber, filename);

//            //Journalmap page
//            count = Integer.parseInt(counts.get("Journalmap").toString());
//            countKosher = Integer.parseInt(counts.get("Journalmap").toString());
//            imageUrl = null;
//            notes = "<a href='http://journalmap.org'>JournalMap</a>";
//            speciesPage(false, fw, "My Area", "JournalMap Articles", notes, tableNumber, count, -1, figureNumber, null,
//                    csvs.get("Journalmap").toString());
//            tableNumber++;
//            fw.write("</body></html>");
//            fw.close();
//            fileNumber++;

        } catch (Exception e) {
            LOGGER.error("failed to produce report pdf", e);
        }
    }

    private void makePDF(String headerHtml, String[] inputHtmls, String outputPdf, long maxRunTime) {
        //generate pdf
        String[] cmdStart = new String[]{
                wkhtmltopdfPath,
            /* page margins (mm) */
                "-B", "10", "-L", "10", "-T", "10", "-R", "10",
            /* encoding */
                "--encoding", "UTF-8",
            /* footer settings */
                "--footer-font-size", "9",
                "--footer-line",
                "--footer-left", "    www.ala.org.au",
                "--footer-right", "Page [page] of [toPage]     "
        };

        String[] cmd = new String[cmdStart.length + 4 + inputHtmls.length + 2];
        System.arraycopy(cmdStart, 0, cmd, 0, cmdStart.length);
        cmd[cmdStart.length] = headerHtml;

        /* table of contents */
        cmd[cmdStart.length + 1] = "toc";
        cmd[cmdStart.length + 2] = "--xsl-style-sheet";
        cmd[cmdStart.length + 3] = filePath + "/toc.xsl";

        System.arraycopy(inputHtmls, 0, cmd, cmdStart.length + 4, inputHtmls.length);
        cmd[cmd.length - 2] = serverUrl + "/area-report/furtherLinks.html";

        cmd[cmd.length - 1] = outputPdf;

        System.out.println(StringUtils.join(cmd, " "));

        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.environment().putAll(System.getenv());
        builder.redirectErrorStream(true);
        Process proc = null;

        long start = System.currentTimeMillis();

        try {

            proc = builder.start();

            while (maxRunTime + start > System.currentTimeMillis()) {
                Thread.sleep(500);
                try {
                    proc.exitValue();

                    //finished
                    break;

                } catch (IllegalThreadStateException e) {
                    //still running
                }
            }

            if (maxRunTime + start < System.currentTimeMillis()) {
                LOGGER.error("wkhtmltopdf took longer than " + maxRunTime + "ms and was cancelled");
                proc.destroy();
            } else {
                proc.waitFor();
            }
        } catch (Exception e) {
            LOGGER.error("error running wkhtmltopdf", e);
        }
    }

    private FileWriter startHtmlOut(int fileNumber, String filename) throws Exception {
        FileWriter fw = new FileWriter(filename.replace(".", "." + fileNumber + "."));
        fw.write("<html>");
        fw.write("<head><link rel='stylesheet' type='text/css' href='" + serverUrl + "/area-report/areaReport.css'></link></head>");
        fw.write("<body>");
        return fw;
    }

    private void speciesPage(boolean isSpecies, FileWriter fw, String areaName, String title, String notes, int tableNumber, int count, int countKosher, int figureNumber, String imageUrl, String csv) throws Exception {
        String imageUrlActual = imageUrl;
        if (imageUrlActual != null) {
            imageUrlActual = filePath + "/" + imageUrlActual;
        }
        fw.write("<table id='species'>");
        fw.write("<tr>");
        fw.write("<td id='title'><h1>");
        fw.write(title);
        fw.write("</h1></td>");
        fw.write("</tr><tr>");
        fw.write("<td>");
        fw.write("<br />Number of " + title.toLowerCase() + ": <b>" + count + "</b>");
        fw.write("</td>");
        fw.write("</tr><tr>");
        fw.write("<td><br />");
        fw.write(notes);
        fw.write("</td>");
        fw.write("</tr><tr>");
        if (countKosher >= 0) {
            fw.write("<td>");
            fw.write("<br />Number of " + title.toLowerCase() + " (spatially valid only): <b>" + countKosher + "</b>");
            fw.write("</td>");
            fw.write("</tr><tr>");
        }

        if ((count > 0 || countKosher > 0) && imageUrlActual != null) {
            fw.write("<td>");
            fw.write("<br /><img src='" + imageUrlActual + "' />");
            fw.write("</td>");
            fw.write("</tr><tr>");
            fw.write("<td id='figure'>");
            fw.write("<b>Figure " + figureNumber + ":</b> Map of " + title + " in " + areaName);
            fw.write("</td>");
            fw.write("</tr><tr>");
        }

        //tabulation table
        if ((count > 0 || countKosher > 0) && csv != null) {
            CSVReader r = new CSVReader(new StringReader(csv));

            fw.write("<td id='tableNumber'><br /><b>Table " + tableNumber + ":</b> " + title);
            if (speciesLinks.get(title.replace("lifeform - ", "")) != null) {
                fw.write("<a href='" + speciesLinks.get(title.replace("lifeform - ", "")) + "'>(Link to full list)</a>");
            }
            fw.write("</td></tr><tr><td>");

            fw.write("<table id='table'>");

            //reorder select columns
            int[] columnOrder;
            if (isSpecies) {
                columnOrder = new int[]{8, 1, 10, 11};
                fw.write("<tr><td>Family</td><td id='scientificName' >Scientific Name</td><td>Common Name</td><td>No. Occurrences</td>");
                //TODO: additional columns
//                for (int i = 0; i < CommonData.getSpeciesListAdditionalColumnsHeader().size(); i++) {
//                    columnOrder = Arrays.copyOf(columnOrder, columnOrder.length + 1);
//                    columnOrder[columnOrder.length - 1] = 11 + 1 + i;
//                    fw.write("<td>" + CommonData.getSpeciesListAdditionalColumnsHeader().get(i) + "</td>");
//                }
                fw.write("</tr>");
            } else if ("JournalMap Articles".equals(title)) {
                //authors (last_name, first_name), publish_year, title, publication.name, doi, JournalMap URL
                columnOrder = new int[]{0, 1, 2, 3, 4, 5};
                fw.write("<tr><td>Author/s</td><td>Year</td><td>Title</td><td>Publication</td><td>DOI</td><td>URL</td></tr>");
            } else {
                columnOrder = new int[]{4, 1, 3, 7, 8, 11, 12};
                fw.write("<tr><td>Family</td><td id='scientificName' >Scientific Name</td><td>Common Name</td><td>Min Depth</td><td>Max Depth</td><td>Area Name</td><td>Area sq km</td></tr>");
            }
            //use fixed header
            r.readNext();

            String[] line;
            int row = 0;
            while ((line = r.readNext()) != null) {
                fw.write("<tr>");
                for (int i = 0; i < columnOrder.length && columnOrder[i] < line.length; i++) {
                    fw.write("<td><div>" + line[columnOrder[i]] + "</div></td>");
                }
                fw.write("</tr>");

                row++;
            }

            fw.write("</table>");
            fw.write("<td>");
        }

        fw.write("</tr>");
        fw.write("</table>");
    }

    private void mapPage(FileWriter fw, String areaName, int figureNumber, int tableNumber, String imageUrl, String notes, JSONObject tabulation, String legendUrl) throws Exception {
        String imageUrlActual = imageUrl;
        if (imageUrlActual != null) {
            imageUrlActual = filePath + "/" + imageUrlActual;
        }
        fw.write("<table id='mapPage'>");
        fw.write("<tr>");
        fw.write("<td id='title'><h1>");
        fw.write(areaName);
        fw.write("</h1></td>");
        fw.write("</tr><tr>");
        fw.write("<td><br />");
        fw.write(notes);
        fw.write("</td>");
        fw.write("</tr><tr>");
        fw.write("<td>");
        if (imageUrlActual.endsWith("base_area.png")) {
            fw.write("<br /><img src='" + filePath + "/base_area_zoomed_out.png' />");
        }
        fw.write("<br /><img " + (legendUrl != null ? "id='imgWithLegend' " : "") + " src='" + imageUrlActual + "' />");
        if (legendUrl != null) {
            fw.write("<img id='legend' src='" + legendUrl + "'/>");
        }
        fw.write("</td>");
        fw.write("</tr><tr>");
        fw.write("<td id='figure'>");
        fw.write("<b>Figure " + figureNumber + ":</b> Map of " + areaName);
        fw.write("</td>");
        fw.write("</tr><tr>");

        //tabulation table
        if (tabulation != null && tabulation.containsKey("tabulationList")) {
            double totalArea = 0;
            for (Object o : (JSONArray) tabulation.get("tabulationList")) {
                JSONObject jo = (JSONObject) o;
                totalArea += Double.parseDouble(jo.get("area").toString()) / 1000000.0;
            }

            if (totalArea > 0) {
                fw.write("<td id='tableNumber'>");
                fw.write("<br /><b>Table " + tableNumber + ":</b> " + areaName);
                fw.write("</td></tr><tr><td>");
                fw.write("<br /><table id='table'><tr><td>Class/Region</td><td>Area (sq km)</td><td>% of total area</td></tr>");

                for (Object o : (JSONArray) tabulation.get("tabulationList")) {
                    JSONObject jo = (JSONObject) o;
                    fw.write("<tr><td>");
                    fw.write(jo.get("name1").toString());
                    fw.write("</td><td>");
                    fw.write(String.format("%.2f", Double.parseDouble(jo.get("area").toString()) / 1000000.0));
                    fw.write("</td><td>");
                    fw.write(String.format("%.2f", Double.parseDouble(jo.get("area").toString()) / 1000000.0 / totalArea * 100));
                    fw.write("</td></tr>");
                }

                fw.write("</table>");
                fw.write("</td>");
            }
        }

        fw.write("</tr>");
        fw.write("</table>");
    }

    String mlArea;

    final void init() {
        counts = new ConcurrentHashMap<String, String>();
        csvs = new ConcurrentHashMap<String, String>();
        imageMap = new ConcurrentHashMap<String, byte[]>();
        tabulation = new ConcurrentHashMap();
        speciesLinks = new ConcurrentHashMap<String, String>();

        mlArea = addObjectByPid(pid);

        List callables = new ArrayList();

        callables.addAll(initTabulation());

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initImages();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCountArea();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCountSpecies();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCountOccurrences();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCsvSpecies();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCountThreatenedSpecies();

                return null;
            }
        });

//        callables.add(new Callable() {
//            @Override
//            public Object call() throws Exception {
//                initCountEndemicSpecies();
//
//                return null;
//            }
//        });

//        callables.add(new Callable() {
//            @Override
//            public Object call() throws Exception {
//                initCountChecklistAreasAndSpecies();
//
//                return null;
//            }
//        });
//
//        callables.add(new Callable() {
//            @Override
//            public Object call() throws Exception {
//                initCountDistributionAreas();
//
//                return null;
//            }
//        });

//        callables.add(new Callable() {
//            @Override
//            public Object call() throws Exception {
//                initDistributionsCsv("checklists");
//
//                return null;
//            }
//        });
//
//        callables.add(new Callable() {
//            @Override
//            public Object call() throws Exception {
//                initDistributionsCsv("distributions");
//
//                return null;
//            }
//        });
//
//        callables.add(new Callable() {
//            @Override
//            public Object call() throws Exception {
//                initJournalmapCsv();
//
//                return null;
//            }
//        });

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        try {
            executorService.invokeAll(callables);
        } catch (InterruptedException e) {
            LOGGER.error("failed to run all Init callables for detailed pdf", e);
        }

        setProgress("Getting information: saving", 0);
        if (isCancelled()) return;
        try {
            FileWriter fw = new FileWriter(filePath + File.separator + "counts.json");
            fw.write(JSONValue.toJSONString(counts));
            fw.close();

            fw = new FileWriter(filePath + File.separator + "tabulations.json");
            fw.write(JSONValue.toJSONString(tabulation));
            fw.close();

            fw = new FileWriter(filePath + File.separator + "csvs.json");
            fw.write(JSONValue.toJSONString(csvs));
            fw.close();

            FileUtils.copyURLToFile(new URL(serverUrl + "/area-report/toc.xsl"),
                    new File(filePath + "/toc.xsl"));
        } catch (Exception e) {
            LOGGER.error("failed to output area report information", e);
        }

    }

    private Callable getTabulationCallable(String fieldId) {
        final String fid = fieldId;
        return new Callable() {
            @Override
            public Object call() throws Exception {
                try {
                    JSONParser jp = new JSONParser();
                    tabulation.put(fid, jp.parse(Util.getUrl(serverUrl + "/tabulation/" + fid + "/" + areaPid + ".json")));
                } catch (Exception e) {
                    LOGGER.error("failed tabulation: fid=" + fid + ", areaPid=" + areaPid);
                }

                return null;
            }
        };
    }

    private List initTabulation() {

        List callables = new ArrayList();

        for (String[] split : reportLayers) {
            String shortname = split[0];
            String displayname = split[1];
            String geoserver_url = split[2];
            String canSetColourMode = split[3];
            String description = split[4];

            String fid = shortname;
            callables.add(getTabulationCallable(fid));
        }

        return callables;
    }

//    private void initDistributionsCsv(String type) {
//        setProgress("Getting information: " + type, 0);
//        if (isCancelled()) return;
//        StringBuilder sb = new StringBuilder();
//        String[] list = Util.getDistributionsOrChecklists(type, wkt, null, null);
//        for (String line : list) {
//            if (sb.length() > 0) {
//                sb.append("\n");
//            }
//            sb.append(line);
//        }
//        if ("checklists".equals(type)) {
//            checklists = list;
//            if (checklists.length <= 0) {
//                counts.put("Checklist Areas", "0");
//                counts.put("Checklist Species", "0");
//            } else {
//                String[] areaChecklistText = Util.getAreaChecklists(checklists);
//                counts.put("Checklist Areas", String.valueOf(areaChecklistText.length - 1));
//                counts.put("Checklist Species", String.valueOf(checklists.length - 1));
//            }
//        } else {
//            distributions = list;
//            if (distributions.length <= 0) {
//                counts.put("Distribution Areas", "0");
//            } else {
//                counts.put("Distribution Areas", String.valueOf(distributions.length - 1));
//            }
//        }
//
//        csvs.put(type, sb.toString());
//    }
//
//    private void initJournalmapCsv() {
//        setProgress("Getting information: Journalmap", 0);
//        if (isCancelled()) return;
//        StringBuilder sb = new StringBuilder();
//        List<JSONObject> list = CommonData.filterJournalMapArticles(wkt);
//        //empty header
//        sb.append("\n");
//
//        for (JSONObject jo : list) {
//            if (sb.length() > 0) {
//                sb.append("\n");
//            }
//            //authors (last_name, first_name), publish_year, title, publication.name, doi, JournalMap URL
//            if (jo.containsKey("authors")) {
//                String author = "";
//                JSONArray ja = (JSONArray) jo.get("authors");
//                for (int i = 0; i < ja.size(); i++) {
//                    if (i > 0) author += ", ";
//                    JSONObject o = (JSONObject) ja.get(i);
//                    if (o.containsKey("last_name")) {
//                        author += o.get("last_name") + ", ";
//                    }
//                    if (o.containsKey("first_name")) {
//                        author += o.get("first_name");
//                    }
//                }
//                sb.append("\"").append(author.replace("\"", "\"\"")).append("\".");
//            }
//            sb.append(",");
//            if (jo.containsKey("publish_year")) {
//                sb.append("\"").append(jo.get("publish_year").toString().replace("\"", "\"\"")).append(".\"");
//            }
//            sb.append(",");
//            if (jo.containsKey("title")) {
//                sb.append("\"").append(jo.get("title").toString().replace("\"", "\"\"")).append(".\"");
//            }
//            sb.append(",");
//            if (jo.containsKey("publication")) {
//                JSONObject o = (JSONObject) jo.get("publication");
//                if (o.containsKey("name")) {
//                    sb.append("\"").append(o.get("name").toString().replace("\"", "\"\"")).append(".\"");
//                }
//            }
//            sb.append(",");
//            if (jo.containsKey("doi")) {
//                sb.append("\"").append(jo.get("doi").toString().replace("\"", "\"\"")).append(".\"");
//            }
//            sb.append(",");
//            if (jo.containsKey("id")) {
//                String journalmapUrl = CommonData.getSettings().getProperty("journalmap.url", null);
//                String articleUrl = journalmapUrl + "articles/" + jo.get("id").toString();
//                sb.append("<a href='" + articleUrl + "'>" + articleUrl + "</a>");
//            }
//        }
//
//        documents = list;
//
//        if (documents.size() <= 0) {
//            counts.put("Journalmap", "0");
//        } else {
//            counts.put("Journalmap", String.valueOf(documents.size()));
//        }
//
//        csvs.put("Journalmap", sb.toString());
//    }


    private void initCsvSpecies() {
        setProgress("Getting information: species list", 0);
        if (isCancelled()) return;
        csvs.put("Species", speciesList(query));
        speciesLinks.put("Species", biocacheServiceUrl + "/occurrences/search?q=" + query);

        for (int i = 0; i < SPECIES_GROUPS.length; i++) {
            String s = SPECIES_GROUPS[i];
            setProgress("Getting information: species list for lifeform " + s, 0);
            if (isCancelled()) return;
            String q = query + "&fq=species_group%3A" + s;
            csvs.put(s, speciesList(q));
            speciesLinks.put(s, biocacheServiceUrl + "/occurrences/search?q=" + q);
            counts.put(s, String.valueOf(getSpeciesCount(q)));
            counts.put(s + " (spatially valid only)", String.valueOf(getSpeciesCountKosher(q)));
        }

        setProgress("Getting information: threatened species list", 0);
        if (isCancelled()) return;
        String q = query + "&fq=" + speciesListThreatened;
        csvs.put("Threatened_Species", speciesList(q));
        speciesLinks.put("Threatened_Species", biocacheServiceUrl + "/occurrences/search?q=" + q);
        counts.put("Threatened_Species", String.valueOf(getSpeciesCount(q)));

        setProgress("Getting information: iconic species list", 0);
        if (isCancelled()) return;
        q = query + "&fq=species_list_uid%3Adr781";
        csvs.put("Iconic_Species", speciesList(q));
        speciesLinks.put("Iconic_Species", biocacheServiceUrl + "/occurrences/search?q=" + q);
        counts.put("Iconic_Species", String.valueOf(getSpeciesCount(q)));

        setProgress("Getting information: migratory species list", 0);
        if (isCancelled()) return;
        q = query + "&fq=species_list_uid%3Adr1005";
        csvs.put("Migratory_Species", speciesList(q));
        speciesLinks.put("Migratory_Species", biocacheServiceUrl + "/occurrences/search?q=" + q);
        counts.put("Migratory_Species", String.valueOf(getSpeciesCount(q)));

        setProgress("Getting information: invasive species list", 0);
        if (isCancelled()) return;
        q = query + "&fq=" + speciesListInvasive;
        csvs.put("Invasive_Species", speciesList(q));
        speciesLinks.put("Invasive_Species", biocacheServiceUrl + "/occurrences/search?q=" + q);
        counts.put("Invasive_Species", String.valueOf(getSpeciesCount(q)));
    }

    private void initCountSpecies() {
        setProgress("Getting information: species count", 0);
        if (isCancelled()) return;
        counts.put("Species", String.valueOf(getSpeciesCount(query)));
        setProgress("Getting information: species count geospatial_kosher=true", 0);
        if (isCancelled()) return;
        counts.put("Species (spatially valid only)", String.valueOf(getSpeciesCountKosher(query)));
    }

    private void initCountOccurrences() {
        setProgress("Getting information: occurrences", 0);
        if (isCancelled()) return;
        counts.put("Occurrences", String.valueOf(getOccurrenceCount(query)));
        setProgress("Getting information: occurrences count geospatial_kosher=true", 0);
        if (isCancelled()) return;
        counts.put("Occurrences (spatially valid only)", String.valueOf(getOccurrenceCountKosher(query)));
        speciesLinks.put("Occurrences", biocacheServiceUrl + "/occurrences/search?q=" + query);
    }

//    private void initCountEndemicSpecies() {
//        setProgress("Getting information: endemic species count", 0);
//        if (isCancelled()) return;
//        counts.put("Endemic Species", String.valueOf(getEndemicSpeciesCount(query)));
//    }

    private void initCountThreatenedSpecies() {
        setProgress("Getting information: threatened species", 0);
        if (isCancelled()) return;
        String q = query + "&fq=state_conservation:Endangered";
        counts.put("Endangered Species", String.valueOf(getSpeciesCount(q)));
    }

//    private void initCountChecklistAreasAndSpecies() {
//        setProgress("Getting information: checklist areas", 0);
//        checklists = Util.getDistributionsOrChecklists(StringConstants.CHECKLISTS, wkt, null, null);
//
//        if (checklists.length <= 0) {
//            counts.put("Checklist Areas", "0");
//            counts.put("Checklist Species", "0");
//        } else {
//            String[] areaChecklistText = Util.getAreaChecklists(checklists);
//            counts.put("Checklist Areas", String.valueOf(areaChecklistText.length - 1));
//            counts.put("Checklist Species", String.valueOf(checklists.length - 1));
//        }
//    }
//
//    private void initCountDistributionAreas() {
//        setProgress("Getting information: distribution areas", 0);
//        String[] distributions = Util.getDistributionsOrChecklists(StringConstants.DISTRIBUTIONS, wkt, null, null);
//
//        if (checklists.length <= 0) {
//            counts.put("Distribution Areas", "0");
//        } else {
//            counts.put("Distribution Areas", String.valueOf(distributions.length - 1));
//        }
//    }

    private void initCountArea() {
        DecimalFormat df = new DecimalFormat("###,###.##");
        counts.put("Area (sq km)", df.format(SpatialUtil.calculateArea(wkt) / 1000.0 / 1000.0));
    }

    private void initImages() {
        double aspectRatio = 1.6;
        String type = "png";
        int resolution = 0;

        String basemap = "Minimal";

        //mlArea.setColourMode("hatching");

        List<Double> bbox = new ArrayList<Double>();

        //convert POLYGON box to bounds
        String[] split = this.bbox.split(",");
        String[] p1 = split[1].split(" ");
        String[] p2 = split[3].split(" ");

        bbox.add(Math.min(Double.parseDouble(p1[0]), Double.parseDouble(p2[0])));
        bbox.add(Math.min(Double.parseDouble(p1[1]), Double.parseDouble(p2[1])));

        bbox.add(Math.max(Double.parseDouble(p1[0]), Double.parseDouble(p2[0])));
        bbox.add(Math.max(Double.parseDouble(p1[1]), Double.parseDouble(p2[1])));


        //30% width buffer in decimal degrees
        double step = (bbox.get(2) - bbox.get(0)) * 0.3;
        double[] extents = new double[]{bbox.get(0) - step, bbox.get(1) - step, bbox.get(2) + step, bbox.get(3) + step};
        step = (bbox.get(2) - bbox.get(0)) * 0.05;
        double[] extentsSmall = new double[]{bbox.get(0) - step, bbox.get(1) - step, bbox.get(2) + step, bbox.get(3) + step};
        step = (bbox.get(2) - bbox.get(0)) * 10;
        double[] extentsLarge = new double[]{bbox.get(0) - step, bbox.get(1) - step, bbox.get(2) + step, bbox.get(3) + step};
        if (extentsLarge[2] > 180) {
            extentsLarge[2] = 180;
        }
        if (extentsLarge[0] < -180) {
            extentsLarge[0] = -180;
        }
        if (extentsLarge[1] < -85) {
            extentsLarge[1] = -85;
        }
        if (extentsLarge[3] > 85) {
            extentsLarge[3] = 85;
        }

        setProgress("Getting information: images for map of map", 0);
        if (isCancelled()) return;
        String mlSpecies = createSpeciesLayer(query, 0, 0, 255, .6f, false, 9, false);

        List<String> lifeforms = new ArrayList<String>();
        for (int i = 0; i < SPECIES_GROUPS.length; i++) {
            String s = SPECIES_GROUPS[i];
            setProgress("Getting information: images for map of lifeform " + s, 0);
            if (isCancelled()) return;
            lifeforms.add(createSpeciesLayer(query + "&fq=species_group%3A" + s, 0, 0, 255, .6f, false, 9, false));
        }

        setProgress("Getting information: images for map of threatened species", 0);
        if (isCancelled()) return;
        String threatenedSpecies = createSpeciesLayer(query + "&fq=" + speciesListThreatened, 0, 0, 255, .6f, false, 9, false);

        setProgress("Getting information: images for map of iconic species", 0);
        if (isCancelled()) return;
        String iconicSpecies = createSpeciesLayer(query + "&fq=species_list_uid%3Adr781", 0, 0, 255, .6f, false, 9, false);

        setProgress("Getting information: images for map of migratory species", 0);
        if (isCancelled()) return;
        String migratorySpecies = createSpeciesLayer(query + "&fq=species_list_uid%3Adr1005", 0, 0, 255, .6f, false, 9, false);

        setProgress("Getting information: images for map of invasive species", 0);
        if (isCancelled()) return;
        String invasiveSpecies = createSpeciesLayer(query + "&fq=" + speciesListInvasive, 0, 0, 255, .6f, false, 9, false);

        for (String[] s : reportLayers) {
            String shortname = s[0];
            String displayname = s[1];
            String geoserver_url = s[2];
            String canSetColourMode = s[3];
            String description = s[4];

            setProgress("Getting information: images for map of layer " + shortname, 0);
            if (isCancelled()) return;

            String ml = createLayer(shortname, 1.0f);
            if ("Y".equalsIgnoreCase(canSetColourMode)) {
                ml += "&styles=" + shortname + "&format_options=dpi%3A600";
            }

            setProgress("Getting information: making map of " + shortname, 0);
            if (isCancelled()) return;
            imageMap.put(shortname, new PrintMapComposer(extents, basemap, new String[]{mlArea, ml}, aspectRatio, "", type, resolution).get());
        }

        setProgress("Getting information: making map of area", 0);
        if (isCancelled()) return;
        imageMap.put("base_area", new PrintMapComposer(extents, basemap, new String[]{mlArea}, aspectRatio, "", type, resolution).get());

        setProgress("Getting information: making map of area overview", 0);
        if (isCancelled()) return;
        imageMap.put("base_area_zoomed_out", new PrintMapComposer(extentsLarge, basemap, new String[]{mlArea}, aspectRatio, "", type, resolution).get());

        setProgress("Getting information: making occurrences", 0);
        if (isCancelled()) return;
        imageMap.put("occurrences", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, mlSpecies}, aspectRatio, "", type, resolution).get());

        setProgress("Getting information: making threatened species", 0);
        if (isCancelled()) return;
        imageMap.put("Threatened_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, threatenedSpecies}, aspectRatio, "", type, resolution).get());

        setProgress("Getting information: making iconic species", 0);
        if (isCancelled()) return;
        imageMap.put("Iconic_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, iconicSpecies}, aspectRatio, "", type, resolution).get());

        setProgress("Getting information: making migratory species", 0);
        if (isCancelled()) return;
        imageMap.put("Migratory_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, migratorySpecies}, aspectRatio, "", type, resolution).get());

        setProgress("Getting information: making invasive species", 0);
        if (isCancelled()) return;
        imageMap.put("Invasive_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, invasiveSpecies}, aspectRatio, "", type, resolution).get());

        for (int i = 0; i < SPECIES_GROUPS.length; i++) {
            setProgress("Getting information: making map of lifeform " + SPECIES_GROUPS[i], 0);
            if (isCancelled()) return;
            imageMap.put("lifeform - " + SPECIES_GROUPS[i], new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, lifeforms.get(i)}, aspectRatio, "", type, resolution).get());
        }

        //save images
        setProgress("Getting information: saving maps", 0);
        if (isCancelled()) return;
        for (String key : imageMap.keySet()) {
            try {
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath + File.separator + key + ".png"));
                bos.write(imageMap.get(key));
                bos.close();
            } catch (Exception e) {
                LOGGER.error("failed to write image to: " + filePath, e);
            }
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
        String mapLayer = "";
        try {
            mapLayer = addWMSLayer("Objects&viewparams=s%3A" + pid + "&sld_body=" + URLEncoder.encode(filter, "UTF-8") + "&opacity=0.6");
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
            ml += "&ENV=" + URLEncoder.encode(URLEncoder.encode(envString.replace("'", "\\'"), "UTF-8"), "UTF-8");
        } catch (Exception e) {
        }

        return ml;
    }

    public String addWMSLayer(String name) {
        String mapLayer = geoserverUrl + "/wms/reflect?REQUEST=GetMap&VERSION=1.1.0&FORMAT=image/png&layers=ALA%3A" + name;

//        String uriActual = CommonData.getGeoServer() + "/wms?REQUEST=GetLegendGraphic&VERSION=1.0.0&FORMAT=image/png&WIDTH=20&HEIGHT=9&LAYER="
//                + mapLayer.getLayer() + (fieldId.length() < 10 ? "&styles=" + fieldId + "_style" : "");


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

    String getSpeciesCountKosher(String q) {
        return getSpeciesCount(q + "&fq=geospatial_kosher%3Atrue");
    }

    String getOccurrenceCountKosher(String q) {
        return getOccurrenceCount(q + "&fq=geospatial_kosher%3Atrue");
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
