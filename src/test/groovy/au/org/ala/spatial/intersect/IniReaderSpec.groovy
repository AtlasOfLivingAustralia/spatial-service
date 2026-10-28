package au.org.ala.spatial.intersect

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class IniReaderSpec extends Specification {

    @TempDir
    Path tmp

    IniReader read(String content) {
        File f = tmp.resolve('test.ini').toFile()
        f.text = content
        new IniReader(f.path)
    }

    void "reads values by section and key"() {
        when:
        IniReader ini = read('''
# a comment
; another comment
[General]
Title=Annual mean temperature
Version=2
[Data]
MinValue=-12.5
Url=http://example.org/?a=b
''')

        then:
        ini.getStringValue('General', 'Title') == 'Annual mean temperature'
        ini.getIntegerValue('General', 'Version') == 2
        ini.getDoubleValue('Data', 'MinValue') == -12.5d
        ini.getStringValue('Data', 'Url') == 'http://example.org/?a=b'
        ini.valueExists('General', 'Title')
        !ini.valueExists('General', 'Missing')
    }

    void "missing or invalid values have defaults"() {
        when:
        IniReader ini = read('[General]\nTitle=abc\n')

        then:
        ini.getStringValue('General', 'Missing') == ''
        ini.getIntegerValue('General', 'Missing') == 0
        ini.getIntegerValue('General', 'Title') == 0
        ini.getDoubleValue('General', 'Title') == 0d
    }

    void "a missing file gives an empty document"() {
        when:
        IniReader ini = new IniReader(tmp.resolve('missing.ini').toString())

        then:
        !ini.valueExists('General', 'Title')
    }

    void "values survive a write and read round trip"() {
        given:
        IniReader ini = read('[General]\nTitle=abc\n')
        ini.setValue('Data', 'MaxValue', '42.5')
        String out = tmp.resolve('out.ini').toString()

        when:
        ini.write(out)
        IniReader copy = new IniReader(out)

        then:
        copy.getStringValue('General', 'Title') == 'abc'
        copy.getDoubleValue('Data', 'MaxValue') == 42.5d
    }

    // A section name and a key of one character were dropped by two off by one comparisons, so a layer whose
    // .grd header uses short names lost those values.
    void "a section name and a key of one character are read"() {
        when:
        IniReader ini = read('[D]\na=1\nbb=2\n')

        then:
        ini.getIntegerValue('D', 'a') == 1
        ini.getIntegerValue('D', 'bb') == 2
    }
}
