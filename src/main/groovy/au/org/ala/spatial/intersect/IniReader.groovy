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
 * Provides read only access to an ini file.
 * <p/>
 * File format expected is:
 * <code>
 * [section_name]
 * key_name=key_value
 * </code>
 * where key_values are able to be returned when a
 * section_name and key_name are provided.
 * <p/>
 * Errors and absences result in default values returned
 * from get functions.
 *
 * @author Adam Collins
 */
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

//@CompileStatic
@Slf4j
class IniReader {

    /**
     * Log4j instance
     */
    ////protected Logger logger = log.getLogger(this.getClass());
    /**
     * store for ini data after loading
     * <li>map key is concat of section_name + "\\" + key_name
     * <li>map object is key value as a String
     */
    java.util.HashMap<String, String> document

    /**
     * Constructor loads ini file into the document object.
     * Any errors or failure will log an error only.
     *
     * @param filename ini file to load
     */
    IniReader(String filename) {
        document = new java.util.HashMap<String, String>()
        loadFile(filename)
    }

    /**
     * errors result in a log of the error only
     *
     * @param filename file to load into document object
     */
    private void loadFile(String filename) {
        BufferedReader br = null
        try {
            br = new BufferedReader(new FileReader(filename))
            String currentSection = ""
            String key
            String value
            int i
            while (br.ready()) {
                String line = br.readLine().trim()//don't care about whitespace
                // ignore the comments
                if (line.startsWith("#") || line.startsWith(";")) {
                    continue
                }
                if (line.length() > 2 && line.charAt(0) == '[') {
                    i = line.lastIndexOf("]")
                    if (i <= 0 && line.length() > 1) { //last brace might be missing
                        currentSection = line.substring(1)
                    } else if (i > 2) { //empty section names are ignored
                        currentSection = line.substring(1, i)
                    }
                } else if (line.length() > 2) {
                    key = ""
                    value = ""
                    i = line.indexOf("=") //rather than split incase value contains '='
                    if (i > 1) {
                        key = line.substring(0, i)
                    }
                    if (i < line.length() - 1) {
                        value = line.substring(i + 1)
                    }
                    //do not add if key is empty
                    document.put(currentSection + "\\" + key, value)

                }
            }
        } catch (Exception e) {
            log.error("error opening ini file", e)
        } finally {
            if ( br != null ) {
                try {
                    br.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }

    /**
     * @param section section name as String
     * @param key key name as String
     * @return value of key as String
     * empty string when key is not found
     */
    String getStringValue(String section, String key) {
        String ret = document.get(section + "\\" + key)
        if (ret == null) {
            ret = ""
        }
        return ret
    }

    /**
     * @param section section name as String
     * @param key key name as String
     * @return value of key as int
     * 0 when key is not found
     */
    int getIntegerValue(String section, String key) {
        String str = document.get(section + "\\" + key)
        Integer ret
        try {
            ret = Integer.valueOf(str)
        } catch (Exception e) {
            ret = Integer.valueOf(0)
        }
        return ret.intValue()
    }

    /**
     * @param section section name as String
     * @param key key name as String
     * @return value of key as double
     * 0 when key is not found
     */
    double getDoubleValue(String section, String key) {
        String str = document.get(section + "\\" + key)
        Double ret
        try {
            ret = new Double(str)
        } catch (Exception e) {
            ret = new Double(0)
        }
        return ret.doubleValue()
    }

    /**
     * @param section
     * @param key
     * @return true if value was loaded from the ini file
     * false if the value was not loaded from the ini file
     */
    boolean valueExists(String section, String key) {
        return document.get(section + "\\" + key) != null
    }

    void setValue(String section, String key, String value) {
        document.put(section + "\\" + key, value)
    }

    void write(String filename) {
        write(document, filename)
    }

    void write(Map<String, String> doc, String filename) {
        PrintWriter out = null
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(filename)))

            TreeMap<String, String> pmap = new TreeMap<String, String>(doc)
            Iterator<String> it = pmap.keySet().iterator()
            String currentSection = ""
            while (it.hasNext()) {
                String key = it.next()
                String[] sectionkey = key.split("\\\\")
                if (currentSection != sectionkey[0]) {
                    currentSection = sectionkey[0]
                    out.println("\n")
                    out.println("[" + sectionkey[0] + "]")
                }
                out.println(sectionkey[1] + "=" + pmap.get(key))
            }
            out.flush()
        } catch (Exception e) {
            log.error("Unable to write ini to " + filename, e)
        } finally {
            if (out != null) {
                try {
                    out.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
    }
}
