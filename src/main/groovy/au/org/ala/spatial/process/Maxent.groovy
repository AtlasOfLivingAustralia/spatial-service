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

import au.org.ala.layers.grid.GridCutter
import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

@Slf4j
class Maxent extends SlaveProcess {

    void start() {
        //update ALOC
        slaveService.getFile('/modelling/maxent/maxent.jar')

        //list of layers
        def layers = JSON.parse(task.input.layer.toString())
        def contextualLayers = []
        layers.each { layer ->
            if (layer.endsWith('_aloc')) {
                contextualLayers.add(layer)
            } else {
                def l = getField(layer)
                if (l != null && l.type == 'c') {
                    contextualLayers.add(l.id)
                }
            }
        }

        //area to restrict
        def area = JSON.parse(task.input.area.toString())
        def (region, envelope) = processArea(area[0])

        //target resolution
        def resolution = task.input.resolution

        //number of target species
        def species = JSON.parse(task.input.species.toString())

        def speciesArea = getSpeciesArea(species, area)

        def jackknife = task.input.jackknife.toString().toBoolean()
        def responseCurves = task.input.responseCurves.toString().toBoolean()
        def testPercentage = task.input.testPercentage.toString().toDouble()

        new File(getTaskPath()).mkdirs()

        def cutDataPath = cutGrid((layers as List).toArray(new String[layers.size()]), resolution.toString(), region, envelope, null)
        def speciesPath = downloadSpecies(speciesArea)

        if (speciesPath.size() == 0) {
            //TODO: error
        }

        def cmd = ["java", "-mx" + String.valueOf(grailsApplication.config.maxent.mx),
                   "-jar", grailsApplication.config.data.dir + '/modelling/maxent/maxent.jar',
                   "-e", cutDataPath, "-s", speciesPath.get(0), "-a", "tooltips=false",
                   "nowarnings", "noprefixes", "-z",
                   "threads=" + grailsApplication.config.maxent.threads, "randomtestpoints=" + (int) (testPercentage * 50),
                   "-o", getTaskPath()]
        if (jackknife) cmd.add("-J")
        if (responseCurves) cmd.add("-P")
        contextualLayers.each { layer ->
            cmd.add("-t")
            cmd.add(layer)
        }

        cmd.toArray()
        String[] cmdList = new String[cmd.size()]
        cmd.eachWithIndex { def entry, int i ->
            cmdList[i] = entry
        }
        runCmd(cmdList, true)

        //format output

        // check if there is an error
        String maxentError = getMaxentError(new File(getTaskPath() + "maxent.log"), 2)
        if (maxentError != null) {
            //TODO: error
        } else {
            def replaceMap = [:]

            replaceMap.put("Maxent model for species", "Maxent model for " + speciesArea.name)

            String paramlist = "Model reference number: " + task.id + "<br>Species: " + speciesArea.name + "<br>Layers: <ul>"

            layers.each {
                def field = getField(it)

                def displayname = it
                if (field != null) {
                    def layer = getLayer(field.spid)
                    if (layer != null) {
                        displayname = layer.displayname + " (" + field.id + ")"
                    }
                }

                paramlist += "<li>" + displayname + "</li>"

                replaceMap.put("<td>" + it + "</td>", "<td>" + getLayer(field.spid).displayname + "</td>")

                Util.readReplaceAfter(getTaskPath() + "species.html", "(all continuous)", it, displayname)
            }

            replaceMap.put("end of this page.<br>", "end of this page.<br><p>" + paramlist + "</p>")
            //replaceMap.put("This page contains some analysis of the Maxent model for", "This <a href='http://www.cs.princeton.edu/~schapire/maxent/'>Maxent</a> v3.3.3e predictive model for")
            //replaceMap.put(", created", " was created")
            //replaceMap.put(" using Maxent version 3.3.3e.", ".")
            replaceMap.put("If you would like to do further analyses, the raw data used here is linked to at the end of this page", "Links at the bottom of this page to the raw data may be used for further analysis")
            replaceMap.put(getTaskPath(), "")

            paramlist += "</ul>"

            Util.readReplaceBetween(getTaskPath() + "species.html", "Command line", "<br>", "")
            Util.readReplaceBetween(getTaskPath() + "species.html", "Command line", "<br>", "")

            if (responseCurves) {
                StringBuffer sbTable = new StringBuffer()

                contextualLayers.each { ctx ->
                    sbTable.append("<pre>")
                    if (!ctx.endsWith("_aloc")) {
                        sbTable.append("<span style='font-weight: bold; text-decoration: underline'>" + ctx + " legend</span><br />")
                        sbTable.append(IOUtils.toString(new FileInputStream(GridCutter.getLayerPath(resolution.toString(), ctx, ctx) + ".txt")))
                        sbTable.append("<br /><br />")
                        sbTable.append("</pre>")
                    }
                    replaceMap.put("<br><HR><H2>Analysis of variable contributions</H2><br>", sbTable.toString() + "<br><HR><H2>Analysis of variable contributions</H2><br>")
                }
            }

            Util.readReplaceBetween(getTaskPath() + "species.html", "<br>Click <a href=species_explain.bat", "memory.<br>", "")
            Util.readReplaceBetween(getTaskPath() + "species.html", "(A link to the Explain", "additive models.)", "")

            StringBuffer removedSpecies = new StringBuffer()
            try {
                if (speciesPath.size() == 2) {
                    BufferedReader br = new BufferedReader(new FileReader(speciesPath.get(1)))
                    String ss
                    while ((ss = br.readLine()) != null) {
                        removedSpecies.append(ss)
                    }
                    br.close()

                    String header = "'Sensitive species' have been masked out of the model. See: https://www.ala.org.au/about/program-of-projects/sds/\r\n\r\nLSID,Species scientific name,Taxon rank"
                    FileUtils.writeStringToFile(header + removedSpecies.toString(),
                            getTaskPath() + File.separator + "Prediction_maskedOutSensitiveSpecies.csv")

                    String insertBefore = "<a href = \"species.asc\">The"
                    String insertText = "<b><a href = \"Prediction_maskedOutSensitiveSpecies.csv\">'Sensitive species' masked out of the model</a></br></b>"
                    replaceMap.put(insertBefore, insertText + insertBefore)
                }
            } catch (err) {
            }

            Util.replaceTextInFile(getTaskPath() + "species.html", replaceMap)

            //writeProjectionFile(getTaskPath());

            //convert .asc to .grd/.gri
            convertAsc(getTaskPath() + "species.asc", "${grailsApplication.config.data.dir}/layer/${task.id}_species")
        }
        writeMaxentsld(grailsApplication.config.data.dir + "/layer/" + task.id + "_species.sld")
        addOutput("layers", "/layer/" + task.id + "_species.sld")

        addOutput("layers", "/layer/" + task.id + "_species.tif")

        if (new File(getTaskPath() + "species.asc").exists()) {
            addOutput("files", "species.asc", true)
        }

        addOutput("files", "plots/", true)
        addOutput("metadata", "species.html", true)
//        addOutput("files", "species.lambdas", true)
//        addOutput("files", "species_sampleAverages.csv", true)
//        addOutput("files", "species_samplePredictions.csv", true)

        if (speciesPath.size() == 2) {
            File target = new File(getTaskPath() + "removedSpecies.txt")
            if (target.exists()) target.delete()
            FileUtils.moveFile(speciesPath[1], target)
            addOutput("files", "removedSpecies.txt", true)
        }

        addOutput("files", "maxentResults.csv", true)
        addOutput("files", "maxent.log", true)

        if (new File(getTaskPath() + "species.grd").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_species.grd")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + "_species.grd"), target)
            addOutput("layers", "/layer/" + task.id + "_species.grd")
        }
        if (new File(getTaskPath() + "species.gri").exists()) {
            File target = new File(grailsApplication.config.data.dir + '/layer/' + task.id + "_species.gri")
            if (target.exists()) target.delete()
            FileUtils.moveFile(new File(getTaskPath() + "_species.gri"), target)
            addOutput("layers", "/layer/" + task.id + "_species.gri")
        }
    }

