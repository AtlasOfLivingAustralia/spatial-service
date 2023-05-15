/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial.intersect


import groovy.util.logging.Slf4j
import org.apache.commons.lang3.tuple.ImmutablePair

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors

/**
 * SimpleShapeFile is a representation of a Shape File for
 * intersections with points
 * <p/>
 * .shp MULTIPOLYGON only
 * .dbf can read values from String and Number columns only
 * <p/>
 *
 * @author Adam Collins
 */

@Slf4j
//@CompileStatic
class SimpleShapeFile implements Serializable {

    static final long serialVersionUID = -9046250209453575076L

    /**
     * .shp file header contents
     */
    ShapeHeader shapeheader
    /**
     * .shp file record contents
     */
    ShapeRecords shaperecords
    /**
     * Charset for .dbf (read from .cpg)
     */
    Charset charset
    /**
     * .dbf contents
     */
    DBF dbf
    /**
     * for balancing shape files with large numbers of shapes.
     */
    ShapesReference shapesreference
    /**
     * one dbf column, for use after loading from a file
     */
    int[] singleColumn
    /**
     * one lookup for a dbf column, for use after loading from a file
     */
    String[] singleLookup

    /**
     * one dbf column, for use after loading from a file
     */
    List<int[]> multiColumn
    /**
     * one lookup for a dbf column, for use after loading from a file
     */
    List<String[]> multiLookup

    /**
     * column names as they appear in muliColumn and multiLookup
     */
    List<String> multiNames

    protected SimpleShapeFile() {
    }


    /**
     * Constructor for a SimpleShapeFile, requires .dbf and .shp files present
     * on the fileprefix provided.
     *
     * @param fileprefix file path for valid files after appending .shp and .dbf
     */
    SimpleShapeFile(String fileprefix, String column) {
        //If fileprefix exists as-is it is probably a saved SimpleShapeFile
        if (loadRegion(fileprefix)) {
            //previously saved region loaded
        } else {
            /* read cpg (encoding) */
            charset = new CPG(fileprefix + ".cpg").getCharset()
            /* read dbf */
            dbf = new DBF(fileprefix + ".dbf", charset, column)
            singleLookup = getColumnLookup(0)
            singleColumn = new int[dbf.dbfrecords.records.size()]
            for (int i = 0; i < singleColumn.length; i++) {
                singleColumn[i] = java.util.Arrays.binarySearch(singleLookup, dbf.getValue(i, 0))
            }
            dbf = null

            /* read shape header */
            shapeheader = new ShapeHeader(fileprefix)

            /* read shape records */
            shaperecords = new ShapeRecords(fileprefix, shapeheader.getShapeType())
            shapeheader = null

            /* create shapes reference for intersections */
            shapesreference = new ShapesReference(shaperecords)
            shaperecords = null
        }
    }

