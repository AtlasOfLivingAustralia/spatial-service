package au.org.ala.spatial.layers

import au.org.ala.spatial.intersect.SimpleShapeFile

import java.nio.ByteBuffer
import java.nio.ByteOrder

//@CompileStatic
class EndemismLayerGenerator extends CalculatedLayerGenerator {

    EndemismLayerGenerator(BigDecimal resolution, File latLonFile, File shapefileMask, String field) throws IOException {
        super(resolution)

        SimpleShapeFile ssf = null

        if (shapefileMask != null) {
            ssf = new SimpleShapeFile(shapefileMask.getPath(), field)
        }

        readCoordinateSpeciesFlatFile(latLonFile, false, false, true, true, ssf)
    }


    @Override
    protected float handleCell(Map.Entry<BigDecimal, BigDecimal> coordPair, float maxValue, PrintWriter ascPrintWriter, BufferedOutputStream divaOutputStream) throws IOException {
        if (_cellSpecies.containsKey(coordPair)) {
            // Calculate endemism value for the cell. Sum (1 / total
            // species cell count) for each species that occurs in
            // the cell. Then divide by the number of species that
            // occur in the cell.

            float endemicityValue = 0
            BitSet speciesLsids = _cellSpecies.get(coordPair)
            for (int i = 0; i < speciesLsids.length(); i++) {
                if (speciesLsids.get(i)) {
                    int speciesCellCount = _speciesCellCounts.get(i)
                    endemicityValue += 1.0 / speciesCellCount
                }
            }
            endemicityValue = endemicityValue / speciesLsids.cardinality()

            float newMaxValue = 0
            if (maxValue < endemicityValue) {
                newMaxValue = endemicityValue
            } else {
                newMaxValue = maxValue
            }

            ascPrintWriter.print(endemicityValue)

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE])
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.putFloat(endemicityValue)
            divaOutputStream.write(bb.array())

            return newMaxValue
        } else {
            // No species occurrences in this cell. Endemism value
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
