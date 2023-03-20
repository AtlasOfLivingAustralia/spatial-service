package au.org.ala.spatial.layers

import au.org.ala.spatial.intersect.SimpleShapeFile
import com.opencsv.CSVReader

import java.math.RoundingMode

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
//@CompileStatic
abstract class CalculatedLayerGenerator {

    protected Map<Integer, Integer> _speciesCellCounts
    protected Map<Map.Entry<BigDecimal, BigDecimal>, BitSet> _cellSpecies
    protected Map<Map.Entry<BigDecimal, BigDecimal>, Long> _cellOccurrenceCounts
    protected Map<Map.Entry<BigDecimal, BigDecimal>, Map<Integer, Integer>> _cellSpeciesOccurrenceCounts
    protected Map<String, Integer> _speciesIndex
    protected BigDecimal _resolution

    CalculatedLayerGenerator(BigDecimal resolution) throws IOException {
        _resolution = resolution
    }

    protected void readCoordinateSpeciesFlatFile(File coordinateSpeciesFlatFile, Boolean cellSpeciesOccurrenceCounts, Boolean cellOccurrenceCounts, Boolean cellSpeciesCounts, Boolean speciesCellCounts) throws IOException {
        readCoordinateSpeciesFlatFile(coordinateSpeciesFlatFile, cellSpeciesOccurrenceCounts, cellOccurrenceCounts, cellSpeciesCounts, speciesCellCounts, null)
    }

    // Read cell species lists into memory
    protected void readCoordinateSpeciesFlatFile(File coordinateSpeciesFlatFile, Boolean cellSpeciesOccurrenceCounts, Boolean cellOccurrenceCounts, Boolean cellSpeciesCounts, Boolean speciesCellCounts, SimpleShapeFile shapefileMask) throws IOException {
        _cellSpeciesOccurrenceCounts = new HashMap<Map.Entry<BigDecimal, BigDecimal>, Map<Integer, Integer>>()
        _speciesIndex = new HashMap<String, Integer>()
        _cellOccurrenceCounts = new HashMap<Map.Entry<BigDecimal, BigDecimal>, Long>()
        _cellSpecies = new HashMap()
        _speciesCellCounts = new HashMap()

        CSVReader reader = new CSVReader(new BufferedReader(new FileReader(coordinateSpeciesFlatFile)), '\t' as char, '|' as char
        )

        int scale = 2
        if (_resolution.doubleValue() == 1.0d) {
            scale = 0
        }
        if (_resolution.doubleValue() == 0.1d) {
            scale = 1
        }
        if (_resolution.doubleValue() == 0.01d) {
            scale = 2
        }

        String[] line
        int rowCount = 0
        while ((line = reader.readNext()) != null) {
            rowCount++
            if (rowCount % 500000 == 0) {
                System.out.println("reading row: " + rowCount)
            }
            try {
                BigDecimal latitude = new BigDecimal(line[0])
                BigDecimal longitude = new BigDecimal(line[1])

                boolean skip = false

                if (shapefileMask != null) {
                    skip = shapefileMask.intersectInt(longitude.doubleValue(), latitude.doubleValue()) < 0
                }

                if (!skip) {
                    Map.Entry<BigDecimal, BigDecimal> p = new AbstractMap.SimpleEntry<BigDecimal, BigDecimal>(
                            latitude.setScale(scale, RoundingMode.CEILING),
                            longitude.setScale(scale, RoundingMode.FLOOR))

                    if (cellOccurrenceCounts) {
                        Long count = _cellOccurrenceCounts.get(p)
                        if (count == null) {
                            count = 0L
                        }
                        count++
                        _cellOccurrenceCounts.put(p, count)
                    }

                    //scientificName.p, taxonConceptId.p
                    String species = line[2] + '|' + line[3]

                    if (cellSpeciesOccurrenceCounts || speciesCellCounts || cellSpeciesCounts) {
                        Integer sidx = _speciesIndex.get(species)
                        if (sidx == null) {
                            sidx = _speciesIndex.size()
                            _speciesIndex.put(species, sidx)
                        }

                        if (cellSpeciesOccurrenceCounts) {
                            Map<Integer, Integer> m = _cellSpeciesOccurrenceCounts.get(p)
                            if (m == null) {
                                m = new HashMap<Integer, Integer>()
                            }

                            Integer i = m.get(sidx)
                            if (i == null) {
                                i = 0
                            }
                            m.put(sidx, i + 1)

                            _cellSpeciesOccurrenceCounts.put(p, m)
                        }
                        if (cellSpeciesCounts || speciesCellCounts) {
                            BitSet m = _cellSpecies.get(p)
                            if (m == null) {
                                m = new BitSet()
                            }
                            m.set(sidx)

                            _cellSpecies.put(p, m)
                        }
                    }
                }
            } catch (Exception ignored) {

            }
        }

        if (speciesCellCounts) {
            for (BitSet m : (_cellSpecies.values() as List<BitSet>)) {
                for (int i = 0; i < m.length(); i++) {
                    if (m.get(i)) {
                        Integer count = _speciesCellCounts.get(i) as Integer
                        if (count == null) {
                            count = 0
                        }
                        count++
                        _speciesCellCounts.put(i, count)
                    }
                }
            }
        }

        reader.close()
    }


    void writeGrid(File outputFileDirectory, String outputFileNamePrefix) {

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

            for (BigDecimal lat = maxLatitude; lat.compareTo(minLatitude) == 1; lat = lat.subtract(_resolution)) {
                // a row for each _resolution unit of latitude
                for (BigDecimal lon = minLongitude; lon.compareTo(maxLongitude) == -1; lon = lon.add(_resolution)) {
                    // a column for each _resolution unit of longitude
                    Map.Entry<BigDecimal, BigDecimal> coordPair = new AbstractMap.SimpleEntry<BigDecimal, BigDecimal>(lat.setScale(scale, RoundingMode.FLOOR), lon.setScale(scale, RoundingMode.FLOOR));

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
