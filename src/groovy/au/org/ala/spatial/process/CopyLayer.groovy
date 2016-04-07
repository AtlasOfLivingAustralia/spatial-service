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

package au.org.ala.spatial.process

import au.org.ala.layers.dto.Field
import au.org.ala.layers.dto.Layer
import au.org.ala.layers.dto.StoreRequest
import au.org.ala.layers.intersect.IntersectConfig
import au.org.ala.layers.util.LayerStoreFilesUtil
import au.org.ala.layers.util.Util
import au.org.ala.spatial.analysis.layers.LayerDistanceIndex
import org.apache.commons.io.FileUtils
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.type.TypeReference
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import org.json.simple.parser.JSONParser

import java.text.MessageFormat

class CopyLayer extends SlaveProcess {

    void start() {
        //get layer file
        JSONParser jp = new JSONParser();
        JSONObject jo = (JSONObject) jp.parse(Util.readUrl(layersServiceUrl + "/layer/" + layerId));
        jo.remove("shape");
        jo.remove("grid");
        String json = jo.toString();
        Layer layer = new ObjectMapper().readValue(json, Layer.class);
        String layerPath = layer.getPath_orig();
        List layerList = new ArrayList();
        layerList.add(layerPath);
        List layerFilter = new ArrayList();
        layerFilter.add("diva");
        layerFilter.add("shape");
        layerFilter.add("shape_diva");

        StoreRequest storeRequest = new StoreRequest();
        storeRequest.setApiKey(apiKey);
        storeRequest.setInclude(layerList);
        storeRequest.setFilter(layerFilter);
        storeRequest.setLayersServiceUrl(layersServiceUrl);

        File shp = new File(layerIntersectDao.getConfig().getLayerFilesPath() + layer.getPath_orig() + ".shp");
        File diva = new File(layerIntersectDao.getConfig().getLayerFilesPath() + layer.getPath_orig() + ".grd");
        long modTime = (shp.exists() ? shp.lastModified() : diva.exists() ? diva.lastModified() : 0);
        LayerStoreFilesUtil.sync(layerIntersectDao.getConfig().getLayerFilesPath(), storeRequest, true);
        long modTimeNew = (shp.exists() ? shp.lastModified() : diva.exists() ? diva.lastModified() : 0);

        //create layer record
        //default values from the name
        layer.setDisplaypath(
                IntersectConfig.GEOSERVER_URL_PLACEHOLDER
                        + "/gwc/service/wms?service=WMS&version=1.1.0&request=GetMap&layers=ALA:" + layer.getName() + "&format=image/png&styles=");

        //this creates or updates a new layerId, want to use the same layerId
        if (layerDao.getLayerById(layer.getId().intValue()) == null) {
            long oldId = layer.getId();
            layerDao.addLayer(layer);
            jdbcTemplate.update("update layers set id=" + oldId + " where id=" + layer.getId());
            layer.setId(oldId);
        } else {
            layerDao.updateLayer(layer);
        }

        //only do layer creation task if source file is new
        if (modTime < modTimeNew) {
            Map m = new HashMap();
            m.put("rawId", null);
            m.put("layersDir", layerIntersectDao.getConfig().getLayerFilesPath());
            m.put("layerId", layer.getId());
            runTask(LayerIngestionService.LAYER_CREATION, JSONValue.toJSONString(m), 1);
        }

        //copy style from geoserver
        String dispPth = jo.get("displaypath").toString();
        String geoserverUrlSrc = dispPth.substring(0, dispPth.indexOf("/gwc"));
        String layerNameSrc = dispPth.substring(dispPth.indexOf("&layers=") + 8);
        layerNameSrc = layerNameSrc.substring(0, layerNameSrc.indexOf('&') < 0 ? layerNameSrc.length() : layerNameSrc.indexOf('&'));
        JSONObject g = (JSONObject) jp.parse(Util.readUrl(geoserverUrlSrc + "/rest/layers/" + layerNameSrc + ".json"));
        String layerName = ((JSONObject) g.get("layer")).get("name").toString();
        String styleSLD = Util.readUrl(((JSONObject) ((JSONObject) g.get("layer")).get("defaultStyle")).get("href").toString().replace(".json", ".sld"));
        File tmp = File.createTempFile(layerName, ".sld");
        FileUtils.write(tmp, styleSLD);

        //geoserver stuff
        String geoserverUrl = layerIntersectDao.getConfig().getGeoserverUrl();
        String geoserverUsername = layerIntersectDao.getConfig().getGeoserverUsername();
        String geoserverPassword = layerIntersectDao.getConfig().getGeoserverPassword();
        String extra = "";

        //Create style
        UploadSpatialResource.loadCreateStyle(geoserverUrl + "/rest/styles/",
                extra, geoserverUsername, geoserverPassword, layerName + "_style");

        //Upload sld
        UploadSpatialResource.loadSld(geoserverUrl + "/rest/styles/" + layerName + "_style",
                extra, geoserverUsername, geoserverPassword, tmp.getPath());

        //Apply style
        String data = "<layer><enabled>true</enabled><defaultStyle><name>" + layerName + "_style</name></defaultStyle></layer>";
        UploadSpatialResource.assignSld(geoserverUrl + "/rest/layers/ALA:" + layerName, extra, geoserverUsername, geoserverPassword, data);

        //wait for shape file to be imported
//            int count = 0;
//            while (count == 0) {
//                try {
//                    Thread.sleep(20000);
//                    count = jdbcTemplate.queryForInt("select count(*) from \"" + layer.getId() + "\"");
//                } catch (Exception e) {
//
//                }
//            }

        //create or update field records
        List<Field> fields = new ObjectMapper().readValue(Util.readUrl(layersServiceUrl + "/fields"), new TypeReference<List<Field>>() {
        });
        for (Field f : fields) {
            if (f.getSpid().equalsIgnoreCase(String.valueOf(layer.getId()))) {

                //'fix' multiple 'desc' parameters
                if (f.getSdesc() != null && f.getSdesc().contains(",")) f.setSdesc(f.getSdesc().split(",")[0].trim());

                if (fieldDao.getFieldById(f.getId()) == null) {
                    String oldId = f.getId();
                    fieldDao.addField(f);
                    jdbcTemplate.update("update fields set id='" + oldId + "' where id='" + f.getId() + "'");
                    f.setId(oldId);
                } else {
                    fieldDao.updateField(f);
                }

                //delete existing objects table values
                if (layerDao.getLayerById(Integer.parseInt(f.getSpid())).getPath_orig().startsWith("shape/")) {
                    jdbcTemplate.update("DELETE FROM objects WHERE fid = '" + f.getId() + "'");

                    //create objects table values
                    String sql = MessageFormat.format("INSERT INTO objects (pid, id, name, \"desc\", fid, the_geom, namesearch)"
                            + " SELECT nextval(''objects_id_seq''::regclass), {0}, MAX({1}), MAX({2}), ''{3}'', st_multi(st_collect(the_geom)), {4} FROM \"{5}\" GROUP BY {6}", f.getSid(), f.getSid(),
                            f.getSdesc() == null || f.getSdesc().isEmpty() ? "''" : f.getSdesc(), f.getId(), Boolean.toString(f.isNamesearch()), f.getSpid(), f.getSid());
                    jdbcTemplate.update(sql);

                    //only do layer creation task if source file is new
                    if (modTime < modTimeNew) {
                        //copy tabulation files
                        layerFilter.add("tabulation");
                        layerList.clear();
                        //get list of tabulation files
                        for (String s : getTabulationFileNames(layer)) {
                            layerList.add("tabulation/" + s);
                        }
                        LayerStoreFilesUtil.sync(layerIntersectDao.getConfig().getLayerFilesPath(), storeRequest, true);
                    }
                } else {
                    //only do layer creation task if source file is new
                    if (modTime < modTimeNew) {
                        //copy layer distances files
                        JSONObject dists = (JSONObject) jp.parse(Util.readUrl(layersServiceUrl + "/layerdistances.json"));
                        Map<String, Double> newDistances = new HashMap<String, Double>();
                        for (Field field : fieldDao.getFields(false)) {
                            if ("e".equalsIgnoreCase(field.getType())
                                    && !field.getId().equals(f.getId())) {
                                String c = (field.getId().compareTo(f.getId()) < 0 ? field.getId() + " " + f.getId() : f.getId() + " " + field.getId());

                                if (dists.containsKey(c)) {
                                    newDistances.put(c, Double.parseDouble(dists.get(c).toString()));
                                }
                            }
                        }
                        LayerDistanceIndex.put(newDistances);
                    }
                }
            }
        }
    }
}
