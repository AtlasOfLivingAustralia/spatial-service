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

package au.org.ala.spatial


import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.dto.Tabulation
import au.org.ala.spatial.tabulation.TabulationUtil
import au.org.ala.spatial.util.SpatialUtils
import groovy.sql.Sql

class TabulationService {
    LayerService layerService
    SpatialObjectsService spatialObjectsService
    SpatialConfig spatialConfig
    TabulationGeneratorService tabulationGeneratorService

    def sessionFactory
    def dataSource

    String[][] tabulationGridGenerator(List<Tabulation> tabulations, String func) throws IOException {
        //determine x & y field names
        TreeMap<String, String> origObjects1 = new TreeMap<String, String>()
        TreeMap<String, String> origObjects2 = new TreeMap<String, String>()

        for (Tabulation t : tabulations) {
            origObjects1.put(t.getPid1(), t.getName1())
            origObjects2.put(t.getPid2(), t.getName2())
        }

        TreeMap<String, String> objects1
        TreeMap<String, String> objects2
        boolean swap = false
        if (origObjects1.size() <= origObjects2.size()) {
            objects1 = origObjects1
            objects2 = origObjects2
        } else {
            swap = true
            objects1 = origObjects2
            objects2 = origObjects1
        }

        int rows = Math.max(objects1.size(), objects2.size())
        int columns = Math.min(objects1.size(), objects2.size())

        String[][] grid = new String[rows + 1][columns + 1]

        //populate grid

        //row and column sort order and labels
        TreeMap<String, Integer> order1 = new TreeMap<String, Integer>()
        TreeMap<String, Integer> order2 = new TreeMap<String, Integer>()
        int pos = 0
        for (String s : objects1.keySet()) {
            order1.put(s, pos++)
            grid[0][pos] = objects1.get(s)
        }
        pos = 0
        for (String s : objects2.keySet()) {
            order2.put(s, pos++)
            grid[pos][0] = objects2.get(s)
        }

        //grid
        for (Tabulation t : tabulations) {
            String value = null
            if (func == "area") {
                //sq km
                value = String.format(Locale.US, "%.1f", t.getArea())
            } else if (func == "occurrences") {
                value = String.valueOf(t.getOccurrences())
            } else if (func == "species") {
                value = String.valueOf(t.getSpecies())
            }
            if (!swap) {
                grid[order2.get(t.getPid2()) + 1][order1.get(t.getPid1()) + 1] = value
            } else {
                grid[order2.get(t.getPid1()) + 1][order1.get(t.getPid2()) + 1] = value
            }
        }

        return grid
    }

    double[] tabulationSumOfColumnsGenerator(String[][] grid, String func) throws IOException {

        //define row totals
        double[] sumofcolumns = new double[grid.length - 1]

        for (int k = 1; k < grid.length; k++) {
            //sum of rows
            for (int j = 1; j < grid[0].length; j++) {
                //not row and column headers
                if (grid[k][j] != null) {
                    if (func == "area") {
                        sumofcolumns[k - 1] = sumofcolumns[k - 1] + Double.parseDouble(grid[k][j])
                    } else if (func == "occurrences" || func == "species") {
                        sumofcolumns[k - 1] = sumofcolumns[k - 1] + Double.parseDouble(grid[k][j])
                    }
                }
            }
        }
        return sumofcolumns
    }

    double[] tabulationSumOfRowsGenerator(String[][] grid, String func) throws IOException {
        //define column totals
        double[] sumofrows = new double[grid[0].length - 1]
        for (int j = 1; j < grid[0].length; j++) {
            //sum of rows
            for (int k = 1; k < grid.length; k++) {
                //not row and column headers
                if (grid[k][j] != null) {
                    if (func == "area" || func == "arearow" || func == "areacolumn" || func == "areatotal") {
                        sumofrows[j - 1] = sumofrows[j - 1] + Double.parseDouble(grid[k][j])
                    } else if (func == "occurrences" || func == "occurrencesrow" || func == "occurrencescolumn" || func == "occurrencestotal" || func == "species" || func == "speciesrow" || func == "speciescolumn" || func == "speciestotal") {
                        sumofrows[j - 1] = sumofrows[j - 1] + Double.parseDouble(grid[k][j])
                    }
                }
            }
        }
        return sumofrows
    }


