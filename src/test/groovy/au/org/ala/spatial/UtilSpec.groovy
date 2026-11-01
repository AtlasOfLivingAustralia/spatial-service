package au.org.ala.spatial

import com.opencsv.CSVReader
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Util: the CSV output of expert distributions and checklists, which the area report downloads.
 * It is parsed back with opencsv, the CSV library the rest of the code uses.
 */
class UtilSpec extends Specification {

    static List<String[]> parseCsv(String csv) {
        new CSVReader(new StringReader(csv)).withCloseable { it.readAll() }
    }

    static Distributions distribution(Map props) {
        Map defaults = [spcode: 1L, scientific: 'Acacia dealbata', authority_: 'Link', common_nam: 'Silver Wattle',
                        family: 'Fabaceae', genus_name: 'Acacia', specific_n: 'dealbata', min_depth: 10d, max_depth: 200d,
                        metadata_u: 'http://example.org/md', lsid: 'urn:lsid:1', area_name: 'Area 1', area_km: 12.345d,
                        data_resource_uid: 'dr1', intersectArea: 1234567d, wmsurl: 'http://example.org/wms?a']
        Distributions d = new Distributions()
        (defaults + props).each { k, v -> d[k] = v }
        d
    }

    // ---- wrap ----

    @Unroll
    def "wrap(#input) can be read back by a CSV reader"() {
        when:
        String[] row = parseCsv(Util.wrap(input) + ',' + Util.wrap('next'))[0]

        then:
        row as List == [input, 'next']

        where:
        input << ['plain', 'with,comma', 'with "quotes"', 'two\nlines', 'back\\slash', '']
    }

    def "wrap(null) is an empty field"() {
        expect:
        Util.wrap(null) == ''
    }

    // ---- getDistributionsOrChecklists ----

    def "getDistributionsOrChecklists returns no lines for null or no distributions"() {
        expect:
        Util.getDistributionsOrChecklists(null).length == 0
        Util.getDistributionsOrChecklists([]).length == 0
    }

    def "getDistributionsOrChecklists writes a header and one CSV row per distribution"() {
        given:
        def list = [distribution(spcode: 5L, scientific: 'Name, with comma'), distribution(spcode: 6L, common_nam: null)]

        when:
        String[] lines = Util.getDistributionsOrChecklists(list)
        List<String[]> rows = parseCsv(lines.join('\n'))

        then:
        lines.length == 3
        rows[0][0] == 'SPCODE'
        rows[1][0] == '5'
        rows[1][1] == 'Name, with comma'
        rows[1][3] == 'Silver Wattle'
        rows[2][0] == '6'
        rows[2][3] == ''
    }

    def "getDistributionsOrChecklists writes the areas in sq km with two decimals"() {
        when:
        String[] row = parseCsv(Util.getDistributionsOrChecklists([distribution([:])])[1])[0]

        then:
        row[7] == '10.0'
        row[8] == '200.0'
        row[11] == 'Area 1'
        row[12] == '12.35'           // area_km, already in sq km
        row[13] == 'dr1'
        row[14] == '1.23'            // intersectArea, in sq m
    }

    def "getDistributionsOrChecklists writes 0.00 for missing areas"() {
        when:
        String[] row = parseCsv(Util.getDistributionsOrChecklists([distribution(area_km: null, intersectArea: null)])[1])[0]

        then:
        row[12] == '0.00'
        row[14] == '0.00'
    }

    /**
     * The rows have 15 columns (DATA_RESOURCE_UID and the intersected area were added) but the
     * header still has the original 13, so the last two columns of the CSV have no name.
     */
    def "getDistributionsOrChecklists writes a header with one name per column"() {
        when:
        List<String[]> rows = parseCsv(Util.getDistributionsOrChecklists([distribution([:])]).join('\n'))

        then:
        rows[0].length == rows[1].length
    }

