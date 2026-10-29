package au.org.ala.spatial.scatterplot

import au.org.ala.spatial.legend.LegendEqualArea
import au.org.ala.spatial.legend.LegendObject
import spock.lang.Specification

import java.awt.Color

/**
 * BiocacheLegendObject: the legends biocache returns for a scatterplot, as CSV or as JSON.
 */
class BiocacheLegendObjectSpec extends Specification {

    static final String CSV_LEGEND = 'name,red,green,blue,count\n1990,255,0,0,10\n2000,0,0,255,5\n2010,0,0,255,7\n'

    def "a biocache CSV legend gives the colour of each value and merges trailing classes with the same colour"() {
        when:
        BiocacheLegendObject lo = new BiocacheLegendObject('coordinate_uncertainty', CSV_LEGEND)

        then:
        lo.getColour('1990') == new Color(255, 0, 0).RGB
        lo.getColour(2000f) == new Color(0, 0, 255).RGB
        lo.getColour('1234') == LegendObject.DEFAULT_COLOUR
        lo.table == 'name,red,green,blue,count\n"1990",255,0,0,10\n2 more,0,0,255,12'
    }

    def "a biocache JSON legend gives the colour of each category"() {
        given:
        String json = '[{"name":"Birds","red":255,"green":0,"blue":0,"count":10},{"name":"Mammals","red":0,"green":128,"blue":0,"count":4}]'

        when:
        BiocacheLegendObject lo = new BiocacheLegendObject('species_group', json)

        then:
        lo.getColour('Birds') == new Color(255, 0, 0).RGB
        lo.getColour('Mammals') == new Color(0, 128, 0).RGB
        lo.table == 'name,red,green,blue,count\n"Birds",255,0,0,10\n"Mammals",0,128,0,4'
    }

    /** Category names in a CSV legend are parsed with Double.parseDouble without a try/catch. */
    def "a biocache CSV legend of categories that are not numbers can be read"() {
        when:
        BiocacheLegendObject lo = new BiocacheLegendObject('species_group', 'name,red,green,blue,count\nBirds,255,0,0,10\n')

        then:
        lo.getColour('Birds') == new Color(255, 0, 0).RGB
    }

    /** getMinMax returns null when it found values ("if (!first) return null") and [0, 0] when it did not. */
    def "the min and max of a numeric biocache legend are its smallest and largest values"() {
        when:
        BiocacheLegendObject lo = new BiocacheLegendObject('coordinate_uncertainty', CSV_LEGEND)

        then:
        lo.minMax as List == [1990f, 2010f]
    }

    def "a numeric biocache legend can be turned into an equal area legend of its values"() {
        when:
        LegendObject lo = new BiocacheLegendObject('coordinate_uncertainty', CSV_LEGEND).asIntegerLegend

        then:
        lo.numericLegend instanceof LegendEqualArea
        lo.numericLegend.numberOfRecords == 22
        lo.numericLegend.min == 1990f
        lo.numericLegend.max == 2010f
    }
}
