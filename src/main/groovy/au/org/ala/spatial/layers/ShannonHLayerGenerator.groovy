package au.org.ala.spatial.layers


import java.nio.ByteBuffer
import java.nio.ByteOrder

//@CompileStatic
class ShannonHLayerGenerator extends CalculatedLayerGenerator {

    ShannonHLayerGenerator(BigDecimal resolution, File coordinateSpeciesFlatFile) throws IOException {
        super(resolution)
        readCoordinateSpeciesFlatFile(coordinateSpeciesFlatFile, true, false, false, false)
    }

    @Override
    protected float handleCell(Map.Entry<BigDecimal, BigDecimal> coordPair, float maxValue, PrintWriter ascPrintWriter, BufferedOutputStream divaOutputStream) throws IOException {
        if (_cellSpeciesOccurrenceCounts.containsKey(coordPair)) {
            // Calculate shannon H value for the cell. -1 * Sum[i=1-n] (pi * ln (pi))
            // where i is each species in the cell
            // and pi is number of occurrences of species i / total number of occurrences in the cell

            Map<Integer, Integer> m = _cellSpeciesOccurrenceCounts.get(coordPair)

            int totalOccurrences = 0
            for (Integer i : m.values()) {
                totalOccurrences += i
            }

            double sum = 0

            for (Integer i : m.values()) {
                double p = i / (double) totalOccurrences
                sum += p * Math.log(p)
            }

            sum *= -1

            ascPrintWriter.print((float) sum)

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE])
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putFloat((float) sum)
            divaOutputStream.write(bb.array())

            return maxValue < sum ? (float) sum : maxValue
        } else {
            // No species occurrences in this cell.
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
