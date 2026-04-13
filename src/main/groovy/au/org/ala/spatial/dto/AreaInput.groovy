package au.org.ala.spatial.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.CompileStatic

@CompileStatic
class AreaInput {

    String type
    String wkt
    String pid
    String bbox
    String q
    String name
    Double area_km

    @JsonIgnore
    SpeciesInput speciesArea

    @JsonIgnore
    Integer numberOfOccurrences

    @JsonIgnore
    List<String[]> speciesList
}
