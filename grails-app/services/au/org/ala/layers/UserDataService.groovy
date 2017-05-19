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
import au.org.ala.layers.legend.LegendObject
import au.org.ala.layers.legend.QueryField
import org.apache.log4j.Logger

//TODO: create associated Controller
class UserDataService {

    final static Logger logger = Logger.getLogger(UserDataService.class)

    def userDataDao

    //256x256 transparent image
    static Object blankImageObject = new Object()
    static byte[] blankImageBytes = null
    final static int HIGHLIGHT_RADIUS = 3

    boolean importCSV(String name, String ud_header_id, String csv) {
        try {
            CSVReader reader = new CSVReader(new StringReader(csv))

            List userPoints = reader.readAll()

            //if only one column treat it as a list of LSID's
            if (userPoints.size() == 0) {
                throw (new RuntimeException("no data in csv"))
            }

            boolean hasHeader = false

            // check if it has a header
            String[] upHeader = (String[]) userPoints.get(0)
            try {
                if (upHeader.length == 2)
                    Double d0 = new Double(upHeader[0])
                Double d1 = new Double(upHeader[1])
                if (upHeader.length > 2)
                    Double d2 = new Double(upHeader[2])
            } catch (Exception e) {
                hasHeader = true
            }

            logger.debug("hasHeader: " + hasHeader)

            // check if the count of points goes over the threshold.
            int sizeToCheck = (hasHeader) ? userPoints.size() - 1 : userPoints.size()

            ArrayList<QueryField> fields = new ArrayList<QueryField>()
            if (upHeader.length == 2) {
                //only points upload, add 'id' column at the start
                fields.add(new QueryField("id"))
                fields.get(0).ensureCapacity(sizeToCheck)
            }
            String[] defaultHeader = ["id", "longitude", "latitude"]
            for (int i = 0; i < upHeader.length; i++) {
                String header = upHeader[i]
                if (upHeader.length == 2 && i < 2) {
                    header = defaultHeader[i + 1]
                } else if (upHeader.length > 2 && i < 3) {
                    header = defaultHeader[i]
                }
                fields.add(new QueryField("__f" + String.valueOf(i), header, QueryField.FieldType.AUTO))
                fields.get(fields.size() - 1).ensureCapacity(sizeToCheck)
            }

            double[] points = new double[sizeToCheck * 2]
            int counter = 1
            int hSize = hasHeader ? 1 : 0
            double minx = 1000
            double maxx = -1000
            double miny = 1000
            double maxy = -1000
            for (int i = 0; i < userPoints.size() - hSize; i++) {
                String[] up = (String[]) userPoints.get(i + hSize)
                if (up.length > 2) {
                    for (int j = 0; j < up.length && j < fields.size(); j++) {
                        //replace anything that may interfere with facet parsing
                        String s = up[j].replace("\"", "'").replace(" AND ", " and ").replace(" OR ", " or ")
                        if (s.length() > 0 && s.charAt(0) == '*') {
                            s = "_" + s
                        }
                        fields.get(j).add(s)
                    }
                    try {
                        points[i * 2] = Double.parseDouble(up[1])
                        points[i * 2 + 1] = Double.parseDouble(up[2])
                    } catch (Exception e) {
                    }
                } else if (up.length > 1) {
                    fields.get(0).add(ud_header_id + "-" + counter)
                    for (int j = 0; j < up.length && j < fields.size(); j++) {
                        fields.get(j + 1).add(up[j])
                    }
                    try {
                        points[i * 2] = Double.parseDouble(up[0])
                        points[i * 2 + 1] = Double.parseDouble(up[1])
                    } catch (Exception e) {
                    }
                    counter++
                }
                if (!Double.isNaN(points[i * 2])) {
                    if (points[i * 2] < minx) {
                        minx = points[i * 2]
                    }
                    if (points[i * 2] > maxx) {
                        maxx = points[i * 2]
                    }
                }
                if (!Double.isNaN(points[i * 2 + 1])) {
                    if (points[i * 2 + 1] < miny) {
                        miny = points[i * 2 + 1]
                    }
                    if (points[i * 2 + 1] > maxy) {
                        maxy = points[i * 2 + 1]
                    }
                }
            }

            //store data
            userDataDao.setDoubleArray(ud_header_id, "points", points)

            HashMap<String, LegendObject> field_details = new HashMap<String, LegendObject>()
            for (int i = 0; i < fields.size(); i++) {
                fields.get(i).store()  //finalize qf
                userDataDao.setQueryField(ud_header_id, fields.get(i).getName(), fields.get(i))
                field_details.put(fields.get(i).getName() + "\r\n" + fields.get(i).getDisplayName(), fields.get(i).getLegend())
            }

            HashMap<String, Object> metadata = new HashMap<String, Object>()
            metadata.put("title", "User uploaded points")
            metadata.put("name", name)
            metadata.put("date", System.currentTimeMillis())
            metadata.put("number_of_records", points.length / 2)
            metadata.put("bbox", minx + "," + miny + "," + maxx + "," + maxy)
            //metadata.put("user_fields",field_details);

            userDataDao.setMetadata(Long.parseLong(ud_header_id), metadata)

            // close the reader and data streams
            reader.close()

            return true
        } catch (Exception e) {
            logger.error("failed to import user csv", e)
        }

        return false
    }

