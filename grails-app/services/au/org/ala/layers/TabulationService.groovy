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

import au.org.ala.layers.dao.TabulationDAO
import au.org.ala.layers.dto.Tabulation

class TabulationService {

    TabulationDAO tabulationDao

    String[][] tabulationGridGenerator(List<Tabulation> tabulations, String func) throws IOException {
        //determine x & y field names
        TreeMap<String, String> origObjects1 = new TreeMap<String, String>();
        TreeMap<String, String> origObjects2 = new TreeMap<String, String>();

        for (Tabulation t : tabulations) {
            origObjects1.put(t.getPid1(), t.getName1());
            origObjects2.put(t.getPid2(), t.getName2());
        }

        TreeMap<String, String> objects1;
        TreeMap<String, String> objects2;
        boolean swap = false
        if (origObjects1.size() <= origObjects2.size()) {
            objects1 = origObjects1;
            objects2 = origObjects2;
        } else {
            swap = true
            objects1 = origObjects2;
            objects2 = origObjects1;
        }

        int rows = Math.max(objects1.size(), objects2.size());
        int columns = Math.min(objects1.size(), objects2.size());

        String[][] grid = new String[rows + 1][columns + 1];

        //populate grid

        //row and column sort order and labels
        TreeMap<String, Integer> order1 = new TreeMap<String, Integer>();
        TreeMap<String, Integer> order2 = new TreeMap<String, Integer>();
        int pos = 0;
        for (String s : objects1.keySet()) {
            order1.put(s, pos++);
            grid[0][pos] = objects1.get(s);
        }
        pos = 0;
        for (String s : objects2.keySet()) {
            order2.put(s, pos++);
            grid[pos][0] = objects2.get(s);
        }

        //grid
        for (Tabulation t : tabulations) {
            String value = null
            if (func.equals("area")) {
                //convert sqm to sqkm
                value = String.valueOf(t.getArea() / 1000000.0);
            } else if (func.equals("occurrences")) {
                value = String.valueOf(t.getOccurrences());
            } else if (func.equals("species")) {
                value = String.valueOf(t.getSpecies());
            }
            if (!swap) {
                grid[order2.get(t.getPid2()) + 1][order1.get(t.getPid1()) + 1] = value
            } else {
                grid[order2.get(t.getPid1()) + 1][order1.get(t.getPid2()) + 1] = value
            }
        }

        return grid;
    }

    double[] tabulationSumOfColumnsGenerator(String[][] grid, String func) throws IOException {

        //define row totals
        double[] sumofcolumns = new double[grid.length - 1];

        for (int k = 1; k < grid.length; k++) {
            //sum of rows
            for (int j = 1; j < grid[0].length; j++) {
                //not row and column headers
                if (grid[k][j] != null) {
                    if (func.equals("area")) {
                        sumofcolumns[k - 1] = sumofcolumns[k - 1] + Double.parseDouble(grid[k][j]);
                    } else if (func.equals("occurrences") || func.equals("species")) {
                        sumofcolumns[k - 1] = sumofcolumns[k - 1] + Double.parseDouble(grid[k][j]);
                    }
                }
            }
        }
        return sumofcolumns;
    }

    double[] tabulationSumOfRowsGenerator(String[][] grid, String func) throws IOException {
        //define column totals
        double[] sumofrows = new double[grid[0].length - 1];
        for (int j = 1; j < grid[0].length; j++) {
            //sum of rows
            for (int k = 1; k < grid.length; k++) {
                //not row and column headers
                if (grid[k][j] != null) {
                    if (func.equals("area") || func.equals("arearow") || func.equals("areacolumn") || func.equals("areatotal")) {
                        sumofrows[j - 1] = sumofrows[j - 1] + Double.parseDouble(grid[k][j]);
                    } else if (func.equals("occurrences") || func.equals("occurrencesrow") || func.equals("occurrencescolumn") || func.equals("occurrencestotal") || func.equals("species") || func.equals("speciesrow") || func.equals("speciescolumn") || func.equals("speciestotal")) {
                        sumofrows[j - 1] = sumofrows[j - 1] + Double.parseDouble(grid[k][j]);
                    }
                }
            }
        }
        return sumofrows;
    }