    String generateTabulationCSVHTML(String fid1, String fid2, String wkt, String func, String type) throws IOException {
        List<Tabulation> tabulations = getTabulation(fid1, fid2, wkt)
        for (Tabulation t : tabulations) {
            if (t.name1 == null) t.name1 = t.pid1
            if (t.name2 == null) t.name2 = t.pid2
        }

        String[][] grid = tabulationGridGenerator(tabulations, func)
        double[] sumOfColumns = func == "species" ? speciesTotals(tabulations, true) : tabulationSumOfColumnsGenerator(grid, func)
        double[] sumOfRows = func == "species" ? speciesTotals(tabulations, false) : tabulationSumOfRowsGenerator(grid, func)

        double Total = 0.0
        for (int j = 1; j < grid[0].length; j++) {
            Total += sumOfRows[j - 1]
        }

        for (int j = 1; j < grid[0].length; j++) {
            double NumOfNonzeroRows = 0.0
            for (int k = 1; k < grid.length; k++) {
                if (grid[k][j] != null) {
                    NumOfNonzeroRows = NumOfNonzeroRows + 1
                }
            }
        }

        for (int i = 1; i < grid.length; i++) {
            double NumOfNonzeroColumns = 0.0
            for (int k = 1; k < grid[0].length; k++) {
                if (grid[i][k] != null) {
                    NumOfNonzeroColumns = NumOfNonzeroColumns + 1
                }
            }
        }

        //write to csv or json
        StringBuilder sb = new StringBuilder()
        if (type == "csv") {
            for (int i = 0; i < grid.length; i++) {
                if (i > 0) {
                    sb.append("\n")
                }
                for (int j = 0; j < grid[i].length; j++) {
                    if (i == 0 || j == 0) {
                        if (i == 0 && j == 0) {
                            if (func == "area") {
                                sb.append("\"Area (square kilometres)\"")
                            } else if (func == "occurrences") {
                                sb.append("\"Number of occurrences\"")
                            } else if (func == "species") {
                                sb.append("\"Number of species\"")
                            }
                        }
                        if (j > 0) {
                            sb.append(",")
                        }
                        if (grid[i][j] != null) {
                            sb.append("\"").append(grid[i][j].replace("\"", "\"\"")).append("\"")
                        }
                    } else {
                        sb.append(",")
                        if (grid[i][j] != null) {
                            sb.append(grid[i][j])
                        }
                    }
                }
                if (i == 0) {
                    if (func == "area") {
                        sb.append(",\"Total area\"")
                    } else if (func == "occurrences") {
                        sb.append(",\"Total occurrences\"")
                    } else if (func == "species") {
                        sb.append(",\"Total species\"")
                    }
                } else {
                    if (func == "area") {
                        sb.append("," + sumOfColumns[i - 1])
                    } else if (func == "occurrences" || func == "species") {
                        sb.append("," + sumOfColumns[i - 1])
                    }
                }
            }
            sb.append("\n")
            if (func == "area") {
                sb.append("\"Total area\"")
            } else if (func == "occurrences") {
                sb.append("\"Total occurrences\"")
            } else if (func == "species") {
                sb.append("\"Total species\"")
            }
            for (int j = 1; j < grid[0].length; j++) {
                if (func == "area") {
                    sb.append("," + sumOfRows[j - 1])
                } else if (func == "occurrences" || func == "species") {
                    sb.append("," + sumOfRows[j - 1])
                }
            }
            if (func == "area") {
                sb.append("," + Total)
            } else if (func == "occurrences") {
                sb.append("," + Total)
            } else {
                sb.append(",")
            }
            sb.append("\n\n")
            sb.append("Blanks = no intersection\n")
            sb.append("0 = no records in intersection\n")
        } else if (type == "json") {
            sb.append("{")
            for (int i = 1; i < grid.length; i++) {
                if (grid[i][0])
                    sb.append("\"").append(grid[i][0].replace("\"", "\"\"")).append('\":')
                sb.append("{")
                for (int j = 1; j < grid[i].length; j++) {
                    if (grid[i][j]) {
                        sb.append("\"").append(grid[0][j].replace("\"", "\"\"")).append('\":')
                        sb.append(grid[i][j])
                        sb.append(",")
                    }
                }
                if (func == "area") {
                    sb.append('\"Total area\":' + sumOfColumns[i - 1])
                } else if (func == "occurrences") {
                    sb.append('\"Total occurrences\":' + sumOfColumns[i - 1])
                } else if (func == "species") {
                    sb.append('\"Total species\":' + sumOfColumns[i - 1])
                }

                if (sb.toString().endsWith(",")) {
                    sb.deleteCharAt(sb.toString().length() - 1)
                }
                sb.append("}")
                if (i < grid.length - 1) {
                    sb.append(",")
                }
                sb.append("\n")
            }

            if (func == "area") {
                sb.append(',\"Total area\":')
            } else if (func == "occurrences") {
                sb.append(',\"Total occurrences\":')
            } else if (func == "species") {
                sb.append(',\"Total species\":')
            }

            sb.append("{")
            for (int j = 1; j < grid[0].length; j++) {
                sb.append("\"").append(grid[0][j].replace("\"", "\"\"")).append('\":')
                if (func == "area") {
                    sb.append(sumOfRows[j - 1] + ",")
                } else if (func == "occurrences" || func == "species") {
                    sb.append(sumOfRows[j - 1] + ",")
                }
            }

            if (func == "area") {
                sb.append('\"Total area\":' + Total + ",")
            } else if (func == "occurrences") {
                sb.append('\"Total occurrences\":' + Total + ",")
            } else if (func == "species") {
                sb.append('\"Total species\":' + Total + ",")
            }

            if (sb.toString().endsWith(",")) {
                sb.deleteCharAt(sb.toString().length() - 1)
            }
            sb.append("\n")

            sb.append("}")
            sb.append("}")
        }

        sb.toString()
    }

