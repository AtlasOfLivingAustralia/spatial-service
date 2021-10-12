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

package au.org.ala.spatial.process

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.spatial.util.OccurrenceData
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import java.util.logging.Level
import java.util.logging.Logger

@Slf4j
class GDMStep1 extends SlaveProcess {

    void start() {

        def area = JSON.parse(task.input.area.toString())
        def (region, envelope) = processArea(area[0])

        def layers = JSON.parse(task.input.layer.toString())
        def envnameslist = new String[layers.size()]
        layers.eachWithIndex { l, idx ->
            envnameslist[idx] = l
        }

        def species = JSON.parse(task.input.species.toString())
        def speciesArea = getSpeciesArea(species, area)

        //target resolution
        def resolution = task.input.resolution

        if (getSpeciesList(speciesArea).length() < 2) {
            //TODO Log error, >1 species is required
            return
        }

        OccurrenceData od = new OccurrenceData()
        String[] s = od.getSpeciesData(speciesArea.q, speciesArea.bs, null, "names_and_lsid")

        StringBuilder speciesdata = new StringBuilder()
        HashMap taxonNames = new HashMap()
        speciesdata.append("\"X\",\"Y\",\"CODE\"")
        CSVReader reader = new CSVReader(new StringReader(s[0]))
        reader.readNext()  //discard header
        String[] line
        while ((line = reader.readNext()) != null) {
            speciesdata.append("\n").append(line[1]).append(",").append(line[2])
                    .append(",\"").append(getSPindex(taxonNames, line[0])).append("\"")
        }

        // 1. generate species file
        String speciesFile = generateSpeciesFile(getTaskPath(), speciesdata.toString())

        // 2. cut environmental layers
        def cutDataPath = cutGrid(envnameslist, resolution, region, envelope, null)

        //add layer display names to cutDataPath
        String names = ""
        for (int i = 0; i < envnameslist.length; i++) {
            String[] name_displayname = envnameslist[i].split("\\|")
            if (name_displayname.length > 1) {
                envnameslist[i] = name_displayname[0]
                names += "\n" + name_displayname[0] + "=" + name_displayname[1] + " (" + name_displayname[0] + ")"
            } else {
                envnameslist[i] = name_displayname[0]
                names += "\n" + envnameslist[i] + "=" + envnameslist[i]
            }
        }
        FileUtils.writeStringToFile(new File(getTaskPath() + File.separator + "additional_properties.txt"), names)

        // 4. produce domain grid
        //DomainGrid.generate(cutDataPath, layers, region, outputdir);

        // 5. build parameters files for GDM
        String params = generateStep1Paramfile(envnameslist, cutDataPath, speciesFile, getTaskPath())

        // 6. run GDM
        log.error("running GDM:" + grailsApplication.config.gdm.dir.toString() + "-g1 " + params)
        String[] cmd = [grailsApplication.config.gdm.dir.toString(), "-g1", params]
        runCmd(cmd, true, grailsApplication.config.gdm.timeout)

        Scanner sc = new Scanner(new File(getTaskPath() + "Cutpoint.csv"))
        // discard header
        sc.nextLine()

        def cutpoints = ['0,All records,All records']
        def cutpointValues = [0]
        while (sc.hasNextLine()) {
            def nextline = sc.nextLine()
            cutpoints.push(nextline)
            cutpointValues.push(Integer.parseInt(nextline.substring(0, nextline.indexOf(','))))
        }

        generateMetadata(envnameslist, String.valueOf(area[0].area_km), String.valueOf(task.id), getTaskPath())

        // site pairs size parameters
        double maxBytes = 524288000
        long maxS = (int) (maxBytes / ((layers.size() * 3) + 1) / 8)
        // 10% of max
        long minS = (int) (maxS * 0.1)

        def data = [process: 'GDMStep2',
                    input  : [
                            gdmId        : [constraints: [default: task.id]],
                            cutpoint     : [constraints: [content: cutpointValues, labels: cutpoints, header: 'Taxa per Cell,Frequency,Cumulative %']],
                            sitePairsSize: [constraints: [min: minS, max: maxS, default: minS, header: "${minS} - ${maxS}"]]
                    ]
        ]

        addOutput("nextprocess", (data as JSON).toString())
    }