    SimpleShapeFile(String fileprefix, String[] columns) {
        //If fileprefix exists as-is it is probably a saved SimpleShapeFile
        if (loadRegion(fileprefix)) {
            //previously saved region loaded
        } else {
            /* read cpg (encoding) */
            charset = new CPG(fileprefix + ".cpg").getCharset()
            /* read dbf */
            multiColumn = new ArrayList<int[]>()
            multiLookup = new ArrayList<String[]>()
            multiNames = new ArrayList<String>()
            dbf = new DBF(fileprefix + ".dbf", charset, columns)
            for (int j = 0; j < columns.length; j++) {
                String[] names = dbf.getColumnLookup(j)
                int[] ids = new int[dbf.dbfrecords.records.size()]
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = java.util.Arrays.binarySearch(names, dbf.getValue(i, j))
                }
                multiColumn.add(ids)
                multiLookup.add(names)
                multiNames.add(columns[j])
            }
            dbf = null

            /* read shape header */
            shapeheader = new ShapeHeader(fileprefix)

            /* read shape records */
            shaperecords = new ShapeRecords(fileprefix, shapeheader.getShapeType())
            shapeheader = null

            /* create shapes reference for intersections */
            shapesreference = new ShapesReference(shaperecords)
            shaperecords = null
        }
    }

    /**
     * save partial file (enough to reload and use intersect function)
     *
     * @param filename
     */
    static ComplexRegion loadShapeInRegion(String filename, int idx) {
        ComplexRegion cr = null
        FileInputStream fis = null
        try {
            fis = new FileInputStream(filename + "_" + idx)
            BufferedInputStream bis = new BufferedInputStream(fis)
            ObjectInputStream ois = new ObjectInputStream(bis)
            cr = (ComplexRegion) ois.readObject()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
        return cr
    }

    /**
     * defines a region by a points string, POLYGON only
     * <p/>
     * TODO: define better format for parsing, including BOUNDING_BOX and CIRCLE
     *
     * @param pointsString points separated by ',' with longitude and latitude separated by ':'
     * @return SimpleRegion object
     */
    static SimpleRegion parseWKT(String pointsString) {
        if (pointsString == null) {
            return null
        }

        ArrayList<ArrayList<SimpleRegion>> regions = new ArrayList<ArrayList<SimpleRegion>>()

        int bracket1st = pointsString.indexOf('(')
        int bracket2nd = pointsString.indexOf('(', bracket1st + 1)
        int bracket3rd = pointsString.indexOf('(', bracket2nd + 1)
        if (pointsString.startsWith("GEOMETRYCOLLECTION")) {
            regions.addAll(parseGeometryCollection(pointsString.substring(bracket1st + 1, pointsString.length() - 1)))
        } else if (pointsString.startsWith("MULTIPOLYGON")) {
            regions.addAll(parseMultipolygon(pointsString.substring(bracket3rd + 1, pointsString.length() - 3)))
        } else if (pointsString.startsWith("POLYGON")) {
            regions.add(parsePolygon(pointsString.substring(bracket2nd + 1, pointsString.length() - 2)))
        }

        if (regions.size() == 0) {
            return null
        } else if (regions.size() == 1 && regions.get(0).size() == 1) {
            return regions.get(0).get(0)
        } else {
            ComplexRegion cr = new ComplexRegion()
            for (int i = 0; i < regions.size(); i++) {
                cr.addSet(regions.get(i))
            }
            cr.useMask(-1, -1, -1)
            return cr
        }
    }

    static ArrayList<ArrayList<SimpleRegion>> parseGeometryCollection(String pointsString) {
        ArrayList<String> stringsList = new ArrayList<String>()

        int posStart = minPos(pointsString, "POLYGON", "MULTIPOLYGON", 0)
        int posEnd = minPos(pointsString, "POLYGON", "MULTIPOLYGON", posStart + 10)
        while (posEnd > 0) {
            stringsList.add(pointsString.substring(posStart, posEnd - 1))
            posStart = posEnd
            posEnd = minPos(pointsString, "POLYGON", "MULTIPOLYGON", posStart + 10)
        }
        stringsList.add(pointsString.substring(posStart))

        ArrayList<ArrayList<SimpleRegion>> regions = new ArrayList<ArrayList<SimpleRegion>>()
        for (int i = 0; i < stringsList.size(); i++) {
            int bracket1st = stringsList.get(i).indexOf('(')
            int bracket2nd = stringsList.get(i).indexOf('(', bracket1st + 1)
            int bracket3rd = stringsList.get(i).indexOf('(', bracket2nd + 1)

            if (stringsList.get(i).startsWith("MULTIPOLYGON")) {
                //remove trailing ")))"
                regions.addAll(parseMultipolygon(stringsList.get(i).substring(bracket3rd, stringsList.get(i).length() - 3)))
            } else if (stringsList.get(i).startsWith("POLYGON")) {
                //remove trailing "))"
                regions.add(parsePolygon(stringsList.get(i).substring(bracket2nd, stringsList.get(i).length() - 2)))
            }
        }

        return regions
    }

    static ArrayList<ArrayList<SimpleRegion>> parseMultipolygon(String multipolygon) {
        ArrayList<ArrayList<SimpleRegion>> regions = new ArrayList<ArrayList<SimpleRegion>>()
        String[] splitMultipolygon = multipolygon.split("\\)\\),( )*\\(\\(")
        for (int j = 0; j < splitMultipolygon.length; j++) {
            regions.add(parsePolygon(splitMultipolygon[j]))
        }
        return regions
    }

    static ArrayList<SimpleRegion> parsePolygon(String polygon) {
        ArrayList<SimpleRegion> regions = new ArrayList<SimpleRegion>()
        for (String p : polygon.split("\\),( )*\\(")) {
            regions.add(SimpleRegion.parseSimpleRegion(p))
        }
        return regions
    }

    static int minPos(String lookIn, String lookFor1, String lookFor2, int startPos) {
        int pos, p1, p2
        p1 = lookIn.indexOf(lookFor1, startPos)
        p2 = lookIn.indexOf(lookFor2, startPos)
        if (p1 < 0) {
            pos = p2
        } else if (p2 < 0) {
            pos = p1
        } else {
            pos = Math.min(p1, p2)
        }
        return pos
    }

    /**
     * save partial file (enough to reload and use intersect function)
     *
     * @param filename
     * @return true when successful
     */
    boolean loadRegion(String filename) {
        boolean result = false
        if (new File(filename).exists()) {
            FileInputStream fis = null
            try {
                fis = new FileInputStream(filename)
                BufferedInputStream bis = new BufferedInputStream(fis)
                ObjectInputStream ois = new ObjectInputStream(bis)
                shapesreference = (ShapesReference) ois.readObject()

                singleLookup = (String[]) ois.readObject()

                singleColumn = (int[]) ois.readObject()

                result = true
            } catch (Exception e) {
                log.error(e.getMessage(), e)
            } finally {
                if (fis != null) {
                    try {
                        fis.close()
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    }
                }
            }
        }
        return result
    }

    /**
     * returns a list of column names in the
     * .dbf file
     *
     * @return list of column names as String []
     */
    String[] listColumns() {
        return dbf.getColumnNames()
    }

    /**
     * returns set of values found in the .dbf file
     * at a column number, zero base.
     *
     * @param column integer representing column whose set of values
     *               is to be returned.  see <code>listColumns()</code> for listing
     *               column names.
     * @return set of values in the column as String [] sorted
     */
    String[] getColumnLookup(int column) {
        if (singleLookup != null) {
            return singleLookup
        }
        if (multiLookup != null) {
            return multiLookup.get(column)
        }
        return dbf.getColumnLookup(column)
    }

    /**
     * returns the position, zero indexed, of the provided
     * column_name from within the .dbf
     *
     * @param column_name
     * @return -1 if not found, otherwise column index number, zero base
     */
    int getColumnIdx(String column_name) {
        if (singleColumn != null) {
            return 0
        }
        if (multiColumn != null) {
            for (int i = 0; i < multiNames.size(); i++) {
                if (multiNames.get(i).equalsIgnoreCase(column_name)) {
                    return i
                }
            }
        }
        return dbf.getColumnIdx(column_name)
    }

    /**
     * returns the position, zero indexed, of the provided
     * column_name from within the .dbf
     *
     * @param column_name
     * @return -1 if not found, otherwise column index number, zero base
     */
    int[] getColumnIdxs(String column_name) {
        if (singleColumn != null) {
            return singleColumn
        }
        if (multiColumn != null) {
            for (int i = 0; i < multiNames.size(); i++) {
                if (multiNames.get(i).equalsIgnoreCase(column_name)) {
                    return multiColumn.get(i)
                }
            }
        }
        return null
    }

    /**
     * use when created from a shape file
     * <p/>
     * identifies the index within a lookup list provided
     * for each provided point, or -1 for not found.
     *
     * @param points double [n][2]
     *               where
     *               n is number of points
     *               [][0] is longitude
     *               [][1] is latitude
     * @param lookup String [], same as output from <code>getColumnLookup(column)</code>
     * @param column .dbf column value to use
     * @return index within a lookup list provided
     * for each provided point, or -1 for not found as int []
     */
    int[] intersect(double[][] points, String[] lookup, int column, int threadcount) {
        int i

        //copy, tag and sort points
        PointPos[] p = new PointPos[points.length]
        for (i = 0; i < p.length; i++) {
            p[i] = new PointPos(points[i][0], points[i][1], i)
        }
        java.util.Arrays.sort(p, new Comparator<PointPos>() {

            @Override
            int compare(PointPos o1, PointPos o2) {
                if (o1.x == o2.x) {
                    if (o1.y == o2.y) {
                        return 0
                    } else {
                        return ((o1.y - o2.y) > 0) ? 1 : -1
                    }
                } else {
                    return ((o1.x - o2.x) > 0) ? 1 : -1
                }
            }
        })

        /* setup for thread count */
        ArrayList<Integer> threadstart = new ArrayList(threadcount * 10)
        int step = (int) Math.ceil(points.length / (double) (threadcount * 10))
        if (step % 2 != 0) {
            step++
        }
        int pos = 0
        for (i = 0; i < threadcount * 10; i++) {
            threadstart.add(Integer.valueOf(pos))
            pos += step
        }

        LinkedBlockingQueue<Integer> lbq = new LinkedBlockingQueue(threadstart)
        CountDownLatch cdl = new CountDownLatch(lbq.size())

        IntersectionThread[] it = new IntersectionThread[threadcount]

        int[] target = new int[points.length]

        for (i = 0; i < threadcount; i++) {
            it[i] = new IntersectionThread(shapesreference, p, lbq, step, target, cdl)
        }

        //wait for all parts to be finished
        try {
            cdl.await()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        //end threads
        for (i = 0; i < threadcount; i++) {
            it[i].interrupt()
        }

        //transform target from shapes_idx to column_idx
        if (singleColumn == null && multiColumn == null) {
            for (i = 0; i < target.length; i++) {
                String s = dbf.getValue(target[i], column)
                int v = java.util.Arrays.binarySearch(lookup, s)
                if (v < 0) {
                    v = -1
                }
                target[i] = v
            }
        } else if (singleColumn != null) {
            for (i = 0; i < target.length; i++) {
                if (target[i] >= 0 && target[i] < singleColumn.length) {
                    target[i] = singleColumn[target[i]]
                } else {
                    target[i] = -1
                }
            }
        } else {
            for (i = 0; i < target.length; i++) {
                int[] multiCol = multiColumn.get(column)
                if (target[i] >= 0 && target[i] < multiCol.length) {
                    target[i] = multiCol[target[i]]
                } else {
                    target[i] = -1
                }
            }
        }

        return target
    }

    String intersect(double longitude, double latitude) {
        int idx = shapesreference.intersection(longitude, latitude)

        if (idx >= 0 && idx < singleColumn.length) {
            return singleLookup[singleColumn[idx]]
        }

        return null
    }

    List<ImmutablePair<String, Double>> intersect(double longitude, double latitude, double distance) {
        List<ImmutablePair<Integer, Double>> distancesIndices = shapesreference.intersection(longitude, latitude, distance)
        List<ImmutablePair<String, Double>> withValues = new ArrayList<>()

        for (ImmutablePair<Integer, Double> r : distancesIndices) {
            if (r.left >= 0 && r.left < singleColumn.length) {
                withValues.add(ImmutablePair.of(singleLookup[singleColumn[r.left]], r.right))
            }
        }

        return withValues
    }

    int intersectInt(double longitude, double latitude) {
        if (singleColumn != null) {
            int v = shapesreference.intersection(longitude, latitude)
            if (v >= 0) {
                return singleColumn[v]
            } else {
                return -1
            }
        } else {
            return shapesreference.intersection(longitude, latitude)
        }
    }

    List<ImmutablePair<Integer, Double>> intersectInt(double longitude, double latitude, double distance) {
        List<ImmutablePair<Integer, Double>> distancesIndices = shapesreference.intersection(longitude, latitude, distance)
        List<ImmutablePair<Integer, Double>> withValues = new ArrayList<>()
        for (ImmutablePair<Integer, Double> r : distancesIndices) {
            if (singleColumn != null) {
                if (r.left >= 0) {
                    withValues.add(ImmutablePair.of(singleColumn[r.left], r.right))
                } else {
                    withValues.add(ImmutablePair.of(-1, r.right))
                }
            } else {
                withValues.add(ImmutablePair.of(r.left, r.right))
            }
        }
        return withValues
    }

    /**
     * gets shape header as String
     *
     * @return String
     */
    String getHeaderString() {
        return shapeheader.toString()
    }

    String getValueString(int idx) {
        return singleLookup[idx]
    }

    String[] getColumnLookup() {
        return singleLookup
    }
}

/**
 * represents partial shape file header structure
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class ShapeHeader implements Serializable {

    static final long serialVersionUID = 1219127870707511387L
    //private static final Logger logger = log.getLogger(ShapeHeader.class);
    /* from .shp file header specification */
    int filecode
    int filelength
    int version
    int shapetype
    double[] boundingbox

    /* TODO: use appropriately for failed constructor */
    boolean isvalid

    /**
     * constructor takes shapefile file path, appends .shp itself,
     * and reads in the shape file header values.
     * <p/>
     * TODO: any validation
     *
     * @param fileprefix
     */
    ShapeHeader(String fileprefix) {
        FileInputStream fis = null
        try {
            fis = new FileInputStream(fileprefix + ".shp")
            FileChannel fc = fis.getChannel()
            ByteBuffer buffer = ByteBuffer.allocate(1024)        //header will be smaller

            fc.read(buffer)
            buffer.flip()

            buffer.order(ByteOrder.BIG_ENDIAN)
            filecode = buffer.getInt()
            buffer.getInt()
            buffer.getInt()
            buffer.getInt()
            buffer.getInt()
            buffer.getInt()
            filelength = buffer.getInt()

            buffer.order(ByteOrder.LITTLE_ENDIAN)
            version = buffer.getInt()
            shapetype = buffer.getInt()
            boundingbox = new double[8]
            for (int i = 0; i < 8; i++) {
                boundingbox[i] = buffer.getDouble()
            }

            isvalid = true
        } catch (Exception e) {
            log.error("loading header error: " + fileprefix + ": " + e.toString(), e)
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    /**
     * returns shape file type as indicated in header
     *
     * @return shape file type as int
     */
    int getShapeType() {
        return shapetype
    }

    /**
     * format .shp header contents
     *
     * @return
     */
    @Override
    String toString() {

        int i = 0


        String sb = "\r\nFile Code: \r\n" +
                filecode +
                "\r\nFile Length: \r\n" +
                filelength +
                "\r\nVersion: \r\n" +
                version +
                "\r\nShape Type: \r\n" +
                shapetype +
                "\r\nXmin: \r\n" +
                boundingbox[i++] +
                "\r\nYmin: \r\n" +
                boundingbox[i++] +
                "\r\nXmax: \r\n" +
                boundingbox[i++] +
                "\r\nYmax: \r\n" +
                boundingbox[i++] +
                "\r\nZmin: \r\n" +
                boundingbox[i++] +
                "\r\nZmax: \r\n" +
                boundingbox[i++] +
                "\r\nMmin: \r\n" +
                boundingbox[i++] +
                "\r\nMmax: \r\n" +
                boundingbox[i++]

        return sb
    }

    /**
     * @return true iff header loaded and passed validation
     */
    boolean isValid() {
        return isvalid
    }
}

/**
 * collection of shape file records
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class ShapeRecords implements Serializable {

    static final long serialVersionUID = -8141403235810528840L
    //private static final Logger logger = log.getLogger(ShapeRecords.class);
    /**
     * true if constructor was successful
     */
    boolean isvalid
    ArrayList<ComplexRegion> region

    /**
     * constructor creates the shape file from filepath
     * with specified shape type (only 5, MULTIPOLYGON for now)
     * <p/>
     * TODO: static
     * TODO: validation
     *
     * @param fileprefix
     * @param shapetype
     */
    ShapeRecords(String fileprefix, int shapetype) {
        isvalid = false
        FileInputStream fis = null
        try {
            fis = new FileInputStream(fileprefix + ".shp")
            FileChannel fc = fis.getChannel()
            ByteBuffer buffer = ByteBuffer.allocate((int) fc.size() - 100)

            fc.read(buffer, 100)                //records header starts at 100
            buffer.flip()
            buffer.order(ByteOrder.BIG_ENDIAN)

            region = new ArrayList<ComplexRegion>()
            while (buffer.hasRemaining()) {
                ShapeRecord shr = new ShapeRecord(buffer, shapetype, region.size())

                if (shr != null && shr.shape != null) {
                    ComplexRegion sr = new ComplexRegion()
                    ArrayList<SimpleRegion> regions = new ArrayList()

                    /* add each polygon (list of points) belonging to
                     * this shape record to the new ComplexRegion
                     */
                    for (int j = 0; j < shr.getNumberOfParts(); j++) {
                        SimpleRegion s = new SimpleRegion()
                        s.setPolygon(shr.getPoints(j))
                        //sr.addPolygon(s);
                        regions.add(s)
                    }

                    sr.addSet(regions)

                    region.add(sr)
                } else {
                    //no more records
                    break
                }
            }

            isvalid = true
        } catch (Exception e) {
            log.error("loading shape records error: " + fileprefix + ": " + e.toString(), e)
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    /**
     * @return true iff records loaded and passed validation
     */
    boolean isValid() {
        return isvalid
    }

    /**
     * creates a list of ComplexRegion objects from
     * loaded shape file records	 *
     *
     * @return
     */
    ArrayList<ComplexRegion> getRegions() {
        return region
    }

    /**
     * gets number of shape records
     *
     * @return
     */
    int getNumberOfRecords() {
        return region.size()
    }
}

/**
 * collection of shape file records
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class ShapeRecord implements Serializable {

    static final long serialVersionUID = -4426292545633280160L
    //private static final Logger logger = log.getLogger(ShapeRecord.class);
    int recordnumber
    int contentlength
    Shape shape

    ShapeRecord(ByteBuffer bb, int shapetype, int expectedRecordNumber) {
        bb.order(ByteOrder.BIG_ENDIAN)
        recordnumber = bb.getInt()

        if (recordnumber != expectedRecordNumber + 1) {
            return
        }

        contentlength = bb.getInt()

        switch (shapetype) {
            case 5:
                shape = new Polygon(bb)
                break
            case 15:
                shape = new PolygonZ(bb, contentlength)
                break
            default:
                log.debug("unknown shape type: " + shapetype)
        }
    }

    /**
     * format .shp record summary
     *
     * @return String
     */
    @Override
    String toString() {
        if (shape != null) {
            return "Record Number: " + recordnumber + ", Content Length: " + contentlength +shape
        }

        return "Record Number: " + recordnumber + ", Content Length: " + contentlength
    }

    /**
     * gets the list of points for a shape part
     *
     * @param part index of shape part
     * @return points as double[]
     * where
     * [] is longitude,latitude
     */
    double[] getPoints(int part) {
        return shape.getPoints(part)
    }

    /**
     * gets number of parts in this shape
     *
     * @return number of parts as int
     */
    int getNumberOfParts() {
        return shape.getNumberOfParts()
    }
}

/**
 * empty shape template
 * <p/>
 * TODO: abstract
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class Shape implements Serializable {

    static final long serialVersionUID = 8573677305368105719L

    /**
     * default constructor
     */
    Shape() {
    }

    /**
     * returns a list of points for the numbered shape part.
     *
     * @param part index of part to return
     * @return double[] containing longitude and latitude
     * pairs where
     * [] is longitude,latitude
     */
    double[] getPoints(int part) {
        return null
    }

    /**
     * returns number of parts in this shape
     *
     * @return number of parts as int
     */
    int getNumberOfParts() {
        return 0
    }
}

/**
 * object for shape file POLYGON
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class Polygon extends Shape {

    /**
     * shape file POLYGON record fields
     */
    int shapetype
    double[] boundingbox
    int numparts
    int numpoints
    int[] parts
    double[] points

    /**
     * creates a shape file POLYGON from a ByteBuffer
     * <p/>
     * TODO: static
     *
     * @param bb ByteBuffer containing record bytes
     *           from a shape file POLYGON record
     */
    Polygon(ByteBuffer bb) {
        int i

        bb.order(ByteOrder.LITTLE_ENDIAN)
        shapetype = bb.getInt()

        boundingbox = new double[4]
        for (i = 0; i < 4; i++) {
            boundingbox[i] = bb.getDouble()
        }

        numparts = bb.getInt()

        numpoints = bb.getInt()

        parts = new int[numparts]
        for (i = 0; i < numparts; i++) {
            parts[i] = bb.getInt()
        }

        points = new double[numpoints * 2]            //x,y pairs
        for (i = 0; i < numpoints * 2; i++) {
            points[i] = bb.getDouble()
        }
    }

    /**
     * output .shp POLYGON summary
     *
     * @return String
     */
    @Override
    String toString() {

        String sb = "(" +
                boundingbox[0] +
                ", " +
                boundingbox[1] +
                ") (" +
                boundingbox[2] +
                ", " +
                boundingbox[3] +
                ") parts=" +
                numparts +
                " points=" +
                numpoints

        return sb
    }

    /**
     * returns number of parts in this shape
     * <p/>
     * one part == one polygon
     *
     * @return number of parts as int
     */
    @Override
    int getNumberOfParts() {
        return numparts
    }

    /**
     * returns a list of points for the numbered shape part.
     *
     * @param part index of part to return
     * @return double[] containing longitude and latitude
     * pairs where
     * [] is longitude, latitude
     */
    @Override
    double[] getPoints(int part) {
        double[] output                //data to return
        int start = parts[part]        //first index of this part

        /* last index of this part */
        int end = numpoints
        if (part < numparts - 1) {
            end = parts[part + 1]
        }

        /* fill output */
        output = new double[(end - start) * 2]
        int end2 = end * 2
        int start2 = start * 2
        if (end2 - start2 >= 0) System.arraycopy(points, start2, output, 0, end2 - start2)
        return output
    }
}

