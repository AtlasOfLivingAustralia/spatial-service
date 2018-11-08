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


import grails.converters.JSON
import groovy.util.logging.Slf4j


import org.jfree.chart.ChartFactory;

import org.jfree.chart.JFreeChart
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
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

        DefaultCategoryDataset cumulative_ds = new DefaultCategoryDataset();
        DefaultCategoryDataset ratio_ds = new DefaultCategoryDataset();

        if(resultsOfyears_1.size()>0) {
            //add the first item in
            cumulative_ds.addValue(resultsOfyears_1[0].count, species1_name, Integer.parseInt(resultsOfyears_1[0].label))
            ratio_ds.addValue(resultsOfyears_1[0].count, species1_name, Integer.parseInt(resultsOfyears_1[0].label))

            for (int i = 1; i < resultsOfyears_1.size(); i++) {
                def result = resultsOfyears_1[i];
                try {
                    def year = Integer.parseInt(result.label)
                    def v = result.count;
                    result.count = resultsOfyears_1[i - 1].count + result.count;

                    ratio_ds.addValue(v, species1_name, year)
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

            //cumulative count
            if (resultsOfyears_2.size()>0){

                cumulative_ds.addValue(resultsOfyears_2[0].count, species2_name, Integer.parseInt(resultsOfyears_2[0].label))
                ratio_ds.addValue(resultsOfyears_2[0].count, species2_name, Integer.parseInt(resultsOfyears_2[0].label))

                for(int i=1;i<resultsOfyears_2.size();i++){
                    def result = resultsOfyears_2[i];
                    try {
                        def year = Integer.parseInt(result.label)
                        def v = result.count;
                        ratio_ds.addValue(v, species2_name, year)

                        result.count = resultsOfyears_2[i - 1].count + v;
                        cumulative_ds.addValue( result.count , species2_name, year) //push into category for csv generation

                    }catch(Exception e){
                        println i + ' record is incorrect' + resultsOfyears_2[i].toString()
                    }
                }

                String[] cumufiles = generateRatioChart(cumulative_ds,'Cumulative Ratio: ' + species1_name +" / " +species2_name,'cumulative_ratio',true)
                cumufiles.each {
                    addOutput("files", it, true)
                }

                String[] frefiles = generateRatioChart(ratio_ds,'Frequency Ratio: '+ species1_name +" / " +species2_name,'frequency_ratio',true)
                frefiles.each {
                    addOutput("files", it, true)
                }
            }
          }else{  //if only one taxon has been selected.

            String[] frefiles = generateChart(ratio_ds,'Frequency of '+species1_name,'frequency',false)
            frefiles.each {
                addOutput("files", it, true)
            }

            String[] cumfiles = generateChart(cumulative_ds,'Cumulative frequency of '+species1_name,'cumulative_frequency',true)
            cumfiles.each {
                addOutput("files", it, true)
            }
        }

    }


    //return  Files of csv and jpeg
    String[] generateChart(DefaultCategoryDataset ds, String title, String outputfile,isLineChart){
        JFreeChart chart = createChartInstance(ds,title,isLineChart)
        // Use nomral Y format
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        DecimalFormat pctFormat = new DecimalFormat("#");
        yAxis.setNumberFormatOverride(pctFormat);



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

        int width = 640;    /* Width of the image */
        int height = 480;   /* Height of the image */

        List<float[]> ratios = generateRatio(ds)

        DefaultCategoryDataset ratio_ds = new DefaultCategoryDataset();
        //The third one is ratio
        ratios.each {ratio_ds.addValue(it[3],'ratio',  new Integer((int)it[0]))}
        def ratio_chart = createChartInstance(ratio_ds,title,true);

        new File(getTaskPath().toString()).mkdirs()
        File ratio_chart_file = new File( getTaskPath() + outputfile +".jpeg");
        saveChartAsJPEG( ratio_chart_file ,ratio_chart, width , height )

        //Keys: Acacia, Koala etc
        String[] sps = ds.getRowKeys()

        File ratio_csv_file = new File(getTaskPath() + outputfile+'.csv')
        ratio_csv_file.withWriter{ out ->
            out.println('Year,'+ sps.join(',') +','+'ratio'); //add header
            ratios.each {
                out.println((int)it[0]+','+(int)it[1]+','+(int)it[2]+','+it[3])}
        }

        return [outputfile+'.jpeg',outputfile+'.csv']
    }

    JFreeChart createChartInstance(DefaultCategoryDataset ds,String title,boolean  isLineChart){
        // Ratio chart
        def chart,plot
        ChartFactory.setChartTheme(StandardChartTheme.createLegacyTheme());
        if (isLineChart) {
            chart = ChartFactory.createLineChart(
                    title,
                    "Year", "",
                     ds, PlotOrientation.VERTICAL,
                    false, true, false);
        }
        else {
            chart = ChartFactory.createBarChart(
                    title,
                    "Year", "",
                    ds, PlotOrientation.VERTICAL,
                    false, true, false);
        }

        plot = (CategoryPlot) chart.getPlot();
        plot.getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_90);


        if(isLineChart){
            LineAndShapeRenderer renderer =
                    (LineAndShapeRenderer)plot.getRenderer()
            renderer.setBaseShapesVisible(true);

            NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
            DecimalFormat pctFormat = new DecimalFormat("#.##%");
            yAxis.setNumberFormatOverride(pctFormat);
        }
        else{
            // disable bar outlines...
            final BarRenderer renderer = (BarRenderer) plot.getRenderer();
            renderer.setDrawBarOutline(false);
        }

        return chart
    }



    //Need and only need two taxon
    //Year, s0, s1, ratio
    List<float[]> generateRatio(DefaultCategoryDataset ds){
        int rowNum = ds.getRowCount();
        int colNum = ds.getColumnCount()

        //Keys: Acacia, Koala etc
        String[] sps = ds.getRowKeys()
        List years = ds.getColumnKeys().toSorted()
        //years = years.sort()

        //String[][] csv = new String[colNum][rowNum]
        List<float[]> csv= new ArrayList()

        for(int i=0;i<colNum;i++) {
            try{

                int year = years[i];
                Long v0 = ds.getValue(sps[0],year);
                Long v1 = ds.getValue(sps[1],year);

                if ( v0 && v1 ){
                    float ratio = v0/v1
                    float[] row = [(int)year,(int)v0,(int)v1,ratio]
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
                    row[j + 1] = ds.getValue(sps[j], year)?(int)ds.getValue(sps[j], year):''
                } catch (Exception e) {
                    row[j] = '';
                }
            }
            csv[i] = row;
        }

        File csvFile = new File(getTaskPath() + filename)
        csvFile.withWriter{ out ->
            out.println('Year,'+ sps.join(','));
            csv.each { out.println(it.join(","))}
        }
        return filename;
    }
}
