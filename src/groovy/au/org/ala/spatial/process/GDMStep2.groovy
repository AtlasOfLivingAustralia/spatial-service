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

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.layers.intersect.IniReader
import grails.converters.JSON
import groovy.util.logging.Commons
import org.apache.commons.io.FileUtils
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtilities
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.CategoryAxis
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.CategoryPlot
import org.jfree.chart.plot.PlotOrientation
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYDotRenderer
import org.jfree.chart.renderer.xy.XYSplineRenderer
import org.jfree.data.category.CategoryDataset
import org.jfree.data.category.DefaultCategoryDataset
import org.jfree.data.general.Dataset
import org.jfree.data.xy.DefaultXYDataset
import org.jfree.data.xy.XYDataset

import java.awt.*
import java.util.List
import java.util.logging.Level
import java.util.logging.Logger

@Commons
class GDMStep2 extends SlaveProcess {

    void start() {

        def distance = task.input.distance
        def weighting = task.input.weighting
        def subsample = task.input.subsample
        def sitePairsSize = task.input.sitePairsSize
        def cutpoint = task.input.cutpoint

        def taskId = task.input.gdmId

        Properties props = new Properties()
        props.load(new FileInputStream(getTaskPath() + "ala.properties"))

        def area = JSON.parse(task.input.area.toString())

        def layers = JSON.parse(task.input.layer.toString())
        def envnameslist = new String[layers.size()]
        layers.eachWithIndex { l, idx ->
            envnameslist[idx] = l
        }

        // 5. build parameters files for GDM
        String params = updateParamfile(cutpoint, distance, weighting, subsample, sitePairsSize, getTaskPath())

        // 6. run GDM
        runCmd([grailsApplication.config.gdm.dir, "-g", "1", params])

        // 7. process params file

        // 7.1 generate/display charts
        generateCharts(getTaskPath())

        generateMetadata(envnameslist, area[0].area_km, task.id, getTaskPath())

        // 7.2 generate/display transform grid
        Iterator<File> files = FileUtils.iterateFiles(new File(getTaskPath()), ["grd"], false)
        while (files.hasNext()) {
            File f = files.next()
            if (f.getName().startsWith("domain")) {
                continue
            }
            String lyr = f.getName().substring(0, f.getName().length() - 4)

            //SpatialTransformer.convertDivaToAsc(outputdir + lyr, outputdir + lyr + ".asc");

            addOutput(lyr, true)
        }

    }

    String updateParamfile(String cutpoint, String useDistance, String weighting, String useSubSample, String sitePairsSize, String outputdir) {
        try {
            IniReader ir = new IniReader(outputdir + "/gdm_params.txt")
            ir.setValue("GDMODEL", "UseEuclidean", useDistance)
            ir.setValue("GDMODEL", "UseSubSample", useSubSample)
            ir.setValue("GDMODEL", "NumSamples", sitePairsSize)
            ir.setValue("GDMODEL", "Cutpoint", cutpoint)
            ir.setValue("RESPONSE", "UseWeights", weighting)
            ir.write(outputdir + "/gdm_params.txt")
        } catch (Exception e) {
            System.out.println("Unable to update params file")
            e.printStackTrace(System.out)
        }

        return outputdir + "gdm_params.txt"
    }

    void generateCharts(String outputdir) {

        // Check if there is 'plots' dir. if not create it.
        File plots = new File(outputdir + "/plots/")
        plots.mkdirs()

        // generate the Observed vs. Prediction chart
        generateCharts123(outputdir)
        generateCharts45(outputdir)
    }

