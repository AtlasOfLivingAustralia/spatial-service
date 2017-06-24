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

package au.org.ala.layers

import au.com.bytecode.opencsv.CSVReader
import grails.converters.JSON

class TabulationController {

    def fieldDao
    def tabulationService
    def tabulationDao
    def objectDao

    def index() {
        def tabulations = tabulationDao.listTabulations()

        if (params.containsKey('format') && params.format == 'json') {
            render tabulations as JSON
        } else {
            render(view: "index.gsp", model: [tabulations: tabulations])
        }
    }

    def list() {
        render tabulationDao.listTabulations() as JSON
    }

    def single(String fid, String pid) {
        if (pid) pid = pid.replace(".json","")
        if ("single".equalsIgnoreCase(fid)) {
            fid = pid
            pid = params.containsKey('wkt') ? params.wkt : ''
        }
        if (!params.wkt && pid) {
            pid = objectDao.getObjectsGeometryById(pid, 'wkt')
        }
        try {
            def tabulation = tabulationDao.getTabulationSingle(fid, pid)
            if (tabulation)
                render tabulationDao.getTabulationSingle(fid, pid) as JSON
        } catch (Exception e) {
        }

        render status: 404
    }

    def show(String func1, String fid1, String fid2, String type) {
        String wkt = params?.wkt
        if (params?.wkt && params.wkt.toString().isNumber()) {
            wkt = objectDao.getObjectsGeometryById(params.wkt.toString(), "wkt")
        }
        if (!wkt) wkt = ''

        if ("single".equalsIgnoreCase(func1)) {
            func1 = fid1
            fid1 = fid2
            fid2 = null
        }

        if ("data" == func1) {
            render tabulationService.tabulationDao.getTabulation(fid1, fid2, wkt) as JSON
        } else {
            String data = tabulationService.generateTabulationCSVHTML(fid1, fid2, wkt, func1, "html" == type ? "csv" : type)

            if ("html" == type) {
                CSVReader reader = new CSVReader(new StringReader(data))
                List<String[]> csv = reader.readAll()
                reader.close()

                String label = 'Tabulation for "' + fieldDao.getFieldById(fid1).name + '" and "' +
                        fieldDao.getFieldById(fid2).name + '"'
                if ('area' == func1) label += ' - (sq km) for Area (square kilometres)'
                if ('species' == func1) label += ' - Number of species'
                if ('occurrences' == func1) label += ' - Number of occurrences'

                String info = 'Occurrences and species numbers are reported correctly but the area of some intersections may be reported as "0" sq.km. when they are < 50% of the smallest grid cell used for tabulation.'

                render(view: "show.gsp", model: [data: csv, label: label, info: info])
            } else {
                if ("csv" == type) {
                    response.setContentType("text/comma-separated-values")
                } else if ("json" == type) {
                    response.setContentType("application/json")
                }
                OutputStream os = null
                try {
                    os = response.getOutputStream()
                    os.write(data.getBytes("UTF-8"))
                    os.flush()
                } catch (err) {
                    log.trace(err.getMessage(), err)
                } finally {
                    if (os != null) {
                        try {
                            os.close()
                        } catch (err) {
                            log.trace(err.getMessage(), err)
                        }
                    }
                }
            }
        }
    }
}
