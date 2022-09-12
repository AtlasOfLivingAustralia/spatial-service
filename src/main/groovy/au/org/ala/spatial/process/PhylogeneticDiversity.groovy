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
import au.org.ala.spatial.Util
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.apache.http.message.BasicNameValuePair
import org.json.simple.JSONArray

@Slf4j
class PhylogeneticDiversity extends SlaveProcess {

    void start() {

        //area to restrict
        def areas = JSON.parse(taskWrapper.input.area.toString())

        String phyloServiceUrl = taskWrapper.input.phyloServiceUrl

        def species = JSON.parse(taskWrapper.input.species.toString())

        def trees = JSON.parse(taskWrapper.input.phylo.toString())

        def areaPds = []
        def areaSpeciesMatches = []

        def selectedTrees = []

        int count = 1
        areas.each { area ->
            taskWrapper.message = "Running"
            taskLog("Processing " + area.name + " (" + count + " of " + areas.size() + ")")

            //species list
            def speciesArea = getSpeciesArea(species, area)

            taskLog("Fetching species in " + area.name)
            def speciesList = getSpeciesList(speciesArea)
            taskLog("Loading species in " + area.name)
            CSVReader r = new CSVReader(new StringReader(speciesList))

            JSONArray ja = new JSONArray()
            for (String[] s : r.readAll()) {
                ja.add(s[1])
            }
            String q = ja.toString()

            //get PD
            taskLog("Reading phylogenetic diversity data")
            String url = phyloServiceUrl + "/phylo/getPD"

            BasicNameValuePair[] parts = new BasicNameValuePair[2]
            parts[0] = new BasicNameValuePair("noTreeText", "true")
            parts[1] = new BasicNameValuePair("speciesList", q)

            def pds = JSON.parse(Util.postUrl(url, parts,null, null))

            //tree info
            taskLog("Getting expert trees")
            url = phyloServiceUrl + "/phylo/getExpertTrees"
            def allTrees = JSON.parse(Util.getUrl(url))
            allTrees.each { t ->
                trees.each { i ->
                    if (t.studyId == i) {
                        selectedTrees.push(t)
                    }
                }
            }

            Map<String, String> pdrow = new HashMap<String, String>()
            Map<String, List> speciesRow = new HashMap<String, List>()

            taskLog("Generating results")
            for (int j = 0; j < pds.size(); j++) {
                String tree = "" + pds.get(j).get("studyId")
                pdrow.put(tree, pds.get(j).get("pd").toString())
                speciesRow.put(tree, pds.get(j).get("taxaRecognised"))

                //maxPD retrieval
                String maxPd = pds.get(j).get("maxPd").toString()
                for (int k = 0; k < selectedTrees.size(); k++) {
                    if (selectedTrees.get(k).get("studyId").equals(pds.get(j).get("studyId"))) {
                        selectedTrees.get(k).put("maxPd", maxPd)
                    }
                }
            }

            areaPds.add(pdrow)
            areaSpeciesMatches.add(speciesRow)
        }

        makeCSV(selectedTrees, areas, areaPds, areaSpeciesMatches)
    }

    def makeCSV(selectedTrees, selectedAreas, areaPds, areaSpeciesMatches) {
        StringBuilder sb = new StringBuilder()

        //header
        sb.append("Area Name,Area (sq km),PD,Proportional PD (PD / Tree PD),")
        sb.append("Species,Proportional Species (Species / Tree Species),Tree Name,Tree ID,DOI,Study Name,Notes,Tree PD")

        //rows
        for (int j = 0; j < selectedTrees.size(); j++) {
            Map<String, String> map = (Map<String, String>) selectedTrees.get(j)

            for (int i = 0; i < selectedAreas.size(); i++) {
                sb.append("\n")

                //numbers
                double pd = 0
                double maxpd = 0
                int speciesFound = 0
                int studySpecieCount = 1
                String studyId = map.get("studyId").toString()
                try {
                    //area pd
                    pd = Double.parseDouble(areaPds.get(i).get(studyId))
                    //tree pd
                    maxpd = Double.parseDouble(map.get("maxPd"))
                    //species found in tree
                    speciesFound = areaSpeciesMatches.get(i).get(studyId).size()
                    //tree species count
                    studySpecieCount = Integer.parseInt(map.get("numberOfLeaves"))
                } catch (Exception e) {
                }

                sb.append(toCSVString(selectedAreas.get(i).name)).append(",")
                sb.append(selectedAreas.get(i).area_km).append(",")

                String s = areaPds.get(i).get(studyId)
                if (s == null) {
                    s = ""
                }
                sb.append(s).append(",")
                sb.append(String.format("%.4f,", pd / maxpd))

                sb.append("" + speciesFound).append(",")
                sb.append(String.format("%.4f,", speciesFound / (double) studySpecieCount))

                //tree name
                sb.append(toCSVString(map.get("authors"))).append(",")
                //tree id
                sb.append(toCSVString(studyId)).append(",")
                //doi
                sb.append(toCSVString(map.get("doi"))).append(",")
                //study name
                sb.append(toCSVString(map.get("studyName"))).append(",")
                //notes
                sb.append(toCSVString(map.get("notes"))).append(",")
                //tree pd
                sb.append(toCSVString(map.get("maxPd")))
            }
        }

        FileUtils.writeStringToFile(new File(getTaskPath() + "phylogeneticDiversity.csv"), sb.toString())

        addOutput("csv", "phylogeneticDiversity.csv", true)
    }

    private String toCSVString(Object obj) {
        if (obj == null) {
            return ""
        } else {
            return "\"" + obj.toString().replace("\"", "\"\"").replace("\\", "\\\\") + "\""
        }
    }
}