    void generateCharts123(String outputdir) {
        try {
            IniReader ir = new IniReader(outputdir + "/gdm_params.txt")
            double intercept = ir.getDoubleValue("GDMODEL", "Intercept")

            // 1. read the ObservedVsPredicted.csv file
            System.out.println("Loading csv data")
            CSVReader csv = new CSVReader(new FileReader(outputdir + "ObservedVsPredicted.csv"))
            List<String[]> rawdata = csv.readAll()
            double[][] dataCht1 = new double[2][rawdata.size() - 1]
            double[][] dataCht2 = new double[2][rawdata.size() - 1]

            // for Chart 1: obs count
            int[] obscount = new int[11]
            for (int i = 0; i < obscount.length; i++) {
                obscount[i] = 0
            }

            System.out.println("populating data")
            for (int i = 1; i < rawdata.size(); i++) {
                String[] row = rawdata.get(i)
                double obs = Double.parseDouble(row[4])
                dataCht1[0][i - 1] = Double.parseDouble(row[6])
                dataCht1[1][i - 1] = obs

                dataCht2[0][i - 1] = Double.parseDouble(row[5]) - intercept
                dataCht2[1][i - 1] = obs

                int obc = (int) Math.round(obs * 10)
                obscount[obc]++
            }

            DefaultXYDataset dataset1 = new DefaultXYDataset()
            dataset1.addSeries("", dataCht1)

            DefaultXYDataset dataset2 = new DefaultXYDataset()
            dataset2.addSeries("", dataCht2)

            DefaultCategoryDataset dataset3 = new DefaultCategoryDataset()
            for (int i = 0; i < obscount.length; i++) {
                String col = "0." + i + "-0." + (i + 1)
                if (i == 10) {
                    col = "0.9-1.0"
                }
                dataset3.addValue(obscount[i] + 100, "col", col)
            }
            generateChartByType("Response Histogram", "Observed Dissimilarity Class", "Number of Site Pairs", dataset3, outputdir, "bar", "resphist")


            XYDotRenderer renderer = new XYDotRenderer()
            //Shape cross = ShapeUtilities.createDiagonalCross(3, 1);
            //renderer.setSeriesShape(0, cross);
            renderer.setDotWidth(3)
            renderer.setDotHeight(3)
            renderer.setSeriesPaint(0, Color.BLACK)


            JFreeChart jChart1 = ChartFactory.createScatterPlot("Observed versus predicted compositional dissimilarity", "Predicted Compositional Dissimilarity", "Observed Compositional Dissimilarity", dataset1, PlotOrientation.VERTICAL, false, false, false)
            jChart1.getTitle().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14))