/**
 * object for shape file POLYGON
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class PolygonZ extends Shape {

    /**
     * shape file POLYGONZ record fields
     */
    int shapetype
    double[] boundingbox
    int numparts
    int numpoints
    int[] parts
    double[] points

    /**
     * creates a shape file POLYGONZ from a ByteBuffer
     * <p/>
     * TODO: static
     *
     * @param bb ByteBuffer containing record bytes
     *           from a shape file POLYGONZ record
     */
    PolygonZ(ByteBuffer bb, int contentlength) {
        int i

        bb.order(ByteOrder.LITTLE_ENDIAN)

        int pos = bb.position()

        shapetype = bb.getInt()

        boundingbox = new double[4]
        for (i = 0; i < 4; i++) {
            boundingbox[i] = bb.getDouble()
        }

        numparts = bb.getInt()

        numpoints = bb.getInt()

        parts = new int[numparts]
        for (i = 0; i < numparts; i++) {
            parts[i] = bb.getInt()
        }

        points = new double[numpoints * 2]            //x,y pairs
        for (i = 0; i < numpoints * 2; i++) {
            points[i] = bb.getDouble()
        }

        //Z range + z array(numpoints)
        int len = 2 + numpoints
        for (i = 0; i < len; i++) {
            bb.getDouble()
        }

        //read M if contentlength not yet reached
        if (bb.position() - pos < contentlength * 2) {
            //M range + m array(numpoints)
            for (i = 0; i < len; i++) {
                bb.getDouble()
            }
        }
    }

    /**
     * output .shp POLYGON summary
     *
     * @return String
     */
    @Override
    String toString() {

        String sb = "(" +
                boundingbox[0] +
                ", " +
                boundingbox[1] +
                ") (" +
                boundingbox[2] +
                ", " +
                boundingbox[3] +
                ") parts=" +
                numparts +
                " points=" +
                numpoints

        return sb
    }

    /**
     * returns number of parts in this shape
     * <p/>
     * one part == one polygon
     *
     * @return number of parts as int
     */
    @Override
    int getNumberOfParts() {
        return numparts
    }

    /**
     * returns a list of points for the numbered shape part.
     *
     * @param part index of part to return
     * @return double[] containing longitude and latitude
     * pairs where
     * [] is longitude, latitude
     */
    @Override
    double[] getPoints(int part) {
        double[] output                //data to return
        int start = parts[part]        //first index of this part

        /* last index of this part */
        int end = numpoints
        if (part < numparts - 1) {
            end = parts[part + 1]
        }

        /* fill output */
        output = new double[(end - start) * 2]
        int end2 = end * 2
        int start2 = start * 2
        if (end2 - start2 >= 0) System.arraycopy(points, start2, output, 0, end2 - start2)
        return output
    }
}

