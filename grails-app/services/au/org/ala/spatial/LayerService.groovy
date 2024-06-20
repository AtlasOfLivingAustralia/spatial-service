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
package au.org.ala.spatial

import au.org.ala.spatial.dto.GridClass
import au.org.ala.spatial.dto.IntersectionFile
import au.org.ala.spatial.intersect.SimpleShapeFileCache
import grails.plugin.cache.Cacheable
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.apache.commons.lang.StringUtils
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.type.TypeReference

import java.util.concurrent.ConcurrentHashMap

@Slf4j
class LayerService {

    def dataSource

    SpatialConfig spatialConfig
    FieldService fieldService

    ConcurrentHashMap<String, IntersectionFile> intersectionFiles = new ConcurrentHashMap()
    ConcurrentHashMap<String, HashMap<Integer, GridClass>> classGrids = new ConcurrentHashMap<>()

    List<Layers> getLayers() {
        log.debug("Getting a list of all enabled layers")
        List<Layers> l = Layers.findAllByEnabled(true)
        fieldService.updateDisplayPaths(l)
        return l
    }

    void delete(String layerId) {
        Sql.newInstance(dataSource).execute("delete from layers where id=" + Integer.parseInt(layerId))
    }

    Layers getLayerById(Long id, boolean enabledLayersOnly = true) {
        log.debug("Getting enabled layer info for id = " + id)
        Layers l
        Layers.withTransaction {
            if (enabledLayersOnly) {
                l = Layers.findByIdAndEnabled(id, enabledLayersOnly)
            } else {
                l = Layers.findById(id)
            }
        }
        fieldService.updateDisplayPaths([l])
        l
    }

    void updateLayer(Layers layer) {
        log.debug("Updating layer metadata for " + layer.getName())
        String sql = "update layers set citation_date=:citation_date, classification1=:classification1, classification2=:classification2, datalang=:datalang, description=:description, displayname=:displayname, displaypath=:displaypath, enabled=:enabled, domain=:domain, environmentalvaluemax=:environmentalvaluemax, environmentalvaluemin=:environmentalvaluemin, environmentalvalueunits=:environmentalvalueunits, extents=:extents, keywords=:keywords, licence_link=:licence_link, licence_notes=:licence_notes, licence_level=:licence_level, lookuptablepath=:lookuptablepath, maxlatitude=:maxlatitude, maxlongitude=:maxlongitude, mddatest=:mddatest, mdhrlv=:mdhrlv, metadatapath=:metadatapath, minlatitude=:minlatitude, minlongitude=:minlongitude, name=:name, notes=:notes, path=:path, path_1km=:path_1km, path_250m=:path_250m, path_orig=:path_orig, pid=:pid, respparty_role=:respparty_role, scale=:scale, source=:source, source_link=:source_link, type=:type, uid=:uid where id=:id"
        Layers.executeUpdate(sql, layer)
    }


    Layers getLayerByName(String name) {
        getLayerByName(name, true)
    }


    Layers getLayerByName(String name, boolean enabledLayersOnly) {
        Layers layer = null
        if (enabledLayersOnly) {
            layer = Layers.findByNameAndEnabled(name, true)
        } else {
            layer = Layers.findByName(name)
        }
        fieldService.updateDisplayPaths([layer])

        layer
    }


    Layers getLayerByDisplayName(String name) {
        Layers layer = Layers.findByDisplaynameAndEnabled(name, true)
        fieldService.updateDisplayPaths([layer])

        layer
    }


    List<Layers> getLayersByEnvironment() {
        List<Layers> layers = Layers.findAllByTypeAndEnabled('Environmental', true)
        fieldService.updateDisplayPaths(layers)

        layers
    }


    List<Layers> getLayersByContextual() {
        List<Layers> layers = Layers.findAllByTypeAndEnabled('Contextual', true)
        fieldService.updateDisplayPaths(layers)

        layers
    }


    List<Layers> getLayersByCriteria(String keywords) {
        log.debug("Getting a list of all enabled layers by criteria: " + keywords)
        String sql = ""
        sql += "select * from layers where "
        sql += " enabled=true AND ( "
        sql += "keywords ilike :keywords "
        sql += " or displayname ilike :keywords "

        sql += " or name ilike :keywords "
        sql += " or domain ilike :keywords "
        sql += ") order by displayname "

        String term = "%" + keywords + "%"

        List<Layers> layers = new ArrayList()

        Sql.newInstance(dataSource).query(sql, [keywords: term], {
            Layers layer = it as Layers
            fieldService.updateDisplayPaths([layer])
            layers.add(layer)
        })

        layers
    }


    Layers getLayerByIdForAdmin(int id) {
        Layers layer = Layers.findById(id)
        fieldService.updateDisplayPaths([layer])

        layer
    }


