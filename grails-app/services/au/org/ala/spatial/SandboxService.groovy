package au.org.ala.spatial

import au.org.ala.spatial.dto.SandboxIngress
import au.org.ala.ws.service.WebService
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.http.entity.ContentType
import org.gbif.dwc.terms.Term
import org.gbif.dwc.terms.TermFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile

import javax.annotation.PostConstruct
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import static java.util.concurrent.TimeUnit.MILLISECONDS

class SandboxService {
    private static final Logger logger = LoggerFactory.getLogger(SandboxService.class)

    SpatialConfig spatialConfig
    WebService webService

    // Treating sandbox queue separate from other processes, not sure if this is the best approach.
    // However, GeneratePoints can run a sandbox import independently of this executor.
    ThreadPoolExecutor executorService

    // Unique Id for each request
    AtomicInteger requestId = new AtomicInteger(0)

    // the requestId of the most recent item in the queue
    AtomicInteger queuePosition = new AtomicInteger(0)

    // this method is only valid for the life of the application
    SandboxIngress getStatus(String dataResourceUid) {
        if (!isValidUUID(dataResourceUid)) {
            return null;
        }

        // get the status of the data resource
        StatusItem statusItem = queueItemStatus.get(dataResourceUid);
        if (statusItem != null) {
            // update the status when the item is still in the queue
            if (statusItem.sandboxIngress.requestId > queuePosition.get()) {
                statusItem.sandboxIngress.status = "queued";
                statusItem.sandboxIngress.message = "waiting for " + (statusItem.sandboxIngress.requestId - queuePosition.get()) + " items to finish";
            }

            return statusItem.sandboxIngress;
        }

        return null;
    }

    class StatusItem {
        SandboxIngress sandboxIngress;
        Runnable runnable;

        StatusItem(SandboxIngress sandboxIngress, Runnable runnable) {
            this.sandboxIngress = sandboxIngress;
            this.runnable = runnable;
        }
    }

    Map<String, StatusItem> queueItemStatus = new ConcurrentHashMap<>()