    private String getSPindex(HashMap taxonNames, String sp) {
        if (!taxonNames.containsKey(sp)) {
            taxonNames.put(sp, String.valueOf(taxonNames.size()))
        }
        return (String) taxonNames.get(sp)
    }

    private String generateStep1Paramfile(String[] layers, String layersPath, String speciesfile, String outputdir) {
        try {

            Properties additionalProperties = new Properties()
            File apFile = new File(outputdir + File.separator + "additional_properties.txt")
            if (apFile.exists()) {
                try {
                    additionalProperties.load(new FileReader(apFile))
                } catch (Exception e) {
                }
            }

            StringBuilder envLayers = new StringBuilder()
            StringBuilder useEnvLayers = new StringBuilder()
            StringBuilder predSpline = new StringBuilder()
            for (int i = 0; i < layers.length; i++) {
                envLayers.append("EnvGrid").append(i + 1).append("=").append(layersPath).append(layers[i]).append("\n")
                envLayers.append("EnvGridName").append(i + 1).append("=").
                        append(additionalProperties.getProperty(layers[i], layers[i])).append("\n")
                useEnvLayers.append("UseEnv").append(i + 1).append("=1").append("\n")
                predSpline.append("PredSpl").append(i + 1).append("=3").append("\n")
            }

            StringBuilder sbOut = new StringBuilder()
            sbOut.append("[GDMODEL]").append("\n").append("WorkspacePath=" + outputdir).append("\n").
                    append("RespDataType=RD_SitePlusSpecies").append("\n").append("PredDataType=ED_GridData").
                    append("\n").append("Quantiles=QUANTS_FromData").append("\n").append("UseEuclidean=0").
                    append("\n").append("UseSubSample=1").append("\n").append("NumSamples=10000").append("\n").
                    append("[RESPONSE]").append("\n").append("InputData=" + speciesfile).append("\n").
                    append("UseWeights=0").append("\n").append("[PREDICTORS]").append("\n").append("EuclSpl=3").
                    append("\n").append("NumPredictors=" + layers.length).append("\n").append(envLayers).
                    append("\n").append(useEnvLayers).append("\n").append(predSpline).append("\n")
            PrintWriter spWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputdir + "gdm_params.txt")))
            spWriter.write(sbOut.toString())
            spWriter.close()

            return outputdir + "gdm_params.txt"
        } catch (Exception e) {
            System.out.println("Unable to write the initial params file")
            e.printStackTrace(System.out)
        }

        return ""
    }

    private String generateSpeciesFile(String outputdir, String speciesdata) {
        try {

            File fDir = new File(outputdir)
            fDir.mkdir()

            File spFile = new File(fDir, "species_points.csv")
            FileUtils.writeStringToFile(spFile, speciesdata + "\n", "UTF-8")

            return spFile.getAbsolutePath()

        } catch (Exception e) {
            System.out.println("error generating species file")
            e.printStackTrace(System.out)
        }
    }

    private void generateMetadata(String[] layers, String area, String pid, String outputdir) {
        try {
            int i = 0
            System.out.println("Generating metadata...")
            StringBuilder sbMetadata = new StringBuilder()

            sbMetadata.append("<!doctype html><head><meta charset='utf-8'><title>Genralized Dissimilarity Model</title><meta name='description' content='ALA GDM Metadata'>")
            sbMetadata.append("<style type='text/css'>body{font-family:Verdana,'Lucida Sans';font-size:small;}div#core{display:block;clear:both;margin-bottom:20px;}section{width:95%;margin:0 15px;border-bottom:1px solid #000;}.clearfix:after{content:'.';display:block;clear:both;visibility:hidden;line-height:0;height:0;}.clearfix{display:inline-block;}html[xmlns] .clearfix{display:block;}* html .clearfix{height:1%;}</style>")
            sbMetadata.append("</head><body><div id=wrapper><header><h1>Genralized Dissimilarity Model</h1></header><div id=core class=clearfix><section><p>")
            sbMetadata.append("This GDM model was created Wed Feb 29 20:50:37 EST 2012. Data available in this folder is available for further analysis. </p>")
            sbMetadata.append("<h3>Your options:</h3><ul>")

            sbMetadata.append("<li>Model reference number:").append(pid).append("</li>")
            //sbMetadata.append("<li>Assemblage:").append(name).append("</li>")
            sbMetadata.append("<li>Area:").append(area).append("</li>")

            Properties additionalProperties = new Properties()
            File apFile = new File(outputdir + File.separator + "additional_properties.txt")
            if (apFile.exists()) {
                try {
                    additionalProperties.load(new FileReader(apFile))
                } catch (Exception e) {
                }
            }

            sbMetadata.append("<li>Layers: <ul>")
            String images = ""
            for (i = 0; i < layers.length; i++) {
                sbMetadata.append("<li>").append(additionalProperties.getProperty(layers[i], layers[i])).append("</li>")
                images += "<img src='plots/" + layers[i] + ".png'/>"
            }
            sbMetadata.append("</li></ul></li></ul></section>")

            sbMetadata.append("<section><h3>Response Histogram (observed dissimilarity class):</h3><p> The Response Histogram plots the distribution of site pairs within each observed dissimilarity class. The final column in the dissimilarity class > 1 represents the number of site pairs that are totally dissimilar from each other. This chart provides an overview of potential bias in the distribution of the response data. </p><p><img src='plots/resphist.png'/></p></section><section><h3>Observed versus predicted compositional dissimilarity (raw data plot):</h3><p> The 'Raw data' scatter plot presents the Observed vs Predicted degree of compositional dissimilarity for a given model run. Each dot on the chart represents a site-pair. The line represents the perfect 1:1 fit. (Note that the scale and range of values on the x and y axes differ). </p><p> This chart provides a snapshot overview of the degree of scatter in the data. That is, how well the predicted compositional dissimilarity between site pairs matches the actual compositional dissimilarity present in each site pair. </p><p><img src='plots/obspredissim.png'/></p></section><section><h3>Observed compositional dissimilarity vs predicted ecological distance (link function applied to the raw data plot):</h3><p> The 'link function applied' scatter plot presents the Observed compositional dissimilarity vs Predicted ecological distance. Here, the link function has been applied to the predicted compositional dissimilarity to generate the predicted ecological distance. Each dot represents a site-pair. The line represents the perfect 1:1 fit. The scatter of points signifies noise in the relationship between the response and predictor variables. </p><p><img src='plots/dissimdist.png'/></p></section><section><h3>Predictor Histogram:</h3><p> The Predictor Histogram plots the relative contribution (sum of coefficient values) of each environmental gradient layer that is relevant to the model. The sum of coefficient values is a measure of the amount of predicted compositional dissimilarity between site pairs. </p><p> Predictor variables that contribute little to explaining variance in compositional dissimilarity between site pairs have low relative contribution values. Predictor variables that do not make any contribution to explaining variance in compositional dissimilarity between site pairs (i.e., all coefficient values are zero) are not shown. </p><p><img src='plots/predhist.png'/></p></section><section><h3>Fitted Functions:</h3><p> The model output presents the response (compositional turnover) predicted by variation in each predictor. The shape of the predictor is represented by three I-splines, the values of which are defined by the environmental data distribution: min, max and median (i.e., 0, 50 and 100th percentiles). The GDM model estimates the coefficients of the I-splines for each predictor. The coefficient provides an indication of the total amount of compositional turnover correlated with each value at the 0, 50 and 100th percentiles. The sum of these coefficient values is an indicator of the relative importance of each predictor to compositional turnover. </p><p> The coefficients are applied to the ecological distance from the minimum percentile for a predictor. These plots of fitted functions show the sort of monotonic transformations that will take place to a predictor to render it in GDM space. The relative maximum y values (sum of coefficient values) indicate the amount of influence that each predictor makes to the total GDM prediction. </p>" +
                    "<p>" +
                    images +
                    "</p>" +
                    "</section></div><footer><p>&copy; <a href='https://www.ala.org.au/'>Atlas of Living Australia 2012</a></p></footer></div></body></html>")
            sbMetadata.append("")

            File spFile = new File(outputdir + "gdm.html")
            System.out.println("Writing metadata to: " + spFile.getAbsolutePath())
            PrintWriter spWriter
            spWriter = new PrintWriter(new BufferedWriter(new FileWriter(spFile)))
            spWriter.write(sbMetadata.toString())
            spWriter.close()
        } catch (IOException ex) {
            Logger.getLogger(GDMWSController.class.getName()).log(Level.SEVERE, null, ex)
        }
    }
}
