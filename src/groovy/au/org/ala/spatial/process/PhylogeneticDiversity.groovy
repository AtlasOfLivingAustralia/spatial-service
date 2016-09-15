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
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

class PhylogeneticDiversity extends SlaveProcess {

    void start() {

        //area to restrict (only interested in area.q part)
        JSONParser jp = new JSONParser()
        def areas = (JSONArray) jp.parse(task.input.area.toString())

        String phyloServiceUrl = task.input.phyloServiceUrl

        def species = JSON.parse(task.input.species.toString())

        def trees = (JSONArray) jp.parse(task.input.phylo.toString())

        int count = 1
        areas.each {
            task.message = "Processing " + it.name + " (" + count + " of " + areas.size() + ")"

            //species list
            def speciesList = getSpeciesList(species, it)

            //get PD
            String url = phyloServiceUrl + "/phylo/getPD";
            NameValuePair[] params = new NameValuePair[2];
            params[0] = new NameValuePair("noTreeText", "true");
            params[1] = new NameValuePair("speciesList", speciesList);

            JSONArray pds = (JSONArray) jp.parse(Util.postUrl(url, params));

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
        }

    }
}
