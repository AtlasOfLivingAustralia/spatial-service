package au.org.ala.spatial.dto

import groovy.transform.CompileStatic
import org.locationtech.jts.geom.Geometry

@CompileStatic
class FieldValue {
    String sname
    String sdesc
    List<Geometry> geom
}
