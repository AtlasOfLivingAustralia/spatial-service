package au.org.ala.spatial

import spock.lang.Specification
import spock.lang.TempDir

import java.util.zip.ZipFile

/**
 * Util: text replacement in the Maxent and classification HTML outputs, and zipping of downloads.
 */
class UtilReplaceSpec extends Specification {

    @TempDir
    File tmp

    def "replaceTextInFile replaces every occurrence of each key"() {
        given:
        File f = new File(tmp, 'a.html')
        f.text = '<td>el1</td><td>el2</td>\n<td>el1</td>'

        when:
        Util.replaceTextInFile(f.path, ['<td>el1</td>': '<td>Temperature</td>', 'el2': 'Rain'])

        then:
        f.text == '<td>Temperature</td><td>Rain</td>\n<td>Temperature</td>'
    }

    /**
     * replaceTextInFile uses String.replaceAll, so keys are regular expressions and values are
     * replacement patterns. Maxent puts the user supplied species name in a value; a name with '$'
     * makes replaceAll throw and the Maxent output is not post processed.
     */
    def "replaceTextInFile replaces text literally"() {
        given:
        File f = new File(tmp, 'b.html')
        f.text = 'Maxent model for species (all continuous). Cost: 5 dollars'

        when:
        Util.replaceTextInFile(f.path, ['Maxent model for species': 'Maxent model for Price$1 \\ species', '(all continuous).': '(all).'])

        then:
        f.text == 'Maxent model for Price$1 \\ species (all). Cost: 5 dollars'
    }

    def "readReplaceAfter replaces only the text after the first occurrence of the start marker"() {
        given:
        File f = new File(tmp, 'c.html')
        f.text = 'el1 before\nheader el1 (all continuous) el1\nel1 after\n'

        when:
        Util.readReplaceAfter(f.path, '(all continuous)', 'el1', 'Temperature')

        then:
        f.text == 'el1 before\nheader el1 (all continuous) Temperature\nTemperature after\n'
    }

    /** afterPos > 0 is used as "the marker was found", which is false when it is at column 0. */
    def "readReplaceAfter replaces the lines after a start marker at the beginning of a line"() {
        given:
        File f = new File(tmp, 'd.html')
        f.text = 'el1 before\n(all continuous) el1\nel1 after\n'

        when:
        Util.readReplaceAfter(f.path, '(all continuous)', 'el1', 'Temperature')

        then:
        f.text == 'el1 before\n(all continuous) Temperature\nTemperature after\n'
    }

    def "readReplaceBetween replaces the text from the start marker to the end marker"() {
        given:
        File f = new File(tmp, 'e.html')
        f.text = 'keep<br>Command line used: java -jar maxent.jar a=b<br>keep too\n'

        when:
        Util.readReplaceBetween(f.path, 'Command line', '<br>', '')

        then:
        f.text == 'keep<br>keep too\n'
    }

    def "readReplaceBetween spans lines and leaves the file as is when the start marker is missing"() {
        given:
        File f = new File(tmp, 'f.html')
        f.text = 'a\n(A link to the Explain\ntool) additive models.) b\n'
        File g = new File(tmp, 'g.html')
        g.text = 'nothing to remove\n'

        when:
        Util.readReplaceBetween(f.path, '(A link to the Explain', 'additive models.)', '')
        Util.readReplaceBetween(g.path, 'Command line', '<br>', '')

        then:
        f.text == 'a\n b\n'
        g.text == 'nothing to remove\n'
    }

    /**
     * When the end marker is missing, indexOf returns -1 and the text from the start marker to
     * position endOldText.length() - 1 is replaced, which removes part of the start marker instead.
     */
    def "readReplaceBetween leaves the file as is when the end marker is missing"() {
        given:
        File f = new File(tmp, 'h.html')
        f.text = 'Command line without an end marker\n'

        when:
        Util.readReplaceBetween(f.path, 'Command line', '<br>', '')

        then:
        f.text == 'Command line without an end marker\n'
    }

    // ---- zip ----

    def "zip stores each file under the file name of its archive name"() {
        given:
        File a = new File(tmp, 'a.csv')
        a.text = 'x,y\n1,2\n'
        File b = new File(tmp, 'b.bin')
        b.bytes = (0..2000).collect { (byte) it } as byte[]
        File empty = new File(tmp, 'empty.txt')
        empty.text = ''
        File zip = new File(tmp, 'out.zip')

        when:
        Util.zip(zip.path, [a.path, b.path, empty.path] as String[], ['dir/data.csv', 'other/b.bin', 'empty.txt'] as String[])
        Map<String, byte[]> entries = new ZipFile(zip).withCloseable { z ->
            z.entries().collectEntries { e -> [(e.name): z.getInputStream(e).bytes] }
        }

        then:
        entries.keySet() == ['data.csv', 'b.bin', 'empty.txt'] as Set
        entries['data.csv'] == a.bytes
        entries['b.bin'] == b.bytes
        entries['empty.txt'].length == 0
    }
}
