package au.org.ala.spatial.service

class Distributions {

    public static final String EXPERT_DISTRIBUTION = "e";
    public static final String SPECIES_CHECKLIST = "c";
    public static final String TRACK = "t";

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
    String metadata_u
    String geometry
    String wmsurl
    String lsid
    String type
    String area_name
    String pid
    String checklist_name
    Double area_km
    String notes
    Long geom_idx

    // additional fields
    String group_name
    String family_lsid
    String genus_lsid
    Boolean estuarine_fl
    Boolean coastal_fl
    Boolean desmersal_fl
    String caab_species_number
    String caab_family_number
    String data_resource_uid
    String image_quality
    String bounding_box
    Boolean endemic
    String imageUrl

    static transients = [ "intersectArea" ]
    Double intersectArea
}
