package au.org.ala.spatial.analysis.layers;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Generates a layer using species cell count and cell species list data
 * generated from the biocache-store. The layer is generated for the entire
 * globe (latitude -90 to 90, longitude -180 to 180)
 * <p/>
 * Layer data is output in both ESRI ASCII grid format (.asc) and in DIVA grid
 * format (.gri/.grd). -9999 is used as the no data value.
 *
 * @author ChrisF
 */
public abstract class CalculatedLayerGenerator {

    protected Map<String, Integer> _speciesCellCounts;
    protected Map<Map.Entry<BigDecimal, BigDecimal>, List<String>> _cellSpecies;
    protected Map<Map.Entry<BigDecimal, BigDecimal>, Long> _cellOccurrenceCounts;
    protected Map<Map.Entry<BigDecimal, BigDecimal>, Map<Integer, Integer>> _cellSpeciesOccurrenceCounts;
    protected Map<String, Integer> _cellSpeciesOccurrenceCountsSpeciesIndex;
    protected BigDecimal _resolution;

    public CalculatedLayerGenerator(BigDecimal resolution) throws IOException {
        _resolution = resolution;
    }

    // read species cell counts into memory
    protected void readSpeciesCellCounts(File speciesCellCountFile) throws IOException {
        _speciesCellCounts = new HashMap<String, Integer>();

        List<String> speciesCellCountLines = FileUtils.readLines(speciesCellCountFile);
        for (String line : speciesCellCountLines) {
            String[] tokens = line.split(",");
            String speciesLsid = tokens[0];
            int cellCount = Integer.parseInt(tokens[1]);
            _speciesCellCounts.put(speciesLsid, cellCount);
        }
    }

    // Read cell species lists into memory
    protected void readCellSpeciesLists(File cellSpeciesFile) throws IOException {
        _cellSpecies = new HashMap<Map.Entry<BigDecimal, BigDecimal>, List<String>>();

        List<String> cellSpeciesLines = FileUtils.readLines(cellSpeciesFile);
        for (String line : cellSpeciesLines) {
            String[] tokens = line.split(",");
            BigDecimal latitude = new BigDecimal(tokens[0]).setScale(2, BigDecimal.ROUND_UNNECESSARY);
            BigDecimal longitude = new BigDecimal(tokens[1]).setScale(2, BigDecimal.ROUND_UNNECESSARY);
            List<String> speciesLsids = Arrays.asList(Arrays.copyOfRange(tokens, 2, tokens.length));
            _cellSpecies.put(new AbstractMap.SimpleEntry<BigDecimal, BigDecimal>(latitude, longitude), speciesLsids);
        }
    }

    // Read cell species lists into memory
    protected void readCellOccurrenceCounts(File cellOccurrenceCountsFile) throws IOException {
        _cellOccurrenceCounts = new HashMap<Map.Entry<BigDecimal, BigDecimal>, Long>();

        List<String> cellOccurrenceCountsLines = FileUtils.readLines(cellOccurrenceCountsFile);
        for (String line : cellOccurrenceCountsLines) {
            String[] tokens = line.split(",");
            BigDecimal latitude = new BigDecimal(tokens[0]).setScale(2, BigDecimal.ROUND_UNNECESSARY);
            BigDecimal longitude = new BigDecimal(tokens[1]).setScale(2, BigDecimal.ROUND_UNNECESSARY);
            long cellOccurrencesCount = Long.parseLong(tokens[2]);
            _cellOccurrenceCounts.put(new AbstractMap.SimpleEntry<BigDecimal, BigDecimal>(latitude, longitude), cellOccurrencesCount);
        }
    }

