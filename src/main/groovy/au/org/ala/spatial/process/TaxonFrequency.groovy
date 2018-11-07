/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.process

import au.org.ala.layers.util.SpatialUtil
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.WKTReader
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.geotools.kml.KML
import org.geotools.kml.KMLConfiguration
import org.geotools.xml.Encoder

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYBoxAnnotation
import org.jfree.chart.axis.CategoryAxis
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.GrayPaintScale;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYShapeRenderer
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYZDataset;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.ChartUtilities;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


import java.awt.geom.Point2D
import java.text.DecimalFormat

import static org.jfree.chart.ChartUtilities.*

@Slf4j
class TaxonFrequency extends SlaveProcess {

    void start() {

        //grid size
        //def gridSize = task.input.resolution.toDouble()

        //area to restrict
        def area = JSON.parse(task.input.area.toString())

        //number of target species
        def species1 = JSON.parse(task.input.species1.toString())
        def species1_name = species1.name;
        def species1Area = getSpeciesArea(species1, area[0])

        def  results_1 = facetOccurenceCount('year',species1Area)
        List resultsOfyears_1 = results_1.find{it.fieldName=="year"}.fieldResult
        //Used to create CSV
        DefaultCategoryDataset cumulative_ds = new DefaultCategoryDataset();
        DefaultCategoryDataset ratio_ds = new DefaultCategoryDataset();

//        XYSeriesCollection cumulative_xy_dataset = new XYSeriesCollection();
//        XYSeries cumulative_series1 = new XYSeries(species1_name);
//        cumulative_xy_dataset.addSeries(cumulative_series1)

//        XYSeriesCollection freq_xy_dataset = new XYSeriesCollection();
//        XYSeries freq_series1 = new XYSeries(species1_name);
//        freq_xy_dataset.addSeries(freq_series1)

        if(resultsOfyears_1.size()>0) {
            //add the first item in

//          cumulative_series1.add( Integer.parseInt(resultsOfyears_1[0].label),resultsOfyears_1[0].count)

            cumulative_ds.addValue(resultsOfyears_1[0].count, species1_name, Integer.parseInt(resultsOfyears_1[0].label))
            ratio_ds.addValue(resultsOfyears_1[0].count, species1_name, Integer.parseInt(resultsOfyears_1[0].label))

            for (int i = 1; i < resultsOfyears_1.size(); i++) {
                def result = resultsOfyears_1[i];
                try {
                    def year = Integer.parseInt(result.label)
                    def v = result.count;
                    result.count = resultsOfyears_1[i - 1].count + result.count;
                    //freq_series1.add(year,v)
                    ratio_ds.addValue(v, species1_name, year)


                    //cumulative_series1.add(year, result.count)
                    cumulative_ds.addValue(result.count, species1_name, year) //push into category for csv generation
                } catch (Exception e) {
                    println i + ' record is incorrect' + resultsOfyears_1[i].toString()
                }
            }
        }

       
        if (task.input.species2) {
            def species2 = JSON.parse(task.input.species2.toString())
            def species2_name = species2.name;
            def species2Area = getSpeciesArea(species2, area[0])

            def results_2 = facetOccurenceCount('year',species2Area)
            def resultsOfyears_2 = results_2.find{it.fieldName=="year"}.fieldResult

            //XYSeries cumulative_series2 = new XYSeries(species2_name);
            //cumulative_xy_dataset.addSeries(cumulative_series2)
            //cumulative count
            if (resultsOfyears_2.size()>0){
               //cumulative_series2.add( Integer.parseInt(resultsOfyears_2[0].label),resultsOfyears_2[0].count)

                cumulative_ds.addValue(resultsOfyears_2[0].count, species2_name, Integer.parseInt(resultsOfyears_2[0].label))
                ratio_ds.addValue(resultsOfyears_2[0].count, species2_name, Integer.parseInt(resultsOfyears_2[0].label))

                for(int i=1;i<resultsOfyears_2.size();i++){
                    def result = resultsOfyears_2[i];
                    try {
                        def year = Integer.parseInt(result.label)
                        def v = result.count;
                        ratio_ds.addValue(v, species2_name, year)

                        result.count = resultsOfyears_2[i - 1].count + v;
                        //cumulative_series2.add(year, result.count )
                        cumulative_ds.addValue( result.count , species2_name, year) //push into category for csv generation

                    }catch(Exception e){
                        println i + ' record is incorrect' + resultsOfyears_2[i].toString()
                    }
                }

                String[] cumufiles = generateRatioChart(cumulative_ds,'Cumulative Ratio','cumulative_ratio',true)
                cumufiles.each {
                    addOutput("files", it, true)
                }

                String[] frefiles = generateRatioChart(ratio_ds,'Frequency Ratio','frequency_ratio',false)
                frefiles.each {
                    addOutput("files", it, true)
                }
            }
          }else{  //if only one taxon has been selected.
            String[] frefiles = generateChart(ratio_ds,'Frequency','frequency',false)
            frefiles.each {
                addOutput("files", it, true)
            }

            String[] cumfiles = generateChart(cumulative_ds,'Cumulative frequency','cumulative_frequency',true)
            cumfiles.each {
                addOutput("files", it, true)
            }

           /* String freq_chart = createLineChart(freq_xy_dataset,'Frequence of '+species1_name, 'Frequence of ' +species1_name+'.jpg');
            addOutput("files", freq_chart, true)

            String cumulative_chart = createLineChart(cumulative_xy_dataset,'Cumulative frequence of '+species1_name, 'Cumulative Frequence of ' +species1_name+'.jpg');
            addOutput("files", cumulative_chart, true)


            //create csv file
            String freq_csvFile = createCSVFromXY(freq_series1, 'Frequence of ' +species1_name +".csv")
            addOutput("files", freq_csvFile, true)

            String cumu_csvFile = createCSVFromXY(cumulative_series1, 'Cumulative Frequence of ' +species1_name +".csv")
            addOutput("files", cumu_csvFile, true)
*/
        }

    }


