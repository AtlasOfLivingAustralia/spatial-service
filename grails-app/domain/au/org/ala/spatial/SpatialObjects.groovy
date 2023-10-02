package au.org.ala.spatial

import io.swagger.v3.oas.annotations.Hidden
import org.locationtech.jts.geom.Geometry

class SpatialObjects {
    String pid
    String description
    String name
    String fid

    @Hidden
    Geometry geometry

    Integer name_id
    String bbox
    Double area_km
    Boolean namesearch

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

        version(false)

        description column: '"desc"'
        geometry column: 'the_geom'
        bbox sqlType: "character varying(300)"
        fid sqlType: "character varying(8)", index: 'objects_fid_idx'
        namesearch index: 'objects_namesearch_idx'
    }

    static constraints = {
        description nullable: true
        name_id nullable: true
        bbox nullable: true
        area_km nullable: true
        name nullable: true
        geometry nullable: true
        fid nullable: true
        namesearch nullable: true
    }
}
