package au.org.ala.spatial


import org.locationtech.jts.geom.Geometry

class SpatialObjects {
    String pid
    String description
    String name
    String fid
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
    }

    static constraints = {
        description nullable: true
        name_id nullable: true
        bbox nullable: true
        area_km nullable: true
    }
}
