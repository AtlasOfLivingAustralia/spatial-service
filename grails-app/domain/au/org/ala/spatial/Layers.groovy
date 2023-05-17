package au.org.ala.spatial

import javax.persistence.GeneratedValue

class Layers {
    @GeneratedValue
    Long id

    String uid
    String name
    String displayname
    String description
    String type
    String source
    String path
    String displaypath
    String scale
    String extents
    Double minlatitude
    Double minlongitude
    Double maxlatitude
    Double maxlongitude
    String notes
    Boolean enabled
    String environmentalvaluemin
    String environmentalvaluemax
    String environmentalvalueunits
    String lookuptablepath
    String metadatapath
    String classification1
    String classification2
    String mddatest
    String citation_date
    String datalang
    String mdhrlv
    String respparty_role
    String licence_level
    String licence_link
    String licence_notes
    String source_link
    String keywords
    String path_orig
    String path_1km
    String path_250m
    String domain
    String pid
    Date dt_added

    static mapping = {
        table 'layers'
        id generator: 'assigned'

        notes sqlType: 'text'
        description sqlType: 'text'
        licence_notes sqlType: 'text'
        displaypath sqlType: 'text'
    }

    static constraints = {
         uid nullable: true
         name nullable: true
         displayname nullable: true
         description nullable: true
         type nullable: true
         source nullable: true
         path nullable: true
         displaypath nullable: true
         scale nullable: true
         extents nullable: true
         minlatitude nullable: true
         minlongitude nullable: true
         maxlatitude nullable: true
         maxlongitude nullable: true
         enabled nullable: true
         environmentalvaluemin nullable: true
         environmentalvaluemax nullable: true
         environmentalvalueunits nullable: true
         lookuptablepath nullable: true
         metadatapath nullable: true
         classification1 nullable: true
         classification2 nullable: true
         mddatest nullable: true
         citation_date nullable: true
         datalang nullable: true
         mdhrlv nullable: true
         respparty_role nullable: true
         licence_level nullable: true
         licence_link nullable: true
         licence_notes nullable: true
         source_link nullable: true
         keywords nullable: true
         path_orig nullable: true
         path_1km nullable: true
         path_250m nullable: true
         domain nullable: true
         pid nullable: true
         dt_added nullable: true
        notes nullable: true
    }

    static transients = ['fields', 'requestedId']
    List<Fields> fields
    String requestedId
}
