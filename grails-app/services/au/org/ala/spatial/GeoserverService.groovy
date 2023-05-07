package au.org.ala.spatial


import javax.annotation.PostConstruct

/**
 * A small cache for data resource attribution.
 * This should be revisited if this cache grows or needs regular refreshing.
 */
//@CompileStatic
class GeoserverService {
    SpatialConfig spatialConfig

    List<Fields> fields
    List<Layers> layers

    @PostConstruct
    void init() {
        sync()
    }

    void sync() {
        // fetch all layers and fields from geoserver

    }
}
