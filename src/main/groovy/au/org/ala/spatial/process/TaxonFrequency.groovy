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

import au.org.ala.spatial.dto.AreaInput
import au.org.ala.spatial.dto.SpeciesInput
import com.opencsv.CSVWriter
import grails.converters.JSON
import groovy.util.logging.Slf4j
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

    static int width = 640    /* Width of the image */
    static int height = 480   /* Height of the image */

    void start() {
        //min year
        def minYear = getInput('minYear').toInteger()

        //area to restrict
        List<AreaInput> area = JSON.parse(getInput('area').toString()) as List<AreaInput>

        //number of target species
        SpeciesInput species1 = JSON.parse(getInput('species1').toString()) as SpeciesInput
        def species1Name = species1.name
        def species1Area = getSpeciesArea(species1, area)

        def facets1 = facetOccurenceCount('year', species1Area)
        if (facets1.size() == 0) {
            def error = "No 'year' values found in the selected area for the species '" + species1Name + "'."
            taskLog(error)
            return
        }
        List years1 = facets1.find { it.fieldName == "year" }.fieldResult

        TimeSeries cumulative1 = new TimeSeries(species1Name, "Year", "Count")
        TimeSeries count1 = new TimeSeries(species1Name, "Year", "Count")

        buildDatasets(count1, cumulative1, years1, minYear)

        //if only one taxon has been selected.
        List<String> files = []
        files.push(generateChart(count1, 'Frequency of ' + species1Name, 'frequency', false, false))
        files.push(generateChart(cumulative1, 'Cumulative frequency of ' + species1Name, 'cumulative_frequency', true, false))

        SpeciesInput species2 = JSON.parse(getInput('species2').toString()) as SpeciesInput
        def species2Name = species2.name

        if (species2.q) {
            def species2Area = getSpeciesArea(species2, area)

            def facets2 = facetOccurenceCount('year', species2Area)
            if (facets2.size() == 0) {
                def error = "No 'year' values found in the selected area for the species '" + species2Name + "'."
                taskLog(error)
                return
            }
            def years2 = facets2.find { it.fieldName == "year" }.fieldResult

            TimeSeries cumulative2 = new TimeSeries(species2Name, "Year", "Count")
            TimeSeries count2 = new TimeSeries(species2Name, "Year", "Count")

            buildDatasets(count2, cumulative2, years2, minYear)

            TimeSeries cumulativeRatio = new TimeSeries("", "", "")
            TimeSeries ratio = new TimeSeries("", "", "")

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

        String metadata = "<html><body>"

        def imgs = files.findAll { it.endsWith('.jpeg') || it.endsWith('.jpg') }
        metadata += "<a href='data.csv'>Download CSV</a>"
        imgs.each {
            def img = '<div><img src=' + it + '></div><br>'
            metadata += img
        }
        metadata += '</body></html>'

        new File(getTaskPath() + "charts.html").write(metadata)

        addOutput("metadata", "charts.html", true)

    }

    //return  Files of csv and jpeg
    String generateChart(TimeSeries ds, String title, String outputfile, Boolean isLineChart, Boolean isPercent) {
        JFreeChart chart = createChartInstance(ds, title, isLineChart, isPercent, "Count")

        new File(getTaskPath().toString()).mkdirs()
        File chart_file = new File(getTaskPath() + outputfile + ".jpeg")
        saveChartAsJPEG(chart_file, chart, width, height)

        outputfile + '.jpeg'
    }

    def createRatio(TimeSeries ds1, TimeSeries ds2, TimeSeries ratio, TimeSeries cumulativeRatio) {

        //The third one is ratio
        int maxYear = Math.max(ds1.getTimePeriods().max().year, ds2.getTimePeriods().max().year)
        int minYear = Math.min(ds1.getTimePeriods().min().year, ds2.getTimePeriods().min().year)
        int sum1 = 0
        int sum2 = 0
        double ratioSum = 0
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
                ratioSum += sum1 / sum2
                cumulativeRatio.add(y, ratioSum)
            }
        }
    }

    //return  Files of csv and jpeg
    String generateRatioChart(TimeSeries ratio_ds, String title, String outputfile, Boolean isLineChart, Boolean isPercent) {

        def ratio_chart = createChartInstance(ratio_ds, title, isLineChart, isPercent, 'Ratio')

        new File(getTaskPath().toString()).mkdirs()
        File ratio_chart_file = new File(getTaskPath() + outputfile + ".jpeg")
        saveChartAsJPEG(ratio_chart_file, ratio_chart, width, height)

        outputfile + '.jpeg'
    }

    JFreeChart createChartInstance(TimeSeries ds, String title, boolean isLineChart, boolean isPercent, String yLabel) {
        // Ratio chart
        TimeSeriesCollection series = new TimeSeriesCollection(ds)
        JFreeChart chart
        XYPlot plot
        ChartFactory.setChartTheme(StandardChartTheme.createLegacyTheme())
        if (isLineChart) {
            chart = ChartFactory.createTimeSeriesChart(
                    title,
                    "Year",
                    yLabel,
                    series,
                    false, true, false)

            plot = (XYPlot) chart.getPlot()

            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer()
            renderer.setBaseShapesVisible(true)

            if (isPercent) {
                NumberAxis yAxis = (NumberAxis) plot.getRangeAxis()
                DecimalFormat pctFormat = new DecimalFormat("#.##%")
                yAxis.setNumberFormatOverride(pctFormat)
            }
        } else {
            chart = ChartFactory.createXYBarChart(
                    title,
                    "Year",
                    true,
                    yLabel,
                    series, PlotOrientation.VERTICAL,
                    false, true, false)

            plot = (XYPlot) chart.getPlot()
            plot.getRenderer()

            plot.getRenderer().setShadowVisible(false)

        }

        chart
    }

    String createCSV(TimeSeries ds1, TimeSeries ds2, TimeSeries ratio, String speciesName1, String speciesName2, String filename) {
        CSVWriter writer = new CSVWriter(new FileWriter(getTaskPath() + filename))

        if (ds2 != null) {
            writer.writeNext(["Year", "Frequency-" + speciesName1, "Cumulative frequency-" + speciesName1,
                                         "Frequency-" + speciesName2, "Cumulative frequency-" + speciesName2,
                                         "Ratio", "Cumulative ratio"] as String [])

            def i1sum = 0
            def i2sum = 0
            for (TimeSeriesDataItem i : (ratio.getItems() as List<TimeSeriesDataItem>)) {
                def i1 = ds1.getValue(i.period)
                def i2 = ds2.getValue(i.period)
                i1sum += i1 ?: 0
                i2sum += i2 ?: 0
                writer.writeNext((String[]) [i.period, i1?.value ?: '', i1sum, i2?.value ?: '', i2sum,
                                             i2?.value > 0 ? i1?.value / i2?.value : '', i2sum > 0 ? i1sum / i2sum : ''])
            }
        } else {
            writer.writeNext(["Year", "Frequency-" + speciesName1, "Cumulative frequency-" + speciesName1,
                                         "Frequency-" + speciesName2] as String [])

            def i1sum = 0
            for (TimeSeriesDataItem i : (ds1.getItems() as List<TimeSeriesDataItem>)) {
                def i1 = ds1.getValue(i.period)
                i1sum += i1 ?: 0
                writer.writeNext((String[]) [i.period, i1?.value ?: '', i1sum])
            }
        }

        writer.flush()
        writer.close()

        filename
    }

    void buildDatasets(TimeSeries count,TimeSeries  cumulative, List list, Integer min) {
        if (list.size() > 0) {
            int sum = 0
            for (int i = 0; i < list.size(); i++) {
                def item = list[i]

                if (item.label?.isNumber()) {
                    def year = Integer.parseInt(item.label)

                    if (year >= min) {
                        sum += item.count

                        count.add(new Year(year), new Double(item.count))
                        cumulative.add(new Year(year), sum)
                    }
                }
            }
        }
    }
}