/**
 * .cpg file object, specifies character encoding for the DBF file.
 *
 * https://desktop.arcgis.com/en/arcmap/latest/manage-data/shapefiles/shapefile-file-extensions.htm
 */
@Slf4j
//@CompileStatic
class CPG implements Serializable {

    private static final long serialVersionUID = -292802307279651655L

    //private static final Logger logger = log.getLogger(CPG.class);

    // Default charset would be the system, but use ISO-8859-1 to maintain backward compatibility of this library.
    Charset charset = StandardCharsets.ISO_8859_1

    /**
     * constructor for new CPG from .cpg filename
     *
     * @param filename path and file name of .cpg file
     */
    CPG(String filename) {
        try {
            String cpg = Files.readAllLines(Paths.get(filename)).get(0)
            charset = Charset.forName(cpg)
        } catch (Exception e) {
            log.debug("loading cpg issue, assuming default ISO-8859-1 encoding: " + filename + ": " + e.toString(), e)
        }
    }

    Charset getCharset() {
        return charset
    }
}

/**
 * .dbf file object
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class DBF implements Serializable {

    static final long serialVersionUID = -1631837349804567374L
    /**
     * .dbf file header object
     */
    DBFHeader dbfheader
    /**
     * .dbf records
     */
    DBFRecords dbfrecords
    /**
     * flag for only a single column loaded in dbfrecords
     */
    boolean singleColumn

    /**
     * constructor for new DBF from .dbf filename
     *
     * @param filename path and file name of .dbf file
     */
    DBF(String filename, Charset charset) {
        singleColumn = false

        /* get file header */
        dbfheader = new DBFHeader(filename)

        /* get records */
        dbfrecords = new DBFRecords(filename, charset, dbfheader)
    }

    DBF(String filename, Charset charset, String column) {
        singleColumn = true

        /* get file header */
        dbfheader = new DBFHeader(filename)

        /* get records */
        String[] columns = column.split(",")
        int[] idx = new int[columns.length]
        for (int i = 0; i < idx.length; i++) {
            idx[i] = dbfheader.getColumnIdx(columns[i])
            if (idx[i] < 0) {
                throw new RuntimeException("Column " + columns[i] + " not found in DBF")
            }
        }
        dbfrecords = new DBFRecords(filename, charset, dbfheader, idx, true)
    }

    DBF(String filename, Charset charset, String[] columns) {
        singleColumn = false

        /* get file header */
        dbfheader = new DBFHeader(filename)

        /* get records */
        int[] idx = new int[columns.length]
        for (int i = 0; i < idx.length; i++) {
            idx[i] = dbfheader.getColumnIdx(columns[i])
            if (idx[i] < 0) {
                throw new RuntimeException("Column " + columns[i] + " not found in DBF. Columns are " + String.join(", ", dbfheader.getColumnNames()))
            }
        }
        dbfrecords = new DBFRecords(filename, charset, dbfheader, idx, false)
    }

    /**
     * returns index of a column by column name
     *
     * @param column_name column name as String
     * @return index of column_name as int
     * -1 for none
     */
    int getColumnIdx(String column_name) {
        return dbfheader.getColumnIdx(column_name)
    }

    /**
     * gets the list of column names in column order
     *
     * @return column names as String []
     */
    String[] getColumnNames() {
        return dbfheader.getColumnNames()
    }

    /**
     * gets the value at a row and column
     *
     * @param row row index as int
     * @param column column index as int
     * @return value as String
     */
    String getValue(int row, int column) {
        if (singleColumn) {
            return dbfrecords.getValue(row, 0)
        }
        return dbfrecords.getValue(row, column)
    }

    /**
     * lists all values in a specified column as a sorted set
     *
     * @param column index of column values to return
     * @return sorted set of values in the column as String[]
     */
    String[] getColumnLookup(int column) {
        int i, len
        TreeSet<String> ts = new TreeSet<String>()

        /* build set */
        len = dbfheader.getNumberOfRecords()
        for (i = 0; i < len; i++) {
            ts.add(getValue(i, column))
        }

        /* convert to sorted [] */
        String[] sa = new String[ts.size()]
        ts.toArray(sa)

        return sa
    }

    /**
     * lists values that are occur on more than one row
     *
     * @param column index of column values to return
     * @return sorted set of duplicated values in the column as String[]
     */
    String[] getColumnMultipleValues(int column) {
        int i, len
        TreeSet<String> ts = new TreeSet<String>()
        TreeSet<String> dup = new TreeSet<String>()

        /* build set */
        len = dbfheader.getNumberOfRecords()
        for (i = 0; i < len; i++) {
            String v = getValue(i, column)
            if (ts.contains(v)) {
                dup.add(v)
            } else {
                ts.add(v)
            }
        }

        /* convert to sorted [] */
        String[] sa = new String[dup.size()]
        dup.toArray(sa)

        return sa
    }
}

