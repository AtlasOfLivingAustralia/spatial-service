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

import au.org.ala.layers.client.Client
import au.org.ala.layers.dto.IntersectionFile
import au.org.ala.layers.intersect.Grid
import au.org.ala.layers.intersect.SimpleShapeFile
import au.org.ala.spatial.util.RecordsSmall
import grails.util.Holders
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import java.nio.ByteBuffer

@Slf4j
class TabulationCounts extends SlaveProcess {

    void start() {

        // get all fields requiring an intersection
        List allFields = getFields()
        List<Map> fields = []
        taskWrapper.message = 'getting field list'
        allFields.each { field ->
            if (field.intersect) {
                slaveService.getFile(taskService.getResourcePath([type: 'layer'], field.layer.name))
                fields.add(field as Map)
            }
        }

        // load records
        taskWrapper.message = 'getting records'
        String dir = Holders.config.data.dir + File.separator + "sample"
        RecordsSmall.fileList().each { filename ->
            slaveService.getFile(dir + File.separator + filename)
        }

        RecordsSmall records = new RecordsSmall(dir)

        taskWrapper.message = 'reading records'
        float[] allPoints = records.getUniquePointsAll()
        int[] pidx = records.getUniqueIdx()

        double[][] points = new double[allPoints.length / 2][2]
        for (int i = 0; i < allPoints.length; i += 2) {
            points[(i / 2).intValue()][0] = allPoints[(i + 1).intValue()]
            points[(i / 2).intValue()][1] = allPoints[i]
        }

        List<File> pidFiles = []

        //produce sampling files
        fields.eachWithIndex { field, idx ->
            taskWrapper.message = 'get/make sampling for: ' + field.id
            pidFiles.add(sample(points, field))
        }

        points = null

        // sql, reset values
        FileUtils.writeStringToFile(new File(getTaskPath() + "init.sql"),
                "UPDATE tabulation SET occurrences=0 WHERE occurrences is null; UPDATE tabulation SET species=0 WHERE species is null;")
        addOutput('sql', 'init.sql')

        // produce output sql files
        fields.eachWithIndex { field1, idx1 ->
            Object[] o1 = null
            fields.eachWithIndex { field2, idx2 ->
                if (idx1 < idx2 /*&& (field1.id.equals('cl1052') || field2.id.equals('cl1058'))*/) {
                    taskWrapper.message = 'tabulating ' + field1.id + ' and ' + field2.id
                    try {
                        if (o1 == null) o1 = loadFile(pidFiles[idx1], (allPoints.length / 2).intValue())
                        Object[] o2 = loadFile(pidFiles[idx2], (allPoints.length / 2).intValue())

                        String pth = field1.id + field2.id + '.sql'
                        File sqlFile = new File(getTaskPath() + pth)
                        addOutput('sql', pth)

                        // compare
                        List sqlUpdates = compare(records, pidx, (short[]) o1[0], (short[]) o2[0], (String[]) o1[1], (String[]) o2[1], field1.id.toString(), field2.id.toString())

                        FileWriter fw = new FileWriter(sqlFile)
                        sqlUpdates.each { sql ->
                            fw.write(sql.toString())
                        }
                        fw.flush()
                        fw.close()
                        taskWrapper.message = 'finished tabulating ' + field1.id + ' and ' + field2.id
                    } catch (err) {
                        log.error 'failed tabulating ' + field1.id + ' and ' + field2.id, err
                        taskLog('failed tabulating ' + field1.id + ' and ' + field2.id)
                    }
                }
            }
        }

        records.close()
    }

