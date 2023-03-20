package au.org.ala.spatial


import org.locationtech.jts.geom.Geometry

import groovy.transform.CompileStatic
//@CompileStatic
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

        geometry column: 'the_geom', index: 'gist (the_geom)'

        family_lsid type: 'text'
        genus_lsid type: 'text'
        caab_species_number type: 'text'
        caab_family_number type: 'text'
        notes type: 'text'
        group_name type: 'text'

        spcode index: 'distributions_spcode_idx'
        scientific index: 'distributions_scientific_idx'
        family index: 'distributions_family_idx'
        genus_name index: 'distributions_genus_name_idx'
        min_depth index: 'distributions_min_depth_idx'

        max_depth index: 'distributions_max_depth_idx'

        pelagic_fl index: 'distributions_pelagic_fl_idx'

        estuarine_fl index: 'distributions_estuarine_fl_idx'

        coastal_fl index: 'distributions_coastal_fl_idx'

        desmersal_fl index: 'distributions_desmersal_fl_idx'

        metadata_u index: 'distributions_metadata_u_idx'
        lsid index: 'distributions_lsid_idx'
        family_lsid index: 'distributions_family_lsid_idx'
        genus_lsid index: 'distributions_genus_lsid_idx'

        caab_species_number index: 'distributions_caab_species_number_idx'
        caab_family_number index: 'distributions_caab_family_number_idx'
        type index: 'distributions_type_idx'

        checklist_name index: 'distributions_checklist_name_idx'
        group_name index: 'distributions_group_name_idx'
        genus_exemplar index: 'distributions_genus_exemplar_idx'
        family_exemplar index: 'distributions_family_exemplar_idx'

        data_resource_uid index: 'distributions_data_resource_uid_idx'
        image_quality index: 'distributions_image_quality'

    }
}