/**
 * .dbf header object
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class DBFHeader implements Serializable {

    static final long serialVersionUID = -1807390252140128281L
    //private static final Logger logger = log.getLogger(DBFHeader.class);
    /**
     * .dbf header fields (partial)
     */
    int filetype
    int[] lastupdate
    int numberofrecords
    int recordsoffset
    int recordlength
    int tableflags
    /* int codepagemark; */
    /**
     * list of fields/columns in the .dbf header
     */
    ArrayList<DBFField> fields
    /* TODO: use appropriately for failed constructor */
    boolean isvalid

    /**
     * constructor for DBFHeader from  a .dbf filename
     *
     * @param filename filename of .dbf file as String
     */
    DBFHeader(String filename) {

        isvalid = false
        FileInputStream fis = null
        try {
            int i

            /* load whole file */
            fis = new FileInputStream(filename)
            FileChannel fc = fis.getChannel()
            ByteBuffer buffer = ByteBuffer.allocate((int) fc.size())
            fc.read(buffer)
            buffer.flip()                        //ready to read
            buffer.order(ByteOrder.BIG_ENDIAN)

            filetype = (0xFF & buffer.get())
            lastupdate = new int[3]
            for (i = 0; i < 3; i++) {
                lastupdate[i] = (0xFF & buffer.get())
            }
            numberofrecords = (0xFF & buffer.get()) + 256 * ((0xFF & buffer.get())
                    + 256 * ((0xFF & buffer.get()) + 256 * (0xFF & buffer.get())))
            recordsoffset = (0xFF & buffer.get()) + 256 * (0xFF & buffer.get())
            recordlength = (0xFF & buffer.get()) + 256 * (0xFF & buffer.get())
            for (i = 0; i < 16; i++) {
                buffer.get()
            }
            tableflags = (0xFF & buffer.get())
            /* codepagemark = */
            buffer.get()
            buffer.get()
            buffer.get()

            fields = new ArrayList()
            byte nextfsr
            while ((nextfsr = buffer.get()) != 0x0D) {
                // Note headers are always in ISO-8859-1 encoding
                fields.add(new DBFField(nextfsr, buffer, StandardCharsets.ISO_8859_1))
            }
            /* don't care dbc, skip */

            isvalid = true
        } catch (Exception e) {
            log.error("loading dbfheader error: " + filename + ": " + e.toString(), e)
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    /**
     * gets field list
     *
     * @return all fields as ArrayList<DBFField>
     */
    ArrayList<DBFField> getFields() {
        return fields
    }

    /**
     * gets records offset in .dbf file
     *
     * @return record offset as int
     */
    int getRecordsOffset() {
        return recordsoffset
    }

    /**
     * gets the number of records in the .dbf
     *
     * @return number of records as int
     */
    int getNumberOfRecords() {
        return numberofrecords
    }

    /**
     * gets the list of column names in column order
     *
     * @return column names as String []
     */
    String[] getColumnNames() {
        String[] s = new String[fields.size()]
        for (int i = 0; i < s.length; i++) {
            s[i] = fields.get(i).getName()
        }
        return s
    }

    /**
     * gets the index of a column from a column name
     * <p/>
     * case insensitive
     *
     * @param column_name column name as String
     * @return index of column name
     * -1 for not found
     */
    int getColumnIdx(String column_name) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).getName().equalsIgnoreCase(column_name)) {
                return i
            }
        }

        return -1
    }

    /**
     * format .dbf header summary
     *
     * @return String
     */
    @Override
    String toString() {
        if (!isvalid) {
            return "invalid header"
        }

        StringBuffer sb = new StringBuffer()

        sb.append("filetype: ")
        sb.append(filetype)
        sb.append("\r\nlastupdate: ")
        sb.append(lastupdate[0])
        sb.append(" ")
        sb.append(lastupdate[1])
        sb.append(" ")
        sb.append(lastupdate[2])
        sb.append("\r\nnumberofrecords: ")
        sb.append(numberofrecords)
        sb.append("\r\nrecordsoffset: ")
        sb.append(recordsoffset)
        sb.append("\r\nrecordlength: ")
        sb.append(recordlength)
        sb.append("\r\ntableflags: ")
        sb.append(tableflags)
        sb.append("\r\nnumber of fields: ")
        sb.append(fields.size())

        for (int i = 0; i < fields.size(); i++) {
            sb.append(fields.get(i).toString())
        }

        return sb.toString()
    }
}

