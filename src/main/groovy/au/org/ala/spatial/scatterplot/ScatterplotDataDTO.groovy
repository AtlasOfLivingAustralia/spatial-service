package au.org.ala.spatial.scatterplot

import groovy.transform.CompileStatic
import org.codehaus.jackson.annotate.JsonIgnoreProperties

/**
 * Created by a on 10/03/2014.
 */
//@CompileStatic
@JsonIgnoreProperties(ignoreUnknown = true)
class ScatterplotDataDTO {

    //data
    double[] points
    String[] series
    String[] ids
    double[][] data
    double[] seriesValues
    double[] backgroundPoints
    String[] backgroundSeries
    String[] backgroundIds
    double[][] backgroundData

    int missingCount

    double[][] gridData
    float[][] gridCutoffs

    //fields that can change and then require data to be refreshed
    String colourMode
    String layer1
    String layer2

    ScatterplotDataDTO() {
    }

    double[] getPoints() {
        return points
    }

    void setPoints(double[] points) {
        this.points = points
    }

    String[] getSeries() {
        return series
    }

    void setSeries(String[] series) {
        this.series = series
    }

    String[] getIds() {
        return ids
    }

    void setIds(String[] ids) {
        this.ids = ids
    }

    double[][] getData() {
        return data
    }

    void setData(double[][] data) {
        this.data = data
    }

    double[] getSeriesValues() {
        return seriesValues
    }

    void setSeriesValues(double[] seriesValues) {
        this.seriesValues = seriesValues
    }

    double[] getBackgroundPoints() {
        return backgroundPoints
    }

    void setBackgroundPoints(double[] backgroundPoints) {
        this.backgroundPoints = backgroundPoints
    }

    String[] getBackgroundSeries() {
        return backgroundSeries
    }

    void setBackgroundSeries(String[] backgroundSeries) {
        this.backgroundSeries = backgroundSeries
    }

    String[] getBackgroundIds() {
        return backgroundIds
    }

    void setBackgroundIds(String[] backgroundIds) {
        this.backgroundIds = backgroundIds
    }

    double[][] getBackgroundData() {
        return backgroundData
    }

    void setBackgroundData(double[][] backgroundData) {
        this.backgroundData = backgroundData
    }

    int getMissingCount() {
        return missingCount
    }

    void setMissingCount(int missingCount) {
        this.missingCount = missingCount
    }

    double[][] getGridData() {
        return gridData
    }

    void setGridData(double[][] gridData) {
        this.gridData = gridData
    }

    float[][] getGridCutoffs() {
        return gridCutoffs
    }

    void setGridCutoffs(float[][] gridCutoffs) {
        this.gridCutoffs = gridCutoffs
    }

    String getColourMode() {
        return colourMode
    }

    void setColourMode(String colourMode) {
        this.colourMode = colourMode
    }

    String getLayer1() {
        return layer1
    }

    void setLayer1(String layer1) {
        this.layer1 = layer1
    }

    String getLayer2() {
        return layer2
    }

    void setLayer2(String layer2) {
        this.layer2 = layer2
    }

    //only returns extents for the first two layers
    double[][] layerExtents() {
        double minx = Double.NaN
        double maxx = Double.NaN
        double miny = Double.NaN
        double maxy = Double.NaN

        for (int i = 0; i < data.length; i++) {
            if (Double.isNaN(minx) || minx > data[i][0]) {
                minx = data[i][0]
            }
            if (Double.isNaN(maxx) || maxx < data[i][0]) {
                maxx = data[i][0]
            }
            if (Double.isNaN(miny) || miny > data[i][1]) {
                miny = data[i][1]
            }
            if (Double.isNaN(maxy) || maxy < data[i][1]) {
                maxy = data[i][1]
            }
        }

        [[minx, maxx], [miny, maxy]] as double[][]
    }
}