    double[] speciesTotals(List<Tabulation> tabulations, boolean row) throws IOException {
        TreeMap<String, String> origObjects1 = new TreeMap<String, String>()
        TreeMap<String, String> origObjects2 = new TreeMap<String, String>()

        for (Tabulation t : tabulations) {
            origObjects1.put(t.getPid1(), t.getName1())
            origObjects2.put(t.getPid2(), t.getName2())
        }

        TreeMap<String, String> objects1
        TreeMap<String, String> objects2
        if (origObjects1.size() <= origObjects2.size()) {
            objects1 = origObjects1
            objects2 = origObjects2
        } else {
            objects1 = origObjects2
            objects2 = origObjects1
        }

        int rows = Math.max(objects1.size(), objects2.size())
        int columns = Math.min(objects1.size(), objects2.size())

        double[] grid = new double[row ? rows : columns]

        //populate grid

        //row and column sort order and labels
        TreeMap<String, Integer> order1 = new TreeMap<String, Integer>()
        TreeMap<String, Integer> order2 = new TreeMap<String, Integer>()
        int pos = 0
        for (String s : objects1.keySet()) {
            order1.put(s, pos++)
        }
        pos = 0
        for (String s : objects2.keySet()) {
            order2.put(s, pos++)
        }

        //grid
        for (Tabulation t : tabulations) {
            if (origObjects1.size() <= origObjects2.size()) {
                if (row) grid[order2.get(t.getPid2())] = t.getSpeciest2() == null ? 0 : t.getSpeciest2()
                else grid[order1.get(t.getPid1())] = t.getSpeciest1() == null ? 0 : t.getSpeciest1()
            } else {
                if (row) grid[order2.get(t.getPid1())] = t.getSpeciest1() == null ? 0 : t.getSpeciest1()
                else grid[order1.get(t.getPid2())] = t.getSpeciest2() == null ? 0 : t.getSpeciest2()
            }
        }

        return grid
    }

