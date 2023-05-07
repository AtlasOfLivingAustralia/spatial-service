package au.org.ala.spatial.dto

import au.org.ala.spatial.Fields
import au.org.ala.spatial.Layers

import java.nio.file.attribute.FileTime

class Upload {
    String filename
    String raw_id
    String new_id
    FileTime created
    Layers layer
//    String name
//    String displayname
    String data_resource_uid
//    String layer_id
//    Boolean has_layer
    String uniqueLayerName
    Boolean checklist
    Boolean distribution
//    String layer_creation
    List<Fields> fields
    String error
    String status
    List<String> shapefileFields
    String wmsUrl
    List<String> classifications
//    Boolean enabled
//
//    ArrayList columns
//
//    String test_id
//    String test_url
//    Double minlongitude
//    Double maxlongitude
//    Double minlatitude
//    Double maxlatitude
//    String environmentalvaluemin
//    String environmentalvaluemax
//    String type
//    List<String> classifications
//    String description

//    public Upload() {}
}
