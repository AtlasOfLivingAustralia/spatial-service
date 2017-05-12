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

import au.org.ala.layers.dao.IntersectCallback;
import au.org.ala.layers.dao.LayerIntersectDAO;
import au.org.ala.layers.dto.IntersectionFile;
import au.org.ala.layers.util.IntersectUtil;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Adam
 */
public class BatchConsumer {
    static List<Thread> threads = new ArrayList<Thread>();
    static LinkedBlockingQueue<String> waitingBatchDirs;

    synchronized public static void start(LayerIntersectDAO layerIntersectDao, String batchDir, int numThreads) {
        if (threads.size() == 0) {
            waitingBatchDirs = new LinkedBlockingQueue<String>();

            int size = numThreads;
            for (int i = 0; i < size; i++) {
                Thread t = new BatchConsumerThread(waitingBatchDirs, layerIntersectDao, batchDir);
                t.start();
                threads.add(t);
            }

            //get jobs that may have been interrupted but a shutdown
            File f = new File(batchDir);
            File[] files = f.listFiles();
            Arrays.sort(files);
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()
                        && !(new File(files[i].getPath() + File.separator + "error.txt")).exists()
                        && !(new File(files[i].getPath() + File.separator + "finished.txt")).exists()) {
                    System.out.println("found incomplete batch_sampling: " + files[i].getPath());
                    try {
                        addBatch(files[i].getPath() + File.separator);
                    } catch (InterruptedException e) {

                    }
                }
            }
        }
    }

    public static void addBatch(String batchDir) throws InterruptedException {
        if (!waitingBatchDirs.contains(batchDir)) {
            waitingBatchDirs.put(batchDir);
        }
    }

    static void end() {
        for (Thread t : threads) {
            t.interrupt();
        }
        ;
    }
}

class BatchConsumerThread extends Thread {
    LinkedBlockingQueue<String> waitingBatchDirs;
    LayerIntersectDAO layerIntersectDao;
    String batchDir;

    public BatchConsumerThread(LinkedBlockingQueue<String> waitingBatchDirs, LayerIntersectDAO layerIntersectDao
            , String batchDir) {
        this.waitingBatchDirs = waitingBatchDirs;
        this.layerIntersectDao = layerIntersectDao;
        this.batchDir = batchDir;
    }

    private static void writeToFile(String filename, String string, boolean append) throws IOException {
        FileWriter fw = new FileWriter(filename, append);
        fw.write(string);
        fw.close();
    }

    private static String readFile(String filename) throws IOException {
        return FileUtils.readFileToString(new File(filename));
    }