    Layers getLayerByNameForAdmin(String name) {
        Layers layer = Layers.findByName(name)
        fieldService.updateDisplayPaths([layer])

        layer
    }


    List<Layers> getLayersForAdmin() {
        List<Layers> layers = Layers.findAll()

        fieldService.updateDisplayPaths(layers)

        layers
    }


//    void addLayer(Layer layer) {
//        log.debug("Add new layer metadta for " + layer.getName())
//
//        Map<String, Object> parameters = layer.toMap()
//        parameters.remove("uid")
//        parameters.remove("id")
//        insertLayer.execute(parameters)
//        //layer.name is unique, fetch newId
//        Layer newLayer = getLayerByName(layer.getName(), false)
//
//        //attempt to apply requested layer id
//        if (layer.getId() > 0 && getLayerById(layer.getId().intValue()) == null) {
//            //requested id is not in use
//
//            jdbcTemplate.update("UPDATE layers SET id=" + layer.getId() + " WHERE id=" + newLayer.getId())
//
//            newLayer = getLayerByName(layer.getName(), false)
//        }
//
//        layer.setId(newLayer.getId())
//
//    }
//
//
//    void updateLayer(Layer layer) {
//        log.debug("Updating layer metadata for " + layer.getName())
//        String sql = "update layers set citation_date=:citation_date, classification1=:classification1, classification2=:classification2, datalang=:datalang, description=:description, displayname=:displayname, displaypath=:displaypath, enabled=:enabled, domain=:domain, environmentalvaluemax=:environmentalvaluemax, environmentalvaluemin=:environmentalvaluemin, environmentalvalueunits=:environmentalvalueunits, extents=:extents, keywords=:keywords, licence_link=:licence_link, licence_notes=:licence_notes, licence_level=:licence_level, lookuptablepath=:lookuptablepath, maxlatitude=:maxlatitude, maxlongitude=:maxlongitude, mddatest=:mddatest, mdhrlv=:mdhrlv, metadatapath=:metadatapath, minlatitude=:minlatitude, minlongitude=:minlongitude, name=:name, notes=:notes, path=:path, path_1km=:path_1km, path_250m=:path_250m, path_orig=:path_orig, pid=:pid, respparty_role=:respparty_role, scale=:scale, source=:source, source_link=:source_link, type=:type, uid=:uid where id=:id"
//        namedParameterJdbcTemplate.update(sql, layer.toMap())
//    }
//
//    void delete(String layerId) {
//        jdbcTemplate.update("delete from layers where id=" + Integer.parseInt(layerId))
//    }
//
    IntersectionFile getIntersectionFile(String id) {
        IntersectionFile file = intersectionFiles.get(id)

        // rebuild intersectionFiles if not found
        if (!file) {
            file = getIntersectionFiles().get(id)
        }

        file
    }

