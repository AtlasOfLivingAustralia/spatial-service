package au.org.ala.spatial

class UrlMappings {

    static mappings = {
        //
        // match webservices with layers-service
        //
        "/checklists(.$format)?"(controller: "checklist", action: "index")
        "/checklist/$id(.$format)?"(controller: "checklist", action: "show")

        "/checklist/lsid/$lsid**"(controller: "checklist", action: "lsid")
        "/checklist/lsids/$lsid**"(controller: "checklist", action: "lsids")

        "/distributions/counts(.$format)?"(controller: "distribution", action: "count")
        "/distributions/radius/counts(.$format)?"(controller: "distribution", action: "pointRadiusCount")
        "/distributions/radius(.$format)?"(controller: "distribution", action: "pointRadius")
        "/distribution/lsid/$lsid**"(controller: "distribution", action: "lsid")
        "/distribution/lsids/$lsid**"(controller: "distribution", action: "lsids")
        "/distribution/map/$lsid**?"(controller: "distribution", action: "lsidMapFirst")
        "/distribution/map/png/$geomIdx(.$format)?"(controller: "distribution", action: "overviewMapPng")
        "/distribution/outliers/$lsid**?"(controller: "distribution", action: "outliers")
        "/distribution/map/lsid/$lsid**?"(controller: "distribution", action: "overviewMapPngLsid")
        "/distribution/map/spcode/$spcode(.$format)?"(controller: "distribution", action: "overviewMapPngSpcode")
        "/distribution/map/name/$name(.$format)?"(controller: "distribution", action: "overviewMapPngName")
        "/distribution/identifyOutlierPointsForDistribution"(controller: "distribution", action: "identifyOutlierPointsForDistribution")
        "/distribution/map/lsids/$lsid**?"(controller: "distribution", action: "lsidMaps")
        "/distribution/$id(.$format)?"(controller: "distribution", action: "show")
        "/distributions(.$format)?"(controller: "distribution", action: "index")
        "/distributions/lsids(.$format)?"(controller: "distribution", action: "listLsids")

        "/tracks/counts(.$format)?"(controller: "track", action: "count")
        "/tracks/radius/counts(.$format)?"(controller: "track", action: "pointRadiusCount")
        "/tracks/radius(.$format)?"(controller: "track", action: "pointRadius")
        "/track/lsid/$lsid**"(controller: "track", action: "lsid")
        "/track/lsids/$lsid**"(controller: "track", action: "lsids")
        "/track/map/$lsid**?"(controller: "track", action: "lsidMapFirst")
        "/track/map/png/$geomIdx(.$format)?"(controller: "track", action: "overviewMapPng")
        "/track/map/seed(.$format)?"(controller: "track", action: "overviewMapSeed")
        "/track/outliers/$outliers(.$format)?"(controller: "track", action: "outliers")
        "/track/map/lsid/$lsid**"(controller: "track", action: "overviewMapPngLsid")
        "/track/map/spcode/$spcode(.$format)?"(controller: "track", action: "overviewMapPngSpcode")
        "/track/map/name/$name(.$format)?"(controller: "track", action: "overviewMapPngName")
        "/track/map/lsids/$lsid**"(controller: "track", action: "lsidMaps")
        "/track/$id(.$format)?"(controller: "track", action: "show")
        "/tracks(.$format)?"(controller: "track", action: "index")
        "/tracks/lsids(.$format)?"(controller: "track", action: "listLsids")

        "/attribution/clearCache(.$format)?"(controller: "distribution", action: "clearAttributionCache")

        "/fields(.$format)?"(controller: "field", action: "index")
        "/field/$id(.$format)?"(controller: "field", action: "show")
        "/fieldsdb(.$format)?"(controller: "field", action: "db")
        "/fields/search(.$format)?"(controller: "field", action: "search")
        "/fields/layers/search(.$format)?"(controller: "field", action: "searchLayers")

        "/intersect/$ids/$lat/$lng"(controller: "intersect", action: "intersect")
        "/intersect/batch(.$format)?"(controller: "intersect", action: "batch")
        "/intersect/batch/$id(.$format)?"(controller: "intersect", action: "batchStatus")
        "/intersect/batch/download/$id(.$format)?"(controller: "intersect", action: "batchDownload")
        "/intersect/reloadconfig(.$format)?"(controller: "intersect", action: "reloadConfig")
        "/intersect/pointradius/$fid/$lat/$lng/$radius"(controller: "intersect", action: "pointRadius")
        "/intersect/wkt/$fid(.$format)?"(controller: "intersect", action: "wktGeometryIntersect")
        "/intersect/geojson/$fid(.$format)?"(controller: "intersect", action: "geojsonGeometryIntersect")
        "/intersect/object/$fid/$pid"(controller: "intersect", action: "objectIntersect")

        "/layers(.$format)?"(controller: "layer", action: "index")
        "/layer/$id(.$format)?"(controller: "layer", action: "show")
        "/layers/search(.$format)?"(controller: "layer", action: "search")
        "/layers/grids(.$format)?"(controller: "layer", action: "grids")
        "/layers/shapes(.$format)?"(controller: "layer", action: "shapes")
        "/layers/rif-cs(.$format)?"(controller: "layer", action: "rif-cs")
        "/layers/index(.$format)?"(controller: "layer", action: "list")
        "/layers/view/more/$id(.$format)?"(controller: "layer", action: "more")
        "/layers/csv(.$format)?"(controller: "layer", action: "csvlist")
        "/layers/layers.csv"(controller: "layer", action: "csvlist")
        "/layers/layerUsage"(controller: "layer", action: "layerUsage")

        "/object/$pid(.$format)?"(controller: "object", action: "show")
        "/objects/$id(.$format)?"(controller: "object", action: "fieldObjects")
//        "/objects/csv/$id(.$format)?"(controller: "object", action: "fieldObjectsCsv")
        "/objects/$id/$lat/$lng"(controller: "object", action: "listByLocation")
        "/objects/inarea/$id(.$format)?"(controller: "object", action: "objectsInArea")
        "/object/intersect/$pid/$lat/$lng"(controller: "object", action: "intersectObject")

        "/search(.$format)?"(controller: "search", action: "search")

        "/shape/wkt/$id(.$format)?"(controller: "shapes", action: "wkt")
        "/shape/geojson/$id(.$format)?"(controller: "shapes", action: "geojson")
        "/shape/shp/$id(.$format)?"(controller: "shapes", action: "shp")
        "/shape/shapefile/$id(.$format)?"(controller: "shapes", action: "shp")
        "/shape/kml/$id(.$format)?"(controller: "shapes", action: "kml")
        "/shape/upload/wkt(.$format)?"(controller: "shapes", action: "uploadWkt")
        "/shape/upload/wkt/$pid(.$format)?"(controller: "shapes", action: "updateWithWKT")
        "/shape/upload/geojson(.$format)?"(controller: "shapes", action: "uploadGeoJSON")
        "/shape/upload/geojson/$pid(.$format)?"(controller: "shapes", action: "updateWithGeojson")
        "/shape/upload/shp(.$format)?"(controller: "shapes", action: 'uploadShapeFile')

        //Post a small set of features via queryString.
        "/shape/upload/shp/$shapeId/$featureIndex(.$format)?"(controller: "shapes", action: 'saveFeatureFromShapeFile')
        //Post features via body to avoid oversize queryString
        "/shape/upload/shp/$shapeId/featureIndex"(controller: "shapes", action: 'saveFeatureFromShapeFile')

        "/shape/upload/shp/$objectPid/$shapeId/$featureIndex(.$format)?"(controller: "shapes", action: 'updateFromShapeFileFeature')
        "/shape/upload/shp/image/$shapeId/$featureIndexes(.$format)?"(controller: "shapes", action: 'shapeImage')
        "/shape/upload/kml(.$format)?"(controller: "shapes", action: 'uploadKMLFile')
        "/shape/upload/pointradius/$latitude/$longitude/$radius"(controller: "shapes", action: 'createPointRadius')
        "/shape/upload/pointradius/$objectPid/$latitude/$longitude/$radius"(controller: "shapes", action: 'updateWithPointRadius')
        "/shape/upload/$pid(.$format)?"(controller: "shapes", action: "deleteShape")
        "/poi(.$format)?"(controller: "shapes", action: "poi")
        "/poi/$id(.$format)?"(controller: "shapes", action: "poiRequest")

        "/tabulation/$func1/$fid1/$fid2/tabulation.$type"(controller: "tabulation", action: "show")
        "/tabulations(.$format)?"(controller: "tabulation", action: "index")
        "/tabulations/"(controller: "tabulation", action: "index")
        "/tabulation/$fid/$pid(.$format)?"(controller: "tabulation", action: "single")

        "/capabilities(.$format)?"(controller: "tasks", action: "capabilities")

        "/tasks/output/$p1/$p2?/$p3?(.$format)?"(controller: "tasks", action: "output")
        "/tasks/output/$p1/$p2?"(controller: "tasks", action: "output")

        "/files/inter_layer_association.csv"(controller: "layerDistances", action: "csv")

        // compatability with pre-refactor instances
        "/master/resource"(controller: "manageLayers", action: "resource")
        "/master/resourcePeek"(controller: "manageLayers", action: "resourcePeek")

        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // apply constraints here
            }
        }


        "/"(view: "/index")
        "500"(view: '/error')
        "404"(view: '/blank')
    }
}
