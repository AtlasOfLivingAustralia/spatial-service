package au.org.ala.spatial.dto

import au.org.ala.spatial.Fields

import java.nio.file.attribute.FileTime

//@CompileStatic
class Upload {
    String filename
    String raw_id
    FileTime created
    String name
    String displayname
    String data_resource_uid
    String layer_id
    String checklist
    String layer_creation
    Fields [] fields
}
