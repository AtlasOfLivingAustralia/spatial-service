package au.org.ala.spatial


import org.locationtech.jts.geom.Geometry

class Distributions {

    public static final String EXPERT_DISTRIBUTION = "e"
    public static final String SPECIES_CHECKLIST = "c"
    public static final String TRACK = "t"

    Long gid
    Long spcode
    String scientific
    String authority_
    String common_nam
    String family
    String genus_name
    String specific_n
    Double min_depth
    Double max_depth
    Double pelagic_fl
    Boolean estuarine_fl
    Boolean coastal_fl
    Boolean desmersal_fl
    String metadata_u
    String wmsurl
    String lsid
    String family_lsid
    String genus_lsid
    String caab_species_number
    String caab_family_number
    String area_name
    String pid
    String type
    String checklist_name
    Double area_km
    String notes
    Integer geom_idx
    String group_name
    String data_resource_uid
    String image_quality
    Geometry bounding_box
    Boolean endemic

    String genus_exemplar
    String family_exemplar

    Geometry geometry

    static transients = ["imageUrl", "intersectArea"]
    Double intersectArea
    String imageUrl

    static mapping = {
        id name: 'spcode', generator: 'assigned'
        version false

        geometry column: 'the_geom'

        lsid type: 'text'
        family_lsid type: 'text'
        genus_lsid type: 'text'
        caab_species_number type: 'text'
        caab_family_number type: 'text'
        notes type: 'text'
        group_name type: 'text'

    }
}
