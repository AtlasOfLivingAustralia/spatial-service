package au.org.ala.spatial.intersect

import spock.lang.Specification
import spock.lang.TempDir

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
}
