package au.org.ala.spatial.dto

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.AutoClone
import groovy.transform.CompileStatic

@AutoClone
class ProcessSpecification {
    String name
    String description

    // background processes are queued, otherwise run immediately
    Boolean isBackground

    String version

    @JsonProperty("private")
    PrivateSpecification privateSpecification

    static class PrivateSpecification {
        // only one instance of the process can run at a time
        Boolean unique

        // admin auth not required to run this task if public
        Boolean isPublic

        String classname

        // Override configuration path. Used by externally defined spec files
        String configPath
    }

    @JsonProperty("input")
    Map<String, InputSpecification> input

    static class InputSpecification {
        String description
        InputType type

        @JsonProperty("constraints")
        ConstraintSpecification constraints
    }

    enum InputType {
        AUTO, // Import value from configuration
        DOUBLE, // Double value
        INT, // Integer value
        BOOLEAN, // Boolean value
        AREA,  // List of AreaInput
        SPECIES,  // List of SpeciesInput
        LIST, // List selection
        STRING, // String value
        UPLOAD, // Uploaded layer file. Admin processes only.
        ENVELOPE, // Layer and ranges used to define an envelope\
        LAYER, // List of Fields
        COLOUR, // Colour selection
        TEXT, // Text area
        PROCESS // A prior process
    }

    static class ConstraintSpecification {
        Double min  // (DOUBLE, INT, BOOLEAN): min value, (AREA, SPECIES, LIST, LAYER): min number of items
        Double max  // (DOUBLE, INT), BOOLEAN: max value, (AREA, SPECIES, LIST, LAYER): max number of items
        Double defaultvalue // (DOUBLE, INT, BOOLEAN, LIST): defalut value,
        Double minArea  // AREA: min area size in sq km
        Double maxArea // AREA: max area size in sq km
        Boolean optional // ALL: input when true
        SelectionType selection // LIST:  selection type
        List<String> content // LIST: List content
        Boolean contextual // LAYER: include contextual fields
        Boolean environmental // LAYER: include environmental fields
        Boolean analysis // LAYER: include task output layers
    }

    enum SelectionType {
        SINGLE,
        MULTIPLE
    }

    Map<OutputType, OutputSpecification> output

    enum OutputType {
        METADATA, // metadata as HTML
        AREAS, // area/s created in spatial-service
        LAYERS, // layer/s created in spatial-service
        SPECIES, // species created in sandbox
        FILES, // files produced
        DOWNLOAD, // contents of downloadable output
        SQL, // SQL to run on layersdb
        PROCESS // Tasks to queue
    }

    static class OutputSpecification {
        String description
    }
}