    @PostConstruct
    void init() {
        // create the require directories

        File uploadDir = new File(spatialConfig.data.dir + "/sandbox/upload");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        File processingDir = new File(spatialConfig.data.dir + "/sandbox/upload");
        if (!processingDir.exists()) {
            processingDir.mkdirs();
        }

        File tmpDir = new File(spatialConfig.data.dir + "/sandbox/tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }

        // setup executor
        executorService = new ThreadPoolExecutor(1, 100, 0, MILLISECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    boolean isValidUUID(String uuid) {
        // validate that uuid is a correctly formed UUID
        try {
            return UUID.fromString(uuid).toString().equals(uuid);
        } catch (Exception ignored) {
            return false;
        }
    }

    SandboxIngress upload(MultipartFile file, String datasetName, String userId) throws IOException {
        // get uuid
        String uuid = UUID.randomUUID().toString();

        SandboxIngress sandboxIngress = new SandboxIngress();
        sandboxIngress.setId(uuid);
        sandboxIngress.setUserId(userId);
        sandboxIngress.setDataResourceUid(UUID.randomUUID().toString());
        sandboxIngress.setDescription(datasetName);
        sandboxIngress.setStatusUrl(spatialConfig.spatialService.url + "/sandbox/status/" + sandboxIngress.getDataResourceUid());
        sandboxIngress.setRequestId(requestId.incrementAndGet())

        if (file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            importCsv(file, sandboxIngress);
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            importZip(file, sandboxIngress);
        } else {
            throw new Exception("Unsupported file type: " + file.getOriginalFilename())
        }

        Runnable task = new Runnable() {
            @Override
            void run() {
                // update queue position, if necessary
                if (queuePosition.get() < sandboxIngress.getRequestId()) {
                    queuePosition.set(sandboxIngress.getRequestId());
                }

                ingress(sandboxIngress);
            }
        }

        queueItemStatus.put(sandboxIngress.getDataResourceUid(), new StatusItem(sandboxIngress, task));

        executorService.execute(task);

        return sandboxIngress;
    }

    /**
     * import a CSV immediately, returning the dataResourceUid when done
     *
     * @param csv a CSV with a header and at least decimalLatitude and decimalLongitude columns
     * @param datasetName
     * @param userId
     * @return a SandboxIngress with dataResourceUid, status (for errors), etc
     * @throws IOException
     */
    SandboxIngress importPoints(String csv, String datasetName, String userId) throws IOException {
        // get uuid
        String uuid = UUID.randomUUID().toString();

        SandboxIngress sandboxIngress = new SandboxIngress();
        sandboxIngress.setId(uuid);
        sandboxIngress.setUserId(userId);
        sandboxIngress.setDataResourceUid(UUID.randomUUID().toString());
        sandboxIngress.setDescription(datasetName);
        sandboxIngress.setStatusUrl(spatialConfig.spatialService.url + "/sandbox/status/" + sandboxIngress.getDataResourceUid());

        // put the csv into a MultipartFile object
        MultipartFile file = new MultipartFile() {
            @Override
            String getName() {
                return "points.csv";
            }

            @Override
            String getOriginalFilename() {
                return "points.csv";
            }

            @Override
            String getContentType() {
                return "text/csv";
            }

            @Override
            boolean isEmpty() {
                return false;
            }

            @Override
            long getSize() {
                return csv.length();
            }

            @Override
            byte[] getBytes() {
                return csv.getBytes(Charset.defaultCharset());
            }

            @Override
            InputStream getInputStream() {
                return new ByteArrayInputStream(csv.getBytes(Charset.defaultCharset()));
            }

            @Override
            void transferTo(File dest) throws IOException {
                FileUtils.write(dest, csv, Charset.defaultCharset());
            }
        };

        importCsv(file, sandboxIngress);

        ingress(sandboxIngress);

        return sandboxIngress;
    }

    void importZip(MultipartFile file, SandboxIngress sandboxIngress) {
        // upload and unzip file
        File thisDir = new File(spatialConfig.data.dir + "/sandbox/upload/" + sandboxIngress.getId());
        try {
            FileUtils.forceMkdir(thisDir);
            File zipFile = new File(thisDir, "archive.zip");
            file.transferTo(zipFile);

            // unzip into thisDir, the files "meta.xml" and "occurrences.txt"
            ZipFile zip = new ZipFile(zipFile);

            // check if this is a DwCA by finding "meta.xml" in the zip file
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().toLowerCase().endsWith("meta.xml")) {
                    sandboxIngress.setIsDwCA(true);
                    break;
                }
            }

            // treat as a csv or tsv file when not a DwCA
            if (!sandboxIngress.getIsDwCA()) {
                entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    File entryFile = new File(thisDir, entry.getName());

                    if (!entry.isDirectory()) {
                        InputStream input = zip.getInputStream(entry);
                        OutputStream out = new FileOutputStream(entryFile);
                        IOUtils.copy(input, out);
                        IOUtils.closeQuietly(input);
                        IOUtils.closeQuietly(out);
                    }
                }

                sandboxIngress.setIsDwCA(false);

                String[] header = null;

                // look for a single csv or tsv file
                File[] files = thisDir.listFiles();
                for (File f : files) {
                    if (f.getName().toLowerCase().endsWith(".csv")) {
                        // move to occurrences.txt
                        header = convertCsvToDwCA(f, thisDir, sandboxIngress.getUserId(), sandboxIngress.getDescription());
                    } else if (f.getName().toLowerCase().endsWith(".tsv")) {
                        BufferedReader br = new BufferedReader(new FileReader(f));
                        header = br.readLine().split("\n");
                    }
                }
                if (header != null) {
                    sandboxIngress.setHeaders(interpretHeader(header));
                } else {
                    sandboxIngress = null;

                    logger.error("Error interpreting header: " + thisDir.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            sandboxIngress = null;
            logger.error("Error importing ZIP file: " + thisDir.getAbsolutePath(), e);
        }

        // delete directory on error
        if (sandboxIngress == null) {
            try {
                FileUtils.deleteDirectory(thisDir);
            } catch (IOException e) {
                logger.error("Error deleting directory: " + thisDir.getAbsolutePath(), e);
            }
        }
    }

    void importCsv(MultipartFile file, SandboxIngress si) throws IOException {
        String[] header = null;
        try {
            File thisDir = new File(spatialConfig.data.dir + "/sandbox/upload/" + si.dataResourceUid);
            FileUtils.forceMkdir(thisDir);
            File csvFile = new File(thisDir, "occurrences.csv");
            file.transferTo(csvFile);

            // convert csv to tsv
            si.setHeaders(convertCsvToDwCA(csvFile, thisDir, si.getUserId(), si.getDescription()));
        } catch (IOException e) {
            logger.error("Error importing CSV file", e);
        }

        // update sandboxIngress
        si.setIsDwCA(false);
    }

    String[] convertCsvToDwCA(File csvFile, File thisDir, String userID, String datasetName) throws IOException {
        CSVReader reader = null;
        CSVWriter writer = null;

        String[] header = null;
        try {
            // convert csv to tsv
            File tsvFile = new File(thisDir, "occurrence.tsv");
            reader = new CSVReader(new FileReader(csvFile));
            writer = new CSVWriter(new FileWriter(tsvFile), '\t' as char);

            String[] nextLine;
            int occurrenceIDIndex = -1;
            int occurrenceIDIndexNew = -1;
            int userIDIndex = -1;
            int datasetNameIndex = -1;
            int row = 0;
            while ((nextLine = reader.readNext()) != null) {
                // First row is the header
                if (row == 0) {
                    header = interpretHeader(nextLine);

                    // Append occurrenceID to the header, if absent
                    String occurrenceIDQualified = TermFactory.instance().findTerm("occurrenceID").qualifiedName();
                    occurrenceIDIndex = Arrays.asList(header).indexOf(occurrenceIDQualified);
                    if (occurrenceIDIndex < 0) {
                        String[] newHeader = new String[header.length + 1];
                        System.arraycopy(header, 0, newHeader, 0, header.length);
                        newHeader[header.length] = occurrenceIDQualified;
                        header = newHeader;
                    }

                    // Append userID to the header, if absent
                    String userIDQualified = TermFactory.instance().findTerm("userId").qualifiedName();
                    userIDIndex = Arrays.asList(header).indexOf(userIDQualified);
                    if (userIDIndex < 0) {
                        String[] newHeader = new String[header.length + 1];
                        System.arraycopy(header, 0, newHeader, 0, header.length);
                        newHeader[header.length] = userIDQualified;
                        header = newHeader;
                    }

                    // Append datasetName to the header, if absent
                    String datasetNameQualified = TermFactory.instance().findTerm("datasetName").qualifiedName();
                    datasetNameIndex = Arrays.asList(header).indexOf(datasetNameQualified);
                    if (datasetNameIndex < 0) {
                        String[] newHeader = new String[header.length + 1];
                        System.arraycopy(header, 0, newHeader, 0, header.length);
                        newHeader[header.length] = datasetNameQualified;
                        header = newHeader;
                    }
                } else {
                    // Append row number as the unique occurrenceID
                    if (occurrenceIDIndex < 0) {
                        String[] newLine = new String[nextLine.length + 1];
                        occurrenceIDIndexNew = nextLine.length;

                        System.arraycopy(nextLine, 0, newLine, 0, nextLine.length);
                        newLine[nextLine.length] = Integer.toString(row);
                        nextLine = newLine;
                    } else {
                        // replace occurrenceID with the row number to prevent errors
                        nextLine[occurrenceIDIndex] = Integer.toString(row);

                        occurrenceIDIndexNew = occurrenceIDIndex;
                    }

                    // Append ALA userID as the userID
                    if (userIDIndex < 0) {
                        String[] newLine = new String[nextLine.length + 1];
                        System.arraycopy(nextLine, 0, newLine, 0, nextLine.length);
                        newLine[nextLine.length] = userID;
                        nextLine = newLine;
                    } else {
                        // replace userID with ALA userID
                        nextLine[userIDIndex] = userID;
                    }

                    // Append datasetName to the row
                    if (datasetNameIndex < 0) {
                        String[] newLine = new String[nextLine.length + 1];
                        System.arraycopy(nextLine, 0, newLine, 0, nextLine.length);
                        newLine[nextLine.length] = datasetName;
                        nextLine = newLine;
                    } else {
                        // replace datasetName with the datasetName
                        nextLine[datasetNameIndex] = datasetName;
                    }

                    writer.writeNext(nextLine);
                }

                row ++;
            }

            writer.flush();

            // create meta.xml
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\"?>\n" +
                    "<archive xmlns=\"http://rs.tdwg.org/dwc/text/\">\n" +
                    "  <core encoding=\"UTF-8\" linesTerminatedBy=\"\\r\\n\" fieldsTerminatedBy=\"\\t\" fieldsEnclosedBy=\"&quot;\" ignoreHeaderLines=\"0\" rowType=\"http://rs.tdwg.org/dwc/terms/Occurrence\">\n" +
                    "    <files>\n" +
                    "      <location>occurrence.tsv</location>\n" +
                    "    </files>\n" +
                    "    <id index=\"" + occurrenceIDIndexNew  + "\"/>\n");
            for (int i = 0; i < header.length; i++) {
                sb.append("    <field index=\"").append(i).append("\" term=\"").append(header[i]).append("\"/>\n");
            }
            sb.append("  </core>\n" +
                    "</archive>");
            FileUtils.write(new File(csvFile.getParent(), "meta.xml"), sb.toString(), "UTF-8");

            // create the zip file
            File zipFile = new File(thisDir, "archive.zip");
            FileOutputStream fos = new FileOutputStream(zipFile);
            ZipOutputStream zipOut = new ZipOutputStream(fos);

            ZipEntry entry = new ZipEntry("occurrence.tsv");
            zipOut.putNextEntry(entry);
            FileInputStream input = new FileInputStream(tsvFile);
            IOUtils.copy(input, zipOut);
            IOUtils.closeQuietly(input);
            zipOut.closeEntry();

            entry = new ZipEntry("meta.xml");
            zipOut.putNextEntry(entry);
            input = new FileInputStream(new File(thisDir, "meta.xml"));
            IOUtils.copy(input, zipOut);
            IOUtils.closeQuietly(input);
            zipOut.closeEntry();

            zipOut.close();
        } catch (IOException e) {
            logger.error("Error importing CSV file", e);
        }

        if (reader != null) {
            reader.close();
        }

        if (writer != null) {
            writer.close();
        }

        return header;
    }

    private String[] interpretHeader(String[] header) {
        TermFactory factory = TermFactory.instance();

        String[] matched = new String[header.length];
        for (int i = 0; i < header.length; i++) {
            Term term = factory.findTerm(header[i]);
            if (term != null) {
                matched[i] = term.qualifiedName();
            } else {
                matched[i] = header[i];
            }
        }
        return matched;
    }

    String getUserId(String id) {
        if (!isValidUUID(id)) {
            return null;
        }

        // get userId from SOLR
        Map resp = webService.get(spatialConfig.sandboxSolrUrl + "/" + spatialConfig.sandboxSolrCollection + "/select?q=dataResourceUid%3A" + id + "&fl=userId&rows=1", null, ContentType.APPLICATION_JSON, false, false, null);

        if (resp?.resp != null && resp.resp.containsKey("response") && resp.resp.get("response") instanceof Map) {
            Map response = (Map) resp.resp.get("response");
            if (response.containsKey("docs") && response.get("docs") instanceof List) {
                List docs = (List) response.get("docs");
                if (docs.size() > 0) {
                    Map doc = (Map) docs.get(0);
                    if (doc.containsKey("userId")) {
                        return (String) doc.get("userId");
                    }
                }
            }
        }

        return null;
    }

    /**
     * Delete a sandbox data resource, from the /upload/ directory and SOLR.
     *
     * @param id upload UUID (dataResourceUid)
     * @param userId user ID or null to skip user check
     * @return
     */
    boolean delete(String id, String userId, boolean isAdmin) {
        if (!isValidUUID(id)) {
            return false;
        }

        // check that the user owns this data resource or is admin
        String drUserId = getUserId(id);
        if (!isAdmin || !drUserId.equals(userId)) {
            return false;
        }

        // delete the file uploaded
        File thisDir = new File(spatialConfig.data.dir + "/sandbox/upload/" + id);
        try {
            if (thisDir.exists()) {
                FileUtils.deleteDirectory(thisDir);
            }
        } catch (IOException e) {
            logger.error("Error deleting directory: " + thisDir.getAbsolutePath(), e);
        }

        // delete from SOLR
        String json = '{"delete":{"query":"dataResourceUid:' + id + '"}}'
        Map resp = webService.post(spatialConfig.sandboxSolrUrl + "/" + spatialConfig.sandboxSolrCollection + "/update?commit=true", json, null, ContentType.APPLICATION_JSON, false, false, null)

        return resp != null && resp.statusCode == 200
    }

    /**
     * Load a DwCA into the pipelines
     *
     * @param sandboxIngress
     */
    def ingress(SandboxIngress sandboxIngress) {
        String datasetID = sandboxIngress.getDataResourceUid();

        String [] dwcaToSandboxOpts = new String [] {
                "au.org.ala.pipelines.java.DwcaToSolrPipeline",
                "--datasetId=" + datasetID,
                "--targetPath=" + spatialConfig.data.dir + "/sandbox/processed",
                "--inputPath=" + spatialConfig.data.dir + "/sandbox/upload/" + datasetID,
                "--solrCollection=" + spatialConfig.sandboxSolrCollection,
                "--solrHost=" + spatialConfig.sandboxSolrUrl,
                "--includeSampling=true",
                "--config=" + spatialConfig.data.dir + "/la-pipelines/config/la-pipelines.yaml",
                "--maxThreadCount=" + spatialConfig.sandboxThreadCount
        }
        sandboxIngress.status = "running"
        sandboxIngress.message = "started"
        int result = pipelinesExecute(dwcaToSandboxOpts, new File(spatialConfig.data.dir + "/sandbox/processed/" + datasetID + "/logs/DwcaToSolrPipeline.log"), sandboxIngress)

        if (result != 0) {
            sandboxIngress.status = "error"
            sandboxIngress.message = "DwcaToSolrPipeline failed"
            return
        }

        // delete processing files
        File processedDir = new File(spatialConfig.data.dir + "/sandbox/processed/" + datasetID);
        try {
            if (processedDir.exists()) {
                FileUtils.deleteDirectory(processedDir);
            }
        } catch (IOException e) {
            logger.error("Error deleting directory: " + processedDir.getAbsolutePath(), e);
        }

        // double check SOLR has the records
        try {
            long sleepMs = 500; // 0.5s
            Thread.sleep(sleepMs);
            int maxWaitRetry = 100; // 100x 0.5s = 50s max wait in this loop
            int retry = 0;
            while (retry < maxWaitRetry) {
                ResponseEntity<Map> response = new RestTemplate().exchange(
                        spatialConfig.sandboxSolrUrl + "/" + spatialConfig.sandboxSolrCollection + "/select?q=dataResourceUid:" + sandboxIngress.getDataResourceUid(),
                        HttpMethod.GET,
                        null,
                        Map.class);

                // This will wait only until the first SOLR count > 0 is returned. It is concievable that SOLR may still
                // be processing records.
                if (response.getStatusCode().is2xxSuccessful() &&
                        ((Integer) ((Map) response.getBody().get("response")).get("numFound")) > 0) {
                    int solrCount = ((Integer) ((Map) response.getBody().get("response")).get("numFound"));

                    logger.info("SOLR import successful: " + solrCount + " records");

                    sandboxIngress.status = "finished";
                    sandboxIngress.message = "Import complete: " + solrCount + " records"

                    return solrCount;
                }
                Thread.sleep(sleepMs);
                retry++;
            }
        } catch (Exception e) {
            logger.error("SOLR request failed: " + e.getMessage());
        }

        sandboxIngress.status = "error";
        sandboxIngress.message = "SOLR import failed (or timed out)"

        return 0;
    }

    int pipelinesExecute(String[] opts, File logFile, SandboxIngress sandboxIngress) {
        String [] prefix = spatialConfig.pipelinesCmd.split(" ");
        String [] cmd = new String[prefix.length + opts.length + 1];
        System.arraycopy(prefix, 0, cmd, 0, prefix.length);
        System.arraycopy(opts, 0, cmd, prefix.length, opts.length);
        cmd[cmd.length - 1] = spatialConfig.pipelinesConfig;

        try {
            logger.info("Executing pipeline: " + StringUtils.join(cmd, " "));
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.environment().putAll(System.getenv());
            builder.redirectErrorStream(true);

            Process proc = builder.start();

            logSandboxStream(proc.getInputStream(), logFile, sandboxIngress);

            return proc.waitFor();
        } catch (Exception e) {
            logger.error("Error executing pipeline: " + Arrays.toString(cmd), e);
            throw new RuntimeException(e);
        }

        return 1; // error
    }

    private static void logSandboxStream(InputStream stream, File logFile, SandboxIngress sandboxIngress) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;

                if (logFile != null) {
                    logFile.getParentFile().mkdirs();

                    try (FileWriter writer = new FileWriter(logFile)) {
                        while ((line = reader.readLine()) != null) {
                            writer.write(line + "\n");

                            if (sandboxIngress != null && line.contains("PROGRESS")) {
                                sandboxIngress.message = line.substring(line.indexOf("PROGRESS") + 10);
                            }
                        }

                        writer.flush();
                    } catch (IOException e) {
                        logger.error("Error writing log file", e);
                    }
                } else {
                    while ((line = reader.readLine()) != null) {
                        if (sandboxIngress != null && line.contains("PROGRESS")) {
                            sandboxIngress.message = line.substring(line.indexOf("PROGRESS"));
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error reading stream", e);
            }
        }).start();
    }

    // wait a bit, but not too long, to allow stuff to finish, maybe
    void smallSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            logger.error("Error waiting", e);
        }
    }
}