    String generateTabulationCSVHTML(String fid1, String fid2, String wkt, String func, String type) throws IOException {
        List<Tabulation> tabulations = tabulationDao.getTabulation(fid1, fid2, wkt)

        String[][] grid = tabulationGridGenerator(tabulations, func);
        double[] sumOfColumns = func == "species" ? speciesTotals(tabulations, true) : tabulationSumOfColumnsGenerator(grid, func);
        double[] sumOfRows = func == "species" ? speciesTotals(tabulations, false) : tabulationSumOfRowsGenerator(grid, func);

        double Total = 0.0;
        for (int j = 1; j < grid[0].length; j++) {
            Total += sumOfRows[j - 1];
        }

        for (int j = 1; j < grid[0].length; j++) {
            double NumOfNonzeroRows = 0.0;
            for (int k = 1; k < grid.length; k++) {
                if (grid[k][j] != null) {
                    NumOfNonzeroRows = NumOfNonzeroRows + 1;
                }
            }
        }

        for (int i = 1; i < grid.length; i++) {
            double NumOfNonzeroColumns = 0.0;
            for (int k = 1; k < grid[0].length; k++) {
                if (grid[i][k] != null) {
                    NumOfNonzeroColumns = NumOfNonzeroColumns + 1;
                }
            }
        }

        //write to csv or json
        StringBuilder sb = new StringBuilder();
        if (type.equals("csv")) {
            for (int i = 0; i < grid.length; i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                for (int j = 0; j < grid[i].length; j++) {
                    if (i == 0 || j == 0) {
                        if (i == 0 && j == 0) {
                            if (func.equals("area")) {
                                sb.append("\"Area (square kilometres)\"");
                            } else if (func.equals("occurrences")) {
                                sb.append("\"Number of occurrences\"");
                            } else if (func.equals("species")) {
                                sb.append("\"Number of species\"");
                            }
                        }
                        if (j > 0) {
                            sb.append(",");
                        }
                        if (grid[i][j] != null) {
                            sb.append("\"").append(grid[i][j].replace("\"", "\"\"")).append("\"");
                        }
                    } else {
                        sb.append(",");
                        if (grid[i][j] != null) {
                            sb.append(grid[i][j]);
                        }
                    }
                }
                if (i == 0) {
                    if (func.equals("area")) {
                        sb.append(",\"Total area\"");
                    } else if (func.equals("occurrences")) {
                        sb.append(",\"Total occurrences\"");
                    } else if (func.equals("species")) {
                        sb.append(",\"Total species\"");
                    }
                } else {
                    if (func.equals("area")) {
                        sb.append("," + sumOfColumns[i - 1]);
                    } else if (func.equals("occurrences") || func.equals("species")) {
                        sb.append("," + sumOfColumns[i - 1]);
                    }
                }
            }
            sb.append("\n");
            if (func.equals("area")) {
                sb.append("\"Total area\"");
            } else if (func.equals("occurrences")) {
                sb.append("\"Total occurrences\"");
            } else if (func.equals("species")) {
                sb.append("\"Total species\"");
            }
            for (int j = 1; j < grid[0].length; j++) {
                if (func.equals("area")) {
                    sb.append("," + sumOfRows[j - 1]);
                } else if (func.equals("occurrences") || func.equals("species")) {
                    sb.append("," + sumOfRows[j - 1]);
                }
            }
            if (func.equals("area")) {
                sb.append("," + Total);
            } else if (func.equals("occurrences")) {
                sb.append("," + Total);
            } else {
                sb.append(",");
            }
            sb.append("\n\n");
            sb.append("Blanks = no intersection\n");
            sb.append("0 = no records in intersection\n");
        } else if (type.equals("json")) {
            sb.append("{");
            for (int i = 1; i < grid.length; i++) {
                sb.append("\"").append(grid[i][0].replace("\"", "\"\"")).append("\":");
                sb.append("{");
                for (int j = 1; j < grid[i].length; j++) {
                    if (grid[i][j] != null) {
                        sb.append("\"").append(grid[0][j].replace("\"", "\"\"")).append("\":");
                        sb.append(grid[i][j]);
                        sb.append(",");
                    }
                }
                if (func.equals("area")) {
                    sb.append("\"Total area\":" + sumOfColumns[i - 1]);
                } else if (func.equals("occurrences")) {
                    sb.append("\"Total occurrences\":" + sumOfColumns[i - 1]);
                } else if (func.equals("species")) {
                    sb.append("\"Total species\":" + sumOfColumns[i - 1]);
                }

                if (sb.toString().endsWith(",")) {
                    sb.deleteCharAt(sb.toString().length() - 1);
                }
                sb.append("}");
                if (i < grid.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }

            if (func.equals("area")) {
                sb.append(",\"Total area\":");
            } else if (func.equals("occurrences")) {
                sb.append(",\"Total occurrences\":");
            } else if (func.equals("species")) {
                sb.append(",\"Total species\":");
            }

            sb.append("{");
            for (int j = 1; j < grid[0].length; j++) {
                sb.append("\"").append(grid[0][j].replace("\"", "\"\"")).append("\":");
                if (func.equals("area")) {
                    sb.append(sumOfRows[j - 1] + ",");
                } else if (func.equals("occurrences") || func.equals("species")) {
                    sb.append(sumOfRows[j - 1] + ",");
                }
            }

            if (func.equals("area")) {
                sb.append("\"Total area\":" + Total + ",");
            } else if (func.equals("occurrences")) {
                sb.append("\"Total occurrences\":" + Total + ",");
            } else if (func.equals("species")) {
                sb.append("\"Total species\":" + Total + ",");
            }

            if (sb.toString().endsWith(",")) {
                sb.deleteCharAt(sb.toString().length() - 1);
            }
            sb.append("\n");

            sb.append("}");
            sb.append("}");
        }

        sb.toString()
    }

    def double[] speciesTotals(List<Tabulation> tabulations, boolean row) throws IOException {
        TreeMap<String, String> origObjects1 = new TreeMap<String, String>();
        TreeMap<String, String> origObjects2 = new TreeMap<String, String>();

        for (Tabulation t : tabulations) {
            origObjects1.put(t.getPid1(), t.getName1());
            origObjects2.put(t.getPid2(), t.getName2());
        }

        TreeMap<String, String> objects1;
        TreeMap<String, String> objects2;
        if (origObjects1.size() <= origObjects2.size()) {
            objects1 = origObjects1;
            objects2 = origObjects2;
        } else {
            objects1 = origObjects2;
            objects2 = origObjects1;
        }

        int rows = Math.max(objects1.size(), objects2.size());
        int columns = Math.min(objects1.size(), objects2.size());

        double[] grid = new double[row ? rows : columns];

        //populate grid

        //row and column sort order and labels
        TreeMap<String, Integer> order1 = new TreeMap<String, Integer>();
        TreeMap<String, Integer> order2 = new TreeMap<String, Integer>();
        int pos = 0;
        for (String s : objects1.keySet()) {
            order1.put(s, pos++);
        }
        pos = 0;
        for (String s : objects2.keySet()) {
            order2.put(s, pos++);
        }

        //grid
        for (Tabulation t : tabulations) {
            if (origObjects1.size() <= origObjects2.size()) {
                if (row) grid[order2.get(t.getPid2())] = t.getSpeciest2() == null ? 0 : t.getSpeciest2();
                else grid[order1.get(t.getPid1())] = t.getSpeciest1() == null ? 0 : t.getSpeciest1();
            } else {
                if (row) grid[order2.get(t.getPid1())] = t.getSpeciest1() == null ? 0 : t.getSpeciest1();
                else grid[order1.get(t.getPid2())] = t.getSpeciest2() == null ? 0 : t.getSpeciest2();
            }
        }

        return grid;
    }
}
