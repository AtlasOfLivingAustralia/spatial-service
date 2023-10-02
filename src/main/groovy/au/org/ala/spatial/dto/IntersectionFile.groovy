package au.org.ala.spatial.dto


/**
 * @author Adam
 */
//@CompileStatic
class IntersectionFile {

    String name
    String filePath
    String shapeFields
    String layerName
    String fieldId
    String layerPid
    String fieldName
    String type
    HashMap<Integer, GridClass> classes

    IntersectionFile(String name, String filePath, String shapeFields, String layerName, String fieldId, String fieldName, String layerPid, String type, HashMap<Integer, GridClass> classes) {
        this.name = name.trim()
        this.filePath = filePath.trim()
        this.shapeFields = (shapeFields == null) ? null : shapeFields.trim()
        this.layerName = layerName
        this.fieldId = fieldId
        this.fieldName = fieldName
        this.layerPid = layerPid
        this.type = type
        this.classes = classes
    }

}
