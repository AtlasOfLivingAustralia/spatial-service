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

import au.org.ala.spatial.Util
import grails.converters.JSON
import org.apache.commons.httpclient.NameValuePair
import org.apache.commons.io.FileUtils
import org.json.simple.JSONArray
import org.json.simple.JSONObject

class PhylogeneticDiversity extends SlaveProcess {

    void start() {

        //area to restrict
        def areas = JSON.parse(task.input.area.toString())

        String phyloServiceUrl = task.input.phyloServiceUrl

        def species = JSON.parse(task.input.species.toString())

        def trees = JSON.parse(task.input.phylo.toString())

        def areaPds = []
        def areaSpeciesMatches = []

        int count = 1
        areas.each { area ->
            task.message = "Processing " + area.name + " (" + count + " of " + area.size() + ")"

            //species list
            def speciesList = getSpeciesList(species, area)

            //get PD
            String url = phyloServiceUrl + "/phylo/getPD";
            NameValuePair[] params = new NameValuePair[2];
            params[0] = new NameValuePair("noTreeText", "true");
            params[1] = new NameValuePair("speciesList", speciesList);

            def pds = JSON.parse(Util.postUrl(url, params));

            Map<String, String> pdrow = new HashMap<String, String>();
            Map<String, JSONArray> speciesRow = new HashMap<String, JSONArray>();

            for (int j = 0; j < pds.size(); j++) {
                String tree = "" + ((JSONObject) pds.get(j)).get("studyId");
                pdrow.put(tree, ((JSONObject) pds.get(j)).get("pd").toString());
                speciesRow.put(tree, (JSONArray) ((JSONObject) pds.get(j)).get("taxaRecognised"));

                //maxPD retrieval
                String maxPd = ((JSONObject) pds.get(j)).get("maxPd").toString();
                for (int k = 0; k < trees.size(); k++) {
                    if (((Map<String, String>) trees.get(k)).get("studyId").equals(tree)) {
                        ((Map<String, String>) trees.get(k)).put("maxPd", maxPd);
                    }
                }
            }

            areaPds.add(pdrow);
            areaSpeciesMatches.add(speciesRow);
        }

        makeCSV(trees, areas, areaPds, areaSpeciesMatches);
    }

    def makeCSV(selectedTrees, selectedAreas, areaPds, areaSpeciesMatches) {
        StringBuilder sb = new StringBuilder();

        //header
        sb.append("Area Name,Area (sq km),PD,Proportional PD (PD / Tree PD),");
        sb.append("Species,Proportional Species (Species / Tree Species),Tree Name,Tree ID,DOI,Study Name,Notes,Tree PD");

        //rows
        for (int j = 0; j < selectedTrees.size(); j++) {
            Map<String, String> map = (Map<String, String>) selectedTrees.get(j);

            for (int i = 0; i < selectedAreas.size(); i++) {
                sb.append("\n");

                //numbers
                double pd = 0;
                double maxpd = 0;
                int speciesFound = 0;
                int studySpecieCount = 1;
                try {
                    //area pd
                    pd = Double.parseDouble(areaPds.get(i).get(map.get("studyId")));
                    //tree pd
                    maxpd = Double.parseDouble(map.get("maxPd"));
                    //species found in tree
                    speciesFound = areaSpeciesMatches.get(i).get(map.get("studyId")).size();
                    //tree species count
                    studySpecieCount = Integer.parseInt(map.get("numberOfLeaves"));
                } catch (Exception e) {
                }

                //'current extent' does not have a map layer
                if (selectedAreas.get(i).getMapLayer() == null) {
                    sb.append(toCSVString("Current extent")).append(",");
                    sb.append(selectedAreas.get(i).getKm2Area()).append(",");
                } else {
                    sb.append(toCSVString(selectedAreas.get(i).getMapLayer().getDisplayName())).append(",");
                    sb.append(toCSVString(selectedAreas.get(i).getKm2Area())).append(",");
                }

                String s = areaPds.get(i).get(map.get("studyId"));
                if (s == null) {
                    s = "";
                }
                sb.append(s).append(",");
                sb.append(String.format("%.4f,", pd / maxpd));

                sb.append("" + speciesFound).append(",");
                sb.append(String.format("%.4f,", speciesFound / (double) studySpecieCount));

                //tree name
                sb.append(toCSVString(map.get("authors"))).append(",");
                //tree id
                sb.append(toCSVString(map.get("studyId"))).append(",");
                //doi
                sb.append(toCSVString(map.get("doi"))).append(",");
                //study name
                sb.append(toCSVString(map.get("studyName"))).append(",");
                //notes
                sb.append(toCSVString(map.get("notes"))).append(",");
                //tree pd
                sb.append(toCSVString(map.get("maxPd")));
            }
        }

        FileUtils.writeStringToFile(new File(getTaskPath() + "phylogeneticDiversity.csv"), sb.toString())

        addOutput("csv", "phylogeneticDiversity.csv", true)
    }

    private String toCSVString(String string) {
        if (string == null) {
            return "";
        } else {
            return "\"" + string.replace("\"", "\"\"").replace("\\", "\\\\") + "\"";
        }
    }
}
