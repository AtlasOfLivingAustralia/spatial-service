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

package au.org.ala.spatial


import grails.converters.JSON
import groovy.util.logging.Slf4j
import org.grails.web.json.JSONArray
import org.springframework.util.StreamUtils

import java.nio.charset.Charset

@Slf4j
class TestUtil {

    static String getResourceAsString(String path) {
        return StreamUtils.copyToString(LayersDistancesServiceSpec.class.getResourceAsStream("/resources/$path"),
                Charset.forName("UTF-8"))
    }

    static String getResourcePath(String path) {
        LayersDistancesServiceSpec.class.getResource("/resources/$path").file
    }

    //
    static <T> List<T> getListFromJSON(String path, Class<T> clazz) {
        def json = (JSONArray) JSON.parse(getResourceAsString(path))

        try {
            List<Layers> list = json.collect { o ->
                T f = clazz.newInstance()
                o.each { k, v ->
                    //set value with Setter
                    def df = clazz.getDeclaredField(k.toString())
                    if (df != null) {
                        def dm = clazz.getDeclaredMethod("set" + k.toString().substring(0, 1).toUpperCase() + k.toString().substring(1, k.toString().length()),
                                (Class[]) [df.getType()])
                        if (dm != null) {
                            if (df.getType().isPrimitive()) {
                                dm.invoke(f, v)
                            } else {
                                dm.invoke(f, df.getType().newInstance(v))
                            }
                        }
                    }
                }
                f
            }

            return list
        } catch (Exception e) {
            e.printStackTrace()
        }

        return null
    }

    static def GDALInstalled(String path) {
        // check for some gdal executables
        def gdal_translate = new File(path + "/gdal_translate")
        if (!gdal_translate.exists()) {
            new Error("GDAL is not installed. Unable to find " + gdal_translate.getPath()).printStackTrace()
            false
        } else {
            true
        }
    }
}