    List<Tabulation> getTabulation(String fid1, String fid2, String wkt) {
        List<Tabulation> tabulations = null

        String min, max
        if (fid1 < fid2) {
            min = fid1
            max = fid2
        } else {
            min = fid2
            max = fid1
        }

        if (wkt == null || wkt.length() == 0) {
            /* before "tabulation" table is updated with column "occurrences", to just make sure column "area" is all good */
            String sql = "SELECT i.pid1, i.pid2, i.fid1, i.fid2, i.area, o1.name as name1, o2.name as name2, i.occurrences, i.species, i.speciest1, i.speciest2 FROM " + "(SELECT pid1, pid2, fid1, fid2, area, occurrences, species, speciest1, speciest2 FROM tabulation WHERE fid1= ? AND fid2 = ? ) i, " + "(select t1.pid1 as pid, name from tabulation t1 left join objects o3 on t1.fid1=o3.fid and t1.pid1=o3.pid where t1.fid1= ? group by t1.pid1, name) o1, " + "(select t2.pid2 as pid, name from tabulation t2 left join objects o4 on t2.fid2=o4.fid and t2.pid2=o4.pid where t2.fid2= ? group by t2.pid2, name) o2 " + "WHERE i.pid1=o1.pid AND i.pid2=o2.pid "

            tabulations = []

            Sql.newInstance(dataSource).query(sql, [min, max, min, max], { it ->
                while (it.next()) {
                    Tabulation t = new Tabulation()
                    t.pid1 = it.getString(1)
                    t.pid2 = it.getString(2)
                    t.fid1 = it.getString(3)
                    t.fid2 = it.getString(4)
                    t.area = it.getDouble(5)
                    t.name1 = it.getString(6)
                    t.name2 = it.getString(7)
                    t.occurrences = it.getInt(8)
                    t.species = it.getInt(9)
                    t.speciest1 = it.getInt(10)
                    t.speciest2 = it.getInt(11)
                    tabulations.add(t)
                }
            })
        } else {
            String sql = "SELECT fid1, pid1, fid2, pid2, ST_AsText(newgeom) as geometry, name1, name2, occurrences, species, speciest1, speciest2 FROM " + "(SELECT fid1, pid1, fid2, pid2, (ST_INTERSECTION(ST_GEOMFROMTEXT( ? ,4326), i.the_geom)) as newgeom, o1.name as name1, o2.name as name2, i.occurrences, i.species FROM " + "(SELECT * FROM tabulation WHERE fid1= ? AND fid2 = ? ) i, " + "(select t1.pid1 as pid, name from tabulation t1 left join objects o3 on t1.fid1=o3.fid and t1.pid1=o3.pid where t1.fid1= ? group by t1.pid1, name) o1, " + "(select t2.pid2 as pid, name from tabulation t2 left join objects o4 on t2.fid2=o4.fid and t2.pid2=o4.pid where t2.fid2= ? group by t2.pid2, name) o2 " + "WHERE i.pid1=o1.pid AND i.pid2=o2.pid) a " + "WHERE a.newgeom is not null AND ST_Area(a.newgeom) > 0"

            tabulations = []

            Sql.newInstance(dataSource).query(sql, [wkt, min, max, min, max], { it ->
                while (it.next()) {
                    Tabulation t = new Tabulation()
                    t.fid1 = it.getString(1)
                    t.pid1 = it.getString(2)
                    t.fid2 = it.getString(3)
                    t.pid2 = it.getString(4)
                    t.geometry = it.getString(5)
                    t.name1 = it.getString(6)
                    t.name2 = it.getString(7)
                    t.occurrences = it.getInt(8)
                    t.species = it.getInt(9)
                    t.speciest1 = it.getInt(10)
                    t.speciest2 = it.getInt(11)
                    tabulations.add(t)
                }
            })

            for (Tabulation t : (tabulations as List<Tabulation>)) {
                try {
                    t.setArea(SpatialUtils.calculateArea(t.getGeometry()))
                    t.setOccurrences(TabulationUtil.calculateOccurrences(spatialConfig.occurrence_species_records_filename, t.getGeometry()))
                    t.setSpecies(TabulationUtil.calculateSpecies(spatialConfig.occurrence_species_records_filename, t.getGeometry()))
                } catch (Exception e) {
                    log.error("fid1:" + fid1 + " fid2:" + fid2 + " wkt:" + wkt, e)
                }
            }
        }

        //fill in 'name' for 'grids as classes'/fields.type='a'/pids with ':'
        IntersectionFile f = layerService.getIntersectionFile(min)
        if (f.getType() == "a") {
            for (Tabulation t : (tabulations as List<Tabulation>)) {
                t.setName1(f.getClasses().get(Integer.parseInt(t.getPid1().split(':')[1])).getName())
            }
        }
        f = layerService.getIntersectionFile(max)
        if (f.getType() == "a") {
            for (Tabulation t : (tabulations as List<Tabulation>)) {
                t.setName2(f.getClasses().get(Integer.parseInt(t.getPid2().split(':')[1])).getName())
            }
        }

        return tabulations
    }


    List<Tabulation> listTabulations() {
        String incompleteTabulations = "select fid1, fid2 from tabulation where area is null and the_geom is not null group by fid1, fid2"
        String sql = "SELECT fid1, fid2, f1.name as name1, f2.name as name2 " + " FROM (select t1.* from " + "(select fid1, fid2, sum(area) a from tabulation group by fid1, fid2) t1 left join " + " (" + incompleteTabulations + ") i on t1.fid1=i.fid1 and t1.fid2=i.fid2 where i.fid1 is null" + ") t" + ", fields f1, fields f2 " + " WHERE f1.id = fid1 AND f2.id = fid2 AND a > 0 " + " AND f1.intersect=true AND f2.intersect=true " + " GROUP BY fid1, fid2, name1, name2"
        List<Tabulation> result = []

        List<String> fields = Fields.findAll().collect { if (it.enabled) it.id }
        Sql.newInstance(dataSource).query(sql, { it ->
            while (it.next()) {
                Tabulation t = new Tabulation()
                t.fid1 = it.getString(1)
                t.fid2 = it.getString(2)
                t.name1 = it.getString(3)
                t.name2 = it.getString(4)

                if (fields.contains(t.fid1) && fields.contains(t.fid2)) {
                    result.add(t)
                }
            }
        })
        result
    }


