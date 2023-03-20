package au.org.ala.spatial.layers


import java.nio.ByteBuffer
import java.nio.ByteOrder

//@CompileStatic
class OccurrenceDensityLayerGenerator extends CalculatedLayerGenerator {

    OccurrenceDensityLayerGenerator(BigDecimal resolution, File cellOccurrenceCountsFile) throws IOException {
        super(resolution)
        readCoordinateSpeciesFlatFile(cellOccurrenceCountsFile, false, true, false, false)
    }

    @Override
    protected float handleCell(Map.Entry<BigDecimal, BigDecimal> coordPair, float maxValue, PrintWriter ascPrintWriter, BufferedOutputStream divaOutputStream) throws IOException {
        if (_cellOccurrenceCounts.containsKey(coordPair)) {
            // Write species richness value for the cell. This is the number of
            // occurrence records in the cell.

            long cellOccurrenceCount = _cellOccurrenceCounts.get(coordPair)

            float newMaxValue = 0
            if (maxValue < cellOccurrenceCount) {
                newMaxValue = cellOccurrenceCount
            } else {
                newMaxValue = maxValue
            }

            ascPrintWriter.print(cellOccurrenceCount)

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE])
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(cellOccurrenceCount)
            divaOutputStream.write(bb.array())

            return newMaxValue
        } else {
            // No species occurrences in this cell. Occurrence density value
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
