package au.org.ala.spatial.service

import au.org.ala.layers.dto.Layer

import javax.persistence.Column
import javax.persistence.Id
import javax.persistence.Temporal
import javax.persistence.TemporalType

class Field {

    String id;
    String name;
    String desc;
    String type;
    String spid;
     String sid;
     String sname;
     String sdesc;
     Boolean indb;
     Boolean enabled;
     Date last_update;
     Boolean namesearch;
     Boolean defaultlayer
     Boolean intersect
     Boolean layerbranch
     Boolean analysis
     Boolean addtomap


    static transients = [ "number_of_objects", "layer", "wms", "objects" ]
     Integer number_of_objects

     Layer layer

     String wms

     List<Objects> objects

}
