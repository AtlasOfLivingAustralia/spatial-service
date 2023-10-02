/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.scatterplot


import com.opencsv.CSVReader
import org.codehaus.jackson.annotate.JsonIgnore
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Adam
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ScatterplotDTO implements Serializable {

    private static final Logger logger = LoggerFactory.getLogger(ScatterplotDTO.class)

    String[] layers
    String[] layernames
    String[] layerunits

    String name

    String foregroundOccurrencesQs
    String foregroundOccurrencesBs
    String foregroundName

    String backgroundOccurrencesQs
    String backgroundOccurrencesBs
    String backgroundName

    String filterWkt

    //grid
    int gridDivisions = 0
    private String id

    ScatterplotDTO() {
    }

    ScatterplotDTO(String fqs, String fbs, String fname, String bqs, String bbs, String bname, String name, String layer1, String layer1name, String layer2, String layer2name, int gridDivisions, String filterWkt, String layer1units, String layer2units) {
        this.foregroundOccurrencesQs = fqs
        this.foregroundOccurrencesBs = fbs
        this.foregroundName = fname

        this.name = name

        this.layers = new String[]{layer1, layer2}
        this.layernames = new String[]{layer1name, layer2name}
        this.layerunits = new String[]{layer1units, layer2units}

        this.backgroundOccurrencesQs = bqs
        this.backgroundOccurrencesBs = bbs
        this.backgroundName = bname

        this.gridDivisions = gridDivisions

        this.filterWkt = filterWkt
    }

    @JsonIgnore
    String getLayer2() {
        if (layers != null) {
            return layers[1]
        } else {
            return null
        }
    }

    @JsonIgnore
    void setLayer2(String layer2) {
        if (layers == null) {
            layers = new String[2]
        }

        layers[1] = layer2
    }

    @JsonIgnore
    String getLayer2name() {

        if (layernames != null) {
            return layernames[1]
        } else {
            return null
        }
    }

    @JsonIgnore
    void setLayer2name(String layer2name) {
        if (layernames == null) {
            layernames = new String[2]
        }

        layernames[1] = layer2name
    }

    @JsonIgnore
    String getLayer2units() {

        if (layerunits != null) {
            return layerunits[1]
        } else {
            return ""
        }
    }

    @JsonIgnore
    void setLayer2units(String layer2units) {
        if (layerunits == null) {
            layerunits = new String[]{"", ""}
        }

        layerunits[1] = layer2units
    }

    @JsonIgnore
    String getLayer1units() {

        if (layerunits != null) {
            return layerunits[0]
        } else {
            return ""
        }
    }

    @JsonIgnore
    void setLayer1units(String layer1units) {
        if (layerunits == null) {
            layerunits = new String[]{"", ""}
        }

        layerunits[0] = layer1units
    }

    @JsonIgnore
    String getLayer1() {
        if (layers != null) {
            return layers[0]
        } else {
            return null
        }
    }

    @JsonIgnore
    void setLayer1(String layer1) {
        if (layers == null) {
            layers = new String[2]
        }

        layers[0] = layer1
    }

    @JsonIgnore
    String getLayer1name() {

        if (layernames != null) {
            return layernames[0]
        } else {
            return null
        }
    }

    @JsonIgnore
    void setLayer1name(String layer2name) {
        if (layernames == null) {
            layernames = new String[2]
        }

        layernames[0] = layer2name
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getForegroundOccurrencesQs() {
        return foregroundOccurrencesQs
    }

    void setForegroundOccurrencesQs(String foregroundOccurrencesQs) {
        this.foregroundOccurrencesQs = foregroundOccurrencesQs
    }

    String getForegroundOccurrencesBs() {
        return foregroundOccurrencesBs
    }

    void setForegroundOccurrencesBs(String foregroundOccurrencesBs) {
        this.foregroundOccurrencesBs = foregroundOccurrencesBs
    }

    String getBackgroundOccurrencesQs() {
        return backgroundOccurrencesQs
    }

    void setBackgroundOccurrencesQs(String backgroundOccurrencesQs) {
        this.backgroundOccurrencesQs = backgroundOccurrencesQs
    }

    String getBackgroundOccurrencesBs() {
        return backgroundOccurrencesBs
    }

    void setBackgroundOccurrencesBs(String backgroundOccurrencesBs) {
        this.backgroundOccurrencesBs = backgroundOccurrencesBs
    }

    boolean isEnvGrid() {
        return gridDivisions > 0
    }

    String getForegroundName() {
        return foregroundName
    }

    void setForegroundName(String foregroundName) {
        this.foregroundName = foregroundName
    }

    String getBackgroundName() {
        return backgroundName
    }

    void setBackgroundName(String backgroundName) {
        this.backgroundName = backgroundName
    }

    int getGridDivisions() {
        return gridDivisions
    }

    void setGridDivisions(int gridDivisions) {
        this.gridDivisions = gridDivisions
    }

    String getFilterWkt() {
        return filterWkt
    }

    void setFilterWkt(String filterWkt) {
        this.filterWkt = filterWkt
    }

    String getId() {
        return id
    }

    void setId(String id) {
        this.id = id
    }

    String[] getLayers() {
        return layers
    }

    void setLayers(String[] layers) {
        if (layers != null && layers.length >= 2) {
            this.layers = layers
        } else if (layers != null && layers.length >= 1) {
            setLayersFromString(layers[0])
        }
    }

    void setLayersFromString(String layers) {
        this.layers = layers.split(":")
    }

    String[] getLayernames() {
        return layernames
    }

    void setLayernames(String[] layernames) {
        if (layernames != null && layernames.length >= 2) {
            this.layernames = layernames
        } else if (layernames != null && layernames.length >= 1) {
            setLayernamesFromString(layernames[0])
        }
    }

    String[] getLayerunits() {
        if (layerunits == null || layerunits.length < 2) {
            layerunits = new String[]{"", ""}
        }
        return layerunits
    }

    void setLayerunits(String[] layerunits) {
        if (layernames != null && layerunits.length >= 2) {
            this.layerunits = layerunits
        } else if (layerunits != null && layerunits.length >= 1) {
            setLayerunitsFromString(layerunits[0])
        }
    }

    void setLayernamesFromString(String layernames) {
        try {
            CSVReader reader = new CSVReader(new StringReader(layernames))

            this.layernames = reader.readNext()
        } catch (Exception e) {
            logger.error("failed to read layernames to string as CSV: " + layernames, e)
        }
    }

    void setLayerunitsFromString(String layerunits) {
        try {
            CSVReader reader = new CSVReader(new StringReader(layerunits))

            this.layerunits = reader.readNext()
        } catch (Exception e) {
            logger.error("failed to read layerunits to string as CSV: " + layerunits, e)
        }
    }
}