    ArrayList<String> compare(RecordsSmall records, int[] pOrder, short[] v1, short[] v2, String[] s1, String[] s2, String fid1, String fid2) {
        ArrayList<String> sqlUpdates = new ArrayList<String>()
        BitSet bitset
        Integer count
        String key
        HashMap<String, BitSet> species = new HashMap<String, BitSet>()
        HashMap<String, Integer> occurrences = new HashMap<String, Integer>()
        Map<String, BitSet> speciesTotals = new HashMap<String, BitSet>()

        int countNa = 0
        String row
        String col
        for (int i = 0; i < pOrder.length; i++) {
            row = v1[pOrder[i]] < 0 ? null : s1[v1[pOrder[i]]]
            col = v2[pOrder[i]] < 0 ? null : s2[v2[pOrder[i]]]
            key = row + ' ' + col

            //row and column totals
            if (row != null && col != null) {
                bitset = speciesTotals.get(col)
                if (bitset == null) bitset = new BitSet()
                bitset.set(records.getSpeciesNumber(i))
                speciesTotals.put(col, bitset)

                bitset = speciesTotals.get(row)
                if (bitset == null) bitset = new BitSet()
                bitset.set(records.getSpeciesNumber(i))
                speciesTotals.put(row, bitset)

                countNa++
            }

            bitset = species.get(key)
            if (bitset == null) bitset = new BitSet()
            bitset.set(records.getSpeciesNumber(i))
            species.put(key, bitset)

            count = occurrences.get(key)
            if (count == null) {
                count = 0
            }
            count = count + 1
            occurrences.put(key, count)
        }

        // produce sql update statements
        for (String k : species.keySet()) {
            String[] pids = k.split(" ", 2)
            if (pids.length == 1)
                pids = [pids[0], '']
            sqlUpdates.add("UPDATE tabulation SET " + "species = " + species.get(k).cardinality() + ", " + "occurrences = " + occurrences.get(k) + " WHERE (pid1='" + pids[0] + "' AND pid2='"
                    + pids[1] + "') " + "OR (pid1='" + sqlEscapeString(pids[1]) + "' AND pid2='" + sqlEscapeString(pids[0]) + "');")
        }

        // produce sql update statements
        for (String k : speciesTotals.keySet()) {
            String pid = sqlEscapeString(k)
            sqlUpdates.add("UPDATE tabulation SET " + "speciest1 = " + speciesTotals.get(k).cardinality()
                    + " WHERE (pid1='" + pid + "' AND fid1='" + fid1 + "' AND fid2='" + fid2 + "') OR "
                    + " (pid2='" + pid + "' AND fid2='" + fid1 + "' AND fid1='" + fid2 + "');")
            sqlUpdates.add("UPDATE tabulation SET " + "speciest2 = " + speciesTotals.get(k).cardinality()
                    + " WHERE (pid2='" + pid + "' AND fid1='" + fid1 + "' AND fid2='" + fid2 + "') OR "
                    + " (pid1='" + pid + "' AND fid2='" + fid1 + "' AND fid1='" + fid2 + "');")
        }

        return sqlUpdates
    }

    Object[] loadFile(File file, int size) {
        List s = []
        short[] pids = null
        try {
            BufferedReader br = new BufferedReader(new FileReader(file.getPath() + '.cat'))
            String line
            int i = 0
            while ((line = br.readLine()) != null) {
                s.add(line)
                i++
            }
            br.close()

            byte[] e = FileUtils.readFileToByteArray(file)
            ByteBuffer bb = ByteBuffer.wrap(e)

            pids = new short[size]
            for (i = 0; i < size && bb.hasRemaining(); i++) {
                pids[i] = bb.getShort()
            }
        } catch (Exception e) {
            log.error 'failed to load file: ' + file, e
        }

        Object[] o = new Object[2]
        o[0] = pids
        o[1] = s.toArray()

        o
    }

