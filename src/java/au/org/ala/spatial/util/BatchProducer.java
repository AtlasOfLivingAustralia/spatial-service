/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p/>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p/>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial.util;

import org.apache.commons.io.FileUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Adam
 */
public class BatchProducer {
    static private ConcurrentHashMap log = new ConcurrentHashMap();

    static public String produceBatch(String batchPath, String requestInfo, String fids, String points, String gridcache) {
        String id = String.valueOf(System.currentTimeMillis());
        try {
            String dir = batchPath + File.separator + id + File.separator;
            new File(dir).mkdirs();

            Map m = new HashMap();

            m.put("points", (StringUtils.countOccurrencesOf(points, ",") + 1) / 2);
            m.put("fields", fids.split(",").length);
            m.put("progress", 0);
            m.put("waiting", "In queue");
            m.put("status", "waiting");

            log.put(id, m);

            writeToFile(dir + "request.txt", requestInfo);
            writeToFile(dir + "fids.txt", fids);
            writeToFile(dir + "points.txt", points);
            writeToFile(dir + "gridcache.txt", gridcache);

            BatchConsumer.addBatch(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return id;
    }

    private static void writeToFile(String filename, String string) throws IOException {
        FileWriter fw = new FileWriter(filename);
        fw.write(string);
        fw.close();
    }

    private static String readFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString();
    }

    public static void addInfoToMap(String batchPath, String batchId, Map map) throws IOException {
        Map more = (Map) log.get(batchId);
        if (more != null) {
            map.putAll(more);
        } else {
            String dir = batchPath + File.separator + batchId + File.separator;
            if (new File(dir).exists()) {
                int count = 0;

                if (new File(dir + "error.txt").exists()) {
                    count++;
                    map.put("error", readFile(dir + "error.txt"));
                    map.put("status", "error");
                }
                if (new File(dir + "finished.txt").exists()) {
                    count++;
                    map.put("finished", readFile(dir + "finished.txt"));
                    map.put("status", "finished");
                }
                if (new File(dir + "started.txt").exists()) {
                    if (count == 0) {
                        //count points
                        map.put("points", getPointCount(batchPath, batchId));

                        //count fields
                        int fieldCount = getFieldCount(batchPath, batchId);
                        map.put("fields", fieldCount);

                        map.put("progress", 0);
                    }
                    count++;
                    map.put("started", readFile(dir + "started.txt"));
                    map.put("status", "started");
                }

                if (count == 0) {
                    map.put("waiting", "In queue");
                    map.put("status", "waiting");
                }
            } else {
                map.put("error", "unknown batchId: " + batchId);
            }

            log.put(batchId, map);
        }
    }

    public static void logUpdateEntry(String batchId, String newStatus, String infoKey, String infoValue, Integer newProgress) {
        Map m = (Map) log.get(batchId);

        if (m == null) {
            //cannot update status for a missing entry
            return;
        }

        if (newStatus != null) {
            //remove keys that can become irrelevant with status updates
            m.remove("waiting");

            m.put("status", newStatus);
        }

        if (infoKey != null) {
            m.put(infoKey, infoValue);
        }

        if (newProgress != null) {
            //progress cannot get smaller
            if (m.containsKey("progress")) {
                Integer oldProgress = (Integer) m.get("progress");
                if (oldProgress < newProgress) {
                    m.put("progress", newProgress);
                }
            } else {
                m.put("progress", newProgress);
            }

        }

    }

    private static int getPointCount(String batchPath, String batchId) throws IOException {
        int count = 0;
        String dir = batchPath + File.separator + batchId + File.separator;
        if (new File(dir).exists()) {
            File f = new File(dir + "points.txt");
            if (f.exists() && f.length() > 0) {
                count = (StringUtils.countOccurrencesOf(
                        FileUtils.readFileToString(f),
                        ",") + 1) / 2;
            }
        }
        return count;
    }

    private static int getFieldCount(String batchPath, String batchId) throws IOException {
        int count = 0;
        String dir = batchPath + File.separator + batchId + File.separator;
        if (new File(dir).exists()) {
            File f = new File(dir + "fids.txt");
            if (f.exists() && f.length() > 0) {
                count = FileUtils.readFileToString(new File(dir + "fids.txt")).split(",").length;
            }
        }
        return count;
    }
}
