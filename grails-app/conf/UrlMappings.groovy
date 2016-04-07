class UrlMappings {

    static mappings = {
        //
        // match webservices with layers-service
        //
        "/checklists"(controller: "checklist", action: "index")
        "/checklist/$id"(controller: "checklist", action: "show")

        "/distribution/counts"(controller: "distribution", action: "count")
        "/distribution/radius/count"(controller: "distribution", action: "pointRadiusCount")
        "/distribution/radius"(controller: "distribution", action: "pointRadius")
        "/distribution/lsid/$id"(controller: "distribution", action: "lsid")
        "/distribution/lsids/$id"(controller: "distribution", action: "lsids")
        "/distribution/map/$id"(controller: "distribution", action: "map")
        "/distribution/map/png/$id"(controller: "distribution", action: "overviewMapPng")
        "/distribution/map/seed"(controller: "distribution", action: "overviewMapSeed")
        "/distribution/outliers/$id"(controller: "distribution", action: "outliers")
        "/distribution/map/lsid/$id"(controller: "distribution", action: "overviewMapPngLsid")
        "/distribution/map/spcode/$id"(controller: "distribution", action: "overviewMapPngSpcode")
        "/distribution/map/name/$id"(controller: "distribution", action: "overviewMapPngName")
        "/distribution/map/lsids/$id"(controller: "distribution", action: "lsidMaps")
        "/distribution/$id"(controller: "distribution", action: "show")
        "/distributions"(controller: "distribution", action: "index")

        "/attribution/clearCache"(controller: "distribution", action: "clearAttributionCache")

        "/fields"(controller: "field", action: "index")
        "/field/$id"(controller: "field", action: "show")
        "/fieldsdb"(controller: "field", action: "db")
        "/fields/search"(controller: "field", action: "search")
        "/fields/layers/search"(controller: "field", action: "searchLayers")

        "/intersect/$ids/$lat/$lng"(controller: "intersect", action: "intersect")
        "/intersect/batch"(controller: "intersect", action: "batch")
        "/intersect/batch/$id"(controller: "intersect", action: "batchStatus")
        "/intersect/batch/download/$id"(controller: "intersect", action: "batchDownload")
        "/intersect/reloadconfig"(controller: "intersect", action: "reloadConfig")
        "/intersect/pointradius/$fid/$lat/$lng/$radius"(controller: "intersect", action: "pointRadius")
        "/intersect/wkt/$fid"(controller: "intersect", action: "wktGeometryIntersect")
        "/intersect/geojson/$fid"(controller: "intersect", action: "wktGeometryIntersect")
        "/intersect/object/$fid/$pid"(controller: "intersect", action: "objectIntersect")
        "/intersect/poi/pointradius/$lat/$lng/$radius"(controller: "intersect", action: "poiPointRadiusIntersect")
        "/intersect/poi/wkt"(controller: "intersect", action: "wktPoiIntersectGet")
        "/intersect/poi/geojson"(controller: "intersect", action: "geojsonPoiIntersectGet")
        "/intersect/poi/object/$pid"(controller: "intersect", action: "objectPoiIntersect")
        "/intersect/poi/count/pointradius/$lat/$lng/$radius"(controller: "intersect", action: "poiPointRadiusIntersectCount")
        "/intersect/poi/count/wkt"(controller: "intersect", action: "wktPoiIntersectGetCount")
        "/intersect/poi/count/geojson"(controller: "intersect", action: "geojsonPoiIntersectGetCount")
        "/intersect/poi/count/object/$pid"(controller: "intersect", action: "geojsonPoiIntersectGetCount")

        "/layers"(controller: "layer", action: "index")
        "/layer/$id"(controller: "layer", action: "show")
        "/layers/search"(controller: "layer", action: "search")
        "/layers/grids"(controller: "layer", action: "grids")
        "/layers/shapes"(controller: "layer", action: "shapes")
        "/layers/rif-cs"(controller: "layer", action: "rif-cs")
        "/layers/index"(controller: "layer", action: "list")
        "/layers/more/$id"(controller: "layer", action: "more")
        "/layers/csv"(controller: "layer", action: "csvlist")

        "/object/$pid"(controller: "object", action: "show")
        "/objects/$id"(controller: "object", action: "fieldObjects")
        "/objects/$id/$lat/$lng"(controller: "object", action: "fieldObjectsPoint")
        "/objects/inarea/$id"(controller: "object", action: "objectsInArea")

        "/search"(controller: "search", action: "search")

        "/shape/wkt/$id"(controller: "shapes", action: "wkt")
        "/shape/geojson/$id"(controller: "shapes", action: "geojson")
        "/shape/shp/$id"(controller: "shapes", action: "shp")
        "/shape/kml/$id"(controller: "shapes", action: "kml")
        "/shape/upload/wkt"(controller: "shapes", action: "uploadWkt")
        "/shape/upload/wkt/$pid"(controller: "shapes", action: "uploadWithWkt")
        "/shape/upload/geojson"(controller: "shapes", action: "uploadGeojson")
        "/shape/upload/geojson/$pid"(controller: "shapes", action: "uploadWithGeojson")
        "/shape/upload/shp"(controller: "shapes", action: 'uploadShp')
        "/shape/upload/shp/$shapeId/$featureIndex"(controller: "shapes", action: 'saveFeatureFromShapeFile')
        "/shape/upload/shp/$objectPid/$shapeId/$featureIndex"(controller: "shapes", action: 'updateFromShapeFileFeature')
        "/shape/upload/pointradius/$latitude/$longitude/$radius"(controller: "shapes", action: 'createPointRadius')
        "/shape/upload/pointradius/$objectPid/$latitude/$longitude/$radius"(controller: "shapes", action: 'updateWithPointRadius')
        "/shape/upload/$pid"(controller: "shapes", action: "deleteShape")
        "/poi"(controller: "shapes", action: "poi")
        "/poi/$id"(controller: "shapes", action: "poiRequest")

        //"/tabulation/$func1/$func2/$fid1/$fid2/tabulation.html"(controller: "tabulation", action:"displayTabulation")
        //"/tabulation/$func1/$func2/$fid1/$fid2/tabulation.$type"(controller: "tabulation", action:"displayTabulationCSVHTML")
        "/tabulation/$func1/$fid1/$fid2/tabulation.$type"(controller: "tabulation", action: "show")
        "/tabulations(.$format)?"(controller: "tabulation", action: "index")
        "/tabulations/"(controller: "tabulation", action: "index")

        "/capabilities"(controller: "admin", action: "capabilities")

        "/tasks/output/$p1/$p2?/$p3?"(controller: "tasks", action: "output")


        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // apply constraints here
            }
        }


        "/"(controller: "main", action: "/index")
        "500"(view: '/error')
    }
}
