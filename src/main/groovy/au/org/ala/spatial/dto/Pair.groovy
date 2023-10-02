package au.org.ala.spatial.dto

import org.locationtech.jts.geom.Geometry

class Pair {

    String key
    int occurrences
    BitSet species = new BitSet()
    double area
    String v1, v2
    Geometry geom

    Pair(String key) {
        this.key = key
        String[] split = key.split(" ")
        v1 = split[0]
        v2 = split[1]
    }
}
