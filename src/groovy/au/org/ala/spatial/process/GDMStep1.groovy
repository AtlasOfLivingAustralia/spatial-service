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
import au.org.ala.layers.intersect.SimpleShapeFile
import au.org.ala.layers.util.LayerFilter
import au.org.ala.spatial.util.OccurrenceData
import grails.converters.JSON
import org.apache.commons.io.FileUtils

class GDMStep1 extends SlaveProcess {

    void start() {

        String area = JSON.parse(task.input.area.toString())
        String region = null
        String envelope = null
        if (area != null && area.startsWith("ENVELOPE")) {
            envelope = LayerFilter.parseLayerFilters(area)
        } else {
            region = SimpleShapeFile.parseWKT(area)
        }

        def layers = JSON.parse(task.input.layer.toString())
        def envnameslist = new String[layers.size()]
        layers.eachWithIndex { l, idx ->
            envnameslist[idx] = l
        }

        def species = JSON.parse(task.input.species.toString())

        //target resolution
        def resolution = task.input.resolution

        OccurrenceData od = new OccurrenceData();
        String[] s = od.getSpeciesData(species.q, species.bs, null, "names_and_lsid");

        StringBuilder speciesdata = new StringBuilder();
        HashMap taxonNames = new HashMap();
        speciesdata.append("\"X\",\"Y\",\"CODE\"");
        CSVReader reader = new CSVReader(new StringReader(s[0]));
        reader.readNext();  //discard header
        String[] line;
        while ((line = reader.readNext()) != null) {
            speciesdata.append("\n").append(line[1]).append(",").append(line[2])
                    .append(",\"").append(getSPindex(taxonNames, line[0])).append("\"");
        }

        // 1. generate species file
        String speciesFile = generateSpeciesFile(getTaskPath(), speciesdata.toString());

        // 2. cut environmental layers
        def cutDataPath = cutGrid(envnameslist, resolution, region, envelope, null);

        //add layer display names to cutDataPath
        String names = "";
        for (int i = 0; i < envnameslist.length; i++) {
            String[] name_displayname = envnameslist[i].split("\\|");
            if (name_displayname.length > 1) {
                envnameslist[i] = name_displayname[0];
                names += "\n" + name_displayname[0] + "=" + name_displayname[1] + " (" + name_displayname[0] + ")";
            } else {
                envnameslist[i] = name_displayname[0];
                names += "\n" + envnameslist[i] + "=" + envnameslist[i];
            }
        }
        FileUtils.writeStringToFile(new File(getTaskPath() + File.separator + "additional_properties.txt"), names);

        // 4. produce domain grid
        //DomainGrid.generate(cutDataPath, layers, region, outputdir);

        // 5. build parameters files for GDM
        String params = generateStep1Paramfile(envnameslist, cutDataPath, speciesFile, getTaskPath());

        // 6. run GDM
        runCmd([grailsApplication.config.gdm.dir, " -g", "1", params])

        Scanner sc = new Scanner(new File(getTaskPath() + "Cutpoint.csv"));
        def cutpoints = []
        while (sc.hasNextLine()) {
            cutpoints.push(sc.nextLine())
        }

        def data = [process: 'GDMStep2',
                    input  : [
                            gdmId    : [constraints: [default: task.id]],
                            cutpoints: [constraints: [content: cutpoints]]
                    ]
        ]

        addOutput("process", (data as JSON).toString())
    }

    private String getSPindex(HashMap taxonNames, String sp) {
        if (!taxonNames.containsKey(sp)) {
            taxonNames.put(sp, String.valueOf(taxonNames.size()));
        }
        return (String) taxonNames.get(sp);
    }

    private String generateStep1Paramfile(String[] layers, String layersPath, String speciesfile, String outputdir) {
        try {

            Properties additionalProperties = new Properties();
            File apFile = new File(outputdir + File.separator + "additional_properties.txt");
            if (apFile.exists()) {
                try {
                    additionalProperties.load(new FileReader(apFile));
                } catch (Exception e) {
                }
            }

            StringBuilder envLayers = new StringBuilder();
            StringBuilder useEnvLayers = new StringBuilder();
            StringBuilder predSpline = new StringBuilder();
            for (int i = 0; i < layers.length; i++) {
                envLayers.append("EnvGrid").append(i + 1).append("=").append(layersPath).append(layers[i]).append("\n");
                envLayers.append("EnvGridName").append(i + 1).append("=").
                        append(additionalProperties.getProperty(layers[i], layers[i])).append("\n");
                useEnvLayers.append("UseEnv").append(i + 1).append("=1").append("\n");
                predSpline.append("PredSpl").append(i + 1).append("=3").append("\n");
            }

            StringBuilder sbOut = new StringBuilder();
            sbOut.append("[GDMODEL]").append("\n").append("WorkspacePath=" + outputdir).append("\n").
                    append("RespDataType=RD_SitePlusSpecies").append("\n").append("PredDataType=ED_GridData").
                    append("\n").append("Quantiles=QUANTS_FromData").append("\n").append("UseEuclidean=0").
                    append("\n").append("UseSubSample=1").append("\n").append("NumSamples=10000").append("\n").
                    append("[RESPONSE]").append("\n").append("InputData=" + speciesfile).append("\n").
                    append("UseWeights=0").append("\n").append("[PREDICTORS]").append("\n").append("EuclSpl=3").
                    append("\n").append("NumPredictors=" + layers.length).append("\n").append(envLayers).
                    append("\n").append(useEnvLayers).append("\n").append(predSpline).append("\n");
            PrintWriter spWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputdir + "gdm_params.txt")));
            spWriter.write(sbOut.toString());
            spWriter.close();

            return outputdir + "gdm_params.txt";
        } catch (Exception e) {
            System.out.println("Unable to write the initial params file");
            e.printStackTrace(System.out);
        }

        return "";
    }

    private String generateSpeciesFile(String outputdir, String speciesdata) {
        try {

            File fDir = new File(outputdir);
            fDir.mkdir();

            File spFile = new File(fDir, "species_points.csv");
            FileUtils.writeStringToFile(spFile, speciesdata + "\n", "UTF-8");

            return spFile.getAbsolutePath();

        } catch (Exception e) {
            System.out.println("error generating species file");
            e.printStackTrace(System.out);
        }

        return null;
    }
}
