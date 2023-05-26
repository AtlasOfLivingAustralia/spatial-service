package au.org.ala.spatial.dto

import groovy.transform.AutoClone

@AutoClone
class ScatterplotSpeciesInput extends SpeciesInput {

    // scatteplot unnecessary
    Long scatterplotId
    String scatterplotUrl
    Integer red
    Integer green
    Integer blue
    Float opacity
    Integer size

    String highlightWkt
    Double[][] scatterplotExtents
    Double[] scatterplotSelectionExtents
    String[] scatterplotLayers
    Integer scatterplotSelectionMissingCount
}
