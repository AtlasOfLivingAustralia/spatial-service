package au.org.ala.spatial

import groovy.transform.CompileStatic
import org.locationtech.jts.geom.Geometry

class SpatialObjects {
    String id
    String pid
    String desc
    String name
    String fid
    Geometry geometry
    Integer name_id
    String bbox
    Double area_km

    static transients = ["degrees", "distance", "wmsurl", "featureType", "centroid", "fieldname"]
    Double degrees
    Double distance
    String wmsurl
    String featureType
    String centroid
    String fieldname

    static mapping = {
        table 'objects'
        id name: 'pid'
        version false

        desc column: '"desc"', nullable: true
        name_id nullable: true
        bbox nullable: true
        area_km nullable: true

        geometry column: 'the_geom'
    }
}
