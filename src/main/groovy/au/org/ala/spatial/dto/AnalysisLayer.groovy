/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.dto

import au.org.ala.spatial.Fields
import au.org.ala.spatial.Layers

/**
 * @author Adam
 */
//@CompileStatic
class AnalysisLayer {

    Fields field
    Layers layer

    Fields getField() {
        return field
    }

    void setField(Fields field) {
        this.field = field
    }

    Layers getLayer() {
        return layer
    }

    void setLayer(Layers layer) {
        this.layer = layer
    }
}
