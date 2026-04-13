package au.org.ala.spatial

import au.org.ala.spatial.dto.AttributionDTO
import au.org.ala.spatial.dto.MapDTO
import groovy.transform.CompileStatic
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

import javax.annotation.PostConstruct

/**
 * A small cache for data resource attribution.
 * This should be revisited if this cache grows or needs regular refreshing.
 */

import java.util.concurrent.ConcurrentHashMap

@CompileStatic
class AttributionService {

    AttributionService attributionCache
    Map<String, AttributionDTO> cache = new ConcurrentHashMap<String, AttributionDTO>()
    String collectionsUrl
    SpatialConfig spatialConfig

    @PostConstruct
    void init() {
        this.collectionsUrl = spatialConfig.collections.url
    }

    AttributionDTO getAttributionFor(String dataResourceUid) throws Exception {
        AttributionDTO a = cache.get(dataResourceUid)
        if (a == null) {
            ObjectMapper om = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
            a = om.readValue(new URL(collectionsUrl + "/ws/dataResource/" + dataResourceUid).openStream(), AttributionDTO.class)
            cache.put(dataResourceUid, a)
        }
        return a
    }

    void clear() {
        cache.clear()
    }

    MapDTO getMapDTO(Distributions distribution) {
        MapDTO m = new MapDTO()
        m.setDataResourceUID(distribution.data_resource_uid)
        m.setUrl((spatialConfig.grails.serverURL + "/distribution/map/png/" + distribution.getGeom_idx()) as String)

        // set the attribution info
        AttributionDTO dto = getAttributionFor(distribution.data_resource_uid)
        m.setAvailable(true)
        m.setDataResourceName(dto.getName())
        m.setLicenseType(dto.getLicenseType())
        m.setLicenseVersion(dto.getLicenseVersion())
        m.setRights(dto.getRights())
        m.setDataResourceUrl(dto.getWebsiteUrl())
        m.setMetadataUrl(dto.getAlaPublicUrl())

        m
    }
}
