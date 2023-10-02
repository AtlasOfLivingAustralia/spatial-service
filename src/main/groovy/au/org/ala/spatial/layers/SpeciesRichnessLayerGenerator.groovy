package au.org.ala.spatial.layers


import java.nio.ByteBuffer
import java.nio.ByteOrder

//@CompileStatic
class SpeciesRichnessLayerGenerator extends CalculatedLayerGenerator {

    SpeciesRichnessLayerGenerator(BigDecimal resolution, File cellSpeciesFile) throws IOException {
        super(resolution)
        readCoordinateSpeciesFlatFile(cellSpeciesFile, false, false, true, false)
    }


    @Override
    protected float handleCell(Map.Entry<BigDecimal, BigDecimal> coordPair, float maxValue, PrintWriter ascPrintWriter, BufferedOutputStream divaOutputStream) throws IOException {
        if (_cellSpecies.containsKey(coordPair)) {
            // Write species richness value for the cell. This is the number of
            // species for which there are occurrences recorded in the cell.

            BitSet speciesLsids = _cellSpecies.get(coordPair)
            int cellSpeciesCount = speciesLsids.cardinality()

            float newMaxValue = 0
            if (maxValue < cellSpeciesCount) {
                newMaxValue = cellSpeciesCount
            } else {
                newMaxValue = maxValue
            }

            ascPrintWriter.print(cellSpeciesCount)

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE])
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(cellSpeciesCount)
            divaOutputStream.write(bb.array())

            return newMaxValue
        } else {
            // No species occurrences in this cell. Species richness value
            // is zero.
            ascPrintWriter.print("0")

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE])
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(0)
            divaOutputStream.write(bb.array())
            return maxValue
        }
    }
}