    /**
     * min_depth/max_depth are written with the Groovy elvis operator, so a depth of 0 (the surface,
     * common for marine species) is written as an empty field, the same as an unknown depth.
     */
    def "getDistributionsOrChecklists writes a depth of 0"() {
        when:
        String[] row = parseCsv(Util.getDistributionsOrChecklists([distribution(min_depth: 0d, max_depth: 0d)])[1])[0]

        then:
        row[7] == '0.0'
        row[8] == '0.0'
    }

    def "getDistributionsOrChecklists writes an unknown depth as an empty field"() {
        when:
        String[] row = parseCsv(Util.getDistributionsOrChecklists([distribution(min_depth: null, max_depth: null)])[1])[0]

        then:
        row[7] == ''
        row[8] == ''
    }

    // ---- getDistributionsOrChecklistsRollup ----

    def "getDistributionsOrChecklistsRollup writes one row per species with its likely and maybe areas"() {
        given:
        def list = [
                distribution(scientific: 'A a', lsid: 'urn:a', area_name: 'Likely habitat', intersectArea: 2e6d),
                distribution(scientific: 'A a', lsid: 'urn:a', area_name: 'Maybe habitat', intersectArea: 3.5e6d),
                distribution(scientific: 'B b', lsid: 'urn:b', area_name: 'Likely habitat', intersectArea: 1e6d),
        ]

        when:
        List<String[]> rows = parseCsv(Util.getDistributionsOrChecklistsRollup(list).join('\n'))
        Map<String, List<String>> bySpecies = rows.tail().collectEntries { [(it[1]): it as List] }

        then:
        rows[0] as List == ['FAMILY', 'SCIENTIFIC_NAME', 'COMMON_NAME', 'LSID', 'LIKELY_AREA', 'MAYBE_AREA']
        rows.size() == 3
        bySpecies['A a'] == ['Fabaceae', 'A a', 'Silver Wattle', 'urn:a', '2.00', '3.50']
        bySpecies['B b'] == ['Fabaceae', 'B b', 'Silver Wattle', 'urn:b', '1.00', '']
    }

    def "getDistributionsOrChecklistsRollup returns no lines for no distributions"() {
        expect:
        Util.getDistributionsOrChecklistsRollup([]).length == 0
    }

    // ---- getAreaChecklists ----

    def "getChecklistsBySpcode finds a checklist by its spcode"() {
        given:
        def list = [distribution(spcode: 1L), distribution(spcode: 22L)]

        expect:
        Util.getChecklistsBySpcode('22', list).is(list[1])
        Util.getChecklistsBySpcode('3', list) == null
    }

    /**
     * getAreaChecklists groups the checklists of the same area (same wmsurl) into one row with a
     * species count. It reads the wmsurl with distribution.get("wmsurl"), left over from when the
     * list was a JSON array; on a Distributions domain object that is the static GORM get(id), so it
     * throws (or looks up a record with id "wmsurl") and the area report gets no checklist table.
     */
    def "getAreaChecklists writes one row per checklist area with the number of species"() {
        given:
        def list = [
                distribution(spcode: 1L, area_name: 'Area A', wmsurl: 'http://wms/a'),
                distribution(spcode: 2L, area_name: 'Area B', wmsurl: 'http://wms/b'),
                distribution(spcode: 3L, area_name: 'Area A', wmsurl: 'http://wms/a'),
        ]
        String[] records = Util.getDistributionsOrChecklists(list)

        when:
        String[] lines = Util.getAreaChecklists(records, list)
        List<String[]> rows = parseCsv(lines.join('\n'))

        then:
        rows.size() == 3
        rows[0][0] == 'SPCODE'
        rows[0][-1] == 'SPECIES_COUNT'
        rows.tail().collectEntries { [(it[11]): it[-1]] } == ['Area A': '2', 'Area B': '1']
        // species specific columns are blanked
        rows.tail().every { it[1] == '' && it[3] == '' }
    }

    // ---- replaceTextInFile / readReplaceAfter / readReplaceBetween ----
}