    //return  Files of csv and jpeg
    String[] generateChart(DefaultCategoryDataset ds, String title, String outputfile,isLineChart){
        JFreeChart chart
        if (isLineChart)
            chart= ChartFactory.createLineChart(
                    title,
                    "Year", "",
                    ds,PlotOrientation.VERTICAL,
                    true, true, false);
        else
            chart= ChartFactory.createBarChart(
                    title,
                    "Year", "",
                    ds,PlotOrientation.VERTICAL,
                    true, true, false);


        CategoryPlot cbarplot = (CategoryPlot) chart.getPlot();
        cbarplot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        int width = 640;    /* Width of the image */
        int height = 480;   /* Height of the image */


        new File(getTaskPath().toString()).mkdirs()
        File chart_file = new File( getTaskPath() + outputfile +".jpeg");
        saveChartAsJPEG( chart_file ,chart, width , height )

        String csvfile = createCSV(ds,outputfile+'.csv')

        return [outputfile+'.jpeg',csvfile]
    }




    //return  Files of csv and jpeg
    String[] generateRatioChart(DefaultCategoryDataset ds, String title, String outputfile,isLineChart){
        List<int[]> ratios = generateRatio(ds)

        DefaultCategoryDataset ratio_ds = new DefaultCategoryDataset();
        ratios.each {ratio_ds.addValue(it[1],'ration',it[0])}

        // Ratio chart
        JFreeChart ratio_chart
        if (isLineChart)
            ratio_chart= ChartFactory.createLineChart(
                    title,
                    "Year", "Ratio",
                    ratio_ds,PlotOrientation.VERTICAL,
                    true, true, false);
        else
            ratio_chart= ChartFactory.createBarChart(
                    title,
                    "Year", "Ratio",
                    ratio_ds,PlotOrientation.VERTICAL,
                    true, true, false);


        CategoryPlot cbarplot = (CategoryPlot) ratio_chart.getPlot();
        cbarplot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        int width = 640;    /* Width of the image */
        int height = 480;   /* Height of the image */


        new File(getTaskPath().toString()).mkdirs()
        File ratio_chart_file = new File( getTaskPath() + outputfile +".jpeg");
        saveChartAsJPEG( ratio_chart_file ,ratio_chart, width , height )


        File ratio_csv_file = new File(getTaskPath() + outputfile+'.csv')
        ratio_csv_file.withWriter{ out ->
            ratios.each { out.println(it[0] +',' + it[1])}
        }

        return [outputfile+'.jpeg',outputfile+'.csv']
    }


    String createCSVFromXY(XYSeries xy, String outputFile){
        List ds = xy.getItems()

        File csvFile = new File(getTaskPath() + outputFile)
        csvFile.withWriter{ out ->
            ds.each { out.println(it.getX() +',' + it.getY())}
        }

        return outputFile

    }

    /**
     * Line chart is only for Ratio
     * @param sc
     * @param title
     * @param outputFile
     * @return
     */
    String createLineChart(XYSeriesCollection sc, String title, String outputFile){
        JFreeChart lineChart = ChartFactory.createXYLineChart(
                title,
                "Species", "Frequency",
                sc,PlotOrientation.VERTICAL,
                true, true, false);

        XYPlot plot = (XYPlot) lineChart.getPlot();
        ((NumberAxis)plot.getDomainAxis()).setNumberFormatOverride(new DecimalFormat("####"));


        int width = 640;    /* Width of the image */
        int height = 480;   /* Height of the image */

        new File(getTaskPath().toString()).mkdirs()
        File targetChartfile = new File( getTaskPath() + outputFile);
        saveChartAsJPEG( lineChart, targetChartfile , width , height )
        return filename;
    }

    //Need and only need two taxon
    List<int[]> generateRatio(DefaultCategoryDataset ds){
        int rowNum = ds.getRowCount();
        int colNum = ds.getColumnCount()

        //Keys: Acacia, Koala etc
        String[] sps = ds.getRowKeys()
        List years = ds.getColumnKeys().toSorted()
        //years = years.sort()

        //String[][] csv = new String[colNum][rowNum]
        List<int[]> csv= new ArrayList()
        for(int i=0;i<colNum;i++) {
            try{

                int year = years[i];
                Long v0 = ds.getValue(sps[0],year);
                Long v1 = ds.getValue(sps[1],year);

                if ( v0 && v1 ){
                    int ratio = Math.round(v0*100/v1)
                    int[] row = [year,ratio]
                    csv.push(row);
                }
            }catch(Exception e){

            }
        }
        return csv;
    }
    
    String createCSV(DefaultCategoryDataset ds, String filename){
        //CSV generation
        int rowNum = ds.getRowCount();
        int colNum = ds.getColumnCount()

        //Keys: Acacia, Koala etc
        String[] sps = ds.getRowKeys()
        List years = ds.getColumnKeys().toSorted()
        //years = years.sort()

        String[][] csv = new String[colNum][rowNum]
        for(int i=0;i<colNum;i++) {
            String[] row = new String[rowNum + 1]
            int year = years[i];
            row[0] = year;
            for (int j = 0; j < rowNum; j++) {
                try {
                    row[j + 1] = ds.getValue(sps[j], year)?ds.getValue(sps[j], year):''
                } catch (Exception e) {
                    row[j] = '';
                }

            }
            csv[i] = row;
        }

        File csvFile = new File(getTaskPath() + filename)
        csvFile.withWriter{ out ->
            csv.each { out.println(it.join(","))}
        }
        return filename;
    }
}