/**
 * .dbf header field object
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class DBFField implements Serializable {

    static final long serialVersionUID = 6130879839715559815L
    //private static final Logger logger = log.getLogger(DBFField.class);
    /*
     * .dbf Field records (partial)
     */
    String name
    char type
    int displacement
    int length
    int decimals
    int flags
    byte[] data        //placeholder for reading byte blocks
    /* don't care autoinc */


    static String convertToUTF8(String myString) {
        byte[] ptext = myString.getBytes(StandardCharsets.UTF_8)
        return new String(ptext, StandardCharsets.UTF_8)
    }

    /**
     * constructor for DBFField with first byte separated from
     * rest of the data structure
     * <p/>
     * TODO: static and more cleaner
     *
     * @param firstbyte first byte of .dbf field record as byte
     * @param buffer remaining byes of .dbf field record as ByteBuffer
     */
    DBFField(byte firstbyte, ByteBuffer buffer, Charset charset) {
        int i
        byte[] ba = new byte[12]
        ba[0] = firstbyte
        for (i = 1; i < 11; i++) {
            ba[i] = buffer.get()
        }
        try {
            name = convertToUTF8(new String(ba, charset).trim().toUpperCase())
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        byte[] ba2 = new byte[1]
        ba2[0] = buffer.get()
        try {
            type = convertToUTF8(new String(ba2, charset).trim()).charAt(0)
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }

        displacement = (0xFF & buffer.get()) + 256 * ((0xFF & buffer.get())
                + 256 * ((0xFF & buffer.get()) + 256 * (0xFF & buffer.get())))

        length = (0xFF & buffer.get())
        data = new byte[length]

        decimals = (0xFF & buffer.get())
        flags = (0xFF & buffer.get())

        /* skip over end */
        for (i = 0; i < 13; i++) {
            buffer.get()
        }
    }

    void test() {
    }

    /**
     * gets field name
     *
     * @return field name as String
     */
    String getName() {
        return name
    }

    /**
     * gets field type
     *
     * @return field type as char
     */
    char getType() {
        return type
    }

    /**
     * gets data block
     * <p/>
     * for use in .read(getDataBlock()) like functions
     * when reading records
     *
     * @return
     */
    byte[] getDataBlock() {
        return data
    }

    /**
     * format Field summary
     *
     * @return String
     */
    @Override
    String toString() {
        return "name: " + name + " type: " + type + " displacement: " + displacement + " length: " + length + "\r\n"
    }
}

