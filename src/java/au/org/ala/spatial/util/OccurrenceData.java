package au.org.ala.spatial.util;

import au.com.bytecode.opencsv.CSVReader;
import au.org.ala.spatial.Util;
import au.org.ala.spatial.analysis.layers.Records;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;

import java.io.StringReader;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class OccurrenceData {

    private static final Logger logger = Logger.getLogger(OccurrenceData.class);

    static final String SPECIES_LIST_SERVICE_CSV = "/occurrences/facets/download?facets=species_guid&lookup=true&count=true";

    public String[] getSpeciesData(String q, String bs, String records_filename) {
        return getSpeciesData(q, bs, records_filename, "names_and_lsid");
    }

    public String[] getSpeciesData(String q, String bs, String records_filename, String facetName) {
        HashMap<String, Object> result = new HashMap<String, Object>();

        HashSet<String> sensitiveSpeciesFound = new HashSet<String>();

        //add to 'identified' sensitive list
        try {
            CSVReader csv = new CSVReader(new StringReader(getSpecies(q + "&fq=" + URLEncoder.encode("-sensitive:[* TO *]", "UTF-8"), bs)));
            List<String[]> fullSpeciesList = csv.readAll();
            csv.close();
            for (int i = 0; i < fullSpeciesList.size(); i++) {
                sensitiveSpeciesFound.add(fullSpeciesList.get(i)[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //remove sensitive records that will not be LSID matched
        try {
            Records r = new Records(bs, q + "&fq=" + URLEncoder.encode("-sensitive:[* TO *]", "UTF-8"), null, records_filename, null, facetName);

            StringBuilder sb = null;
            if (r.getRecordsSize() > 0) {
                sb = new StringBuilder();
                for (int i = 0; i < r.getRecordsSize(); i++) {
                    if (sb.length() == 0) {
                        //header
                        sb.append("species,longitude,latitude");
                    }
                    if (facetName == null) {
                        sb.append("\nspecies,").append(r.getLongitude(i)).append(",").append(r.getLatitude(i));
                    } else {
                        sb.append("\n\"").append(r.getSpecies(i).replace("\"", "\"\"")).append("\",").append(r.getLongitude(i)).append(",").append(r.getLatitude(i));
                    }
                }
            }

            //collate sensitive species found, no header
            StringBuilder sen = new StringBuilder();
            for (String s : sensitiveSpeciesFound) {
                sen.append(s).append("\n");
            }

            String[] out = {((sb == null) ? null : sb.toString()), (sen.length() == 0) ? null : sen.toString()};

            return out;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    private String getSpecies(String q, String bs) {
        String url = bs + SPECIES_LIST_SERVICE_CSV + "&q=" + q;

        try {
            return Util.getUrl(url);
        } catch (Exception e) {
            logger.error(url, e);
        }

        return null;

    }
}