    def sample(double[][] points, Map field) {

        //create new sampling file when one does not already exist
        File file = new File(Holders.config.data.dir.toString() + '/sample/' + field.spid + '.' + field.sname + '.pid')
        String fieldName = field.sname
        Map l = getLayer(field.spid)
        String filename = Holders.config.data.dir + '/layer/' + l.name

        if (!file.exists()) {
            try {
                slaveService.getFile('/layer/' + l.name + '.shp')
                slaveService.getFile('/layer/' + l.name + '.shx')
                slaveService.getFile('/layer/' + l.name + '.dbf')
                slaveService.getFile('/layer/' + l.name + '.prj')
                slaveService.getFile('/layer/' + l.name + '.grd')
                slaveService.getFile('/layer/' + l.name + '.gri')

                if (new File(filename + ".shp").exists()) {
                    SimpleShapeFile ssf = new SimpleShapeFile(filename, fieldName)

                    String[] catagories
                    int column_idx = ssf.getColumnIdx(fieldName)
                    catagories = ssf.getColumnLookup(column_idx)
                    int[] values = ssf.intersect(points, catagories, column_idx, 4)

                    // catagories to pid
                    List fieldObjects = getObjects(field.id)
                    int[] catToPid = new int[catagories.length]
                    for (int j = 0; j < catToPid.length; j++) {
                        catToPid[j] = -1;
                    }
                    for (int i = 0; i < catagories.length; i++) {
                        for (int j = 0; j < fieldObjects.size(); j++) {
                            if ((catagories[i] == null || fieldObjects.get(j).name == null) &&
                                    catagories[i].compareTo(fieldObjects.get(j).name.toString())) {
                                catToPid[i] = j
                                break
                            } else if (catagories[i] != null && fieldObjects.get(j).name != null &&
                                    catagories[i].compareTo(fieldObjects.get(j).name.toString()) == 0) {
                                catToPid[i] = j
                                break
                            }
                        }
                    }
                    // repeat check with a filter for encoding differences
                    // TODO: why are there encoding differences? Are they only from copied layers?
                    for (int i = 0; i < catagories.length; i++) {
                        if (catToPid[i] < 0) {
                            String cat = catagories[i].replaceAll("[^a-zA-Z0-9]", "")
                            for (int j = 0; j < fieldObjects.size(); j++) {
                                if ((cat == null || fieldObjects.get(j).name == null) &&
                                        cat.compareTo(fieldObjects.get(j).name.toString().replaceAll("[^a-zA-Z0-9]", ""))) {
                                    catToPid[i] = j
                                    break
                                } else if (cat != null && fieldObjects.get(j).name != null &&
                                        cat.compareTo(fieldObjects.get(j).name.toString().replaceAll("[^a-zA-Z0-9]", "")) == 0) {
                                    catToPid[i] = j
                                    break
                                }
                            }
                        }
                    }

                    // export pids in points order
                    DataOutputStream fw = null
                    FileWriter catPid = null
                    try {
                        fw = new DataOutputStream(
                                new BufferedOutputStream(new FileOutputStream(file)))
                        if (values != null) {
                            for (int i = 0; i < values.length; i++) {
                                fw.writeShort(values[i])
                            }
                        }

                        catPid = new FileWriter(file.getPath() + ".cat")

                        if (catToPid != null) {
                            for (int i = 0; i < catToPid.length; i++) {
                                if (i > 0) catPid.write("\n")
                                if (catToPid[i] >= 0) {
                                    catPid.write(fieldObjects.get(catToPid[i]).pid.toString())
                                } else {
                                    taskLog("ERROR: no object pid for " + field.id + ", \"" + catagories[i] + "\"")
                                }
                            }
                        }

                    } catch (err) {
                        log.error 'failed to export pid file', err
                    } finally {
                        if (fw != null) {
                            try {
                                fw.close()
                            } catch (err) {
                                log.error 'faild to close pid file', err
                            }
                        }
                        if (catPid != null) {
                            try {
                                catPid.close()
                            } catch (err) {
                                log.error 'faild to close cat2pid file', err
                            }
                        }
                    }
                } else {
                    //grid as shp
                    Grid g = new Grid(filename)
                    if (g != null) {
                        float[] values = g.getValues(points)

                        // export pids in points order
                        DataOutputStream fw = null
                        FileWriter catPid = null
                        try {
                            fw = new DataOutputStream(
                                    new BufferedOutputStream(new FileOutputStream(file)))
                            if (values != null) {
                                for (int i = 0; i < values.length; i++) {
                                    fw.writeShort((int) values[i])
                                }
                            }

                            catPid = new FileWriter(file.getPath() + ".cat")
                            IntersectionFile f = Client.getLayerIntersectDao().getConfig().getIntersectionFile(field.id);

                            if (f != null) {
                                def maxKey = f.classes.keySet().max()
                                for (int i = 0; i <= maxKey; i++) {
                                    if (i > 0) catPid.write("\n")
                                    def value = f.classes.get(i)
                                    if (value != null) {
                                        catPid.write(value.name)
                                    }
                                }
                            }

                        } catch (err) {
                            log.error 'failed to export pid file', err
                        } finally {
                            if (fw != null) {
                                try {
                                    fw.close()
                                } catch (err) {
                                    log.error 'faild to close pid file', err
                                }
                            }
                            if (catPid != null) {
                                try {
                                    catPid.close()
                                } catch (err) {
                                    log.error 'faild to close cat2pid file', err
                                }
                            }
                        }
                    }
                }

                addOutput('file', '/sample/' + file.getName())
                addOutput('file', '/sample/' + file.getName() + '.cat')
            } catch (err) {
                log.error 'sampling error for field: ' + field.id, err
            }
        }

        return file
    }


}
