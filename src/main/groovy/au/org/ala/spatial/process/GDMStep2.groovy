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
import au.org.ala.layers.util.Diva2bil
import au.org.ala.spatial.Util
import groovy.util.logging.Slf4j
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

@Slf4j
class GDMStep2 extends SlaveProcess {

    void start() {

        // update GDM
        slaveService.getFile('/modelling/gdm/DoGdm')

        def distance = task.input.distance
        def weighting = task.input.weighting
        def sitePairsSize = task.input.sitePairsSize
        def cutpoint = task.input.cutpoint

        def taskId = task.input.gdmId

        // copy previous task
        slaveService.getFile('/public/' + taskId)
        FileUtils.copyDirectory(new File(grailsApplication.config.data.dir + '/public/' + taskId), new File(getTaskPath()))
        Map m = [:]
        m['/' + taskId + '/'] = '/' + task.id + '/'
        Util.replaceTextInFile(getTaskPath() + 'gdm_params.txt', m)

        // 5. build parameters files for GDM
        String params = updateParamfile(cutpoint, distance ? "1" : "0", weighting, "1", sitePairsSize, getTaskPath())

        // 6. run GDM
        runCmd([grailsApplication.config.gdm.dir, "-g2", params] as String[], true)

        // 7. process params file

        // 7.1 generate/display charts
        generateCharts(getTaskPath())

        // 7.2 generate/display transform grid
        Iterator<File> files = FileUtils.iterateFiles(new File(getTaskPath()), ["grd"] as String[], false)
        while (files.hasNext()) {
            File f = files.next()
            if (f.getName().startsWith("domain")) {
                continue
            }
            String lyr = f.getName().substring(0, f.getName().length() - 4)

            Diva2bil.diva2bil(getTaskPath() + lyr, getTaskPath() + lyr)

            def cmd = [grailsApplication.config.gdal.dir + "/gdal_translate", "-of", "GTiff", "-a_srs", "EPSG:4326",
                       "-co", "COMPRESS=DEFLATE", "-co", "TILED=YES", "-co", "BIGTIFF=IF_SAFER",
                       getTaskPath() + lyr + ".bil", getTaskPath() + 'gdm_' + lyr + "_" + task.id + ".tif"]
            task.message = "bil > tif"
            runCmd(cmd as String[], true)

            File target = new File(grailsApplication.config.data.dir + '/layer/gdm_' + lyr + "_" + task.id + ".tif")
            if (target.exists()) target.delete()
            FileUtils.copyFile(new File(getTaskPath() + "gdm_" + lyr + "_" + task.id + ".tif"), target)
            addOutput("layers", "/layer/gdm_" + lyr + "_" + task.id + ".tif", true)

            File sld = new File(grailsApplication.config.data.dir + '/layer/gdm_' + lyr + "_" + task.id + ".sld")
            def resource = GDMStep2.class.getResource("/geoserver/alastyles.sld")
            FileUtils.writeStringToFile(sld, resource.text)
            addOutput("layers", "/layer/gdm_" + lyr + "_" + task.id + ".sld", true)
        }

        for (File f : new File(getTaskPath()).listFiles()) {
            if (!f.getName().endsWith("json") && !f.getName().endsWith("html") && f.isFile()) {
                addOutput("download", f.getName(), true)
            }
        }

        addOutput('metadata', 'gdm.html')

        for (File f : new File(getTaskPath() + "/plots").listFiles()) {
            if (f.isFile()) {
                addOutput("download", "plots/" + f.getName(), true)
            }
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
}
