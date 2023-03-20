package au.org.ala.spatial


import javax.persistence.GeneratedValue

//@CompileStatic
class Fields {

    @GeneratedValue
    String id

    String name
    String desc
    String type
    String spid
    String sid
    String sname
    String sdesc
    Boolean indb
    Boolean enabled
    Date last_update
    Boolean namesearch
    Boolean defaultlayer
    Boolean intersect
    Boolean layerbranch
    Boolean analysis
    Boolean addtomap

    static mapping = {
        table 'fields'
        id generator: 'assigned'
        version false
    }

    static transients = ["number_of_objects", "layer", "wms", "objects", "requestedId"]

    String requestedId

    Integer number_of_objects

    Layers layer

    String wms

    List<SpatialObjects> objects

}
