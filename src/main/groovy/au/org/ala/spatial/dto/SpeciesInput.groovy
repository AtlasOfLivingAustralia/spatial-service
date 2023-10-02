package au.org.ala.spatial.dto

import groovy.transform.AutoClone

@AutoClone
class SpeciesInput {
    List<String> q
    String name
    String bs
    String wkt
    String ws

    // unnecessary
    String speciesListName
    String includeExpertDistributions
    String includeAnimalMovement
    String layerUid
    List<String> fq
    List<String> fqs

}