/**
 * collection of .dbf records
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class DBFRecords implements Serializable {

    static final long serialVersionUID = -2450196133919654852L
    //private static final Logger logger = log.getLogger(DBFRecords.class);
    /**
     * list of DBFRecord
     */
    ArrayList<DBFRecord> records
    /* TODO: use appropriately for failed constructor */
    boolean isvalid

    /**
     * constructor for collection of DBFRecord from a DBFHeader and
     * .dbf filename
     *
     * @param filename .dbf file as String
     * @param header dbf header from filename as DBFHeader
     */
    DBFRecords(String filename, Charset charset, DBFHeader header) {
        /* init */
        records = new ArrayList()
        isvalid = false

        FileInputStream fis = null
        try {
            /* load all records */
            log.debug("start reading shapefile: " + filename)
            fis = new FileInputStream(filename)
            FileChannel fc = fis.getChannel()
            ByteBuffer buffer = ByteBuffer.allocate((int) fc.size() - header.getRecordsOffset())
            fc.read(buffer, header.getRecordsOffset())
            buffer.flip()            //prepare for reading


            /* add each record from byte buffer, provide fields list */
            int i = 0
            ArrayList<DBFField> fields = header.getFields()
            while (i < header.getNumberOfRecords() && buffer.hasRemaining()) {
                records.add(new DBFRecord(buffer, fields, charset))
                i++
            }

            isvalid = true
        } catch (Exception e) {
            log.error("loading records error: " + filename + ": " + e.toString(), e)
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    DBFRecords(String filename, Charset charset, DBFHeader header, int[] columnIdx, boolean mergeColumns) {
        /* init */
        records = new ArrayList()
        isvalid = false

        log.debug("reading shapefile: " + filename)
        FileInputStream fis = null
        try {
            /* load all records */
            fis = new FileInputStream(filename)
            FileChannel fc = fis.getChannel()
            ByteBuffer buffer = ByteBuffer.allocate((int) fc.size() - header.getRecordsOffset())
            fc.read(buffer, header.getRecordsOffset())
            buffer.flip()            //prepare for reading


            /* add each record from byte buffer, provide fields list */
            int i = 0
            ArrayList<DBFField> fields = header.getFields()
            while (i < header.getNumberOfRecords() && buffer.hasRemaining()) {
                records.add(new DBFRecord(buffer, fields, charset, columnIdx, mergeColumns))
                i++
            }

            isvalid = true
        } catch (Exception e) {
            log.error("loading records error: " + filename + ": " + e.toString(), e)
        } finally {
            if (fis != null) {
                try {
                    fis.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    /**
     * gets a value at a row and column
     *
     * @param row row number as int
     * @param column column number as int
     * @return value as String or "" if invalid input or column format
     */
    String getValue(int row, int column) {
        if (row >= 0 && row < records.size()) {
            return records.get(row).getValue(column)
        }
        return ""
    }

    /**
     * format all Records
     *
     * @return String
     */
    @Override
    String toString() {
        StringBuffer sb = new StringBuffer()
        for (DBFRecord r : records) {
            sb.append(r.toString())
            sb.append("\r\n")
        }
        return sb.toString()
    }
}

/**
 * .dbf record object
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class DBFRecord implements Serializable {

    static final long serialVersionUID = 584190536943295242L
    /**
     * String [] representing the current row record in a .dbf
     */
    String[] record
    /**
     * deletion flag
     * <p/>
     * TODO: make use of this
     */
    int deletionflag
    /**
     * tmp values
     */
    String[] fieldValues

    /**
     * constructs new DBFRecord from bytebuffer and list of fields
     * <p/>
     * TODO: implement more than C and N types
     *
     * @param buffer byte buffer for reading fields as ByteBuffer
     * @param fields fields in this record as ArrayList<DBFField>
     */
    DBFRecord(ByteBuffer buffer, ArrayList<DBFField> fields, Charset charset) {
        deletionflag = (0xFF & buffer.get())
        record = new String[fields.size()]

        /* iterate through each record to fill */
        for (int i = 0; i < record.length; i++) {
            DBFField f = fields.get(i)
            byte[] data = f.getDataBlock()        //get pre-built byte[]
            buffer.get(data)
            try {
                switch (f.getType()) {
                    case 'C':            //string
                        record[i] = DBFField.convertToUTF8(new String(data, charset).trim())
                        break
                    case 'N':            //number as string
                        record[i] = DBFField.convertToUTF8(new String(data, charset).trim())
                        break
                }
            } catch (Exception e) {
                //TODO: is this necessary?
            }
        }
    }

    DBFRecord(ByteBuffer buffer, ArrayList<DBFField> fields, Charset charset, int[] columnIdx, boolean mergeColumns) {
        deletionflag = (0xFF & buffer.get())
        record = mergeColumns ? new String[1] : new String[columnIdx.length]
        fieldValues = new String[fields.size()]

        /* iterate through each record to fill */
        for (int i = 0; i < fields.size(); i++) {
            DBFField f = fields.get(i)
            byte[] data = f.getDataBlock()        //get pre-built byte[]
            buffer.get(data)

            try {
                switch (f.getType()) {
                    case 'C':            //string
                        fieldValues[i] = DBFField.convertToUTF8(new String(data, charset).trim())
                        break
                    case 'N':            //number as string
                        fieldValues[i] = DBFField.convertToUTF8(new String(data, charset).trim())
                        break
                }
            } finally {
            }
        }

        for (int j = 0; j < columnIdx.length; j++) {
            if (mergeColumns) {
                if (record[0] == null) {
                    record[0] = ""
                } else if (record[0].length() > 0) {
                    record[0] += ", "
                }

                record[0] += fieldValues[columnIdx[j]]
            } else {
                record[j] = fieldValues[columnIdx[j]]
            }
        }
    }

    /**
     * gets the value in this record at a column
     *
     * @param column column index for value to return
     * @return value as String
     */
    String getValue(int column) {
        if (column < 0 || column >= record.length) {
            return ""
        } else {
            return record[column]
        }
    }

    /**
     * format this record
     *
     * @return String
     */
    @Override
    String toString() {
        StringBuffer sb = new StringBuffer()
        for (String s : record) {
            sb.append(s)
            sb.append(", ")
        }
        return sb.toString()
    }
}

/**
 * for balancing shape files with large numbers of shapes.
 * <p/>
 * creates a grid with each cell listing shapes that overlap with it.
 * <p/>
 * TODO: not square dimensions
 *
 * @author adam
 */
@Slf4j
//@CompileStatic
class ShapesReference implements Serializable {

    /**
     * grid with shape index lists as cells
     */
    ArrayList<Integer>[][] mask
    /**
     * width/height of mask
     */
    int mask_dimension
    /**
     * intersection multiplier for longitude
     */
    double mask_long_multiplier
    /**
     * intersection multiplier for latitude
     */
    double mask_lat_multiplier
    /**
     * mask bounding box, see SimpleRegion boundingbox.
     */
    double[][] boundingbox_all
    /**
     * reference to all shapes
     */
    ShapeRecords sr

    /**
     * constructor
     *
     * @param sr_ all shapes for this mask as ShapeRecords
     */
    ShapesReference(ShapeRecords sr_) {

        sr = sr_
        boundingbox_all = new double[2][2]

        mask_dimension = (int) Math.sqrt(sr.getNumberOfRecords())

        /* define minimum mask size */
        if (mask_dimension < 3) {
            mask = null
            return
        }

        /* create mask */
        mask = new ArrayList[mask_dimension][mask_dimension]

        /* update boundingbox_all */
        boolean first_time = true
        for (ComplexRegion s : sr_.getRegions()) {
            double[][] bb = s.getBoundingBox()
            if (first_time || boundingbox_all[0][0] > bb[0][0]) {
                boundingbox_all[0][0] = bb[0][0]
            }
            if (first_time || boundingbox_all[1][0] < bb[1][0]) {
                boundingbox_all[1][0] = bb[1][0]
            }
            if (first_time || boundingbox_all[0][1] > bb[0][1]) {
                boundingbox_all[0][1] = bb[0][1]
            }
            if (first_time || boundingbox_all[1][1] < bb[1][1]) {
                boundingbox_all[1][1] = bb[1][1]
            }
            first_time = false
        }

        /* get intersecting cells and add */
        ArrayList<ComplexRegion> sra = sr.getRegions()
        for (int j = 0; j < sra.size(); j++) {
            ComplexRegion s = sra.get(j)
            int[][] map = s.getOverlapGridCells_Box(
                    boundingbox_all[0][0], boundingbox_all[0][1], boundingbox_all[1][0], boundingbox_all[1][1], mask_dimension, mask_dimension, s.getBoundingBox(), null, false)

            for (int i = 0; i < map.length; i++) {
                if (mask[map[i][0]][map[i][1]] == null) {
                    mask[map[i][0]][map[i][1]] = new ArrayList<Integer>()
                }
                mask[map[i][0]][map[i][1]].add(j)
            }
        }

        /* calculate multipliers */
        mask_long_multiplier = mask_dimension / (boundingbox_all[1][0] - boundingbox_all[0][0])
        mask_lat_multiplier = mask_dimension / (boundingbox_all[1][1] - boundingbox_all[0][1])
    }

    /**
     * performs intersection on one point
     *
     * @param longitude longitude of point to intersect as double
     * @param latitude latitude of point to intersect as double
     * @return shape index if intersection found, or -1 for no intersection
     */
    int intersection(double longitude, double latitude) {
        ArrayList<ComplexRegion> sra = sr.getRegions()

        int i

        /* test for mask */
        if (mask != null) {
            /* apply multipliers */
            int long1 = (int) Math.floor((longitude - boundingbox_all[0][0]) * mask_long_multiplier)
            int lat1 = (int) Math.floor((latitude - boundingbox_all[0][1]) * mask_lat_multiplier)
            /* check is within mask bounds */
            if (long1 >= 0 && long1 < mask[0].length
                    && lat1 >= 0 && lat1 < mask.length
                    && mask[long1][lat1] != null) {

                /* get list of shapes to check at this mask cell */
                ArrayList<Integer> ali = mask[long1][lat1]

                /* check each potential cell */
                for (i = 0; i < ali.size(); i++) {
                    if (sra.get(ali.get(i).intValue()).isWithin(longitude, latitude)) {
                        return ali.get(i).intValue()
                    }
                }
            }
        } else {
            /* no mask, check all shapes */
            for (i = 0; i < sra.size(); i++) {
                if (sra.get(i).isWithin(longitude, latitude)) {
                    return i
                }
            }
        }
        return -1
    }

    /**
     * performs intersection with a distance on one point
     *
     * @param longitude longitude of point to intersect as double
     * @param latitude latitude of point to intersect as double
     * @param distance maximum distance from the shape
     * @return shape indices and distances for intersections found within the distance
     */
    List<ImmutablePair<Integer, Double>> intersection(double longitude, double latitude, double distance) {
        ArrayList<ComplexRegion> sra = sr.getRegions()

        int i

        List<ImmutablePair<Integer, Double>> r

        if (mask != null && distance < mask_lat_multiplier && distance < mask_long_multiplier) {
            Map<Integer, Double> hitsMap = new HashMap<>()

            // apply multipliers
            int long1 = (int) Math.floor((longitude - distance - boundingbox_all[0][0]) * mask_long_multiplier)
            int long2 = (int) Math.floor((longitude + distance - boundingbox_all[0][0]) * mask_long_multiplier)
            int lat1 = (int) Math.floor((latitude - distance - boundingbox_all[0][1]) * mask_lat_multiplier)
            int lat2 = (int) Math.floor((latitude + distance - boundingbox_all[0][1]) * mask_lat_multiplier)
            // check is within mask bounds
            if (long1 >= 0 && long1 < mask[0].length
                    && lat1 >= 0 && lat1 < mask.length
                    && mask[long1][lat1] != null) {
                // get list of shapes to check at this mask cell
                ArrayList<Integer> ali = mask[long1][lat1]
                checkCells(hitsMap, sra, ali, longitude, latitude, distance)
            }

            if (long1 != long2
                    && long2 >= 0 && long2 < mask[0].length
                    && lat1 >= 0 && lat1 < mask.length
                    && mask[long2][lat1] != null) {
                // get list of shapes to check at this mask cell
                ArrayList<Integer> ali = mask[long2][lat1]
                checkCells(hitsMap, sra, ali, longitude, latitude, distance)
            }

            if (lat1 != lat2
                    && long1 >= 0 && long1 < mask[0].length
                    && lat2 >= 0 && lat2 < mask.length
                    && mask[long1][lat2] != null) {
                // get list of shapes to check at this mask cell
                ArrayList<Integer> ali = mask[long1][lat2]
                checkCells(hitsMap, sra, ali, longitude, latitude, distance)
            }

            if (long1 != long2 && lat1 != lat2
                    && long2 >= 0 && long2 < mask[0].length
                    && lat2 >= 0 && lat2 < mask.length
                    && mask[long2][lat2] != null) {
                // get list of shapes to check at this mask cell
                ArrayList<Integer> ali = mask[long2][lat2]
                checkCells(hitsMap, sra, ali, longitude, latitude, distance)
            }

            r = hitsMap.entrySet().stream().map(e -> ImmutablePair.of(e.getKey(), e.getValue())).collect(Collectors.toList())
        } else {
            List<ImmutablePair<Integer, Double>> hits = new ArrayList<>()
            // no mask, check all shapes
            for (i = 0; i < sra.size(); i++) {
                Double d = sra.get(i).distance(longitude, latitude, distance, false)
                if (d != null) {
                    hits.add(ImmutablePair.of(i, d))
                }
            }
            r = hits
        }

        return r
    }

    private void checkCells(Map<Integer, Double> hitsMap, ArrayList<ComplexRegion> sra, ArrayList<Integer> ali, double longitude, double latitude, double distance) {
        // check each potential cell
        for (int i = 0; i < ali.size(); i++) {
            Double x = sra.get(ali.get(i).intValue()).distance(longitude, latitude, distance, false)
            if (x != null) {
                hitsMap.merge(ali.get(i).intValue(), x, Math::min)
            }
        }
    }
}

@Slf4j
//@CompileStatic
class IntersectionThread implements Runnable {

    //private static final Logger logger = log.getLogger(IntersectionThread.class);

    Thread t
    ShapesReference shapesreference
    PointPos[] points
    LinkedBlockingQueue<Integer> lbq
    int step
    int[] target
    CountDownLatch cdl

    IntersectionThread(ShapesReference shapesreference_, PointPos[] points_, LinkedBlockingQueue<Integer> lbq_, int step_, int[] target_, CountDownLatch cdl_) {
        t = new Thread(this)
        t.setPriority(Thread.MIN_PRIORITY)

        points = points_
        shapesreference = shapesreference_
        lbq = lbq_
        step = step_

        target = target_

        cdl = cdl_

        t.start()
    }

    @Override
    void run() {

        int i, idx

        /* get next batch */
        Integer start
        try {
            while (true) {
                start = lbq.take()

                //log.debug("A*: " + start.intValue());

                int end = start.intValue() + step
                if (end > target.length) {
                    end = target.length
                }
                int sv = start.intValue()
                for (i = sv; i < end; i++) {
                    if (i > sv && points[i - 1].x == points[i].x && points[i - 1].y == points[i].y) {
                        target[points[i].pos] = target[points[i - 1].pos]
                    } else if ((idx = shapesreference.intersection(points[i].x, points[i].y)) >= 0) {
                        target[points[i].pos] = idx
                    } else {
                        target[points[i].pos] = -1
                    }
                }

                cdl.countDown()
            }
        } catch (InterruptedException ie) {
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
    }

    boolean isAlive() {
        return t.isAlive()
    }

    void interrupt() {
        t.interrupt()
    }
}

@Slf4j
//@CompileStatic
class PointPos {

    public double x, y
    public int pos

    PointPos(double x, double y, int pos) {
        this.x = x
        this.y = y
        this.pos = pos
    }
}
