package au.org.ala.spatial.util

import au.org.ala.spatial.intersect.SimpleRegion
import com.opencsv.CSVReader
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

import java.util.Map.Entry
import java.util.zip.GZIPInputStream

/**
 * @author Adam
 */
@Slf4j
@CompileStatic
class Records {

    //private static final Logger logger = log.getLogger(Records.class);

    ArrayList<Double> points
    ArrayList<Integer> lsidIdx
    ArrayList<Short> years
    String[] lsids
    int speciesSize
    Integer[] sortOrder
    int[] sortOrderRowStarts
    double soMinLat
    double soMinLong
    int soHeight
    double soResolution
    boolean soSortedStarts
    boolean soSortedRowStarts

    Records(String biocache_service_url, String q, double[] bbox, String filename, SimpleRegion region) throws IOException {
        init(biocache_service_url, q, bbox, filename, region, "names_and_lsid")
    }

    Records(String biocache_service_url, String q, double[] bbox, String filename, SimpleRegion region, String facetField) throws IOException {
        init(biocache_service_url, q, bbox, filename, region, facetField)
    }

    Records(String filename) throws IOException {
        int speciesEstimate = 250000
        int recordsEstimate = 26000000

        points = new ArrayList<Double>(recordsEstimate)
        lsidIdx = new ArrayList<Integer>(recordsEstimate)
        HashMap<String, Integer> lsidMap = new HashMap<String, Integer>(speciesEstimate)

        int start = 0

        BufferedReader br = new BufferedReader(new FileReader(filename))

        String[] line
        String rawline
        int[] header = new int[4] //to contain [0]=lsid, [1]=longitude, [2]=latitude
        int row = start
        int currentCount = 0
        String lat, lng, sp
        int p1, p2, p3
        line = new String[4]
        while ((rawline = br.readLine()) != null) {
            currentCount++

            p1 = rawline.indexOf(',')
            p2 = rawline.indexOf(',', p1 + 1)
            p3 = rawline.indexOf(',', p2 + 1)
            if (p1 < 0 || p2 < 0 || p3 < 0) {
                continue
            }
            line[0] = rawline.substring(0, p1)
            line[1] = rawline.substring(p1 + 1, p2)
            line[2] = rawline.substring(p2 + 1, p3)
            line[3] = rawline.substring(p3 + 1)

            if (currentCount % 100000 == 0) {
                log.debug("reading row: " + currentCount)
            }

            String facetName = "names_and_lsid"
            if (row == 0) {
                //determine header
                for (int i = 0; i < line.length; i++) {
                    if (line[i] == facetName) {
                        header[0] = i
                    }
                    if (line[i] == "longitude") {
                        header[1] = i
                    }
                    if (line[i] == "latitude") {
                        header[2] = i
                    }
                    if (line[i] == "year") {
                        header[3] = i
                    }
                }
                log.debug("line: " + line[0] + "," + line[1] + "," + line[2] + "," + (line.length > 3 ? line[3] : "null"))
                log.debug("header: " + header[0] + "," + header[1] + "," + header[2] + "," + header[3])
                boolean notZero = header[1] == 0 || header[2] == 0 || (header[3] == 0 && line.length > 3)
                //'year' may be absent
                boolean notOne = line.length < 1 || header[1] == 1 || header[2] == 1 || header[3] == 1
                boolean notTwo = line.length < 2 || header[1] == 2 || header[2] == 2 || header[3] == 2
                boolean notThree = line.length < 3 || header[1] == 3 || header[2] == 3 || header[3] == 3
                if (!notZero) header[0] = 0
                if (!notOne) header[0] = 1
                if (!notTwo) header[0] = 2
                if (!notThree) header[0] = 3
                log.debug("header: " + header[0] + "," + header[1] + "," + header[2] + "," + header[3])
            } else {
                if (line.length >= 3) {
                    try {
                        double longitude = Double.parseDouble(line[header[1]])
                        double latitude = Double.parseDouble(line[header[2]])
                        points.add(longitude)
                        points.add(latitude)
                        String species = line[header[0]]
                        Integer idx = lsidMap.get(species)
                        if (idx == null) {
                            idx = lsidMap.size()
                            lsidMap.put(species, idx)
                        }
                        lsidIdx.add(idx)
                    } catch (Exception ignored) {
                    }
                }
            }
            row++
        }

        br.close()

        //make lsid list
        lsids = new String[lsidMap.size()]
        for (Entry<String, Integer> e : lsidMap.entrySet()) {
            lsids[e.getValue()] = e.getKey()
        }

        log.debug("Got " + getRecordsSize() + " records of " + getSpeciesSize() + " species")
    }

