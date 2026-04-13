package au.org.ala.spatial.intersect

import au.org.ala.spatial.dto.IntersectionFile
import groovy.transform.CompileStatic

@CompileStatic
interface IntersectCallback {
    void setLayersToSample(IntersectionFile[] layersToSample);

    void setCurrentLayer(IntersectionFile layer);

    void setCurrentLayerIdx(Integer layerIdx);

    void progressMessage(String message);
}
