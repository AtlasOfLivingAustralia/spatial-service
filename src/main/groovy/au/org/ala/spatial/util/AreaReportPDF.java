package au.org.ala.spatial.util;

import au.com.bytecode.opencsv.CSVReader;
import au.org.ala.layers.dto.Distribution;
import au.org.ala.spatial.Util;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
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
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AreaReportPDF {
    private static final Logger LOGGER = Logger.getLogger(AreaReportPDF.class);

    private static final int PROGRESS_COUNT = 92;

    private static final String[] SPECIES_GROUPS = new String[]{"Algae", "Amphibians", "Angiosperms", "Animals",
            "Arthropods", "Bacteria"
            , "Birds", "Bryophytes", "Chromista", "Crustaceans", "Dicots", "FernsAndAllies", "Fishes", "Fungi"
            , "Gymnosperms", "Insects", "Mammals", "Molluscs", "Monocots", "Plants", "Protozoa", "Reptiles"};

    private String area_km;

    private String areaName;
    private Map<String, String> counts;
    private Map<String, String> csvs;
    private Map<String, String> speciesLinks;
    private String[] checklists;
    private String[] distributions;
    List<JSONObject> documents;

    private Map tabulation;
    private int fileNumber;

    private String filePath;
    private String query;

    private String wkhtmltopdfPath;
    private String bbox;
    String geoserverUrl;
    String biocacheServiceUrl;
    String biocacheHubUrl;

    String speciesListThreatened;
    String speciesListInvasive;

    String journalMapUrl;

    private Map progress;
    private String serverUrl;
    private String[][] reportLayers = {
            {
                    "cl1918",
                    "National Dynamic Land Cover",
                    "https://spatial.ala.org.au/geoserver/wms?REQUEST=GetLegendGraphic&VERSION=1.0.0&FORMAT=image/png&WIDTH=20&HEIGHT=9&LAYER=dlcmv1",
                    "N",
                    "<br></br>The Dynamic Land Cover Dataset is the first nationally consistent and thematically comprehensive land cover reference for Australia. It provides a base-line for reporting on change and trends in vegetation cover and extent. Information about land cover dynamics is essential to understanding and addressing a range of national challenges such as drought, salinity, water availability and ecosystem health. The data is a synopsis of land cover information for every 250m by 250m area of the country from April 2000 to April 2008. The classification scheme used to describe land cover categories in the Dataset conforms to the 2007 International Standards Organisation (ISO) land cover standard (19144-2). The Dataset shows Australian land covers clustered into 34 ISO classes. These reflect the structural character of vegetation, ranging from cultivated and managed land covers (crops and pastures) to natural land covers such as closed forest and open grasslands. [Ref1]<br></br>Australia's Dynamic Land Cover: <a href='http://www.ga.gov.au/earth-observation/landcover.html'>http://www.ga.gov.au/earth-observation/landcover.html</a><br></br>National Dynamic Land Cover layer: Classification: Vegetation; Type: Contextual (polygonal); Metadata contact organisation: Geoscience Australia (GA). <a href='https://spatial.ala.org.au/ws/layers/view/more/dlcmv1'>https://spatial.ala.org.au/ws/layers/view/more/dlcmv1</a>"
            },
            {
                    "cl1053",
                    "Global Context Ecoregions",
                    "",
                    "Y",
                    "<br></br>Terrestrial Ecoregions of the World (TEOW)<br></br>Terrestrial Ecoregions of the World (TEOW) is a biogeographic regionalisation of the Earth's terrestrial biodiversity. Our biogeographic units are ecoregions, which are defined as relatively large units of land or water containing a distinct assemblage of natural communities sharing a large majority of species, dynamics, and environmental conditions. There are 867 terrestrial ecoregions, classified into 14 different biomes such as forests, grasslands, or deserts. Ecoregions represent the original distribution of distinct assemblages of species and communities. [Ref2]<br></br>TEOW: <a href='https://worldwildlife.org/biome-categories/terrestrial-ecoregions'>https://worldwildlife.org/biome-categories/terrestrial-ecoregions</a><br></br>Terrestrial Ecoregional Boundaries layer: Classification: Biodiversity - Region; Type: Contextual (polygonal); Metadata contact organisation: The Nature Conservancy (TNC).  <a href='https://spatial.ala.org.au/ws/layers/view/more/1053'>https://spatial.ala.org.au/ws/layers/view/more/1053</a>"
            },
            {
                    "cl1052",
                    "Freshwater Ecoregions of the World (FEOW)",
                    "",
                    "Y",
                    "<br></br>Freshwater Ecoregions of the World (FEOW) is a collaborative project providing the first global biogeographic regionalization of the Earth's freshwater biodiversity, and synthesizing biodiversity and threat data for the resulting ecoregions. We define a freshwater ecoregion as a large area encompassing one or more freshwater systems that contains a distinct assemblage of natural freshwater communities and species. The freshwater species, dynamics, and environmental conditions within a given ecoregion are more similar to each other than to those of surrounding ecoregions and together form a conservation unit. [Ref5]<br></br>FEOW: <a href='https://worldwildlife.org/biome-categories/freshwater-ecoregions'>https://worldwildlife.org/biome-categories/freshwater-ecoregions</a><br></br>Freshwater Ecoregions of the World layer: Classification: Biodiversity - Region; Type: Contextual (polygonal); Metadata contact organisation: TNC. <a href='https://spatial.ala.org.au/ws/layers/view/more/1052'>https://spatial.ala.org.au/ws/layers/view/more/1052</a>"
            }
    };

    String pid;
    String dataDir;

    public AreaReportPDF(String geoserverUrl, String biocacheServiceUrl, String biocacheHubUrl, String q, String pid,
                         String areaName,
                         String area_km,
                         List<String> facets, Map progress, String serverUrl,
                         String[][] reportLayers, String outputPath,
                         String journalMapUrl, String dataDir) {
        this.dataDir = dataDir;

        try {
            speciesListThreatened = URLEncoder.encode("species_list_uid:dr1782 OR species_list_uid:dr967 OR species_list_uid:dr656 OR species_list_uid:dr649 OR species_list_uid:dr650 OR species_list_uid:dr651 OR species_list_uid:dr492 OR species_list_uid:dr1770 OR species_list_uid:dr493 OR species_list_uid:dr653 OR species_list_uid:dr884 OR species_list_uid:dr654 OR species_list_uid:dr655 OR species_list_uid:dr490 OR species_list_uid:dr2201", "UTF-8");
            speciesListInvasive = URLEncoder.encode("species_list_uid:dr947 OR species_list_uid:dr707 OR species_list_uid:dr945 OR species_list_uid:dr873 OR species_list_uid:dr872 OR species_list_uid:dr1105 OR species_list_uid:dr1787 OR species_list_uid:dr943 OR species_list_uid:dr877 OR species_list_uid:dr878 OR species_list_uid:dr1013 OR species_list_uid:dr879 OR species_list_uid:dr880 OR species_list_uid:dr881 OR species_list_uid:dr882 OR species_list_uid:dr883 OR species_list_uid:dr927 OR species_list_uid:dr823", "UTF-8");
        } catch (Exception e) {
        }

        this.journalMapUrl = journalMapUrl;

        this.areaName = areaName;
        this.progress = progress;
        this.serverUrl = serverUrl;
        this.geoserverUrl = geoserverUrl;
        this.biocacheServiceUrl = biocacheServiceUrl;
        this.biocacheHubUrl = biocacheHubUrl;

        this.pid = pid;
        this.query = q;

        this.area_km = area_km;

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

        setProgress("Finished", 1);
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

    private void makeHTML() {
        //make report
        fileNumber = 1;
        try {
            //read data

            JSONParser jp = new JSONParser();
            String tabulationFile = filePath + "/tabulations.json";
            LOGGER.debug("Temp tabulation file: " + tabulationFile);
            LOGGER.debug(FileUtils.readFileToString(new File(filePath + "/tabulations.json")));
            JSONObject tabulations = (JSONObject) jp.parse(FileUtils.readFileToString(new File(filePath + "/tabulations.json"), "UTF-8"));
            JSONObject csvs = (JSONObject) jp.parse(FileUtils.readFileToString(new File(filePath + "/csvs.json")));
            JSONObject counts = (JSONObject) jp.parse(FileUtils.readFileToString(new File(filePath + "/counts.json"), "UTF-8"));

            //header
            String filename = filePath + "/report.html";
            FileWriter fw = startHtmlOut(fileNumber, filename);

            //box summary
            fw.write("<img  id='imgHeader' src='" + serverUrl + "/static/image/header.jpg' width='100%' ></img>");
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
                        (JSONArray) tabulations.get(shortname)
                        , geoserver_url.isEmpty() ? null : geoserver_url);
                fw.write("</body></html>");
                fw.close();
            }

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
            //expert distributions
            count = Integer.parseInt(counts.get("Distribution Areas").toString());
            speciesPage(false, fw, "My Area", "Expert Distributions", notes, tableNumber,
                    count, -1, figureNumber, null, csvs.get("e").toString());
            fw.write("</body></html>");
            fw.close();
            fileNumber++;
            fw = startHtmlOut(fileNumber, filename);
            count = Integer.parseInt(counts.get("Checklist Areas").toString());
            speciesPage(false, fw, "My Area", "Checklist Areas", notes, tableNumber,
                    count, -1, figureNumber, null, csvs.get("c").toString());
            fw.write("</body></html>");
            fw.close();
            fileNumber++;
            fw = startHtmlOut(fileNumber, filename);

            //Journalmap page
            count = Integer.parseInt(counts.get("Journalmap").toString());
            countKosher = Integer.parseInt(counts.get("Journalmap").toString());
            imageUrl = null;
            notes = "<a href='https://journalmap.org'>JournalMap</a>";
            speciesPage(false, fw, "My Area", "JournalMap Articles", notes, tableNumber, count, -1, figureNumber, null,
                    csvs.get("Journalmap").toString());
            tableNumber++;
            fw.write("</body></html>");
            fw.close();
            fileNumber++;

        } catch (Exception e) {
            LOGGER.error("failed to produce report pdf", e);
        }
    }

    private FileWriter startHtmlOut(int fileNumber, String filename) throws Exception {
        FileWriter fw = new FileWriter(filename.replace(".", "." + fileNumber + "."));
        fw.write("<html>");
        fw.write("<head><link rel='stylesheet' type='text/css' href='" + serverUrl + "/static/area-report/areaReport.css'></link></head>");
        fw.write("<body>");
        return fw;
    }

    private void speciesPage(boolean isSpecies, FileWriter fw, String areaName, String title, String notes, int tableNumber, int count, int countKosher, int figureNumber, String imageUrl, String csv) throws Exception {
        String imageUrlActual = imageUrl;

        fw.write("<table id='species'>");
        fw.write("<tr>");
        fw.write("<td><h1 class='title' id='" + StringEscapeUtils.escapeHtml(title) + "'>");
        fw.write(StringEscapeUtils.escapeHtml(title));
        fw.write("</h1></td>");
        fw.write("</tr><tr>");
        fw.write("<td>");
        fw.write("<br></br>Number of " + StringEscapeUtils.escapeHtml(title.toLowerCase()) + ": <b>" + count + "</b>");
        fw.write("</td>");
        fw.write("</tr><tr>");
        fw.write("<td><br></br>");
        fw.write(StringEscapeUtils.escapeHtml(notes));
        fw.write("</td>");
        fw.write("</tr><tr>");
        if (countKosher >= 0) {
            fw.write("<td>");
            fw.write("<br></br>Number of " + StringEscapeUtils.escapeHtml(title.toLowerCase()) + " (spatially valid only): <b>" + countKosher + "</b>");
            fw.write("</td>");
            fw.write("</tr><tr>");
        }

        if ((count > 0 || countKosher > 0) && imageUrlActual != null) {
            fw.write("<td>");
            fw.write("<br></br><img src='" + StringEscapeUtils.escapeHtml(imageUrlActual) + "'></img>");
            fw.write("</td>");
            fw.write("</tr><tr>");
            fw.write("<td id='figure'>");
            fw.write("<b>Figure " + figureNumber + ":</b> Map of " + StringEscapeUtils.escapeHtml(title) + " in " + StringEscapeUtils.escapeHtml(areaName));
            fw.write("</td>");
            fw.write("</tr><tr>");
        }

        //tabulation table
        if ((count > 0 || countKosher > 0) && csv != null) {
            CSVReader r = new CSVReader(new StringReader(csv));

            fw.write("<td id='tableNumber'><br></br><b>Table " + tableNumber + ":</b> " + StringEscapeUtils.escapeHtml(title));
            if (speciesLinks.get(title.replace("lifeform - ", "")) != null) {
                fw.write("<a href='" + StringEscapeUtils.escapeHtml(speciesLinks.get(title.replace("lifeform - ", "")).replace(biocacheServiceUrl, biocacheHubUrl)) + "'>(Link to full list)</a>");
            }
            fw.write("</td></tr><tr><td>");

            fw.write("<table id='table'>");

            //reorder select columns
            int[] columnOrder;
            if (isSpecies) {
                columnOrder = new int[]{8, 1, 10, 11};
                fw.write("<tr><td>Family</td><td id='scientificName' >Scientific Name</td><td>Common Name</td><td>No. Occurrences</td>");
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
                if (row % 2 == 0) {
                    fw.write("<tr class='odd'>");
                } else {
                    fw.write("<tr class='even'>");
                }
                for (int i = 0; i < columnOrder.length && columnOrder[i] < line.length; i++) {
                    fw.write("<td><div>" + StringEscapeUtils.escapeHtml(line[columnOrder[i]]) + "</div></td>");
                }
                fw.write("</tr>");

                row++;
            }

            fw.write("</table>");
            fw.write("</td>");
        }

        fw.write("</tr>");
        fw.write("</table>");
    }

    private void mapPage(FileWriter fw, String areaName, int figureNumber, int tableNumber, String imageUrl, String notes, JSONArray tabulation, String legendUrl) throws Exception {
        String imageUrlActual = imageUrl;

        fw.write("<table id='mapPage'>");
        fw.write("<tr>");
        fw.write("<td><h1 class='title' id='" + areaName + "'>");
        fw.write(areaName);
        fw.write("</h1></td>");
        fw.write("</tr><tr>");
        fw.write("<td><br></br>");
        fw.write(notes);
        fw.write("</td>");
        fw.write("</tr><tr>");
        fw.write("<td>");
        if (imageUrlActual.endsWith("base_area.png")) {
            fw.write("<br></br><img src='base_area_zoomed_out.png'></img>");
        }
        fw.write("<br></br><img " + (legendUrl != null ? "id='imgWithLegend' " : "") + " src='" + imageUrlActual + "'></img>");
        if (legendUrl != null) {
            fw.write("<img id='legend' src='" + StringEscapeUtils.escapeHtml(legendUrl) + "'></img>");
        }
        fw.write("</td>");
        fw.write("</tr><tr>");
        fw.write("<td id='figure'>");
        fw.write("<b>Figure " + figureNumber + ":</b> Map of " + StringEscapeUtils.escapeHtml(areaName));
        fw.write("</td>");
        fw.write("</tr><tr>");

        //tabulation table
        if (tabulation != null) {
            double totalArea = 0;
            for (Object o : tabulation) {
                JSONObject jo = (JSONObject) o;
                totalArea += Double.parseDouble(jo.get("area").toString()) / 1000000.0;
            }

            if (totalArea > 0) {
                fw.write("<td id='tableNumber'>");
                fw.write("<br></br><b>Table " + tableNumber + ":</b> " + StringEscapeUtils.escapeHtml(areaName));
                fw.write("</td></tr><tr><td>");
                fw.write("<br></br><table id='table'><tr><td>Class/Region</td><td>Area (sq km)</td><td>% of total area</td></tr>");

                int row = 0;
                for (Object o : tabulation) {
                    JSONObject jo = (JSONObject) o;
                    if (row % 2 == 0) {
                        fw.write("<tr class='odd'>");
                    } else {
                        fw.write("<tr class='odd'>");
                    }
                    fw.write("<td>");
                    fw.write(StringEscapeUtils.escapeHtml(jo.get("name1").toString()));
                    fw.write("</td><td>");
                    fw.write(String.format("%.2f", Double.parseDouble(jo.get("area").toString()) / 1000000.0));
                    fw.write("</td><td>");
                    fw.write(String.format("%.2f", Double.parseDouble(jo.get("area").toString()) / 1000000.0 / totalArea * 100));
                    fw.write("</td></tr>");
                    row++;
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

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCountEndemicSpecies();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initCountDistributionAreas();

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initDistributionsCsv(Distribution.SPECIES_CHECKLIST);

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initDistributionsCsv(Distribution.EXPERT_DISTRIBUTION);

                return null;
            }
        });

        callables.add(new Callable() {
            @Override
            public Object call() throws Exception {
                initJournalmapCsv();

                return null;
            }
        });

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

            FileUtils.copyURLToFile(new URL(serverUrl + "/static/area-report/toc.xsl"),
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
                    tabulation.put(fid, jp.parse(Util.getUrl(serverUrl + "/tabulation/" + fid + "/" + pid + ".json")));
                } catch (Exception e) {
                    LOGGER.error("failed tabulation: fid=" + fid + ", pid=" + pid);
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

    private void initDistributionsCsv(String type) {
        setProgress("Getting information: " + type, 0);
        if (isCancelled()) return;
        StringBuilder sb = new StringBuilder();
        String[] list = new String[0];

        JSONArray checklistsData = null;
        try {
            checklistsData = Util.getDistributionsOrChecklistsData("checklists", pid, null, null, serverUrl);
            list = Util.getDistributionsOrChecklists(checklistsData);
        } catch (Exception e) {
            LOGGER.error("failed to get checklists", e);
        }

        for (String line : list) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        if (type.equals(Distribution.SPECIES_CHECKLIST)) {
            checklists = list;
            if (checklists.length <= 0) {
                counts.put("Checklist Areas", "0");
                counts.put("Checklist Species", "0");
            } else {
                String[] areaChecklistText = Util.getAreaChecklists(checklists, checklistsData);
                counts.put("Checklist Areas", String.valueOf(areaChecklistText.length - 1));

                Set<String> set = new HashSet<>();
                for (int i=0;i<checklistsData.size();i++) {
                    String species = (String) ((JSONObject) checklistsData.get(i)).get("lsid");
                    if (StringUtils.isEmpty(species)) {
                        species = (String) ((JSONObject) checklistsData.get(i)).get("scientific");
                    }
                    if (StringUtils.isNotEmpty(species)) {
                        set.add(species);
                    }
                }

                counts.put("Checklist Species", String.valueOf(set.size()));
            }
        } else {
            distributions = list;
            if (distributions.length <= 0) {
                counts.put("Distribution Areas", "0");
            } else {
                counts.put("Distribution Areas", String.valueOf(distributions.length - 1));
            }
        }

        csvs.put(type, sb.toString());
    }

    private void initJournalmapCsv() throws Exception {
        JSONParser jp = new JSONParser();

        setProgress("Getting information: Journalmap", 0);
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
            counts.put("Journalmap", "0");
        } else {
            counts.put("Journalmap", String.valueOf(documents.size()));
        }

        csvs.put("Journalmap", sb.toString());
    }


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

    private Integer getEndemicSpeciesCount() {
        JSONParser jp = new JSONParser();
        JSONArray ja = null;
        try {
            ja = (JSONArray) jp.parse(Util.getUrl(biocacheServiceUrl + "/explore/endemic/species/" + query.replace("qid:","") + "?facets=names_and_lsid"));

            return ja.size();
        } catch (Exception e) {
            LOGGER.error("failed to parse endemic species for " + query, e);
        }

        return 0;
    }

    private void initCountEndemicSpecies() {
        setProgress("Getting information: endemic species count", 0);
        if (isCancelled()) return;
        counts.put("Endemic Species", String.valueOf(getEndemicSpeciesCount()));
    }

    private void initCountThreatenedSpecies() {
        setProgress("Getting information: threatened species", 0);
        if (isCancelled()) return;
        String q = query + "&fq=state_conservation:Endangered";
        counts.put("Endangered Species", String.valueOf(getSpeciesCount(q)));
    }

    private void initCountDistributionAreas() {
        setProgress("Getting information: distribution areas", 0);
        String[] distributions = new String[0];

        JSONArray distributionsData;
        try {
            distributionsData = Util.getDistributionsOrChecklistsData("distributions", pid, null, null, serverUrl);
            distributions = Util.getDistributionsOrChecklists(distributionsData);
        } catch (Exception e) {
            LOGGER.error("failed to get distributions", e);
        }

        if (checklists.length <= 0) {
            counts.put("Distribution Areas", "0");
        } else {
            counts.put("Distribution Areas", String.valueOf(distributions.length - 1));
        }
    }

    private void initCountArea() {
        DecimalFormat df = new DecimalFormat("###,###.##");
        String area = df.format(Double.parseDouble(area_km));

        counts.put("Area (sq km)", area);
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
            saveImage(shortname, new PrintMapComposer(extents, basemap, new String[]{mlArea, ml}, aspectRatio, "", type, resolution, dataDir, null).get());
        }

        setProgress("Getting information: making map of area", 0);
        if (isCancelled()) return;
        saveImage("base_area", new PrintMapComposer(extents, basemap, new String[]{mlArea}, aspectRatio, "", type, resolution, dataDir, null).get());

        setProgress("Getting information: making map of area overview", 0);
        if (isCancelled()) return;
        saveImage("base_area_zoomed_out", new PrintMapComposer(extentsLarge, basemap, new String[]{mlArea}, aspectRatio, "", type, resolution, dataDir, null).get());

        setProgress("Getting information: making occurrences", 0);
        if (isCancelled()) return;
        saveImage("occurrences", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, mlSpecies}, aspectRatio, "", type, resolution, dataDir, null).get());

        setProgress("Getting information: making threatened species", 0);
        if (isCancelled()) return;
        saveImage("Threatened_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, threatenedSpecies}, aspectRatio, "", type, resolution, dataDir, null).get());

        setProgress("Getting information: making iconic species", 0);
        if (isCancelled()) return;
        saveImage("Iconic_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, iconicSpecies}, aspectRatio, "", type, resolution, dataDir, null).get());

        setProgress("Getting information: making migratory species", 0);
        if (isCancelled()) return;
        saveImage("Migratory_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, migratorySpecies}, aspectRatio, "", type, resolution, dataDir, null).get());

        setProgress("Getting information: making invasive species", 0);
        if (isCancelled()) return;
        saveImage("Invasive_Species", new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, invasiveSpecies}, aspectRatio, "", type, resolution, dataDir, null).get());

        for (int i = 0; i < SPECIES_GROUPS.length; i++) {
            setProgress("Getting information: making map of lifeform " + SPECIES_GROUPS[i], 0);
            if (isCancelled()) return;
            saveImage("lifeform - " + SPECIES_GROUPS[i], new PrintMapComposer(extentsSmall, basemap, new String[]{mlArea, lifeforms.get(i)}, aspectRatio, "", type, resolution, dataDir, null).get());
        }
    }

    void saveImage(String name, byte[] bytes) {
        try {
            new File(filePath + File.separator).mkdirs();
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath + File.separator + name + ".png"));
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
