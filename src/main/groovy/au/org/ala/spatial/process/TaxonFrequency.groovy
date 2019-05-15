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

import au.com.bytecode.opencsv.CSVWriter
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.jfree.chart.ChartFactory
import org.jfree.chart.JFreeChart
import org.jfree.chart.StandardChartTheme
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import org.jfree.data.time.TimeSeriesDataItem
import org.jfree.data.time.Year

import java.text.DecimalFormat

import static org.jfree.chart.ChartUtilities.saveChartAsJPEG

@Slf4j
class TaxonFrequency extends SlaveProcess {

    static int width = 640;    /* Width of the image */
    static int height = 480;   /* Height of the image */

    void start() {
        //min year
        def minYear = task.input.minYear.toInteger()

        //area to restrict
        def area = JSON.parse(task.input.area.toString())

        //number of target species
        def species1 = JSON.parse(task.input.species1.toString())
        def species1Name = species1.name;
        def species1Area = getSpeciesArea(species1, area[0])

        def facets1 = facetOccurenceCount('year', species1Area)
        if (facets1.size() == 0) {
            taskLog("No occurrences found in the selected area.")
            return
        }
        List years1 = facets1.find { it.fieldName == "year" }.fieldResult

        TimeSeries cumulative1 = new TimeSeries(species1Name, "Year", "Count");
        TimeSeries count1 = new TimeSeries(species1Name, "Year", "Count");

        buildDatasets(count1, cumulative1, years1, minYear)

        //if only one taxon has been selected.
        def files = []
        files.push(generateChart(count1, 'Frequency of ' + species1Name, 'frequency', false, false))
        files.push(generateChart(cumulative1, 'Cumulative frequency of ' + species1Name, 'cumulative_frequency', true, false))

        def species2 = JSON.parse(task.input.species2.toString())
        def species2Name = species2.name;

        if (species2.q) {
            def species2Area = getSpeciesArea(species2, area[0])

            def facets2 = facetOccurenceCount('year', species2Area)
            def years2 = facets2.find { it.fieldName == "year" }.fieldResult

            TimeSeries cumulative2 = new TimeSeries(species2Name, "Year", "Count");
            TimeSeries count2 = new TimeSeries(species2Name, "Year", "Count");

            buildDatasets(count2, cumulative2, years2, minYear)

            TimeSeries cumulativeRatio = new TimeSeries("", "", "");
            TimeSeries ratio = new TimeSeries("", "", "");

            createRatio(count1, count2, ratio, cumulativeRatio)

            //cumulative count
            files.push(generateRatioChart(ratio, 'Frequency Ratio: ' + species1Name + " / " + species2Name, 'frequency_ratio', true, true))
            files.push(generateRatioChart(cumulativeRatio, 'Cumulative Ratio: ' + species1Name + " / " + species2Name, 'cumulative_ratio', true, true))
            files.push(createCSV(count1, count2, ratio, species1Name, species2Name, "data.csv"))
        } else {
            files.push(createCSV(count1, null, null, species1Name, null, "data.csv"))
        }

        files.each {
            addOutput("files", it, true)
        }

        String metadata = "<html><body>";

        def imgs = files.findAll { it.endsWith('.jpeg') || it.endsWith('.jpg') }
        metadata += "<a href='data.csv'>Download CSV</a>"
        imgs.each {
            def img = '<div><img src=' + it + '></div><br>'
            metadata += img;
        }
        metadata += '</body></html>'

        FileUtils.writeStringToFile(new File(getTaskPath() + "charts.html"), metadata)

        addOutput("metadata", "charts.html", true)

    }

    //return  Files of csv and jpeg
    String generateChart(ds, String title, String outputfile, isLineChart, isPercent) {
        JFreeChart chart = createChartInstance(ds, title, isLineChart, isPercent)

        new File(getTaskPath().toString()).mkdirs()
        File chart_file = new File(getTaskPath() + outputfile + ".jpeg");
        saveChartAsJPEG(chart_file, chart, width, height)

        outputfile + '.jpeg'
    }

