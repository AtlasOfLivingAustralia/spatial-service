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

package au.org.ala.spatial.service

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.layers.dto.Distribution

import javax.annotation.PostConstruct

class DistributionsService {

    def distributionDao
    def grailsApplication

    Map<String, List<Distribution>> lsidMapDistribution = new HashMap()
    Map<String, List<Distribution>> spcodeMapDistribution = new HashMap()
    Map<String, List<Distribution>> lsidMapChecklist = new HashMap()
    Map<String, List<Distribution>> spcodeMapChecklist = new HashMap()
    
    @PostConstruct
    void init() {
        refresh(Distribution.EXPERT_DISTRIBUTION)
        refresh(Distribution.SPECIES_CHECKLIST)
    }

    def refresh(String type) {
        if (!distributionDao) return

        try {
            List<Distribution> distributions = distributionDao.queryDistributions("", -1, -1, null, null, null, null,
                    null, -1, "", null, null, null, null, type, null, null)

            Map<String, List<Distribution>> lsidMap = new HashMap()
            Map<String, List<Distribution>> spcodeMap = new HashMap()

            for (Distribution d : distributions) {
                if (d.lsid && d.wmsurl) {
                    List<Distribution> list = lsidMap.get(d.lsid)
                    if (list == null) list = new ArrayList()
                    list.add(d)
                    lsidMap.put(d.lsid, list)
                }

                if (d.spcode) {
                    List<Distribution> list = spcodeMap.get(String.valueOf(d.spcode))
                    if (list == null) list = new ArrayList()
                    list.add(d)
                    spcodeMap.put(String.valueOf(d.spcode), list)
                }
            }

            if (lsidMap.size() > 0 && spcodeMap.size() > 0) {
                if (type == Distribution.EXPERT_DISTRIBUTION) {
                    lsidMapDistribution = lsidMap
                    spcodeMapDistribution = spcodeMap
                } else if (type == Distribution.SPECIES_CHECKLIST) {
                    lsidMapChecklist = lsidMap
                    spcodeMapChecklist = spcodeMap
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
    }

    List<Distribution> getDistributionsBySpcode(String spcode) {
        List<Distribution> list = spcodeMapDistribution.get(spcode)
        if (list == null) return new ArrayList()
        return list
    }

    List<Distribution> getChecklistsBySpcode(String spcode) {
        List<Distribution> list = spcodeMapChecklist.get(spcode)
        if (list == null) return new ArrayList()
        return list
    }

    List<Distribution> getDistributionsByLsid(String lsid) {
        List<Distribution> list = lsidMapDistribution.get(lsid)
        if (list == null) return new ArrayList()
        return list
    }

    List<Distribution> getChecklistsByLsid(String lsid) {
        List<Distribution> list = lsidMapChecklist.get(lsid)
        if (list == null) return new ArrayList()
        return list
    }

    int getSpeciesChecklistCountByWMS(String lookForWMS) {
        int count = 0
        lsidMapChecklist.each { k, v ->
            v.each { d ->
                if (d.wmsurl == lookForWMS) count++
            }
        }
        return count
    }

    String wrap(String s) {
        if (s == null) return ""
        else "\"" + s.replace("\"", "\"\"").replace("\\", "\\\\") + "\"";
    }

    String[] getAreaChecklists(String[] records) {
        String[] lines = null
        try {
            if (records != null && records.length > 0) {
                String[][] data = new String[records.length - 1][]
                // header
                for (int i = 1; i < records.length; i++) {
                    CSVReader csv = new CSVReader(new StringReader(records[i]))
                    data[i - 1] = csv.readNext()
                    csv.close()
                }
                java.util.Arrays.sort(data, new Comparator<String[]>() {
                    @Override
                    public int compare(String[] o1, String[] o2) {
                        // compare WMS urls
                        String s1 = getChecklistsBySpcode(o1[0])[0].getWmsurl();
                        String s2 = getChecklistsBySpcode(o2[0])[0].getWmsurl();
                        if (s1 == null && s2 == null) {
                            return 0
                        } else if (s1 != null && s2 != null) {
                            return s1.compareTo(s2)
                        } else if (s1 == null) {
                            return -1
                        } else {
                            return 1
                        }
                    }
                })

                lines = new String[records.length]
                lines[0] = lines[0] = "SPCODE,SCIENTIFIC_NAME,AUTHORITY_FULL,COMMON_NAME,FAMILY,GENUS_NAME,SPECIFIC_NAME,MIN_DEPTH,MAX_DEPTH,METADATA_URL,LSID,AREA_NAME,AREA_SQ_KM,SPECIES_COUNT";
                int len = 1
                int thisCount = 0
                for (int i = 0; i < data.length; i++) {
                    thisCount++
                    String s1 = getChecklistsBySpcode(data[i][0])[0].getWmsurl()
                    String s2 = i + 1 < data.length ? getChecklistsBySpcode(data[i + 1][0])[0].getWmsurl() : null;
                    if (i == data.length - 1 || (s1 == null && s2 != null) || (s1 != null && s2 == null) ||
                            (s1 != null && s2 != null && !s1.equals(s2))) {
                        StringBuilder sb = new StringBuilder()
                        for (int j = 0; j < data[i].length; j++) {
                            if (j > 0) {
                                sb.append(",")
                            }
                            if (j == 0 || (j >= 9 && j != 10)) {
                                sb.append(wrap(data[i][j]))
                            }
                        }
                        sb.append(",").append(thisCount)
                        lines[len] = sb.toString()
                        len++
                        thisCount = 0
                    }
                }
                lines = java.util.Arrays.copyOf(lines, len)
            }
        } catch (Exception e) {
            log.error("error building species checklist", e)
            lines = null
        }
        return lines
    }

    String[] getDistributionsOrChecklists(String type, String wkt) {
        List<Distribution> list = distributionDao.queryDistributions(wkt, -1, -1, null, null, null, null,
                null, -1, null, null, null, null, null, type, null, null)
                
        String[] lines = new String[list.size() + 1]
        lines[0] = "SPCODE,SCIENTIFIC_NAME,AUTHORITY_FULL,COMMON_NAME,FAMILY,GENUS_NAME,SPECIFIC_NAME,MIN_DEPTH,MAX_DEPTH,METADATA_URL,LSID,AREA_NAME,AREA_SQ_KM"
        for (int i = 0; i < list.size(); i++) {
            Distribution d = list.get(i)
            String spcode = d.spcode 
            String scientific = d.scientific 
            String auth = d.authority_ 
            String common = d.common_nam  
            String family = d.family 
            String genus = d.genus_name 
            String name = d.specific_n 
            String min = d.min_depth 
            String max = d.max_depth 

            String md = d.metadata_u 
            String lsid = d.lsid 
            String areaName = d.area_name 
            String areaKm = d.area_km 
            String dataResourceUid = d.data_resource_uid

            lines[i + 1] = spcode + "," + wrap(scientific) + "," + wrap(auth) + "," + wrap(common) + "," +
                    wrap(family) + "," + wrap(genus) + "," + wrap(name) + "," + min + "," + max +
                    "," + wrap(md) + "," + wrap(lsid) + "," + wrap(areaName) + "," + wrap(areaKm) +
                    "," + wrap(dataResourceUid)
        }

        return lines
    }
}
