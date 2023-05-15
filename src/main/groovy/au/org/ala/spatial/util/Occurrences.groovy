package au.org.ala.spatial.util

import groovy.util.logging.Slf4j
import org.codehaus.jackson.util.ByteArrayBuilder

import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Created by a on 10/03/2014.
 */
import groovy.transform.CompileStatic
@Slf4j
@CompileStatic
class Occurrences {

    /**
     * get csv with occurrence data for the specified fields.
     *
     * @param q
     * @param server
     * @param fields
     * @return
     */
    static String getOccurrences(String q, String server, String fields) throws IOException {
        int pageSize = 1000000

        int start = 0

        StringBuilder output = new StringBuilder()

        //TODO: use info from the original analysis request instead of doing this
        boolean isUserData = server.contains("spatial")
        log.debug("isUserData=" + isUserData + ", server=" + server)

        if (isUserData) {
            String url = server + "/userdata/sample?q=" + q + "&fl=" + fields
            log.debug("getting occurrences from : " + url)
            InputStream is = null
            try {
                is = getUrlStream(url)
                ZipInputStream zis = new ZipInputStream(is)
                ZipEntry ze = zis.getNextEntry()

                ByteArrayBuilder bab = new ByteArrayBuilder()
                byte[] b = new byte[1024]
                int n
                while ((n = zis.read(b, 0, 1024)) > 0) {
                    bab.write(b, 0, n)
                }

                String csv = bab.toByteArray().toString()

                output.append(csv)
            } catch (Exception e) {
                log.error("failed to get userdata as csv for url: " + url, e)
            } finally {
                if (is != null) {
                    try {
                        is.close()
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    }
                }
            }

        } else {
            while (start < 300000000) {
                String url = server + "/webportal/occurrences.gz?q=" + q + "&pageSize=" + pageSize + "&start=" + start + "&fl=" + fields
                log.debug("retrieving from biocache : " + url)
                int tryCount = 0
                InputStream is = null
                String csv = null
                int maxTrys = 4
                while (tryCount < maxTrys && csv == null) {
                    tryCount++
                    try {
                        is = getUrlStream(url)
                        csv = new GZIPInputStream(is).text
                    } catch (Exception e) {
                        log.warn("failed try " + tryCount + " of " + maxTrys + ": " + url, e)
                    } finally {
                        if (is != null) {
                            try {
                                is.close()
                            } catch (Exception e) {
                                log.error(e.getMessage(), e)
                            }
                        }
                    }
                }

                if (csv == null) {
                    throw new IOException("failed to get records from biocache.")
                }

                output.append(csv)

                int currentCount = 0
                int i = -1
                while ((i = csv.indexOf("\n", i + 1)) > 0) {
                    currentCount++
                }

                if (currentCount % pageSize == 0 || currentCount % pageSize < pageSize) {
                    break
                } else {
                    start = currentCount
                }
            }
        }

        return output.toString()
    }

    static InputStream getUrlStream(String url) throws IOException {
        log.debug("getting : " + url + " ... ")
        long start = System.currentTimeMillis()
        URLConnection c = new URL(url).openConnection()
        InputStream is = c.getInputStream()
        log.debug((System.currentTimeMillis() - start) + "ms")
        return is
    }
}
