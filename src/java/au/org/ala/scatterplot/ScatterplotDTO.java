/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.scatterplot;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.log4j.Logger;
import org.codehaus.jackson.annotate.JsonIgnore;

import java.io.Serializable;
import java.io.StringReader;

/**
 * @author Adam
 */
public class ScatterplotDTO implements Serializable {

    private static Logger logger = Logger.getLogger(ScatterplotDTO.class);

    String[] layers;
    String[] layernames;
    String[] layerunits;

    String name;

    String foregroundOccurrencesQs;
    String foregroundOccurrencesBs;
    String foregroundName;

    String backgroundOccurrencesQs;
    String backgroundOccurrencesBs;
    String backgroundName;

    String filterWkt;

    //grid
    int gridDivisions = 0;
    private String id;

    public ScatterplotDTO() {
    }

    public ScatterplotDTO(String fqs, String fbs, String fname, String bqs, String bbs, String bname, String name, String layer1, String layer1name, String layer2, String layer2name, int gridDivisions, String filterWkt, String layer1units, String layer2units) {
        this.foregroundOccurrencesQs = fqs;
        this.foregroundOccurrencesBs = fbs;
        this.foregroundName = fname;

        this.name = name;

        this.layers = new String[]{layer1, layer2};
        this.layernames = new String[]{layer1name, layer2name};
        this.layerunits = new String[]{layer1units, layer2units};

        this.backgroundOccurrencesQs = bqs;
        this.backgroundOccurrencesBs = bbs;
        this.backgroundName = bname;

        this.gridDivisions = gridDivisions;

        this.filterWkt = filterWkt;
    }

    @JsonIgnore
    public String getLayer2() {
        if (layers != null) {
            return layers[1];
        } else {
            return null;
        }
    }

    @JsonIgnore
    public void setLayer2(String layer2) {
        if (layers == null) {
            layers = new String[2];
        }

        layers[1] = layer2;
    }

    @JsonIgnore
    public String getLayer2name() {

        if (layernames != null) {
            return layernames[1];
        } else {
            return null;
        }
    }

    @JsonIgnore
    public void setLayer2name(String layer2name) {
        if (layernames == null) {
            layernames = new String[2];
        }

        layernames[1] = layer2name;
    }

    @JsonIgnore
    public String getLayer2units() {

        if (layerunits != null) {
            return layerunits[1];
        } else {
            return "";
        }
    }

    @JsonIgnore
    public void setLayer2units(String layer2units) {
        if (layerunits == null) {
            layerunits = new String[]{"", ""};
        }

        layerunits[1] = layer2units;
    }

    @JsonIgnore
    public String getLayer1units() {

        if (layerunits != null) {
            return layerunits[0];
        } else {
            return "";
        }
    }

    @JsonIgnore
    public void setLayer1units(String layer1units) {
        if (layerunits == null) {
            layerunits = new String[]{"", ""};
        }

        layerunits[0] = layer1units;
    }

    @JsonIgnore
    public String getLayer1() {
        if (layers != null) {
            return layers[0];
        } else {
            return null;
        }
    }

    @JsonIgnore
    public void setLayer1(String layer1) {
        if (layers == null) {
            layers = new String[2];
        }

        layers[0] = layer1;
    }

    @JsonIgnore
    public String getLayer1name() {

        if (layernames != null) {
            return layernames[0];
        } else {
            return null;
        }
    }

    @JsonIgnore
    public void setLayer1name(String layer2name) {
        if (layernames == null) {
            layernames = new String[2];
        }

        layernames[0] = layer2name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getForegroundOccurrencesQs() {
        return foregroundOccurrencesQs;
    }

    public void setForegroundOccurrencesQs(String foregroundOccurrencesQs) {
        this.foregroundOccurrencesQs = foregroundOccurrencesQs;
    }

    public String getForegroundOccurrencesBs() {
        return foregroundOccurrencesBs;
    }

    public void setForegroundOccurrencesBs(String foregroundOccurrencesBs) {
        this.foregroundOccurrencesBs = foregroundOccurrencesBs;
    }

    public String getBackgroundOccurrencesQs() {
        return backgroundOccurrencesQs;
    }

    public void setBackgroundOccurrencesQs(String backgroundOccurrencesQs) {
        this.backgroundOccurrencesQs = backgroundOccurrencesQs;
    }

    public String getBackgroundOccurrencesBs() {
        return backgroundOccurrencesBs;
    }

    public void setBackgroundOccurrencesBs(String backgroundOccurrencesBs) {
        this.backgroundOccurrencesBs = backgroundOccurrencesBs;
    }


    public boolean isEnvGrid() {
        return gridDivisions > 0;
    }

    public String getForegroundName() {
        return foregroundName;
    }

    public void setForegroundName(String foregroundName) {
        this.foregroundName = foregroundName;
    }

    public String getBackgroundName() {
        return backgroundName;
    }

    public void setBackgroundName(String backgroundName) {
        this.backgroundName = backgroundName;
    }

    public int getGridDivisions() {
        return gridDivisions;
    }

    public void setGridDivisions(int gridDivisions) {
        this.gridDivisions = gridDivisions;
    }

    public String getFilterWkt() {
        return filterWkt;
    }

    public void setFilterWkt(String filterWkt) {
        this.filterWkt = filterWkt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLayers(String[] layers) {
        if (layers != null && layers.length >= 2) {
            this.layers = layers;
        } else if (layers != null && layers.length >= 1) {
            setLayers(layers[0]);
        }
    }

    public void setLayernames(String[] layernames) {
        if (layernames != null && layernames.length >= 2) {
            this.layernames = layernames;
        } else if (layernames != null && layernames.length >= 1) {
            setLayernames(layernames[0]);
        }
    }

    public void setLayerunits(String[] layerunits) {
        if (layernames != null && layerunits.length >= 2) {
            this.layerunits = layerunits;
        } else if (layerunits != null && layerunits.length >= 1) {
            setLayerunits(layerunits[0]);
        }
    }

    public String[] getLayers() {
        return layers;
    }

    public void setLayers(String layers) {
        this.layers = layers.split(":");
    }

    public String[] getLayernames() {
        return layernames;
    }

    public String[] getLayerunits() {
        if (layerunits == null || layerunits.length < 2) {
            layerunits = new String[]{"", ""};
        }
        return layerunits;
    }

    public void setLayernames(String layernames) {
        try {
            CSVReader reader = new CSVReader(new StringReader(layernames));

            this.layernames = reader.readNext();
        } catch (Exception e) {
            logger.error("failed to read layernames to string as CSV: " + layernames, e);
        }
    }

    public void setLayerunits(String layerunits) {
        try {
            CSVReader reader = new CSVReader(new StringReader(layerunits));

            this.layerunits = reader.readNext();
        } catch (Exception e) {
            logger.error("failed to read layerunits to string as CSV: " + layerunits, e);
        }
    }
}
