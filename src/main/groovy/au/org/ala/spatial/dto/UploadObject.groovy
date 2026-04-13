package au.org.ala.spatial.dto

import groovy.transform.CompileStatic

@CompileStatic
class UploadObject {
    String name
    String description
    String user_id
    Map geojson
}
