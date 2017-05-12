package au.org.ala.spatial.util;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;

public class MapCache {

    private static MapCache singleton;
    String mapCachePath;
    String baseUrl;

    private MapCache(String cachePath, String baseUrl) {
        mapCachePath = cachePath;
        this.baseUrl = baseUrl;
    }

    public static MapCache getMapCache(String cachePath, String baseUrl) {
        if (singleton == null) {
            singleton = new MapCache(cachePath, baseUrl);
        }
        return singleton;
    }

    public InputStream getCachedMap(String geomIdx) throws Exception {
        File map = new File(mapCachePath + geomIdx);
        if (!map.exists()) {
            cacheMap(geomIdx);
        }
        return new FileInputStream(map);
    }

    public void cacheMap(String geomIdx) throws IOException {
        File map = new File(mapCachePath + geomIdx);
        File directory = new File(mapCachePath);
        if (!directory.exists()) {
            FileUtils.forceMkdir(directory);
        }
        if (map.exists())
            map.delete(); //remove original

        map.createNewFile();
        //download map and write to file
        InputStream mapInput = (new URL(baseUrl + geomIdx)).openStream();
        FileOutputStream out = new FileOutputStream(map);
        int read = 0;
        byte[] buff = new byte[1024];
        while ((read = mapInput.read(buff)) > 0) {
            out.write(buff, 0, read);
        }
        out.flush();
        mapInput.close();
    }
}