            XYPlot plot = (XYPlot) jChart1.getPlot()
            plot.setBackgroundPaint(Color.WHITE)
            plot.setDomainZeroBaselineVisible(true)
            plot.setRangeZeroBaselineVisible(true)
            plot.setDomainGridlinesVisible(true)
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY)
            plot.setDomainGridlineStroke(new BasicStroke(0.5F, 0, 1))
            plot.setRangeGridlinesVisible(true)
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY)
            plot.setRangeGridlineStroke(new BasicStroke(0.5F, 0, 1))
            plot.setRenderer(0, renderer)

            NumberAxis domain = (NumberAxis) plot.getDomainAxis()
            domain.setAutoRangeIncludesZero(false)
            domain.setAxisLineVisible(false)
            domain.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            NumberAxis range = (NumberAxis) plot.getRangeAxis()
            range.setAutoRangeIncludesZero(false)
            range.setAxisLineVisible(false)
            range.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            double dMinPred = domain.getRange().getLowerBound()
            double dMaxPred = domain.getRange().getUpperBound()

            double dMinObs = range.getRange().getLowerBound()
            double dMaxObs = range.getRange().getUpperBound()

            System.out.println("1..pred.min.max: " + dMinPred + ", " + dMaxPred)

            int regressionLineSegs = 10
            double dInc = (dMaxPred - dMinPred) / regressionLineSegs
            double[][] dataReg1 = new double[2][regressionLineSegs + 1]
            DefaultXYDataset dsReg1 = new DefaultXYDataset()
            int i = 0
            for (double d = dMinPred; d <= dMaxPred; d += dInc) {
                dataReg1[0][i] = d
                dataReg1[1][i] = d
                i++
            }
            dsReg1.addSeries("", dataReg1)
            XYSplineRenderer regressionRenderer = new XYSplineRenderer()
            regressionRenderer.setBaseSeriesVisibleInLegend(true)
            regressionRenderer.setSeriesPaint(0, Color.RED)
            regressionRenderer.setSeriesStroke(0, new BasicStroke(1.5f))
            regressionRenderer.setBaseShapesVisible(false)
            plot.setDataset(1, dsReg1)
            plot.setRenderer(1, regressionRenderer)

            System.out.println("Writing image....")
            ChartUtilities.saveChartAsPNG(new File(outputdir + "plots/obspredissim.png"), jChart1, 600, 400)

            // For chart 3
            JFreeChart jChart2 = ChartFactory.createScatterPlot("Observed compositional dissimilarity vs predicted ecological distance", "Predicted ecological distance", "Observed Compositional Dissimilarity", dataset2, PlotOrientation.VERTICAL, false, false, false)
            jChart2.getTitle().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14))

            plot = (XYPlot) jChart2.getPlot()
            plot.setBackgroundPaint(Color.WHITE)
            plot.setDomainZeroBaselineVisible(true)
            plot.setRangeZeroBaselineVisible(true)
            plot.setDomainGridlinesVisible(true)
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY)
            plot.setDomainGridlineStroke(new BasicStroke(0.5F, 0, 1))
            plot.setRangeGridlinesVisible(true)
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY)
            plot.setRangeGridlineStroke(new BasicStroke(0.5F, 0, 1))
            plot.setRenderer(0, renderer)

            domain = (NumberAxis) plot.getDomainAxis()
            domain.setAutoRangeIncludesZero(false)
            domain.setAxisLineVisible(false)
            domain.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            range = (NumberAxis) plot.getRangeAxis()
            range.setAutoRangeIncludesZero(false)
            range.setAxisLineVisible(false)
            range.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            dMinPred = domain.getRange().getLowerBound()
            dMaxPred = domain.getRange().getUpperBound()

            dMinObs = range.getRange().getLowerBound()
            dMaxObs = range.getRange().getUpperBound()

            System.out.println("2.pred.min.max: " + dMinPred + ", " + dMaxPred)

            regressionLineSegs = 10
            dInc = (dMaxPred - dMinPred) / regressionLineSegs
            dataReg1 = new double[2][regressionLineSegs + 1]
            dsReg1 = new DefaultXYDataset()
            i = 0
            for (double d = dMinPred; d <= dMaxPred; d += dInc) {
                dataReg1[0][i] = d
                dataReg1[1][i] = (1.0 - Math.exp(-d))
                i++
            }
            dsReg1.addSeries("", dataReg1)
            regressionRenderer.setBaseSeriesVisibleInLegend(true)
            regressionRenderer.setSeriesPaint(0, Color.RED)
            regressionRenderer.setSeriesStroke(0, new BasicStroke(1.5f))
            regressionRenderer.setBaseShapesVisible(false)
            plot.setDataset(1, dsReg1)
            plot.setRenderer(1, regressionRenderer)

            System.out.println("Writing image....")
            ChartUtilities.saveChartAsPNG(new File(outputdir + "plots/dissimdist.png"), jChart2, 600, 400)


        } catch (Exception e) {
            System.out.println("Unable to generate charts 2 and 3:")
            e.printStackTrace(System.out)
        }
    }

    void generateCharts45(String outputdir) {
        try {
            // read the gdm_params.txt, and for each predictor Coeff (1, 2, 3),
            // add them up

            DefaultCategoryDataset dataset = new DefaultCategoryDataset()

            IniReader ir = new IniReader(outputdir + "/gdm_params.txt")
            int numpreds = ir.getIntegerValue("PREDICTORS", "NumPredictors")
            System.out.println("ir.numpreds: " + numpreds)
            for (int i = 1; i < numpreds + 1; i++) {
                double sc = ir.getDoubleValue("PREDICTORS", "CoefSum" + i)

                String predictor = ir.getStringValue("PREDICTORS", "EnvGrid" + i)
                predictor = predictor.substring(predictor.lastIndexOf("/") + 1)

                System.out.println("Adding " + predictor + " coeffient ")
                dataset.addValue(sc, "predictor", predictor)

                String pname = ir.getStringValue("CHARTS", "PredPlotDataName" + i)
                String pdata = ir.getStringValue("CHARTS", "PredPlotDataPath" + i)
                generateChart5(outputdir, pname, pdata)
            }
            generateChartByType("Predictor Histogram", "Predictors", "Coefficient", dataset, outputdir, "bar", "predhist")

        } catch (Exception e) {
            System.out.println("Unable to generate charts 2 and 3:")
            e.printStackTrace(System.out)
        }
    }

    static void generateChart5(String outputdir, String plotName, String plotData) {
        try {

            CSVReader csv = new CSVReader(new FileReader(plotData))
            List<String[]> rawdata = csv.readAll()
            double[][] dataCht = new double[2][rawdata.size() - 1]
            for (int i = 1; i < rawdata.size(); i++) {
                String[] row = rawdata.get(i)
                dataCht[0][i - 1] = Double.parseDouble(row[0])
                dataCht[1][i - 1] = Double.parseDouble(row[1])
            }

            DefaultXYDataset dataset = new DefaultXYDataset()
            dataset.addSeries("", dataCht)

            System.out.println("Setting up jChart for " + plotName)
            //generateChartByType(plotName, plotName, "f("+plotName+")", null, outputdir, "xyline", plotName);
            JFreeChart jChart = ChartFactory.createXYLineChart(plotName, plotName, "f(" + plotName + ")", dataset, PlotOrientation.VERTICAL, false, false, false)

            jChart.getTitle().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14))

            XYPlot plot = (XYPlot) jChart.getPlot()
            plot.setBackgroundPaint(Color.WHITE)
            plot.setDomainZeroBaselineVisible(true)
            plot.setRangeZeroBaselineVisible(true)
            plot.setDomainGridlinesVisible(true)
            plot.setDomainGridlinePaint(Color.LIGHT_GRAY)
            plot.setDomainGridlineStroke(new BasicStroke(0.5F, 0, 1))
            plot.setRangeGridlinesVisible(true)
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY)
            plot.setRangeGridlineStroke(new BasicStroke(0.5F, 0, 1))

            NumberAxis domain = (NumberAxis) plot.getDomainAxis()
            domain.setAutoRangeIncludesZero(false)
            domain.setAxisLineVisible(false)
            domain.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            NumberAxis range = (NumberAxis) plot.getRangeAxis()
            range.setAutoRangeIncludesZero(false)
            range.setAxisLineVisible(false)
            range.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            System.out.println("Writing image....")
            ChartUtilities.saveChartAsPNG(new File(outputdir + "plots/" + plotName + ".png"), jChart, 500, 500)
            ChartUtilities.saveChartAsPNG(new File(outputdir + "plots/" + plotName + "_thumb.png"), jChart, 210, 140)

        } catch (Exception e) {
            System.out.println("Unable to generate charts 2 and 3:")
            e.printStackTrace(System.out)
        }
    }


    static void generateChartByType(String title, String xLabel, String yLabel, Dataset dataset, String outputdir, String type, String filename) throws IOException {
        JFreeChart jChart = null
        if ("line".equalsIgnoreCase(type)) {
            jChart = ChartFactory.createLineChart(title, xLabel, yLabel, (CategoryDataset) dataset, PlotOrientation.VERTICAL, false, false, false)
        } else if ("bar".equalsIgnoreCase(type)) {
            System.out.println("Setting up jChart")
            jChart = ChartFactory.createBarChart(title, xLabel, yLabel, (CategoryDataset) dataset, PlotOrientation.VERTICAL, false, false, false)
            System.out.println("Writing image....")
        } else if ("xyline".equalsIgnoreCase(type)) {
            jChart = ChartFactory.createXYLineChart(title, xLabel, yLabel, (XYDataset) dataset, PlotOrientation.VERTICAL, false, false, false)
        }

        if ("xyline".equalsIgnoreCase(type)) {

            XYPlot plot = (XYPlot) jChart.getPlot()
            plot.setBackgroundPaint(Color.WHITE)
            plot.setRangeZeroBaselineVisible(true)
            plot.setRangeGridlinesVisible(true)
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY)
            plot.setRangeGridlineStroke(new BasicStroke(0.5F, 0, 1))

            NumberAxis domain = (NumberAxis) plot.getDomainAxis()
            domain.setAxisLineVisible(false)
            domain.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))
            domain.setAutoRangeIncludesZero(false)

            NumberAxis range = (NumberAxis) plot.getRangeAxis()
            range.setAutoRangeIncludesZero(false)
            range.setAxisLineVisible(false)
            range.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            //System.out.println("dataset.getColumnCount(): " + dataset.getColumnCount());
            //System.out.println("dataset.getRowCount(): " + dataset.getRowCount());

        } else {

            CategoryPlot plot = (CategoryPlot) jChart.getPlot()
            plot.setBackgroundPaint(Color.WHITE)
            plot.setRangeZeroBaselineVisible(true)
            plot.setRangeGridlinesVisible(true)
            plot.setRangeGridlinePaint(Color.LIGHT_GRAY)
            plot.setRangeGridlineStroke(new BasicStroke(0.5F, 0, 1))

            CategoryAxis domain = (CategoryAxis) plot.getDomainAxis()
            domain.setAxisLineVisible(false)
            domain.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            NumberAxis range = (NumberAxis) plot.getRangeAxis()
            range.setAutoRangeIncludesZero(false)
            range.setAxisLineVisible(false)
            range.setLabelFont(new Font(Font.MONOSPACED, Font.PLAIN, 12))

            //System.out.println("dataset.getColumnCount(): " + dataset.getColumnCount());
            //System.out.println("dataset.getRowCount(): " + dataset.getRowCount());

        }

        jChart.getTitle().setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14))
        ChartUtilities.saveChartAsPNG(new File(outputdir + "plots/" + filename + ".png"), jChart, 900, 500)
    }

    private void generateMetadata(String[] layers, String area, String pid, String outputdir) {
        try {
            int i = 0
            System.out.println("Generating metadata...")
            StringBuilder sbMetadata = new StringBuilder()

            sbMetadata.append("<!doctype html><head><meta charset='utf-8'><title>Genralized Dissimilarity Model</title><meta name='description' content='ALA GDM Metadata'>")
            sbMetadata.append("<style type='text/css'>body{font-family:Verdana,'Lucida Sans';font-size:small;}div#core{display:block;clear:both;margin-bottom:20px;}section{width:95%;margin:0 15px;border-bottom:1px solid #000;}.clearfix:after{content:'.';display:block;clear:both;visibility:hidden;line-height:0;height:0;}.clearfix{display:inline-block;}html[xmlns] .clearfix{display:block;}* html .clearfix{height:1%;}</style>")
            sbMetadata.append("</head><body><div id=wrapper><header><h1>Genralized Dissimilarity Model</h1></header><div id=core class=clearfix><section><p>")
            sbMetadata.append("This GDM model was created Wed Feb 29 20:50:37 EST 2012. Data available in this folder is available for further analysis. </p>")
            sbMetadata.append("<h3>Your options:</h3><ul>")

            sbMetadata.append("<li>Model reference number:").append(pid).append("</li>")
            sbMetadata.append("<li>Assemblage:").append(name).append("</li>")
            sbMetadata.append("<li>Area:").append(area).append("</li>")

            Properties additionalProperties = new Properties()
            File apFile = new File(outputdir + File.separator + "additional_properties.txt")
            if (apFile.exists()) {
                try {
                    additionalProperties.load(new FileReader(apFile))
                } catch (Exception e) {
                }
            }

            sbMetadata.append("<li>Layers: <ul>")
            String images = ""
            for (i = 0; i < layers.length; i++) {
                sbMetadata.append("<li>").append(additionalProperties.getProperty(layers[i], layers[i])).append("</li>")
                images += "<img src='plots/" + layers[i] + ".png'/>"
            }
            sbMetadata.append("</li></ul></li></ul></section>")

            sbMetadata.append("<section><h3>Response Histogram (observed dissimilarity class):</h3><p> The Response Histogram plots the distribution of site pairs within each observed dissimilarity class. The final column in the dissimilarity class > 1 represents the number of site pairs that are totally dissimilar from each other. This chart provides an overview of potential bias in the distribution of the response data. </p><p><img src='plots/resphist.png'/></p></section><section><h3>Observed versus predicted compositional dissimilarity (raw data plot):</h3><p> The 'Raw data' scatter plot presents the Observed vs Predicted degree of compositional dissimilarity for a given model run. Each dot on the chart represents a site-pair. The line represents the perfect 1:1 fit. (Note that the scale and range of values on the x and y axes differ). </p><p> This chart provides a snapshot overview of the degree of scatter in the data. That is, how well the predicted compositional dissimilarity between site pairs matches the actual compositional dissimilarity present in each site pair. </p><p><img src='plots/obspredissim.png'/></p></section><section><h3>Observed compositional dissimilarity vs predicted ecological distance (link function applied to the raw data plot):</h3><p> The 'link function applied' scatter plot presents the Observed compositional dissimilarity vs Predicted ecological distance. Here, the link function has been applied to the predicted compositional dissimilarity to generate the predicted ecological distance. Each dot represents a site-pair. The line represents the perfect 1:1 fit. The scatter of points signifies noise in the relationship between the response and predictor variables. </p><p><img src='plots/dissimdist.png'/></p></section><section><h3>Predictor Histogram:</h3><p> The Predictor Histogram plots the relative contribution (sum of coefficient values) of each environmental gradient layer that is relevant to the model. The sum of coefficient values is a measure of the amount of predicted compositional dissimilarity between site pairs. </p><p> Predictor variables that contribute little to explaining variance in compositional dissimilarity between site pairs have low relative contribution values. Predictor variables that do not make any contribution to explaining variance in compositional dissimilarity between site pairs (i.e., all coefficient values are zero) are not shown. </p><p><img src='plots/predhist.png'/></p></section><section><h3>Fitted Functions:</h3><p> The model output presents the response (compositional turnover) predicted by variation in each predictor. The shape of the predictor is represented by three I-splines, the values of which are defined by the environmental data distribution: min, max and median (i.e., 0, 50 and 100th percentiles). The GDM model estimates the coefficients of the I-splines for each predictor. The coefficient provides an indication of the total amount of compositional turnover correlated with each value at the 0, 50 and 100th percentiles. The sum of these coefficient values is an indicator of the relative importance of each predictor to compositional turnover. </p><p> The coefficients are applied to the ecological distance from the minimum percentile for a predictor. These plots of fitted functions show the sort of monotonic transformations that will take place to a predictor to render it in GDM space. The relative maximum y values (sum of coefficient values) indicate the amount of influence that each predictor makes to the total GDM prediction. </p>" +
                    "<p>" +
                    images +
                    "</p>" +
                    "</section></div><footer><p>&copy; <a href='http://www.ala.org.au/'>Atlas of Living Australia 2012</a></p></footer></div></body></html>")
            sbMetadata.append("")

            File spFile = new File(outputdir + "gdm.html")
            System.out.println("Writing metadata to: " + spFile.getAbsolutePath())
            PrintWriter spWriter
            spWriter = new PrintWriter(new BufferedWriter(new FileWriter(spFile)))
            spWriter.write(sbMetadata.toString())
            spWriter.close()
        } catch (IOException ex) {
            Logger.getLogger(GDMWSController.class.getName()).log(Level.SEVERE, null, ex)
        }
    }
}
