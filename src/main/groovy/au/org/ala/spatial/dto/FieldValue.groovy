package au.org.ala.spatial.dto

import org.locationtech.jts.geom.Geometry

class FieldValue {
    String sid
    String sname
    String sdesc
    List<Geometry> geom
}
