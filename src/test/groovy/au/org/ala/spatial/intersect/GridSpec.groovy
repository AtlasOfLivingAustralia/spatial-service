package au.org.ala.spatial.intersect

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

class GridSpec extends Specification {

    @TempDir
    Path tmp

    static String fixture() {
        new File(GridSpec.getResource('/resources/dataDirLayerCreation/uploads/1/relief_ave.grd').toURI()).path - '.grd'
    }

    void "reads the header of a diva grid"() {
        when:
        Grid grid = new Grid(fixture())

        then:
        grid.ncols == 740
        grid.nrows == 340
        grid.xmin == 142.61d
        grid.xmax == 150.01d
        grid.ymin == -43.77d
        grid.ymax == -40.37d
        grid.xres == 0.01d
        grid.yres == 0.01d
        grid.datatype == 'FLOAT'
        grid.minval == 0d
        Math.abs(grid.maxval - 2211.333251953125d) < 1e-6
    }

    void "points outside the grid have no value"() {
        when:
        float[] values = new Grid(fixture()).getValues([[0d, 0d], [151d, -42d]] as double[][])

        then:
        values.length == 2
        Float.isNaN(values[0])
        Float.isNaN(values[1])
    }

    void "points inside the grid are within the grid range"() {
        given:
        Grid grid = new Grid(fixture())

        when:
        // Ben Lomond, Tasmania
        float[] values = grid.getValues([[147.66d, -41.55d]] as double[][])

        then:
        !Float.isNaN(values[0])
        values[0] >= grid.minval
        values[0] <= grid.maxval
    }

    void "the whole grid can be loaded"() {
        when:
        float[] data = new Grid(fixture()).getGrid()

        then:
        data.length == 740 * 340
    }

    void "a written grid can be read back"() {
        given:
        String name = tmp.resolve('written').toString()
        // rows from north to south
        float[] data = [1f, 2f, 3f,
                        4f, 5f, Float.NaN] as float[]

        when:
        new Grid().writeGrid(name, data, 0, 0, 3, 2, 1, 1, 2, 3)
        Grid grid = new Grid(name)

        then:
        grid.ncols == 3
        grid.nrows == 2
        grid.xmin == 0d
        grid.ymax == 2d
        grid.minval == 1d
        grid.maxval == 5d

        when:
        float[] values = grid.getValues([[0.5d, 1.5d], [1.5d, 0.5d], [2.5d, 0.5d], [10d, 10d]] as double[][])

        then:
        values[0] == 1f
        values[1] == 5f
        Float.isNaN(values[2])
        Float.isNaN(values[3])
    }
    void "the header keeps the extents of a grid whose edges are not on a 0.01 degree boundary"() {
        given: 'a 30 arc second grid'
        String name = tmp.resolve('g30').toString()
        double res = 1 / 120d
        double xmin = 112.9041667
        double ymin = -43.7458333
        Locale previous = Locale.default
        Locale.default = Locale.GERMANY

        when:
        new Grid().writeHeader(name, xmin, ymin, xmin + res * 100, ymin + res * 100, res, res, 100, 100, 0, 10)
        new File(name + '.gri').bytes = new byte[100 * 100 * 4]
        String header = new File(name + '.grd').text
        Grid grid = new Grid(name)

        then:
        !header.contains(',')
        Math.abs(grid.xmin - xmin) < 1e-9
        Math.abs(grid.ymin - ymin) < 1e-9
        Math.abs(grid.xmax - (xmin + res * 100)) < 1e-9
        Math.abs(grid.ymax - (ymin + res * 100)) < 1e-9

        cleanup:
        Locale.default = previous
    }

    void "units '1/N unit' divide the stored values by N"() {
        given: 'one cell that stores 250 with units of tenths of a degree'
        String name = tmp.resolve('tenths').toString()
        new Grid().writeHeader(name, 0, 0, 0.5, 0.5, 0.5, 0.5, 1, 1, 250, 250)
        File grd = new File(name + '.grd')
        grd.text = grd.text + '\r\nUnits=1/10 degC'
        new File(name + '.gri').bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(250f).array()

        when:
        Grid grid = new Grid(name)

        then:
        grid.units == 'degC'
        grid.getValues([[0.25d, 0.25d]] as double[][]) == [25f] as float[]
    }
}
