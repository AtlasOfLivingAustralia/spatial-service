(function (angular) {
    'use strict';
    angular.module('predefined-areas-service', ['map-service'])
        .factory("PredefinedAreasService", ['MapService', function (MapService) {
            return {
                getList: function () {
                    var extents = MapService.getExtents()

                    return [
                        MapService.newArea("World",
                            [], //["longitude:[-180 TO 180]","latitude:[-90 TO 90]"],
                            "",
                            510000000,
                            [-180, -90, 180, 90]),
                        MapService.newArea("Australia",
                            ["longitude:[112 TO 154]", "latitude:[-44 TO -9]"],
                            "POLYGON((112.0 -44.0,154.0 -44.0,154.0 -9.0,112.0 -9.0,112.0 -44.0))",
                            16322156.76,
                            [112, -44, 154, -9]),
                        MapService.newArea("Current extent",
                            ["longitude%3A[" + extents[0] + "%20TO%20" + extents[2] + "]", "latitude%3A[" + extents[1] + "%20TO%20" + extents[3] + "]"],
                            'POLYGON((' + extents[0] + ' ' + extents[1] + ',' + extents[0] + ' ' + extents[3] + ',' +
                            extents[2] + ' ' + extents[3] + ',' + extents[2] + ' ' + extents[1] + ',' +
                            extents[0] + ' ' + extents[1] + '))',
                            LGeo.area(Wellknown.parse('POLYGON((' + extents[0] + ' ' + extents[1] + ',' + extents[0] + ' ' + extents[3] + ',' +
                                extents[2] + ' ' + extents[3] + ',' + extents[2] + ' ' + extents[1] + ',' +
                                extents[0] + ' ' + extents[1] + '))')) / 1000000,
                            [extents[0], extents[1], extents[2], extents[3]])
                    ]
                }
            }

        }])
}(angular));