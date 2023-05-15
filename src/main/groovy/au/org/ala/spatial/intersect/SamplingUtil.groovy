package au.org.ala.spatial.intersect

import au.org.ala.spatial.Util
import com.opencsv.CSVReader
import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.grails.web.json.JSONObject
import org.springframework.http.*
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


import groovy.transform.CompileStatic
//@CompileStatic
@Slf4j
class SamplingUtil {

    static List<String> sample(String spatialServiceUrl, String[] layers, double[][] points) {
        List<String> output = null
        long start = System.currentTimeMillis()

        String fields = StringUtils.join(layers, ",")
        String strPoints = ""
        for (int i = 0; i < points.length; i++) {
            if (i > 0) {
                strPoints += ","
            }
            strPoints += (points[i][1]) + "," + points[i][0]
        }
        String requestBody = "fids=" + fields + "&points=" + strPoints
        HttpHeaders headers = new HttpHeaders()
        headers.setContentType(MediaType.TEXT_PLAIN)
        HttpEntity<String> request = new HttpEntity<String>(requestBody, headers)

        try {
            RestOperations restTemplate = new RestTemplate()
            ResponseEntity<String> taskResponse = restTemplate.postForEntity(spatialServiceUrl + "/intersect/batch", request, String.class)

            if (taskResponse.getStatusCode() == HttpStatus.OK) {
                JSONObject taskResult = taskResponse.getBody() as JSONObject
                String checkUrl = taskResult["statusUrl"]

                //check status
                boolean notFinished = true
                String downloadUrl = null
                while (notFinished) {
                    //wait 5s before querying status
                    Thread.sleep(5000)

                    JSONObject jo = Util.getUrl(checkUrl) as JSONObject

                    if (jo.containsKey("error")) {
                        notFinished = false
                    } else if (jo.containsKey("status")) {
                        String status = jo["status"]

                        if ("finished" == status) {
                            downloadUrl = jo["downloadUrl"]
                            notFinished = false
                        } else if ("cancelled" == status || "error" == status) {
                            notFinished = false
                        }
                    }
                }

                ZipInputStream zis = null
                CSVReader csv = null
                InputStream is = null
                ArrayList<StringBuilder> tmpOutput = new ArrayList<StringBuilder>()
                long mid = System.currentTimeMillis()
                try {
                    is = new URI(downloadUrl).toURL().openStream()
                    zis = new ZipInputStream(is)
                    ZipEntry ze = zis.getNextEntry()
                    csv = new CSVReader(new InputStreamReader(zis))

                    for (int i = 0; i < layers.length; i++) {
                        tmpOutput.add(new StringBuilder())
                    }
                    String[] line
                    int row = 0
                    csv.readNext() //discard header
                    while ((line = csv.readNext()) != null) {
                        //order is consistent with request
                        for (int i = 2; i < line.length && i - 2 < tmpOutput.size(); i++) {
                            if (row > 0) {
                                tmpOutput.get(i - 2).append("\n")
                            }
                            tmpOutput.get(i - 2).append(line[i])
                        }
                        row++
                    }

                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                } finally {
                    if (zis != null) {
                        try {
                            zis.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                    if (is != null) {
                        try {
                            is.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                    if (csv != null) {
                        try {
                            csv.close()
                        } catch (Exception e) {
                            log.error(e.getMessage(), e)
                        }
                    }
                }

                output = new ArrayList<String>()
                for (int i = 0; i < tmpOutput.size(); i++) {
                    output.add(tmpOutput.get(i).toString())
                    tmpOutput.set(i, null)
                }

                long end = System.currentTimeMillis()

                log.debug("sample time for " + layers.length + " layers and " + 3 + " coordinates: get response="
                        + (mid - start) + "ms, write response=" + (end - mid) + "ms")

            } else {
                log.error("Batch intersect failed: " + taskResponse.getStatusCode())
            }
        } catch (Exception e) {
            log.error("Failed to create a layer intersect task: " + e.getMessage())
        }

        return output
    }
}