    //TODO: implement getWmsImageStream
//    def getWmsImageStream(cql_filter, env, bboxString, heightString, widthString) {
//        int width = 256, height = 256;
//        try {
//            width = Integer.parseInt(widthString);
//            height = Integer.parseInt(heightString);
//        } catch (Exception e) {
//            logger.error("error parsing to int: " + widthString + " or " + heightString, e);
//        }
//
//        try {
//            env = URLDecoder.decode(env, "UTF-8");
//        } catch (UnsupportedEncodingException e) {
//            logger.error("error decoding env from UTF-8: " + env, e);
//        }
//        int red = 0, green = 0, blue = 0, alpha = 0;
//        String name = "circle";
//        int size = 4;
//        boolean uncertainty = false;
//        String highlight = null;
//        String colourMode = null;
//        for (String s : env.split(";")) {
//            String[] pair = s.split(":");
//            if (pair[0].equals("color")) {
//                while (pair[1].length() < 6) {
//                    pair[1] = "0" + pair[1];
//                }
//                red = Integer.parseInt(pair[1].substring(0, 2), 16);
//                green = Integer.parseInt(pair[1].substring(2, 4), 16);
//                blue = Integer.parseInt(pair[1].substring(4), 16);
//            } else if (pair[0].equals("name")) {
//                name = pair[1];
//            } else if (pair[0].equals("size")) {
//                size = Integer.parseInt(pair[1]);
//            } else if (pair[0].equals("opacity")) {
//                alpha = (int) (255 * Double.parseDouble(pair[1]));
////            } else if (pair[0].equals("uncertainty")) {
////                uncertainty = true;
//            } else if (pair[0].equals("sel")) {
//                try {
//                    highlight = URLDecoder.decode(s.substring(4), "UTF-8").replace("%3B", ";");
//                } catch (Exception e) {
//                }
//            } else if (pair[0].equals("colormode")) {
//                colourMode = pair[1];
//            }
//        }
//
//        double[] bbox = new double[4];
//        int i;
//        i = 0;
//        for (String s : bboxString.split(",")) {
//            try {
//                bbox[i] = Double.parseDouble(s);
//                i++;
//            } catch (Exception e) {
//                logger.error("error converting bounding box value to double: " + s, e);
//            }
//        }
//        try {
//
//            //adjust bbox extents with half pixel width/height
//            double pixelWidth = (bbox[2] - bbox[0]) / width;
//            double pixelHeight = (bbox[3] - bbox[1]) / height;
//            bbox[0] += pixelWidth / 2;
//            bbox[2] -= pixelWidth / 2;
//            bbox[1] += pixelHeight / 2;
//            bbox[3] -= pixelHeight / 2;
//
//            //offset for points bounding box by size
//            double xoffset = (bbox[2] - bbox[0]) / (double) width * (size + (highlight != null ? HIGHLIGHT_RADIUS * 2 + size * 0.2 : 0) + 5);
//            double yoffset = (bbox[3] - bbox[1]) / (double) height * (size + (highlight != null ? HIGHLIGHT_RADIUS * 2 + size * 0.2 : 0) + 5);
//
//            //check offset for points bb by maximum uncertainty (?? 30k ??)
//            if (uncertainty) {
//                double xuoffset = 30000;
//                double yuoffset = 30000;
//                if (xoffset < xuoffset) {
//                    xoffset = xuoffset;
//                }
//                if (yoffset < yuoffset) {
//                    yoffset = yuoffset;
//                }
//            }
//
//            //adjust offset for pixel height/width
//            xoffset += pixelWidth;
//            yoffset += pixelHeight;
//
//            double[][] bb = [[SpatialUtils.convertMetersToLng(bbox[0] - xoffset), SpatialUtils.convertMetersToLat(bbox[1] - yoffset)], [SpatialUtils.convertMetersToLng(bbox[2] + xoffset), SpatialUtils.convertMetersToLat(bbox[3] + yoffset)]]
//
//            double[] pbbox = new double[4]; //pixel bounding box
//            pbbox[0] = SpatialUtils.convertLngToPixel(SpatialUtils.convertMetersToLng(bbox[0]));
//            pbbox[1] = SpatialUtils.convertLatToPixel(SpatialUtils.convertMetersToLat(bbox[1]));
//            pbbox[2] = SpatialUtils.convertLngToPixel(SpatialUtils.convertMetersToLng(bbox[2]));
//            pbbox[3] = SpatialUtils.convertLatToPixel(SpatialUtils.convertMetersToLat(bbox[3]));
//
//            String lsid = null;
//            int p1 = 0;
//            int p2 = cql_filter.indexOf('&', p1 + 1);
//            if (p2 < 0) {
//                p2 = cql_filter.indexOf(';', p1 + 1);
//            }
//            if (p2 < 0) {
//                p2 = cql_filter.length();
//            }
//            if (p1 >= 0) {
//                lsid = cql_filter.substring(0, p2);
//            }
//
//            double[] points = null;
//            ArrayList<QueryField> listHighlight = null;
//            QueryField colours = null;
//            double[] pointsBB = null;
//            Facet facet = null;
//            String[] facetFields = null;
//            if (highlight != null && !(colourMode != null && colourMode.equals("grid"))) {
//                facet = Facet.parseFacet(highlight);
//                facetFields = facet.getFields();
//                listHighlight = new ArrayList<QueryField>();
//            }
//
//            int[] idx = null;
//            if (lsid != null) {
//                Object[] data = (Object[]) RecordsLookup.getData(lsid);
//                points = (double[]) data[1];
//                pointsBB = (double[]) data[4];
//                idx = (int[]) data[5];
//
//                if (points == null || points.length == 0
//                        || pointsBB[0] > bb[1][0] || pointsBB[2] < bb[0][0]
//                        || pointsBB[1] > bb[1][1] || pointsBB[3] < bb[0][1]) {
//                    setImageBlank(response);
//                    return;
//                }
//
//                ArrayList<QueryField> fields = (ArrayList<QueryField>) data[2];
//
//                for (int j = 0; j < fields.size(); j++) {
//                    if (facet != null) {
//                        for (int k = 0; k < facetFields.length; k++) {
//                            if (facetFields[k].equals(fields.get(j).getName())) {
//                                listHighlight.add(fields.get(j));
//                            }
//                        }
//                    }
//                    if (colourMode != null) {
//                        if (fields.get(j).getName().equals(colourMode)) {
//                            synchronized (fields.get(j)) {
//                                //need to resize 'colours' facet to the correct length and store as new QueryField
//                                for (int k = 0; k < fields.size(); k++) {
//                                    if (fields.get(k).getName().equals(colourMode + " resized")) {
//                                        colours = fields.get(k);
//                                    }
//                                }
//
//                                if (colours == null) {
//                                    colours = fields.get(j);
//
//                                    //does it need to be rebuilt to the correct length?
//                                    int count = colours.getIntData() != null ? colours.getIntData().length :
//                                            colours.getLongData() != null ? colours.getLongData().length :
//                                                    colours.getFloatData() != null ? colours.getFloatData().length :
//                                                            colours.getDoubleData() != null ? colours.getDoubleData().length : 0;
//                                    if (count != points.length / 2) {
//                                        QueryField qf = new QueryField();
//                                        qf.setDisplayName(colours.getDisplayName());
//                                        qf.setName(colours.getName() + " resized");
//                                        for (int k = 0; k < idx.length; k++) {
//                                            qf.add(colours.getAsString(idx[k]));
//                                        }
//                                        qf.store();
//
//                                        fields.add(qf);
//
//                                        colours = qf;
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            /* TODO: make this a copy instead of create */
//            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
//            Graphics2D g = (Graphics2D) img.getGraphics();
//            g.setColor(new Color(0, 0, 0, 0));
//            g.fillRect(0, 0, width, height);
//
//            g.setColor(new Color(red, green, blue, alpha));
//            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//            int x, y;
//            int pointWidth = size * 2 + 1;
//            double width_mult = (width / (pbbox[2] - pbbox[0]));
//            double height_mult = (height / (pbbox[1] - pbbox[3]));
//
//            if (colourMode != null && colourMode.equals("grid")) {
//                int divs = 16;
//                double grid_width_mult = (width / (pbbox[2] - pbbox[0])) / (256 / divs);
//                double grid_height_mult = (height / (pbbox[1] - pbbox[3])) / (256 / divs);
//                int[][] gridCounts = new int[divs][divs];
//                for (i = 0; i < points.length; i += 2) {
//                    x = (int) ((SpatialUtils.convertLngToPixel(points[i]) - pbbox[0]) * grid_width_mult);
//                    y = (int) ((SpatialUtils.convertLatToPixel(points[i + 1]) - pbbox[3]) * grid_height_mult);
//                    if (x >= 0 && x < divs && y >= 0 && y < divs) {
//                        gridCounts[x][y]++;
//                    }
//                }
//                int xstep = 256 / divs;
//                int ystep = 256 / divs;
//                for (x = 0; x < divs; x++) {
//                    for (y = 0; y < divs; y++) {
//                        int v = gridCounts[x][y];
//                        if (v > 0) {
//                            if (v > 500) {
//                                v = 500;
//                            }
//                            int colour = Legend.getLinearColour(v, 0, 500, 0xFFFFFF00, 0xFFFF0000);
//                            g.setColor(new Color(colour));
//                            g.fillRect(x * xstep, y * ystep, xstep, ystep);
//                        }
//                    }
//                }
//            } else {
//                //circle type
//                if (name.equals("circle")) {
//                    if (colours == null) {
//                        for (i = 0; i < points.length; i += 2) {
//                            if (points[i] >= bb[0][0] && points[i] <= bb[1][0]
//                                    && points[i + 1] >= bb[0][1] && points[i + 1] <= bb[1][1]) {
//                                x = (int) ((SpatialUtils.convertLngToPixel(points[i]) - pbbox[0]) * width_mult);
//                                y = (int) ((SpatialUtils.convertLatToPixel(points[i + 1]) - pbbox[3]) * height_mult);
//                                g.fillOval(x - size, y - size, pointWidth, pointWidth);
//                            }
//                        }
//                    } else {
//                        int prevColour = -1;    //!= colours[0]
//                        g.setColor(new Color(prevColour));
//                        for (i = 0; i < points.length; i += 2) {
//                            if (points[i] >= bb[0][0] && points[i] <= bb[1][0]
//                                    && points[i + 1] >= bb[0][1] && points[i + 1] <= bb[1][1]) {
//                                //colours is made the correct length, see above
//                                int thisColour = colours.getColour(i / 2);
//                                if (thisColour != prevColour) {
//                                    g.setColor(new Color(thisColour));
//                                    prevColour = thisColour;
//                                }
//                                x = (int) ((SpatialUtils.convertLngToPixel(points[i]) - pbbox[0]) * width_mult);
//                                y = (int) ((SpatialUtils.convertLatToPixel(points[i + 1]) - pbbox[3]) * height_mult);
//                                g.fillOval(x - size, y - size, pointWidth, pointWidth);
//                            }
//                        }
//                    }
//                }
//
//                if (highlight != null && facet != null) {
//                    g.setStroke(new BasicStroke(2));
//                    g.setColor(new Color(255, 0, 0, 255));
//                    int sz = size + HIGHLIGHT_RADIUS;
//                    int w = sz * 2 + 1;
//
//                    for (i = 0; i < points.length; i += 2) {
//                        if (points[i] >= bb[0][0] && points[i] <= bb[1][0]
//                                && points[i + 1] >= bb[0][1] && points[i + 1] <= bb[1][1]) {
//
//                            if (facet.isValid(listHighlight, idx[i / 2])) {
//                                x = (int) ((SpatialUtils.convertLngToPixel(points[i]) - pbbox[0]) * width_mult);
//                                y = (int) ((SpatialUtils.convertLatToPixel(points[i + 1]) - pbbox[3]) * height_mult);
//                                g.drawOval(x - sz, y - sz, w, w);
//                            }
//                        }
//                    }
//                }
//            }
//
//            g.dispose();
//
//            try {
//                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                ImageIO.write(img, "png", os);
//                InputStream fis = new ByteArrayInputStream(os.toByteArray());
//                reurn fis
//            } catch (IOException e) {
//                logger.error("error in outputting wms/reflect image as png", e);
//            }
//        } catch (Exception e) {
//            logger.error("error generating wms/reflect tile", e);
//        }
//
//    }
//
//    def getBlankImage() {
//        if (blankImageBytes == null && blankImageObject != null) {
//            synchronized (blankImageObject) {
//                if (blankImageBytes == null) {
//                    try {
//                        RandomAccessFile raf = new RandomAccessFile(UserDataService.class.getResource("/blank.png").getFile(), "r");
//                        blankImageBytes = new byte[(int) raf.length()];
//                        raf.read(blankImageBytes);
//                        raf.close();
//                    } catch (IOException e) {
//                        log.error("error reading default blank tile", e);
//                    }
//                }
//            }
//        }
//        if (blankImageObject != null) {
//            try {
//                return new ByteArrayInputStream(blankImageBytes)
//            } catch (IOException e) {
//                log.error("error outputting blank tile", e);
//            }
//        }
//    }
}
