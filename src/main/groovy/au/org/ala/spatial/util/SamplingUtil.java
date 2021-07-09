package au.org.ala.spatial.util;

import au.com.bytecode.opencsv.CSVReader;
import au.org.ala.spatial.Util;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class SamplingUtil {
    private static Logger logger = Logger.getLogger(SpatialUtils.class);

    public static List<String> sample(String spatialServiceUrl, String[] layers, double[][] points) {
        ArrayList<String> output = null;

        try {
            long start = System.currentTimeMillis();
            URL url = new URL(spatialServiceUrl + "/intersect/batch");
            URLConnection c = url.openConnection();
            c.setDoOutput(true);

            OutputStreamWriter out = null;
            try {
                out = new OutputStreamWriter(c.getOutputStream());
                out.write("fids=");
                for (int i = 0; i < layers.length; i++) {
                    if (i > 0) {
                        out.write(",");
                    }
                    out.write(layers[i]);
                }
                out.write("&points=");
                for (int i = 0; i < points.length; i++) {
                    if (i > 0) {
                        out.write(",");
                    }
                    out.write(String.valueOf(points[i][1]));
                    out.write(",");
                    out.write(String.valueOf(points[i][0]));
                }
                out.flush();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
            String outputStr = IOUtils.toString(c.getInputStream());
            JSONObject jo = JSONObject.fromObject(outputStr);

            if (jo.has("error")) {
                logger.error("Batch intersect failed: " + jo.get("error"));
            } else {
                String checkUrl = jo.getString("statusUrl");
                //check status
                boolean notFinished = true;
                String downloadUrl = null;
                while (notFinished) {
                    //wait 5s before querying status
                    Thread.sleep(5000);

                    jo = JSONObject.fromObject(Util.getUrl(checkUrl));

                    if (jo.containsKey("error")) {
                        notFinished = false;
                    } else if (jo.containsKey("status")) {
                        String status = jo.getString("status");

                        if ("finished".equals(status)) {
                            downloadUrl = jo.getString("downloadUrl");
                            notFinished = false;
                        } else if ("cancelled".equals(status) || "error".equals(status)) {
                            notFinished = false;
                        }
                    }
                }

                ZipInputStream zis = null;
                CSVReader csv = null;
                InputStream is = null;
                ArrayList<StringBuilder> tmpOutput = new ArrayList<StringBuilder>();
                long mid = System.currentTimeMillis();
                try {
                    is = new URI(downloadUrl).toURL().openStream();
                    zis = new ZipInputStream(is);
                    ZipEntry ze = zis.getNextEntry();
                    csv = new CSVReader(new InputStreamReader(zis));

                    for (int i = 0; i < layers.length; i++) {
                        tmpOutput.add(new StringBuilder());
                    }
                    String[] line;
                    int row = 0;
                    csv.readNext(); //discard header
                    while ((line = csv.readNext()) != null) {
                        //order is consistent with request
                        for (int i = 2; i < line.length && i - 2 < tmpOutput.size(); i++) {
                            if (row > 0) {
                                tmpOutput.get(i - 2).append("\n");
                            }
                            tmpOutput.get(i - 2).append(line[i]);
                        }
                        row++;
                    }

                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    if (zis != null) {
                        try {
                            zis.close();
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    if (csv != null) {
                        try {
                            csv.close();
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }

                output = new ArrayList<String>();
                for (int i = 0; i < tmpOutput.size(); i++) {
                    output.add(tmpOutput.get(i).toString());
                    tmpOutput.set(i, null);
                }

                long end = System.currentTimeMillis();

                logger.info("sample time for " + layers.length + " layers and " + 3 + " coordinates: get response="
                        + (mid - start) + "ms, write response=" + (end - mid) + "ms");

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return output;
    }
}