    Records(String filename, SimpleRegion region) throws IOException {
        int speciesEstimate = 250000
        int recordsEstimate = 26000000

        points = new ArrayList<Double>(recordsEstimate)
        lsidIdx = new ArrayList<Integer>(recordsEstimate)
        HashMap<String, Integer> lsidMap = new HashMap<String, Integer>(speciesEstimate)

        int start = 0

        BufferedReader br = new BufferedReader(new FileReader(filename))

        String[] line
        String rawline
        int[] header = new int[4] //to contain [0]=lsid, [1]=longitude, [2]=latitude
        int row = start
        int currentCount = 0
        String lat, lng, sp
        int p1, p2, p3
        line = new String[4]
        while ((rawline = br.readLine()) != null) {
            currentCount++

            p1 = rawline.indexOf(',')
            p2 = rawline.indexOf(',', p1 + 1)
            p3 = rawline.indexOf(',', p2 + 1)
            if (p1 < 0 || p2 < 0 || p3 < 0) {
                continue
            }
            line[0] = rawline.substring(0, p1)
            line[1] = rawline.substring(p1 + 1, p2)
            line[2] = rawline.substring(p2 + 1, p3)
            line[3] = rawline.substring(p3 + 1)

            if (currentCount % 100000 == 0) {
                log.debug("reading row: " + currentCount)
            }

            String facetName = "names_and_lsid"
            if (row == 0) {
                //determine header
                for (int i = 0; i < line.length; i++) {
                    if (line[i] == facetName) {
                        header[0] = i
                    }
                    if (line[i] == "longitude") {
                        header[1] = i
                    }
                    if (line[i] == "latitude") {
                        header[2] = i
                    }
                    if (line[i] == "year") {
                        header[3] = i
                    }
                }
                log.debug("line: " + line[0] + "," + line[1] + "," + line[2] + "," + line[3])
                log.debug("header: " + header[0] + "," + header[1] + "," + header[2] + "," + header[3])
                boolean notZero = header[1] == 0 || header[2] == 0 || (header[3] == 0 && line.length > 3)
                //'year' may be absent
                boolean notOne = line.length < 1 || header[1] == 1 || header[2] == 1 || header[3] == 1
                boolean notTwo = line.length < 2 || header[1] == 2 || header[2] == 2 || header[3] == 2
                boolean notThree = line.length < 3 || header[1] == 3 || header[2] == 3 || header[3] == 3
                if (!notZero) header[0] = 0
                if (!notOne) header[0] = 1
                if (!notTwo) header[0] = 2
                if (!notThree) header[0] = 3
                log.debug("header: " + header[0] + "," + header[1] + "," + header[2] + "," + header[3])
            } else {
                if (line.length >= 3) {
                    try {
                        double longitude = Double.parseDouble(line[header[1]])
                        double latitude = Double.parseDouble(line[header[2]])
                        if (region != null && !region.isWithin(longitude, latitude)) {
                            continue
                        }
                        points.add(longitude)
                        points.add(latitude)
                        String species = line[header[0]]
                        Integer idx = lsidMap.get(species)
                        if (idx == null) {
                            idx = lsidMap.size()
                            lsidMap.put(species, idx)
                        }
                        lsidIdx.add(idx)
                    } catch (Exception ignored) {
                    }
                }
            }
            row++
        }

        br.close()

        //make lsid list
        lsids = new String[lsidMap.size()]
        for (Entry<String, Integer> e : lsidMap.entrySet()) {
            lsids[e.getValue()] = e.getKey()
        }

        log.debug("\nGot " + getRecordsSize() + " records of " + getSpeciesSize() + " species")
    }

    static InputStream getUrlStream(String url) throws IOException {
        log.debug("getting : " + url + " ... ")
        long start = System.currentTimeMillis()
        URLConnection c = new URL(url).openConnection()
        InputStream is = c.getInputStream()
        log.debug((System.currentTimeMillis() - start) + "ms")
        return is
    }

    static void main(String[] args) {
        log.debug("args[0] = path to save the records file")
        log.debug("args[1] = biocache service URL")

        if (args.length > 0) {
            download(args[0], args[1])
        }
    }

