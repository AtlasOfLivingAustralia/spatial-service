(function (angular) {
    'use strict';
    angular.module('export-area-ctrl', ['biocache-service', 'map-service', 'layers-service'])
        .controller('ExportAreaCtrl', ['$scope', 'MapService', '$timeout', '$rootScope', '$uibModalInstance', 'BiocacheService',
            'LayersService',
            function ($scope, MapService, $timeout, $rootScope, $uibModalInstance, BiocacheService, LayersService) {

                $scope.name = 'exportAreaCtrl'
                $scope.stepNames = ['area to export', 'export type']

                $scope.step = $rootScope.getValue($scope.name, 'step', 1);
                $scope.selectedArea = $rootScope.getValue($scope.name, 'selectedArea', {
                    area: {
                        q: [],
                        wkt: '',
                        bbox: [],
                        name: '',
                        wms: '',
                        legend: ''
                    }
                })
                $scope.type = $rootScope.getValue($scope.name, 'type', 'shp')

                $rootScope.addToSave($scope)

                $scope.hide = function () {
                    $uibModalInstance.close({hide: true});
                }

                $scope.cancel = function (data) {
                    $uibModalInstance.close(data);
                };

                $scope.ok = function (data) {
                    if ($scope.step == 2) {

                        var q = ''
                        if ($scope.selectedArea.area.q && $scope.selectedArea.area.q.length > 0) {
                            q = $scope.selectedArea.area.q
                        }
                        if ($scope.selectedArea.area.wkt && $scope.selectedArea.area.wkt.length > 0) {
                            q = '*:*&wkt=' + $scope.selectedArea.area.wkt
                        }

                        var url = LayersService.getAreaDownloadUrl($scope.selectedArea.area.pid, $scope.type, $scope.selectedArea.area.name)

                        var link = document.createElement("a");
                        link.href = url;
                        link.click();

                        $scope.cancel({noOpen: true})
                    } else {
                        $scope.step = $scope.step + 1
                    }
                };

                $scope.back = function () {
                    if ($scope.step > 1) {
                        $scope.step = $scope.step - 1
                    }
                };

                $scope.isDisabled = function () {
                    if ($scope.step == 1) {
                        return $scope.selectedArea.area.length == 0
                    } else {
                        return false
                    }
                }
            }])
}(angular));