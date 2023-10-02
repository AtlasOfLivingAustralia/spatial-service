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
/**
 * @author Adam
 */
import groovy.transform.CompileStatic
//@CompileStatic
class IntersectUtil {

    /**
     * write sample output into a stream.
     * <p/>
     * Output is a csv.  First two columns are latitude, longitude.  Remaining
     * columns are the fields.
     *
     * @param fields field ids as String [].
     * @param points array of latitude,longitude pairs.
     *               [even]=latitude, [odd]=longitude.  As String[].
     * @param sample sampling output from LayerIntersectDAO, as ArrayList<String>.
     * @param os OutputStream.
     * @throws IOException
     */
    static void writeSampleToStream(String[] fields, String[] points, ArrayList<String> sample, OutputStream os) throws IOException {
        int[] curPos = new int[sample.size()]
        for (int i = 0; i < curPos.length; i++) {
            curPos[i] = 0
        }

        byte[] bNewLine = "\n".bytes
        byte[] bComma = ",".bytes
        byte[] bDblQuote = "\"".bytes

        os.write("latitude,longitude".bytes)
        for (String field : fields) {
            os.write(bComma)
            os.write(field.bytes)
        }

        for (int i = 0; i < points.length; i += 2) {
            os.write(bNewLine)
            os.write(points[i].bytes)
            os.write(bComma)
            os.write(points[i + 1].bytes)

            for (int j = 0; j < sample.size(); j++) {
                os.write(bComma)
                int nextPos = sample.get(j).indexOf('\n', curPos[j])
                if (nextPos == -1) {
                    nextPos = sample.get(j).length()
                }
                if (curPos[j] <= nextPos) {
                    String s = sample.get(j).substring(curPos[j], nextPos)
                    curPos[j] = nextPos + 1

                    if (s != null) {
                        boolean useQuotes = false
                        if (s.contains("\"")) {
                            s = s.replace("\"", "\"\"")
                            useQuotes = true
                        } else if (s.contains(",")) {
                            useQuotes = true
                        }
                        if (useQuotes) {
                            os.write(bDblQuote)
                            os.write(s.bytes)
                            os.write(bDblQuote)
                        } else {
                            os.write(s.bytes)
                        }
                    }
                }
            }
        }
    }
}
