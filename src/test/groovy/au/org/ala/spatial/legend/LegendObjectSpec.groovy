package au.org.ala.spatial.legend

import com.opencsv.CSVReader
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Legends of occurrence attributes: QueryField (the values of one field) and
 * LegendBuilder/LegendObject (numeric or categorical colours and the CSV legend table).
 */
class LegendObjectSpec extends Specification {

    static final int WHITE = 0xFFFFFFFF as int

    static QueryField field(String name, List<String> values, QueryField.FieldType type = QueryField.FieldType.AUTO) {
        QueryField qf = new QueryField(name, type)
        values.each { qf.add(it) }
        qf.store()
        qf
    }

    static List<String[]> parseCsv(String csv) {
        new CSVReader(new StringReader(csv)).withCloseable { it.readAll() }
    }

    // ---- QueryField ----

    @Unroll
    def "QueryField detects #expected for values #values"() {
        expect:
        field('f', values).fieldType == expected

        where:
        values                          | expected
        ['1', '2', '-3']                | QueryField.FieldType.INT
        ['1', '2', '3000000000']        | QueryField.FieldType.LONG
        ['1.5', '2', '-0.25']           | QueryField.FieldType.FLOAT
        ['Birds', 'Mammals', '1']       | QueryField.FieldType.STRING
        ['1', 'n/a', '3', '']           | QueryField.FieldType.INT
    }

    def "QueryField stores missing integer values as n/a"() {
        when:
        QueryField qf = field('year', ['1990', 'n/a', '2.7', 'x'], QueryField.FieldType.INT)

        then:
        qf.getInt(0) == 1990
        qf.getAsString(1) == 'n/a'
        qf.getInt(2) == 2
        qf.getInt(3) == Integer.MIN_VALUE
    }

    def "QueryField stores strings as sorted unique values with counts"() {
        when:
        QueryField qf = field('group', [' Birds ', 'Mammals', 'Birds', null, 'Amphibians'], QueryField.FieldType.STRING)

        then:
        qf.stringData as List == ['', 'Amphibians', 'Birds', 'Mammals']
        qf.stringCounts as List == [1, 1, 2, 1]
        (0..4).collect { qf.getString(it) } == ['Birds', 'Mammals', 'Birds', '', 'Amphibians']
        qf.getAsString(1) == 'Mammals'
    }

    def "QueryField stores floats and doubles with NaN for values that are not numbers"() {
        when:
        QueryField f = field('f', ['1.5', 'abc'], QueryField.FieldType.FLOAT)
        QueryField d = field('d', ['2.25', ''], QueryField.FieldType.DOUBLE)

        then:
        f.getFloat(0) == 1.5f
        Float.isNaN(f.getFloat(1))
        f.getAsString(1) == 'n/a'
        d.getDouble(0) == 2.25d
        d.getAsString(1) == 'n/a'
    }

    @Unroll
    def "FieldType.fromString('#s') is #expected"() {
        expect:
        QueryField.FieldType.fromString(s) == expected

        where:
        s         | expected
        'double'  | QueryField.FieldType.DOUBLE
        'INT'     | QueryField.FieldType.INT
        'unknown' | QueryField.FieldType.AUTO
        null      | QueryField.FieldType.AUTO
    }

    def "GroupType is found by its constant name or its display name"() {
        expect:
        QueryField.GroupType.fromString('record_details') == QueryField.GroupType.RECORD_DETAILS
        QueryField.GroupType.fromString('nothing') == QueryField.GroupType.CUSTOM
        QueryField.GroupType.getGroupType('Record details') == QueryField.GroupType.RECORD_DETAILS
        QueryField.GroupType.TAXONOMIC.order == 1
    }

    // ---- categorical LegendObject ----

    def "a categorical legend gives the first colours to the most frequent categories"() {
        when:
        LegendObject lo = new LegendObject(['a', 'b', 'c'] as String[], [5, 20, 10] as int[])

        then:
        lo.categoryNameOrder as List == ['b', 'c', 'a']
        lo.getColour('b') == LegendObject.colours[0]
        lo.getColour('c') == LegendObject.colours[1]
        lo.getColour('a') == LegendObject.colours[2]
        lo.getColour('missing') == LegendObject.DEFAULT_COLOUR
        lo.getColour(1f) == LegendObject.colours[1]
        lo.getColour(3f) == LegendObject.DEFAULT_COLOUR
        lo.minMax as List == [0f, 3f]
    }