    List<Tabulation> getTabulationSingle(String fid, String wkt) {
        //is it wkt or pid?
        boolean isPid = wkt.indexOf('(') < 0
        //is it grid as contextual layer?
        IntersectionFile f = layerService.getIntersectionFile(fid)

        if (f == null) {
            return []
        }

        if (f.getType().equalsIgnoreCase("c")) {
            if (wkt.length() > 0) {
                String sql
                List<Tabulation> tabulations

                if (isPid) {
                    sql = "SELECT fid1, pid1, name1," + " fid2, pid2, name2, " + " ST_AsText(newgeom) as geometry FROM " + "(" + "SELECT a.fid as fid1, a.pid as pid1, a.name as name1, b.fid as fid2, b.pid as pid2, b.name as name2 " + ", (ST_INTERSECTION(b.the_geom, a.the_geom)) as newgeom FROM " + "(SELECT * FROM objects WHERE fid = ? ) a, (SELECT * FROM objects WHERE pid = ? ) b " + "WHERE ST_INTERSECTS(ST_GEOMFROMTEXT(a.bbox, 4326), ST_GEOMFROMTEXT(b.bbox ,4326))" + ") o " + "WHERE newgeom is not null AND ST_Area(newgeom) > 0"

                    Sql.newInstance(dataSource).query(sql, [fid, wkt], { it ->
                        while (it.next()) {
                            Tabulation t = new Tabulation()
                            t.fid1 = it.getString(1)
                            t.pid1 = it.getString(2)
                            t.name1 = it.getString(3)
                            t.fid2 = it.getString(4)
                            t.pid2 = it.getString(5)
                            t.name2 = it.getString(6)
                            t.geometry = it.getString(7)
                            tabulations.add(t)
                        }
                    })
                } else {
                    sql = "SELECT fid as fid1, pid as pid1, name as name1," + " 'user area' as fid2, 'user area' as pid2, 'user area' as name2, " + " ST_AsText(newgeom) as geometry FROM " + "(SELECT fid, pid, name, (ST_INTERSECTION(ST_GEOMFROMTEXT( ? ,4326), the_geom)) as newgeom FROM " + "objects WHERE fid= ? and ST_INTERSECTS(ST_GEOMFROMTEXT(bbox, 4326), ST_ENVELOPE(ST_GEOMFROMTEXT( ? ,4326)))" + ") o " + "WHERE newgeom is not null AND ST_Area(newgeom) > 0"
                    Sql.newInstance(dataSource).query(sql, [wkt, fid, wkt], { it ->
                        while (it.next()) {
                            Tabulation t = new Tabulation()
                            t.fid1 = it.getString(1)
                            t.pid1 = it.getString(2)
                            t.name1 = it.getString(3)
                            t.fid2 = it.getString(4)
                            t.pid2 = it.getString(5)
                            t.name2 = it.getString(6)
                            t.geometry = it.getString(7)
                            tabulations.add(t)
                        }
                    })
                }


                for (Tabulation t : (tabulations as List<Tabulation>)) {
                    t.setArea(SpatialUtils.calculateArea(t.getGeometry()))

                    //don't return geometry
                    t.setGeometry(null)
                }

                return tabulations
            } else {
                String sql = "SELECT fid1, pid1, name as name1," + " 'world' as fid2, 'world' as pid2, 'world' as name2, " + " area_km as area FROM " + "(SELECT name, fid as fid1, pid as pid1, the_geom as newgeom, area_km FROM " + "objects WHERE fid= ? ) t " + "WHERE newgeom is not null AND ST_Area(newgeom) > 0"

                List<Tabulation> tabulations = []
                Sql.newInstance(dataSource).query(sql, [fid], { it ->
                    while (it.next()) {
                        Tabulation t = new Tabulation()
                        t.fid1 = it.getString(1)
                        t.pid1 = it.getString(2)
                        t.name1 = it.getString(3)
                        t.fid2 = it.getString(4)
                        t.pid2 = it.getString(5)
                        t.area = it.getDouble(6)
                        tabulations.add(t)
                    }
                })

                return tabulations
            }
        } else {
            log.debug("wkt: " + wkt)
            String w = wkt
            if (isPid) {
                //get wkt
                w = spatialObjectsService.getObjectsGeometryById(wkt, "wkt")
            }
            log.debug("w: " + w)
            //TODO: fix
            return tabulationGeneratorService.calc(fid, w)
        }
    }
}
