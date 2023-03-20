package au.org.ala.spatial.dto

import groovy.transform.CompileStatic
//@CompileStatic
class Facet {

    String name
    Integer count

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    Integer getCount() {
        return count
    }

    void setCount(Integer count) {
        this.count = count
    }
}
