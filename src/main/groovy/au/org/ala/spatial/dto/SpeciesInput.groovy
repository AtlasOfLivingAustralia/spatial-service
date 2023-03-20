package au.org.ala.spatial.dto

import groovy.transform.AutoClone

@AutoClone
class SpeciesInput {
    String q
    String name
    String bs
    List<String> fq
    List<String> fqs
    String wkt
    String ws
}
