package au.org.ala.spatial.util

import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.intersect.IntersectCallback

class ConsumerCallback implements IntersectCallback {
    private final String id

    ConsumerCallback(String id) {
        this.id = id
    }

    @Override
    void setLayersToSample(IntersectionFile[] layersToSample) {

    }

    @Override
    void setCurrentLayer(IntersectionFile layer) {

    }

    @Override
    void setCurrentLayerIdx(Integer layerIdx) {
        BatchProducer.logUpdateEntry(id, null, null, null, layerIdx)
    }

    @Override
    void progressMessage(String message) {
        BatchProducer.logUpdateEntry(id, null, "progressMessage", message, null)
    }
}
