/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.process

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.spatial.util.RecordsSmall
import groovy.util.logging.Slf4j

import java.util.zip.ZipInputStream

@Slf4j
class DownloadRecords extends SlaveProcess {

    void start() {
        File file = new File(grailsApplication.config.data.dir.toString() + '/sample/records.csv')
        file.getParentFile().mkdirs()

        task.message = 'downloading new records'
        try {
            ZipInputStream zis = new ZipInputStream(new URL(grailsApplication.config.records.url.toString()).openConnection().getInputStream())

            //only 1 file in the download zip
            zis.getNextEntry()

            CSVReader csv = new CSVReader(new InputStreamReader(zis), '\t' as char, '|' as char)
            BufferedWriter br = new BufferedWriter(new FileWriter(file))

            //convert to unescaped form
            String[] next
            br.write('latitude,longitude,names_and_lsid')
            while ((next = csv.readNext()) != null) {
                if (!'decimalLatitude_p'.equals(next[0]) && !'rowKey'.equals(next[0]) && next[0].length() > 0) {
                    br.write('\n' + next[0] + ',' + next[1] + ',' + (next[2] + '|' + next[3]).replace(',', ' '))
                }
            }

            csv.close()
            br.close()
            zis.close()
        } catch (err) {
            err.printStackTrace()
        }

        addOutput('file', '/sample/records.csv')

        task.message = 'making small records files'

        //small records file
        RecordsSmall records = new RecordsSmall(grailsApplication.config.data.dir.toString() + '/sample/')
        records.close()
        RecordsSmall.fileList().each { filename ->
            addOutput('file', '/sample/' + filename)
        }

        task.message = 'identify contextual layer sampling files for deletion'

        //delete any existing contextual layer sampling files
        List fields = getFields()
        fields.each { field ->
            addOutput('delete', '/sample/' + field.spid + '.' + field.sname + '.pid')
            addOutput('delete', '/sample/' + field.spid + '.' + field.sname + '.pid.cat')
        }

        //initiate new TabulationCounts
        addOutput('process', 'TabulationCounts')
    }
}
