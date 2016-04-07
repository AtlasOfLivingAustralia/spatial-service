(function (angular) {
    'use strict';
    angular.module('species-info-ctrl', ['map-service', 'biocache-service'])
        .controller('SpeciesInfoCtrl', ['$scope', 'MapService', '$timeout', '$rootScope', '$uibModalInstance', 'BiocacheService', 'data',
            function ($scope, MapService, $timeout, $rootScope, $uibModalInstance, BiocacheService, data) {

                $scope.speciesCountKosher = 'counting...'
                $scope.speciesCountKosherAny = 'counting...'
                $scope.speciesCountAll = 'counting...'
                $scope.countKosher = 'counting...'
                $scope.countKosherAny = 'counting...'
                $scope.countAll = 'counting...'
                $scope.speciesList = []
                $scope.dataProviderList = []
                $scope.species = {}

                $scope.init = function (species) {
                    $scope.speciesOrig = species

                    //remove geospatial_kosher
                    var fq = [species.q]
                    for (var i = 0; i < species.fq.length; i++) {
                        if (species.fq[i] !== 'geospatial_kosher:true' &&
                            species.fq[i] !== 'geospatial_kosher:false' &&
                            species.fq[i] !== 'geospatial_kosher:*') {
                            fq.push(species.fq[i])
                        }
                    }
                    var q = fq[0]
                    fq.splice(0, 1)

                    BiocacheService.newLayer({
                        q: q,
                        fq: fq,
                        bs: species.bs,
                        ws: species.ws
                    }, {area: {wkt: species.wkt}}, species.name).then(function (data) {
                        $scope.species = data
                        BiocacheService.speciesCount($scope.species, ['geospatial_kosher:true']).then(function (data) {
                            $scope.speciesCountKosher = data
                        })

                        BiocacheService.speciesCount($scope.species, ['geospatial_kosher:*']).then(function (data) {
                            $scope.speciesCountKosherAny = data
                        })

                        BiocacheService.speciesCount($scope.species).then(function (data) {
                            $scope.speciesCountAll = data
                        })

                        BiocacheService.count($scope.species, ['geospatial_kosher:true']).then(function (data) {
                            $scope.countKosher = data
                        })

                        BiocacheService.count($scope.species, ['geospatial_kosher:*']).then(function (data) {
                            $scope.countKosherAny = data
                        })

                        BiocacheService.count($scope.species).then(function (data) {
                            $scope.countAll = data
                        })

                        BiocacheService.dataProviderList($scope.speciesOrig).then(function (data) {
                            $scope.dataProviderList = data
                        })
                    })
                };

                $scope.init(data)

                $scope.cancel = function (data) {
                    $uibModalInstance.close(data);
                };
            }])
}(angular));