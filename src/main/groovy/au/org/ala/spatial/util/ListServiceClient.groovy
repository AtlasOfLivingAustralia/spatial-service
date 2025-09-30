package au.org.ala.spatial.util;

/**
 *  Resolve list service URLs based on version (v1 or v2)
 */
class ListServiceClient {
    final String baseUrl
    final String version

    ListServiceClient(String baseUrl, String version) {
        this.baseUrl = baseUrl
        this.version = version
    }

    String getSpeciesListFetchUrl(String dr) {
        if (version.equalsIgnoreCase('v2')) {
            return String.format("%s/%s/speciesList/%s", baseUrl, version,dr)
        } else {
            return baseUrl + "/ws/speciesList/"+dr
        }
    }

    String getSpeciesListItemsFetchUrl(String dr, int max=100, int offset=0) {
        return String.format("%s/speciesListItems/%s?includeKVP=true&max=%d&offset=%d", baseUrl,dr, max, offset)
    }
}
