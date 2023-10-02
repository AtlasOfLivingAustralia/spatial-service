package au.org.ala.spatial.util

import groovy.transform.CompileStatic
import org.apache.commons.io.FileUtils

@CompileStatic
class MapCache {

    private static MapCache singleton
    String mapCachePath
    String baseUrl

    private MapCache(String cachePath, String baseUrl) {
        mapCachePath = cachePath
        this.baseUrl = baseUrl
    }

    static MapCache getMapCache(String cachePath, String baseUrl) {
        if (singleton == null) {
            singleton = new MapCache(cachePath, baseUrl)
        }
        return singleton
    }

    InputStream getCachedMap(String geomIdx) throws Exception {
        File map = new File(mapCachePath + geomIdx)
        if (!map.exists()) {
            cacheMap(geomIdx)
        }
        return new FileInputStream(map)
    }

    void cacheMap(String geomIdx) throws IOException {
        File map = new File(mapCachePath + geomIdx)
        File directory = new File(mapCachePath)
        if (!directory.exists()) {
            FileUtils.forceMkdir(directory)
        }
        if (map.exists())
            map.delete() //remove original

        map.createNewFile()
        //download map and write to file
        InputStream mapInput = (new URL(baseUrl + geomIdx)).openStream()
        try {
            FileUtils.copyInputStreamToFile(mapInput, map)
        } finally {
            mapInput.close()
        }
    }
}