    def createRatio(ds1, ds2, ratio, cumulativeRatio) {

        //The third one is ratio
        def maxYear = Math.max(ds1.getTimePeriods().max().year, ds2.getTimePeriods().max().year)
        def minYear = Math.min(ds1.getTimePeriods().min().year, ds2.getTimePeriods().min().year)
        int sum1 = 0;
        int sum2 = 0;
        for (int year = minYear; year <= maxYear; year++) {
            def y = new Year(year)

            def v1 = ds1.getValue(y)
            def v2 = ds2.getValue(y)
            sum1 += v1 ?: 0
            sum2 += v2 ?: 0

            if (v1 != null && v2 != 0) {
                ratio.add(y, v1 / v2)
            }

            if (sum2 > 0 && v1 != null && v2 != null) {
                cumulativeRatio.add(y, sum1 / sum2)
            }
        }
    }

    //return  Files of csv and jpeg
    String generateRatioChart(ratio_ds, String title, String outputfile, isLineChart, isPercent) {

        def ratio_chart = createChartInstance(ratio_ds, title, isLineChart, isPercent);

        new File(getTaskPath().toString()).mkdirs()
        File ratio_chart_file = new File(getTaskPath() + outputfile + ".jpeg");
        saveChartAsJPEG(ratio_chart_file, ratio_chart, width, height)

        outputfile + '.jpeg'
    }

    JFreeChart createChartInstance(ds, String title, boolean isLineChart, boolean isPercent) {
        // Ratio chart
        def series = new TimeSeriesCollection(ds)
        def chart, plot
        ChartFactory.setChartTheme(StandardChartTheme.createLegacyTheme());
        if (isLineChart) {
            chart = ChartFactory.createTimeSeriesChart(
                    title,
                    "Year",
                    "Ratio",
                    series,
                    false, true, false);

            plot = (XYPlot) chart.getPlot();

            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer()
            renderer.setBaseShapesVisible(true);

            if (isPercent) {
                NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
                DecimalFormat pctFormat = new DecimalFormat("#.##%");
                yAxis.setNumberFormatOverride(pctFormat);
            }
        } else {
            chart = ChartFactory.createXYBarChart(
                    title,
                    "Year",
                    true,
                    "Count",
                    series, PlotOrientation.VERTICAL,
                    false, true, false);

            plot = (XYPlot) chart.getPlot();
            plot.getRenderer()

            plot.getRenderer().setShadowVisible(false)

        }

        chart
    }

    String createCSV(ds1, ds2, ratio, speciesName1, speciesName2, String filename) {
        CSVWriter writer = new CSVWriter(new FileWriter(getTaskPath() + filename));

        if (ds2 != null) {
            writer.writeNext((String[]) ["Year", "Frequency-" + speciesName1, "Cumulative frequency-" + speciesName1,
                                         "Frequency-" + speciesName2, "Cumulative frequency-" + speciesName2,
                                         "Ratio", "Cumulative ratio"])

            def i1sum = 0
            def i2sum = 0
            for (TimeSeriesDataItem i : ratio.getItems()) {
                def i1 = ds1.getValue(i.period)
                def i2 = ds2.getValue(i.period)
                i1sum += i1 ?: 0
                i2sum += i2 ?: 0
                writer.writeNext((String[]) [i.period, i1?.value ?: '', i1sum, i2?.value ?: '', i2sum,
                                             i2?.value > 0 ? i1?.value / i2?.value : '', i2sum > 0 ? i1sum / i2sum : ''])
            }
        } else {
            writer.writeNext((String[]) ["Year", "Frequency-" + speciesName1, "Cumulative frequency-" + speciesName1,
                                         "Frequency-" + speciesName2])

            def i1sum = 0
            for (TimeSeriesDataItem i : ds1.getItems()) {
                def i1 = ds1.getValue(i.period)
                i1sum += i1 ?: 0
                writer.writeNext((String[]) [i.period, i1?.value ?: '', i1sum])
            }
        }

        writer.flush()
        writer.close()

        filename
    }

    void buildDatasets(count, cumulative, list, min) {
        if (list.size() > 0) {
            int sum = 0;
            for (int i = 0; i < list.size(); i++) {
                def item = list[i];

                if (item.label?.isNumber()) {
                    def year = Integer.parseInt(item.label)

                    if (year >= min) {
                        sum += item.count;

                        count.add(new Year(year), new Double(item.count))
                        cumulative.add(new Year(year), sum)
                    }
                }
            }
        }
    }
}