    def writeMaxentsld(filename) {
        def resource = Maxent.class.getResource("/maxent/maxent.sld")
        FileUtils.writeStringToFile(new File(filename), resource.text)
    }

    private void writeProjectionFile(String outputpath) {
        try {
            File fDir = new File(outputpath)
            fDir.mkdir()

            PrintWriter spWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputpath + "species.prj")))

            StringBuffer sbProjection = new StringBuffer()
            sbProjection.append("GEOGCS[\"WGS 84\", ").append("\n")
            sbProjection.append("    DATUM[\"WGS_1984\", ").append("\n")
            sbProjection.append("        SPHEROID[\"WGS 84\",6378137,298.257223563, ").append("\n")
            sbProjection.append("            AUTHORITY[\"EPSG\",\"7030\"]], ").append("\n")
            sbProjection.append("        AUTHORITY[\"EPSG\",\"6326\"]], ").append("\n")
            sbProjection.append("    PRIMEM[\"Greenwich\",0, ").append("\n")
            sbProjection.append("        AUTHORITY[\"EPSG\",\"8901\"]], ").append("\n")
            sbProjection.append("    UNIT[\"degree\",0.01745329251994328, ").append("\n")
            sbProjection.append("        AUTHORITY[\"EPSG\",\"9122\"]], ").append("\n")
            sbProjection.append("    AUTHORITY[\"EPSG\",\"4326\"]] ").append("\n")

            spWriter.write(sbProjection.toString())
            spWriter.close()

        } catch (IOException ex) {
            ex.printStackTrace(System.out)
        }
    }

    private String getMaxentError(File file, int count) {
        try {
            RandomAccessFile rf = new RandomAccessFile(file, "r")

            // first check if maxent threw a 'No species selected' error
            String nosp = rf.readLine() // first line: date/time
            nosp = rf.readLine() // second line: maxent version
            nosp = rf.readLine() // third line: "No species selected"
            if (nosp.equals("No species selected")) {
                return "No species selected"
            }

            long flen = file.length() - 1
            int nlcnt = -1
            StringBuilder lines = new StringBuilder()
            while (nlcnt != count) {
                rf.seek(flen--)
                char c = (char) rf.read()
                lines.append(c)
                if (c == '\n') {
                    nlcnt++
                }

            }
            String line = lines.reverse().toString()
            if (line.contains("Warning: Skipping species because it has 0 test samples")) {
                return "Warning: Skipping species because it has 0 test samples"
            }

            rf.close()
        } catch (Exception e) {
            System.out.println("Unable to read lines")
            e.printStackTrace(System.out)
        }

        // return false anyways
        return null
    }
}
