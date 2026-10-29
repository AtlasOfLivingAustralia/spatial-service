package au.org.ala.spatial.grid

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import javax.xml.parsers.DocumentBuilderFactory

/**
 * SLD written for the classes of contextual grid layers (GridClassBuilder.exportSLD).
 */
class GridClassBuilderSpec extends Specification {

    @TempDir
    File tmp

    static List<Map> colorMapEntries(File sld) {
        def doc = DocumentBuilderFactory.newInstance().with { namespaceAware = true; it }.newDocumentBuilder().parse(sld)
        def nodes = doc.getElementsByTagNameNS('http://www.opengis.net/sld', 'ColorMapEntry')
        (0..<nodes.length).collect { i ->
            def n = nodes.item(i)
            [color: n.getAttribute('color'), quantity: n.getAttribute('quantity'), label: n.getAttribute('label')]
        }
    }

    @Unroll
    def "getHexColour(#colour) is '#expected'"() {
        expect:
        GridClassBuilder.getHexColour(colour) == expected

        where:
        colour                  | expected
        0x003366CC              | '3366cc'
        0x000000FF              | '0000ff'
        0xFF102030 as int       | '102030'
        0                       | '000000'
    }

    def "exportSLD writes one entry per class, sorted by label, with quantity max + 1"() {
        given:
        File sld = new File(tmp, 'classes.sld')

        when:
        GridClassBuilder.exportSLD(sld.path, 'layer', [3, 7, 9] as ArrayList<Integer>, ['Wetland', 'Forest', 'Grassland'] as ArrayList<String>)
        List<Map> entries = colorMapEntries(sld)

        then:
        entries.size() == 4
        entries[0].quantity == '1'
        entries.tail()*.label == ['Forest', 'Grassland', 'Wetland']
        entries.tail()*.quantity == ['8.0', '10.0', '4.0']
        entries.tail()*.color == ['#' + GridClassBuilder.getHexColour(GridClassBuilder.colours[1]),
                                  '#' + GridClassBuilder.getHexColour(GridClassBuilder.colours[2]),
                                  '#' + GridClassBuilder.getHexColour(GridClassBuilder.colours[0])]
    }

    /** Class labels come from layer attribute values and are written into the XML without escaping. */
    def "exportSLD writes labels with XML special characters as a valid SLD"() {
        given:
        File sld = new File(tmp, 'escaped.sld')
        List<String> labels = ['Forests & Woodlands', 'Height < 2 m', 'So called "dunes"']

        when:
        GridClassBuilder.exportSLD(sld.path, 'layer', [1, 2, 3] as ArrayList<Integer>, labels as ArrayList<String>)

        then:
        colorMapEntries(sld).tail()*.label as Set == labels as Set
    }
}
