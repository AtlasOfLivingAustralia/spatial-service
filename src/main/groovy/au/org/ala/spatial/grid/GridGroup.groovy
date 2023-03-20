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
package au.org.ala.spatial.grid

import au.org.ala.spatial.intersect.IniReader
import groovy.util.logging.Slf4j

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @author Adam
 */
//@CompileStatic
@Slf4j
class GridGroup {

    public Boolean byteorderLSB
    public int ncols, nrows
    public double nodatavalue
    public Boolean valid
    public double[] values
    public double xmin, xmax, ymin, ymax
    public double xres, yres
    public String datatype
    public String filename
    ArrayList<String> names
    ArrayList<String> files
    float[] cell
    float[] emptyCell
    RandomAccessFile raf
    int cellSize
    byte[] buffer
    Long bufferOffset
    byte[] b
    int size
    byte nbytes

    GridGroup(String fname) throws IOException {
        filename = fname
        readgrd(filename.substring(0, filename.length() - 4) + ".grd")

        raf = new RandomAccessFile(filename.substring(0, filename.length() - 4) + ".gri", "r")

        readHeader(fname)

        buffer = new byte[64 * 4 * cellSize]    //must be multiple of 64
        bufferOffset = raf.length()
        size = 4
        b = new byte[4 * cellSize]
    }

    //transform to file position
    int getcellnumber(double x, double y) {
        if (x < xmin || x > xmax || y < ymin || y > ymax) //handle invalid inputs
        {
            return -1
        }

        int col = (int) ((x - xmin) / xres)
        int row = this.nrows - 1 - (int) ((y - ymin) / yres)

        //limit each to 0 and ncols-1/nrows-1
        if (col < 0) {
            col = 0
        }
        if (row < 0) {
            row = 0
        }
        if (col >= ncols) {
            col = ncols - 1
        }
        if (row >= nrows) {
            row = nrows - 1
        }
        return (row * ncols + col)
    }

    private void readgrd(String filename) {
        IniReader ir = new IniReader(filename)

        datatype = "FLOAT"
        ncols = ir.getIntegerValue("GeoReference", "Columns")
        nrows = ir.getIntegerValue("GeoReference", "Rows")
        xmin = ir.getDoubleValue("GeoReference", "MinX")
        ymin = ir.getDoubleValue("GeoReference", "MinY")
        xmax = ir.getDoubleValue("GeoReference", "MaxX")
        ymax = ir.getDoubleValue("GeoReference", "MaxY")
        xres = ir.getDoubleValue("GeoReference", "ResolutionX")
        yres = ir.getDoubleValue("GeoReference", "ResolutionY")
        if (ir.valueExists("Data", "NoDataValue")) {
            nodatavalue = (float) ir.getDoubleValue("Data", "NoDataValue")
        } else {
            nodatavalue = Double.NaN
        }

        String s = ir.getStringValue("Data", "ByteOrder")

        byteorderLSB = true
        if (s != null && s.length() > 0) {
            if (s == "MSB") {
                byteorderLSB = false
            }
        }
    }

    HashMap<String, Float> sample(double longitude, double latitude) throws IOException {
        HashMap<String, Float> map = new HashMap<String, Float>()

        float[] c = readCell(longitude, latitude)

        for (int i = 0; i < c.length; i++) {
            map.put(names.get(i), c[i])
        }

        return map
    }

    float[] readCell(double longitude, double latitude) throws IOException {
        //seek
        long pos = getcellnumber(longitude, latitude)
        if (pos >= 0) {
            //getBytes(raf, buffer, bufferOffset, pos * size * cellSize, b);
            raf.seek(pos * size * cellSize)
            raf.read(b)
            ByteBuffer bb = ByteBuffer.wrap(b)
            if (byteorderLSB) {
                bb.order(ByteOrder.LITTLE_ENDIAN)
            }
            for (int i = 0; i < cell.length; i++) {
                cell[i] = bb.getFloat()
                if (cell[i] == (float)(Float.MAX_VALUE * -1.0)) {
                    cell[i] = Float.NaN
                }
            }
            return cell
        } else {
            return emptyCell
        }
    }

    private void readHeader(String fname) throws IOException {
        names = new ArrayList<String>()
        files = new ArrayList<String>()

        BufferedReader br = null
        try {
            br = new BufferedReader(new FileReader(fname))
            String line
            while ((line = br.readLine()) != null) {
                names.add(line)
                files.add(line)
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (br != null) {
                try {
                    br.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }

        cellSize = names.size()
        cell = new float[cellSize]
        emptyCell = new float[cellSize]
        for (int i = 0; i < emptyCell.length; i++) {
            emptyCell[i] = Float.NaN
        }
    }
}