    def "categories beyond the number of colours share the last colour"() {
        given:
        int n = LegendObject.colours.length + 5
        String[] names = (0..<n).collect { "c$it".toString() } as String[]
        int[] counts = (0..<n).collect { n - it } as int[]

        when:
        LegendObject lo = new LegendObject(names, counts)

        then:
        lo.getColour("c${n - 1}".toString()) == LegendObject.colours[-1]
        lo.getColour('c0') == LegendObject.colours[0]
    }

    def "the table of a categorical legend lists name, red, green, blue and count as CSV"() {
        given:
        LegendObject lo = new LegendObject(['say "hi", ok', 'b'] as String[], [3, 1] as int[])
        int c = LegendObject.colours[0]

        when:
        List<String[]> rows = parseCsv(lo.table)

        then:
        rows[0] as List == ['name', 'red', 'green', 'blue', 'count']
        rows[1] as List == ['say "hi", ok', "${(c >> 16) & 0xFF}", "${(c >> 8) & 0xFF}", "${c & 0xFF}", '3']*.toString()
        rows[2][0] == 'b'
        rows.size() == 3
    }

    def "LegendObject.getRGB writes the red, green and blue components"() {
        expect:
        LegendObject.getRGB(0x00DC3912) == '220,57,18'
    }

    // ---- LegendBuilder / numeric LegendObject ----

    def "a string QueryField gets a categorical legend"() {
        given:
        QueryField qf = field('group', ['Birds', 'Birds', 'Mammals'], QueryField.FieldType.STRING)

        expect:
        qf.legend.numericLegend == null
        qf.getColour(0) == LegendObject.colours[0]
        qf.getColour(2) == LegendObject.colours[1]
    }

    def "an integer QueryField gets an equal area legend, with white for missing values"() {
        given:
        QueryField qf = field('year', (1..100).collect { it.toString() } + ['n/a'], QueryField.FieldType.INT)

        when:
        LegendObject lo = qf.legend

        then:
        lo.numericLegend instanceof LegendEqualArea
        lo.minMax as List == [1f, 100f]
        qf.getColour(0) == (Legend.colours[0] | (0xFF000000 as int))
        qf.getColour(99) == (Legend.colours[10] | (0xFF000000 as int))
        qf.getColour(100) == WHITE
    }

    def "the table of a numeric legend has one row per class and the counts of all records"() {
        given:
        LegendObject lo = LegendBuilder.legendFromDoubles((1..1000).collect { it as double } as double[],
                new QueryField('v', QueryField.FieldType.INT))

        when:
        List<String[]> rows = parseCsv(lo.table)

        then:
        rows.size() == 11
        rows[1][0].startsWith('name:[* TO ')
        rows[10][0].endsWith(' TO *]')
        rows.tail().sum { it[4] as int } == 1000
    }

    /**
     * LegendObject.getTable reads the class minimums with numericLegend.getCutoffFloats() (the
     * maximums) instead of getCutoffMinFloats(), so every class but the first is written as
     * "name:[max TO max]" and the facet only matches the class maximum.
     */
    def "the classes in the table of a numeric legend are ranges from the class minimum to the class maximum"() {
        given:
        LegendObject lo = LegendBuilder.legendFromDoubles((1..1000).collect { it as double } as double[],
                new QueryField('v', QueryField.FieldType.INT))

        when:
        List<List<Double>> ranges = parseCsv(lo.table).tail().collect { row ->
            (row[0] =~ /name:\[(\S+) TO (\S+)\]/)[0].with { m -> [m[1] == '*' ? 1d : m[1] as double, m[2] == '*' ? 1000d : m[2] as double] }
        }

        then:
        ranges.every { it[0] < it[1] }
        (1..<ranges.size()).every { ranges[it][0] > ranges[it - 1][1] }
    }
}