    // Read cell species lists into memory
    protected void readCoordinateSpeciesFlatFile(File coordinateSpeciesFlatFile) throws IOException {
        _cellSpeciesOccurrenceCounts = new HashMap<Map.Entry<BigDecimal, BigDecimal>, Map<Integer, Integer>>();
        _cellSpeciesOccurrenceCountsSpeciesIndex = new HashMap<String, Integer>();

        CSVReader reader = new CSVReader(new BufferedReader(new FileReader(coordinateSpeciesFlatFile)));

        int scale = 2;
        if (_resolution.doubleValue() == 1.0) {
            scale = 0;
        }
        if (_resolution.doubleValue() == 0.1) {
            scale = 1;
        }
        if (_resolution.doubleValue() == 0.01) {
            scale = 2;
        }

        String[] line;
        int rowCount = 0;
        while ((line = reader.readNext()) != null) {
            rowCount++;
            if (rowCount % 500000 == 0) {
                System.out.println("reading row: " + rowCount);
            }
            try {
                BigDecimal latitude = new BigDecimal(line[0]).setScale(2, scale);
                BigDecimal longitude = new BigDecimal(line[1]).setScale(2, scale);
                Map.Entry<BigDecimal, BigDecimal> p = new AbstractMap.SimpleEntry<BigDecimal, BigDecimal>(latitude, longitude);

                //scientificName.p, taxonConceptId.p
                String species = line[2] + '|' + line[3];
                Integer sidx = _cellSpeciesOccurrenceCountsSpeciesIndex.get(species);
                if (sidx == null) {
                    sidx = _cellSpeciesOccurrenceCountsSpeciesIndex.size();
                    _cellSpeciesOccurrenceCountsSpeciesIndex.put(species, sidx);
                }

                Map<Integer, Integer> m = _cellSpeciesOccurrenceCounts.get(p);
                if (m == null) {
                    m = new HashMap<Integer, Integer>();
                }

                Integer i = m.get(sidx);
                if (i == null) {
                    i = 0;
                }
                m.put(sidx, i + 1);

                _cellSpeciesOccurrenceCounts.put(p, m);
            } catch (NumberFormatException e) {
                //ignore parse errors
            }
        }

        reader.close();
    }

    protected int calculateNumberOfRows() {
        return (int) (180 / _resolution.floatValue());
    }

    protected int calculateNumberOfColumns() {
        return (int) (360 / _resolution.floatValue());
    }

    public void writeGrid(File outputFileDirectory, String outputFileNamePrefix) {

        try {
            // .asc output (ESRI ASCII grid)
            FileWriter ascFileWriter = new FileWriter(new File(outputFileDirectory, outputFileNamePrefix + ".asc"));
            PrintWriter ascPrintWriter = new PrintWriter(ascFileWriter);

            // DIVA output
            BufferedOutputStream divaOutputStream = new BufferedOutputStream(new FileOutputStream(new File(outputFileDirectory, outputFileNamePrefix + ".gri")));

            int numRows = calculateNumberOfRows();
            int numColumns = calculateNumberOfColumns();

            // Write header for .asc output
            ascPrintWriter.println("ncols " + Integer.toString(numColumns));
            ascPrintWriter.println("nrows " + Integer.toString(numRows));
            ascPrintWriter.println("xllcorner -180.0");
            ascPrintWriter.println("yllcorner -90.0");
            ascPrintWriter.println("cellsize " + _resolution.toString());
            ascPrintWriter.println("NODATA_value -9999");

            // Generate layer for the entire globe.
            BigDecimal maxLatitude = new BigDecimal("90.00");
            BigDecimal minLatitude = new BigDecimal("-90.00");
            BigDecimal minLongitude = new BigDecimal("-180.00");
            BigDecimal maxLongitude = new BigDecimal("180.00");

            float maxValue = Float.NEGATIVE_INFINITY;

            for (BigDecimal lat = maxLatitude; lat.compareTo(minLatitude) == 1; lat = lat.subtract(_resolution)) {
                // a row for each _resolution unit of latitude
                // System.out.println(lat.doubleValue());
                for (BigDecimal lon = minLongitude; lon.compareTo(maxLongitude) == -1; lon = lon.add(_resolution)) {
                    // a column for each _resolution unit of longitude
                    Map.Entry<BigDecimal, BigDecimal> coordPair = new AbstractMap.SimpleEntry<BigDecimal, BigDecimal>(lat, lon);

                    maxValue = handleCell(coordPair, maxValue, ascPrintWriter, divaOutputStream);

                    if (lon.compareTo(maxLongitude) != 0) {
                        ascPrintWriter.print(" ");
                    }
                }
                ascPrintWriter.println();
            }

            ascPrintWriter.flush();
            ascPrintWriter.close();

            divaOutputStream.flush();
            divaOutputStream.close();

            // Write header file for DIVA output
            DensityLayers.writeHeader(new File(outputFileDirectory, outputFileNamePrefix + ".grd").getAbsolutePath(), _resolution.doubleValue(), numRows, numColumns, -180, -90, 180, 90, 0, maxValue,
                    -9999);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Handles the cell as defined by the coordiate pair, writes necessary
     * changes to the ascPrintWriter and divaOutputStream
     *
     * @param coordPair        the coordinate pair that defines the cell
     * @param maxValue         the current maximum value for the generated raster layer
     * @param ascPrintWriter   PrintWriter for writing the asc grid
     * @param divaOutputStream output stream for writing the diva grid
     * @return the new maxValue, this will equal to or greater than the maxValue
     * supplied to the method.
     */
    protected abstract float handleCell(Map.Entry<BigDecimal, BigDecimal> coordPair, float maxValue, PrintWriter ascPrintWriter, BufferedOutputStream divaOutputStream) throws IOException;

}