    static private HashMap<Integer, GridClass> getGridClasses(String filePath, String type) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
        HashMap<Integer, GridClass> classes = null
        if (type.equals("Contextual")) {
            if (new File(filePath + ".gri").exists()
                    && new File(filePath + ".grd").exists()
                    && new File(filePath + ".txt").exists()) {
                File gridClassesFile = new File(filePath + ".classes.json");
                if (gridClassesFile.exists()) {
                    classes = mapper.readValue(gridClassesFile, new TypeReference<Map<Integer, GridClass>>() {})
                    log.info("found grid classes for " + gridClassesFile.getPath())
                } else {
                    log.error("classes unavailable for " + gridClassesFile.getPath() + ", build classes offline")
                }
            } else if (new File(filePath + ".gri").exists()
                    && new File(filePath + ".grd").exists()) {
                log.error("missing grid classes for " + filePath)
            }
        } else {

        }
        return classes
    }

    @Cacheable('getIntersectionFiles')
    Map<String, IntersectionFile> getIntersectionFiles() {
        for (Fields f : fieldService.getFields()) {
            Layers layer = getLayerById(Integer.parseInt(f.getSpid()), false)
            if (layer == null) {
                log.error("cannot find layer with id '" + f.getSpid() + "'")
                continue
            }
            HashMap<Integer, GridClass> gridClasses = getGridClasses(spatialConfig.data.dir + File.separator + layer.getPath_orig(), layer.getType())
            IntersectionFile intersectionFile = new IntersectionFile(f.getName(),
                    spatialConfig.data.dir + File.separator + layer.getPath_orig(),
                    f.getSname(),
                    layer.getName(),
                    f.getId(),
                    f.getName(),
                    String.valueOf(layer.getId()),
                    f.getType(),
                    gridClasses)

            intersectionFiles.put(f.getId(), intersectionFile);
            //also register it under the layer name
            //- only if default layer not already added
            if (f.defaultlayer || intersectionFiles.get(layer.getName()) == null) {
                intersectionFiles.put(layer.getName(), intersectionFile)
            }
            //also register it under the layer pid
            if (f.defaultlayer || intersectionFiles.get(String.valueOf(layer.getId())) == null) {
                intersectionFiles.put(String.valueOf(layer.getId()), intersectionFile)
            }
            //classGrids.put(f.getId(), gridClasses)
        }
        intersectionFiles
    }

    String[] getAnalysisLayerInfoV2(String id) {
        String gid, filename, name
        gid = filename = name = null

        String v2Name = spatialConfig.data.dir + File.separator + 'layer' + File.separator + id
        File v2Grd = new File(v2Name + ".grd")
        File v2Shp = new File(v2Name + ".shp")
        if (v2Grd.exists() || v2Shp.exists()) {
            int idx = id.indexOf('_')
            gid = idx > 0 ? id.substring(0, idx) : id
            filename = v2Name
            if (v2Grd.exists()) {
                name = idx > 0 ? id.substring(idx + 1) : "Gridfile"
            } else {
                name = idx > 0 ? id.substring(idx + 1) : "Shapefile"
            }
            name = StringUtils.capitalize(name.replace("_", " "))
        }

        if (gid != null) {
            new String[]{gid, filename, name}
        } else {
            null
        }
    }

    String[] getAnalysisLayerInfo(String id) {
        String gid, filename, name
        gid = filename = name = null

        String[] v2 = getAnalysisLayerInfoV2(id)
        if (v2 != null) {
            return v2
        } else if (id.startsWith("species_")) {
            //maxent layer
            gid = id.substring("species_".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "maxent" + File.separator + gid + File.separator + gid
            name = "Prediction"
        } else if (id.startsWith("aloc_")) {
            //aloc layer
            gid = id.substring("aloc_".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "aloc" + File.separator + gid + File.separator + "aloc"
            name = "Classification"
        } else if (id.startsWith("odensity_")) {
            //occurrence density layer
            gid = id.substring("odensity_".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "sitesbyspecies" + File.separator + gid + File.separator + "occurrence_density"
            name = "Occurrence Density"
        } else if (id.startsWith("srichness_")) {
            //species richness layer
            gid = id.substring("srichness_".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "sitesbyspecies" + File.separator + gid + File.separator + "species_richness"
            name = "Species Richness"
        } else if (id.endsWith("_odensity") && id.indexOf("_") == id.length() - 9) {
            //occurrence density layer and not of the form GDM's number_number_odensity
            gid = id.substring(0, id.length() - "_odensity".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "sitesbyspecies" + File.separator + gid + File.separator + "occurrence_density"
            name = "Occurrence Density"
        } else if (id.endsWith("_srichness") && id.indexOf("_") == id.length() - 10) {
            //species richness layer and not of the form GDM's number_number_srichness
            gid = id.substring(0, id.length() - "_srichness".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "sitesbyspecies" + File.separator + gid + File.separator + "species_richness"
            name = "Species Richness"
        } else if (id.startsWith("envelope_")) {
            //envelope layer
            gid = id.substring("envelope_".length())
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "envelope" + File.separator + gid + File.separator + "envelope"
            name = "Environmental Envelope"
        } else if (id.startsWith("gdm_")) {
            //gdm layer
            int pos1 = id.indexOf("_")
            int pos2 = id.lastIndexOf("_")
            String[] gdmparts = new String[]{id.substring(0, pos1), id.substring(pos1 + 1, pos2), id.substring(pos2 + 1)}
            gid = gdmparts[2]
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "gdm" + File.separator + gid + File.separator + gdmparts[1]
            IntersectionFile f = getIntersectionFile(gdmparts[1].replaceAll("Tran", ""))
            name = "Transformed " + (f != null ? f.getFieldName() : gdmparts[1].replaceAll("Tran", ""))
        } else if (id.contains("_")) {
            //2nd form of gdm layer name, why?
            int pos = id.indexOf("_")
            String[] gdmparts = new String[]{id.substring(0, pos), id.substring(pos + 1)}
            gid = gdmparts[0]
            filename = spatialConfig.data.dir + File.separator + 'layer' + File.separator + "gdm" + File.separator + gid + File.separator + gdmparts[1] + "Tran"
            log.error("id: " + id)
            log.error("parts: " + gdmparts[0] + ", " + gdmparts[1])
            log.debug("parts: " + gdmparts[0] + ", " + gdmparts[1])
            log.error("filename: " + filename)
            IntersectionFile f = getIntersectionFile(gdmparts[1])
            name = "Transformed " + (f != null ? f.getFieldName() : gdmparts[1])
        }

        if (gid != null) {
            new String[]{gid, filename, name}
        } else {
            null
        }
    }


    SimpleShapeFileCache getShapeFileCache() {
        //TODO
    }
}