    static void download(String dir, String biocacheServiceUrl) {
        BufferedOutputStream bos = null
        try {
            //split by longitude
            for (int i = -180; i < 180; i++) {
                double longitude1 = i
                double longitude2 = i + 0.9999999999999
                //adjust last longitude2
                if (i == 179) {
                    longitude2 = 180
                }
                new Records(biocacheServiceUrl, "*:*"
                        , new double[]{longitude1, -90, longitude2, 90}
                        , dir + "." + i, null)
            }

            //join
            bos = new BufferedOutputStream(new FileOutputStream(dir))
            for (int i = -180; i < 180; i++) {
                BufferedReader br = null
                try {
                    br = new BufferedReader(new FileReader(dir + "." + i))

                    String line
                    while ((line = br.readLine()) != null) {
                        bos.write(line.bytes)
                        bos.write("\n".bytes)
                    }

                    FileUtils.deleteQuietly(new File(dir + "." + i))
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                } finally {
                    if (br != null) {
                        br.close()
                    }
                }
            }
            bos.flush()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (bos != null) {
                bos.close()
            }
        }
    }

    void init(String biocache_service_url, String q, double[] bbox, String filename, SimpleRegion region, String facetField) throws IOException {
        int speciesEstimate = 250000
        int recordsEstimate = 26000000
        int pageSize = 50000

        String bboxTerm = null
        if (bbox != null) {
            bboxTerm =
                    "&fq=longitude:%5B" + bbox[0] + "%20TO%20" + bbox[2]+"%5D&fq=latitude:%5B" + bbox[1] + "%20TO%20" + bbox[3] + "%5D"
        } else {
            bboxTerm = ""
        }

        points = new ArrayList<Double>(recordsEstimate)
        lsidIdx = new ArrayList<Integer>(recordsEstimate)
        years = new ArrayList<Short>(recordsEstimate)
        HashMap<String, Integer> lsidMap = new HashMap<String, Integer>(speciesEstimate)

        int start = 0

        RandomAccessFile raf = null
        if (filename != null) {
            raf = new RandomAccessFile(filename, "rw")
        }

        while (start < 300000000) {
            String url = biocache_service_url + "/webportal/occurrences.gz?q=" + q.replace(" ", "%20") + bboxTerm + "&pageSize=" + pageSize + "&fq=year%3A*&start=" + start + "&fl=longitude,latitude," + facetField + ",year"

            int tryCount = 0
            InputStream is = null
            CSVReader csv = null
            int maxTrys = 4
            while (tryCount < maxTrys && csv == null) {
                tryCount++
                try {
                    is = getUrlStream(url)
                    csv = new CSVReader(new InputStreamReader(new GZIPInputStream(is)))
                } catch (Exception e) {
                    log.error("failed try " + tryCount + " of " + maxTrys + ": " + url, e)
                }
            }

            if (csv == null) {
                throw new IOException("failed to get records from biocache.")
            }

            String[] line
            int[] header = new int[4] //to contain [0]=lsid, [1]=longitude, [2]=latitude, [3]=year
            int row = start
            int currentCount = 0
            while ((line = csv.readNext()) != null) {
                if (raf != null) {
                    for (int i = 0; i < line.length; i++) {
                        if (i > 0) {
                            raf.write(",".bytes)
                        }
                        raf.write(line[i].bytes)
                    }
                    raf.write("\n".bytes)
                }
                currentCount++
                if (currentCount == 1) {
                    //determine header
                    for (int i = 0; i < line.length; i++) {
                        if (line[i] == facetField) {
                            header[0] = i
                        }
                        if (line[i] == "longitude") {
                            header[1] = i
                        }
                        if (line[i] == "latitude") {
                            header[2] = i
                        }
                        if (line[i] == "year") {
                            header[3] = i
                        }
                    }
                    log.debug("header info:" + header[0] + "," + header[1] + "," + header[2] + "," + header[3])
                } else {
                    if (line.length >= 3) {
                        try {
                            double longitude = Double.parseDouble(line[header[1]])
                            double latitude = Double.parseDouble(line[header[2]])
                            if (region == null || region.isWithin_EPSG900913(longitude, latitude)) {
                                points.add(longitude)
                                points.add(latitude)
                                String species = line[header[0]]
                                Integer idx = lsidMap.get(species)
                                if (idx == null) {
                                    idx = lsidMap.size()
                                    lsidMap.put(species, idx)
                                }
                                lsidIdx.add(idx)
                                years.add(Short.parseShort(line[header[3]]))
                            }
                        } catch (Exception ignored) {

                        } finally {
                            if (lsidIdx.size() * 2 < points.size()) {
                                points.remove(points.size() - 1)
                                points.remove(points.size() - 1)
                            } else if (years.size() < lsidIdx.size()) {
                                years.add((short) 0)
                            }
                        }
                    }
                }
                row++
            }
            if (start == 0) {
                start = row - 1 //offset for header
            }

            csv.close()
            is.close()

            if (is != null) {
                try {
                    is.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }

            if (currentCount == 0 || currentCount < pageSize) {
                break
            }
        }

        if (raf != null) {
            raf.close()
        }

        //make lsid list
        lsids = new String[lsidMap.size()]
        for (Entry<String, Integer> e : lsidMap.entrySet()) {
            lsids[e.getValue()] = e.getKey()
        }

        log.debug("Got " + getRecordsSize() + " records of " + getSpeciesSize() + " species")
    }

    String getSpecies(int pos) {
        return lsids[lsidIdx.get(pos)]
    }

    int getSpeciesNumber(int pos) {
        return lsidIdx.get(pos)
    }

    double getLongitude(int pos) {
        return points.get(pos * 2)
    }

    double getLatitude(int pos) {
        return points.get(pos * 2 + 1)
    }

    short getYear(int pos) {
        return years.get(pos)
    }

    int getRecordsSize() {
        return lsidIdx.size()
    }

    int getSpeciesSize() {
        if (lsids == null) {
            return speciesSize
        } else {
            return lsids.length
        }
    }

    void removeSpeciesNames() {
        speciesSize = lsids.length
        lsids = null
    }

    int[] sortedRowStarts(double minLat, int height, double resolution) {
        if (sortOrder != null && soMinLat == minLat
                && soHeight == height && soResolution == resolution
                && soSortedRowStarts) {
            return sortOrderRowStarts
        }
        //init
        sortOrder = new Integer[(int)(points.size() / 2)]
        for (int i = 0; i < sortOrder.length; i++) {
            sortOrder[i] = i * 2 + 1
        }

        final double mLat = minLat
        final double res = resolution
        final int h = height

        //sort
        Arrays.sort(sortOrder, new Comparator<Integer>() {

            @Override
            int compare(Integer o1, Integer o2) {
                return (h - 1 - ((int) ((points.get(o1) - mLat) / res)))-(h - 1 - ((int) ((points.get(o2) - mLat) / res)))
            }
        })

        //get row starts
        int[] rowStarts = new int[height]
        int row = 0
        for (int i = 0; i < sortOrder.length; i++) {
            int thisRow = (h - 1 - (int) ((points.get(sortOrder[i]) - mLat) / res))

            //handle overflow
            if (thisRow >= height) {
                for (int j = row + 1; j < height; j++) {
                    rowStarts[j] = rowStarts[j - 1]
                }
                break
            }

            //apply next row start
            if (thisRow > row) {
                for (int j = row + 1; j < thisRow; j++) {
                    rowStarts[j] = i
                }
                rowStarts[thisRow] = i
                row = thisRow
            }
        }
        for (int j = row + 1; j < height; j++) {
            rowStarts[j] = sortOrder.length
        }

        //translate sortOrder values from latitude to idx
        for (int i = 0; i < sortOrder.length; i++) {
            sortOrder[i] = (sortOrder[i] - 1) / 2 as Integer
        }

        sortOrderRowStarts = rowStarts
        soMinLat = minLat
        soHeight = height
        soResolution = resolution
        soSortedStarts = false
        soSortedRowStarts = true
        return rowStarts
    }

    void sortedStarts(double minLat, double minLong, double resolution) {
        if (sortOrder != null && soMinLat == minLat
                && soMinLong == minLong
                && soResolution == resolution
                && soSortedStarts) {
            return
        }
        //init
        sortOrder = new Integer[(int)(points.size() / 2)]
        for (int i = 0; i < sortOrder.length; i++) {
            sortOrder[i] = i * 2 + 1
        }

        final double mLat = minLat
        final double mLong = minLong
        final double res = resolution

        //sort
        Arrays.sort(sortOrder, new Comparator<Integer>() {

            @Override
            int compare(Integer o1, Integer o2) {
                int v = ((int) ((points.get(o1) - mLat) / res))-((int) ((points.get(o2) - mLat) / res))

                if (v == 0) {
                    return ((int) ((points.get(o1 - 1) - mLong) / res))-((int) ((points.get(o2 - 1) - mLong) / res))
                } else {
                    return v
                }
            }
        })

        soMinLat = minLat
        soMinLong = minLong
        soResolution = resolution
        soSortedStarts = true
        soSortedRowStarts = false
    }

    String getSortedSpecies(int pos) {
        return lsids[lsidIdx.get(sortOrder[pos])]
    }

    int getSortedSpeciesNumber(int pos) {
        return lsidIdx.get(sortOrder[pos])
    }

    double getSortedLongitude(int pos) {
        return points.get(sortOrder[pos] * 2)
    }

    double getSortedLatitude(int pos) {
        return points.get(sortOrder[pos] * 2 + 1)
    }

    String getSpeciesN(int speciesNumber) {
        return lsids[speciesNumber]
    }
}
