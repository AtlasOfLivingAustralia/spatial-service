package au.org.ala.spatial

import groovy.transform.CompileStatic
import org.locationtech.jts.geom.Geometry

//@CompileStatic
class SpatialObjects {
    String id
    String pid
    String desc
    String name
    String fid
    String fieldname
    Geometry geometry
    int name_id
    String bbox
    Double area_km

    static transients = ["degrees", "distance", "wmsurl", "featureType", "centroid"]
    Double degrees
    Double distance
    String wmsurl
    String featureType
    String centroid

    static mapping = {
        table 'objects'
        id name: 'pid'
        version false

        geometry column: 'the_geom', index: 'gist (the_geom)'
    }
}
