package au.org.ala.spatial.scatterplot

import org.codehaus.jackson.annotate.JsonIgnoreProperties

/**
 * Created by a on 10/03/2014.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ScatterplotStyleDTO {

    //appearance
    public String colourMode = "-1"
    public int red = 0
    public int green = 0
    public int blue = 255
    public float opacity = 1.0f
    public int size = 4

    String highlightWkt = null

    double[] selection = null
    double[] prevSelection = null

    int width = 320
    int height = 320
    String prevHighlightWkt = null

    ScatterplotStyleDTO() {
    }

    String getColourMode() {
        return colourMode
    }

    void setColourMode(String colourMode) {
        this.colourMode = colourMode
    }

    int getRed() {
        return red
    }

    void setRed(int red) {
        this.red = red
    }

    int getGreen() {
        return green
    }

    void setGreen(int green) {
        this.green = green
    }

    int getBlue() {
        return blue
    }

    void setBlue(int blue) {
        this.blue = blue
    }

    float getOpacity() {
        return opacity
    }

    void setOpacity(float opacity) {
        this.opacity = opacity
    }

    int getSize() {
        return size
    }

    void setSize(int size) {
        this.size = size
    }

    String getHighlightWkt() {
        return highlightWkt
    }

    void setHighlightWkt(String highlightWkt) {
        if (highlightWkt != null && highlightWkt.length() == 0) {
            this.highlightWkt = null
        } else {
            this.highlightWkt = highlightWkt
        }
    }

    double[] getSelection() {
        return selection
    }

    void setSelection(double[] selection) {
        this.selection = selection
    }

    int getWidth() {
        return width
    }

    void setWidth(int width) {
        this.width = width
    }

    int getHeight() {
        return height
    }

    void setHeight(int height) {
        this.height = height
    }

    double[] getPrevSelection() {
        return prevSelection
    }

    void setPrevSelection(double[] prevSelection) {
        this.prevSelection = prevSelection
    }

    void setPrevHighlightWKT(String highlightWkt) {
        this.prevHighlightWkt = highlightWkt
    }


    String getPrevHighlightWkt() {
        return prevHighlightWkt
    }
}
