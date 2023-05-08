package au.org.ala.spatial

import com.fasterxml.jackson.annotation.JsonInclude

import javax.persistence.GeneratedValue

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
        spid nullable: true
        last_update nullable: true
        sid nullable: true
        sname nullable: true
        sdesc nullable: true
        desc column: '"desc"', nullable: true
        name nullable: true
        type nullable: true
        indb nullable: true
        enabled nullable: true
        namesearch nullable: true
        defaultlayer nullable: true
        intersect column: '"intersect"', nullable: true
        layerbranch nullable: true
        analysis nullable: true
        addtomap nullable: true

    }

    static transients = ["number_of_objects", "layer", "wms", "objects", "requestedId"]
    String requestedId
    Integer number_of_objects
    Layers layer
    String wms
    List<SpatialObjects> objects

}
