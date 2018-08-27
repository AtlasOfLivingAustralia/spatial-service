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

package au.org.ala.spatial.util;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class RecordsSmall {
    private static final Logger logger = Logger.getLogger(RecordsSmall.class);

    List<String> lsids;

    final Object pointBufferLock = new Object();
    LRUMap pointBuffer = new LRUMap(10000);

    long maxPoints;
    RandomAccessFile points;
    RandomAccessFile pointsToSpecies;
    DataInputStream pointsDis;
    DataInputStream pointsToSpeciesDis;

    String filename;

    public static String[] fileList() {
        return new String[]{
                "records.csv.small.pointsUniquePoints", "records.csv.small.points",
                "records.csv.small.species", "records.csv.small.pointsToSpecies", "records.csv.small.speciesCount",
                "records.csv.small.pointsUniqueIdx"
        };
    }

    public RecordsSmall(String dir) throws IOException {
        this.filename = dir + File.separator;

        //look for a small file
        File smallFile = new File(filename + "records.csv.small.species");

        if (!smallFile.exists() && new File(filename + "records.csv").exists()) {
            try {
                //makeSmallFile(filename);
                //makeUniquePoints();
            } catch (Exception e) {
                logger.error("failed to make small records files", e);
            }
        }

        try {
            //makeSmallFile(filename);
            makeUniquePoints();
        } catch (Exception e) {
            logger.error("failed to make small records files", e);
        }


        //read species
        if (smallFile.exists()) {
            try {
                //open points and pointsToSpecies
                points = new RandomAccessFile(filename + "records.csv.small.points", "r");
                pointsToSpecies = new RandomAccessFile(filename + "records.csv.small.pointsToSpecies", "r");
                maxPoints = new File(filename + "records.csv.small.pointsToSpecies").length() / 4;
                pointsDis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename + "records.csv.small.points")));
                pointsToSpeciesDis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename + "records.csv.small.pointsToSpecies")));

                lsids = FileUtils.readLines(new File(filename + "records.csv.small.species"));

                getUniquePointsAll();
            } catch (Exception e) {
                logger.error("failed to open small records file", e);
            }
        }
    }

    private void makeSmallFile(String filename) throws Exception {
        FileWriter outputSpecies = new FileWriter(filename + "records.csv.small.species");
        DataOutputStream outputPoints = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filename + "records.csv.small.points")));
        DataOutputStream outputPointsToSpecies = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filename + "records.csv.small.pointsToSpecies")));

        Map<String, Integer> lsidMap = new HashMap<String, Integer>(200000);
        byte start = 0;
        BufferedReader br = new BufferedReader(new FileReader(filename + "records.csv"));
        int[] header = new int[3];
        int row = start;
        int currentCount = 0;
        String[] line = new String[3];

        String rawline;
        while ((rawline = br.readLine()) != null) {
            currentCount++;
            int p1 = rawline.indexOf(44);
            int p2 = rawline.indexOf(44, p1 + 1);
            if (p1 >= 0 && p2 >= 0) {
                line[0] = rawline.substring(0, p1);
                line[1] = rawline.substring(p1 + 1, p2);
                line[2] = rawline.substring(p2 + 1, rawline.length());
                if (currentCount % 100000 == 0) {
                    System.out.print("\rreading row: " + currentCount);
                }

                if (row == 0) {
                    for (int e = 0; e < line.length; e++) {
                        if (line[e].equals("names_and_lsid")) {
                            header[0] = e;
                        }

                        if (line[e].equals("longitude")) {
                            header[1] = e;
                        }

                        if (line[e].equals("latitude")) {
                            header[2] = e;
                        }
                    }

                    logger.debug("header: " + header[0] + "," + header[1] + "," + header[2]);
                } else if (line.length >= 3) {
                    try {
                        double lat = Double.parseDouble(line[header[2]]);
                        double lng = Double.parseDouble(line[header[1]]);

                        String species = line[header[0]];

                        Integer idx = lsidMap.get(species);
                        if (idx == null) {
                            idx = lsidMap.size();
                            lsidMap.put(species, idx);

                            outputSpecies.write(species);
                            outputSpecies.write("\n");
                        }

                        outputPoints.writeDouble(lat);
                        outputPoints.writeDouble(lng);

                        outputPointsToSpecies.writeInt(idx);
                    } catch (Exception e) {
                        logger.error("failed to read records.csv row: " + row, e);
                    }
                }

                row++;
            }
        }

        br.close();

        outputPointsToSpecies.flush();
        outputPointsToSpecies.close();
        outputPoints.flush();
        outputPoints.close();
        outputSpecies.flush();
        outputSpecies.close();

        FileUtils.writeStringToFile(new File(filename + "records.csv.small.speciesCount"), String.valueOf(lsidMap.size()));
    }

    private void makeUniquePoints() throws Exception {
        //make unique points and index
        points = new RandomAccessFile(filename + "records.csv.small.points", "r");
        double[] allPoints = getPointsAll();
        Coord[] p = new Coord[allPoints.length / 2];
        for (int i = 0; i < allPoints.length; i += 2) {
            p[i / 2] = new Coord(allPoints[i], allPoints[i + 1], i / 2);
        }
        allPoints = null; //make available to GC
        Arrays.sort(p, new Comparator<Coord>() {
            public int compare(Coord o1, Coord o2) {
                return o1.longitude == o2.longitude ? (o1.latitude == o2.latitude ? 0 : (o1.latitude - o2.latitude > 0.0 ? 1 : -1)) : (o1.longitude - o2.longitude > 0.0 ? 1 : -1);
            }
        });

        DataOutputStream outputUniquePoints = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filename + "records.csv.small.pointsUniquePoints")));
        DataOutputStream outputUniqueIdx = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filename + "records.csv.small.pointsUniqueIdx")));

        int pos = -1; //first point is set after pos++
        int[] newPos = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            if (i == 0 || p[i].latitude != p[i - 1].latitude || p[i].longitude != p[i - 1].longitude) {
                outputUniquePoints.writeDouble(p[i].latitude);
                outputUniquePoints.writeDouble(p[i].longitude);
                pos++;
            }
            newPos[p[i].pos] = pos;
        }
        for (int i = 0; i < p.length; i++) {
            outputUniqueIdx.writeInt(newPos[i]);
        }

        outputUniqueIdx.flush();
        outputUniqueIdx.close();
        outputUniquePoints.flush();
        outputUniquePoints.close();

        points.close();
    }

    public String getSpecies(int pos) {
        return lsids.get(pos);
    }

    public int getSpeciesNumber(int pos) throws Exception {
        if (pos * 4L != pointsToSpecies.getFilePointer()) {
            pointsToSpecies.seek(pos * 4L);
        }

        return pointsToSpecies.readInt();
    }

    private double[] getPoint(int pos) throws Exception {
        //prepare the point for reading
        double[] point;
        synchronized (pointBufferLock) {
            point = (double[]) pointBuffer.get(pos);
            if (point == null) {
                if (pos * 16L != points.getFilePointer()) {
                    points.seek(pos * 16L);
                }

                point = new double[]{points.readDouble(), points.readDouble()};

                pointBuffer.put(pos, point);
            }
        }

        return point;
    }

    public double[] getPointUnsafe(int pos) throws Exception {
        //prepare the point for reading, not multithread safe
        double[] point;

        if (pos * 16L != points.getFilePointer()) {
            points.seek(pos * 16L);
        }

        point = new double[]{points.readDouble(), points.readDouble()};

        return point;
    }

    public double[] getPointsAll() throws Exception {
        return getAllDouble(filename + "records.csv.small.points");
    }

    private double[] getAllDouble(String file) throws Exception {
        File f = new File(file);

        int size = (int) f.length() / 8;
        double[] all = new double[size];

        byte[] e = FileUtils.readFileToByteArray(f);

        ByteBuffer bb = ByteBuffer.wrap(e);

        for (int i = 0; i < size; i++) {
            all[i] = bb.getDouble();
        }

        return all;
    }

    public double[] getUniquePointsAll() throws Exception {
        return getAllDouble(filename + "records.csv.small.pointsUniquePoints");
    }

    public int[] getUniqueIdx() throws Exception {
        File f = new File(filename + "records.csv.small.pointsUniqueIdx");

        int size = (int) f.length() / 4;
        int[] all = new int[size];

        byte[] e = FileUtils.readFileToByteArray(f);

        ByteBuffer bb = ByteBuffer.wrap(e);

        for (int i = 0; i < size; i++) {
            all[i] = bb.getInt();
        }

        return all;
    }

    // close open files
    public void close() throws Exception {
        try {
            pointsDis.close();
            pointsToSpeciesDis.close();
            points.close();
            pointsToSpecies.close();
        } catch (Exception e) {
            logger.error("failed to close records.small", e);
        }
    }

    // this is a reset for the 'getNext...()' functions
    public void resetNextFunctions() throws Exception {
        pointsDis.close();
        pointsToSpeciesDis.close();

        pointsDis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename + "records.csv.small.points")));
        pointsToSpeciesDis = new DataInputStream(new BufferedInputStream(new FileInputStream(filename + "records.csv.small.pointsToSpecies")));
    }

    // for sequential reads, 2x getNextCoordinate for each getNextSpecies
    public int getNextSpecies() throws Exception {
        return pointsToSpeciesDis.readInt();
    }

    // for sequential reads, longitude then latitude
    public double getNextCoordinate() throws Exception {
        return pointsDis.readDouble();
    }

    public double getLongitude(int pos) throws Exception {
        return getPoint(pos)[1];
    }

    public double getLatitude(int pos) throws Exception {
        return getPoint(pos)[0];
    }

    public int getRecordsSize() {
        return (int) maxPoints;
    }

    public int getSpeciesSize() {
        return lsids.size();
    }

    private class Coord {
        double longitude;
        double latitude;
        int pos;

        public Coord(double latitude, double longitude, int pos) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.pos = pos;
        }
    }
}
