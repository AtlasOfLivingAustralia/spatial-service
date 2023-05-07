package au.org.ala.spatial

import javax.persistence.GeneratedValue

class Layers {
//    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "layers_id_seq")
//    @SequenceGenerator(name = "layers_id_seq", sequenceName = "layers_id_seq")
//    //@Column(name = "id", insertable = false, updatable = false)
    @GeneratedValue
    Long id



//    //@Column(name = "uid")
    String uid

//    //@Column(name = "name")
    String name

    //@Column(name = "displayname")
    String displayname

    //@Column(name = "description")
    String description

    //@Column(name = "type")
    String type

    //@Column(name = "source")
    String source

    //@Column(name = "path")
    String path

    //@Column(name = "displaypath")
    String displaypath

    //@Column(name = "scale")
    String scale

    //@Column(name = "extents")
    String extents

    //@Column(name = "minlatitude")
    Double minlatitude

    //@Column(name = "minlongitude")
    Double minlongitude

    //@Column(name = "maxlatitude")
    Double maxlatitude

    //@Column(name = "maxlongitude")
    Double maxlongitude

    //@Column(name = "notes")
    String notes

    //@Column(name = "enabled")
    Boolean enabled

    //@Column(name = "environmentalvaluemin")
    String environmentalvaluemin

    //@Column(name = "environmentalvaluemax")
    String environmentalvaluemax

    //@Column(name = "environmentalvalueunits")
    String environmentalvalueunits

    //@Column(name = "lookuptablepath")
    String lookuptablepath

    //@Column(name = "metadatapath")
    String metadatapath

    //@Column(name = "classification1")
    String classification1

    //@Column(name = "classification2")
    String classification2

    //@Column(name = "mddatest")
    String mddatest

    //@Column(name = "citation_date")
    String citation_date

    //@Column(name = "datalang")
    String datalang

    //@Column(name = "mdhrlv")
    String mdhrlv

    //@Column(name = "respparty_role")
    String respparty_role

    //@Column(name = "licence_level")
    String licence_level

    //@Column(name = "licence_link")
    String licence_link

    //@Column(name = "licence_notes")
    String licence_notes

    //@Column(name = "source_link")
    String source_link

    //@Column(name = "keywords")
    String keywords

    //@Column(name = "path_orig")
    String path_orig

    //@Column(name = "path_1km")
    String path_1km

    //@Column(name = "path_250m")
    String path_250m

    //@Column(name = "domain")
    String domain

    //@Column(name = "pid")
    String pid

    //@Temporal(TemporalType.TIMESTAMP)
    //@Column(name = "dt_added")
    Date dt_added

    static mapping = {
        table 'layers'
        id generator: 'assigned'
        version false
    }

    static transients = ['fields', 'requestedId']
    List<Fields> fields
    String requestedId
}