    @Override
    public void run() {
        boolean repeat = true;
        String id = "";

        while (repeat) {
            String currentBatch = null;
            try {
                currentBatch = waitingBatchDirs.take();

                id = new File(currentBatch).getName();

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy hh:mm:ss:SSS");
                String str = sdf.format(new Date());
                BatchProducer.logUpdateEntry(id, "started", "started", str, null);
                writeToFile(currentBatch + "status.txt", "started at " + str, true);
                writeToFile(currentBatch + "started.txt", str, true);

                String fids = readFile(currentBatch + "fids.txt");
                String points = readFile(currentBatch + "points.txt");
                String gridcache = readFile(currentBatch + "gridcache.txt");

                ArrayList<String> sample = null;

                HashMap[] pointSamples = null;
                if ("1".equals(gridcache)) {
                    pointSamples = layerIntersectDao.sampling(points, 1);
                } else if ("2".equals(gridcache)) {
                    pointSamples = layerIntersectDao.sampling(points, 2);
                } else {
                    IntersectCallback callback = new ConsumerCallback(id);
                    sample = layerIntersectDao.sampling(fids.split(","), splitStringToDoublesArray(points, ','), callback);
                }

                //convert pointSamples to string array
                if (pointSamples != null) {
                    Set columns = new LinkedHashSet();
                    for (int i = 0; i < pointSamples.length; i++) {
                        columns.addAll(pointSamples[i].keySet());
                    }

                    //fids
                    fids = "";
                    for (Object o : columns) {
                        if (!fids.isEmpty()) {
                            fids += ",";
                        }
                        fids += o;
                    }

                    //columns
                    ArrayList<StringBuilder> sb = new ArrayList<StringBuilder>();
                    for (int i = 0; i < columns.size(); i++) {
                        sb.add(new StringBuilder());
                    }
                    for (int i = 0; i < pointSamples.length; i++) {
                        int pos = 0;
                        for (Object o : columns) {
                            sb.get(pos).append("\n").append(pointSamples[i].get(o));
                            pos++;
                        }
                    }

                    //format
                    sample = new ArrayList<String>();
                    for (int i = 0; i < sb.size(); i++) {
                        sample.add(sb.get(i).toString());
                    }
                }

                System.out.println("start csv output at " + sdf.format(new Date()));
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(currentBatch + "sample.csv"));
                IntersectUtil.writeSampleToStream(splitString(fids, ','), splitString(points, ','), sample, bos);
                bos.flush();
                bos.close();
                System.out.println("finish csv output at " + sdf.format(new Date()));

                str = sdf.format(new Date());
                BatchProducer.logUpdateEntry(id, "finished", "finished", str, fids.split(",").length + 1);
                writeToFile(currentBatch + "status.txt", "finished at " + str, true);
                writeToFile(currentBatch + "finished.txt", str, true);

            } catch (InterruptedException e) {
                //thread stop request
                repeat = false;
                break;
            } catch (Exception e) {
                if (currentBatch != null) {
                    try {
                        BatchProducer.logUpdateEntry(id, "error", "error", e.getMessage(), null);
                        writeToFile(currentBatch + "status.txt", "error " + e.getMessage(), true);
                        writeToFile(currentBatch + "error.txt", e.getMessage(), true);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                e.printStackTrace();
            }

            currentBatch = null;
        }
    }


    private String[] splitString(String string, char delim) {
        //count
        int count = 1;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                count++;
            }
        }

        String[] split = new String[count];
        int idx = 0;

        //position of last comma
        int lastI = -1;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                //write this one, lastI to i
                if (lastI + 1 == i) {
                    split[idx] = "";
                } else {
                    split[idx] = string.substring(lastI + 1, i);
                }
                lastI = i;
                idx++;
            }
        }
        //last string
        if (lastI + 1 == string.length()) {
            split[idx] = "";
        } else {
            split[idx] = string.substring(lastI + 1, string.length());
        }

        return split;
    }

    private double[][] splitStringToDoublesArray(String string, char delim) {
        //count
        int count = 1;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                count++;
            }
        }

        //parse points
        double[][] pointsD = new double[(count + 1) / 2][2];
        int idx = 0;

        //position of last comma
        int lastI = -1;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                //write this one, lastI to i
                if (lastI + 1 == i) {
                    pointsD[idx / 2][idx % 2] = Double.NaN;
                } else {
                    try {
                        pointsD[idx / 2][(idx + 1) % 2] = Double.parseDouble(string.substring(lastI + 1, i));
                    } catch (Exception e) {
                        pointsD[idx / 2][(idx + 1) % 2] = Double.NaN;
                    }
                }
                lastI = i;
                idx++;
            }
        }
        //last string
        if (lastI + 1 == string.length()) {
            pointsD[idx / 2][idx % 2] = Double.NaN;
        } else {
            try {
                pointsD[idx / 2][(idx + 1) % 2] = Double.parseDouble(string.substring(lastI + 1, string.length()));
            } catch (Exception e) {
                pointsD[idx / 2][(idx + 1) % 2] = Double.NaN;
            }
        }

        return pointsD;
    }

}

class ConsumerCallback implements IntersectCallback {
    private String id;

    public ConsumerCallback(String id) {
        this.id = id;
    }

    @Override
    public void setLayersToSample(IntersectionFile[] layersToSample) {

    }

    @Override
    public void setCurrentLayer(IntersectionFile layer) {

    }

    @Override
    public void setCurrentLayerIdx(Integer layerIdx) {
        BatchProducer.logUpdateEntry(id, null, null, null, layerIdx);
    }

    @Override
    public void progressMessage(String message) {
        BatchProducer.logUpdateEntry(id, null, "progressMessage", message, null);
    }
}