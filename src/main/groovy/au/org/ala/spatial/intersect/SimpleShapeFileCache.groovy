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

import groovy.util.logging.Slf4j

@Slf4j
//@CompileStatic
class SimpleShapeFileCache {

    /**
     * Log4j instance
     */

    HashMap<String, SimpleShapeFile> cache
    HashMap<String, SimpleShapeFile> cacheByFieldId

    SimpleShapeFileCache(String[] shapeFileNames, String[] columns, String[] fieldIds) {
        cache = new HashMap<String, SimpleShapeFile>()
        cacheByFieldId = new HashMap<String, SimpleShapeFile>()
        update(shapeFileNames, columns, fieldIds)
    }

    SimpleShapeFile get(String shapeFileName) {
        return cache.get(shapeFileName)
    }

    HashMap<String, SimpleShapeFile> getAll() {
        return cacheByFieldId
    }

    void update(String[] layers, String[] columns, String[] fieldIds) {
        //add layers not loaded
        log.info("start caching shape files")
        System.gc()
        log.info("Memory usage (total/used/free):" + (Runtime.getRuntime().totalMemory() / 1024 / 1024) + "MB / " + (Runtime.getRuntime().totalMemory() / 1024 / 1024 - Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB / " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB")
        for (int i = 0; i < layers.length; i++) {
            if (get(layers[i]) == null) {
                try {
                    SimpleShapeFile ssf = new SimpleShapeFile(layers[i], columns[i].split(","))
                    System.gc()
                    log.info(layers[i] + " loaded, Memory usage (total/used/free):" + (Runtime.getRuntime().totalMemory() / 1024 / 1024) + "MB / " + (Runtime.getRuntime().totalMemory() / 1024 / 1024 - Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB / " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + "MB")

                    if (ssf != null) {
                        cache.put(layers[i], ssf)
                        for (String f : fieldIds[i].split(",")) {
                            cacheByFieldId.put(f, ssf)
                        }
                    }
                } catch (Exception e) {
                    log.error("error with shape file: " + layers[i] + ", field: " + columns[i])
                    log.error(e.getMessage(), e)
                }
            }
        }
    }
}
