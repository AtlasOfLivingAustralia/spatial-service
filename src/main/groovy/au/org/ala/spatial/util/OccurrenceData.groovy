package au.org.ala.spatial.util

import au.org.ala.spatial.Util
import com.opencsv.CSVReader
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.yaml.snakeyaml.util.UriEncoder

@Slf4j
@CompileStatic
class OccurrenceData {

    static final String SPECIES_LIST_SERVICE_CSV = "/occurrences/facets/download?facets=species_guid&lookup=true&count=true"

    static String[] getSpeciesData(String q, String bs, String records_filename) {
        return getSpeciesData(q, bs, records_filename, "names_and_lsid")
    }

    static String[] getSpeciesData(String q, String bs, String records_filename, String facetName) {
        HashMap<String, Object> result = new HashMap<String, Object>()

        HashSet<String> sensitiveSpeciesFound = new HashSet<String>()

        //add to 'identified' sensitive list
        try {
            CSVReader csv = new CSVReader(new StringReader(getSpecies(q + "&fq=" + UriEncoder.encode("-sensitive:[* TO *]"), bs)))
            List<String[]> fullSpeciesList = csv.readAll()
            csv.close()
            for (int i = 0; i < fullSpeciesList.size(); i++) {
                sensitiveSpeciesFound.add(fullSpeciesList.get(i)[0])
            }
        } catch (Exception e) {
            e.printStackTrace()
        }

        //remove sensitive records that will not be LSID matched
        try {
            Records r = new Records(bs, q + "&fq=" + UriEncoder.encode("-sensitive:[* TO *]"), null, records_filename, null, facetName)

            StringBuilder sb = null
            if (r.getRecordsSize() > 0) {
                sb = new StringBuilder()
                for (int i = 0; i < r.getRecordsSize(); i++) {
                    if (sb.length() == 0) {
                        //header
                        sb.append("species,longitude,latitude")
                    }
                    if (facetName == null) {
                        sb.append("\nspecies,").append(r.getLongitude(i)).append(",").append(r.getLatitude(i))
                    } else {
                        sb.append("\n\"").append(r.getSpecies(i).replace("\"", "\"\"")).append("\",").append(r.getLongitude(i)).append(",").append(r.getLatitude(i))
                    }
                }
            }

            //collate sensitive species found, no header
            StringBuilder sen = new StringBuilder()
            for (String s : sensitiveSpeciesFound) {
                sen.append(s).append("\n")
            }

            String[] out = [((sb == null) ? null : sb.toString()), (sen.length() == 0) ? null : sen.toString()]

            return out
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        return null
    }


    private static String getSpecies(String q, String bs) {
        String url = bs + SPECIES_LIST_SERVICE_CSV + "&q=" + q

        try {
            return Util.getUrl(url)
        } catch (Exception e) {
            log.error(url, e)
        }

        return null

    }
}
