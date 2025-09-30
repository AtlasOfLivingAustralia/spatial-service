package au.org.ala.spatial.util

import grails.util.Holders;

/**
 *  Resolve list service URLs based on version (v1 or v2)
 */
class ListServiceClient {
    final String baseUrl
    final String version

    ListServiceClient() {
        this.baseUrl = Holders.config.getProperty('lists.url', String)
        if (!baseUrl) {
            throw new IllegalStateException("Missing required configuration for species list service: 'lists.url'")
        }
        this.version = Holders.config.getProperty('lists.version', String, 'v1')
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
