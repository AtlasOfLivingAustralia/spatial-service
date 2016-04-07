(function (angular) {
    'use strict';
    angular.module('area-report-ctrl', ['map-service', 'biocache-service', 'lists-service'])
        .controller('AreaReportCtrl', ['$scope', 'MapService', '$timeout', '$rootScope', '$uibModalInstance',
            'BiocacheService', 'data', '$http', 'ListsService',
            function ($scope, MapService, $timeout, $rootScope, $uibModalInstance, BiocacheService, data, $http, ListsService) {
                $scope.area = data

                $scope.openExpertDistribution = ''
                $scope.openJournalMapDocuments = ''
                $scope.journalMapDocumentCount = function () {
                }


                $scope.distributions = []
                $scope.setDistributionCount = function (data) {
                    $scope.distributions = data
                    for (var k in $scope.items) {
                        if ('Expert distributions' == $scope.items[k].name) {
                            $scope.items[k].value = data.length
                        }
                    }
                }
                $scope.distributionCounts = function () {
                    if ($scope.area.wkt !== undefined && $scope.area.wkt.length > 0) {
                        $http.get(LayersService.url() + "/distributions?wkt=" + $scope.area.wkt).then(function (response) {
                            $scope.setDistributionCount(response.data)
                        });
                    } else {
                        $http.get(LayersService.url() + "/distributions?pid=" + $scope.area.pid).then(function (response) {
                            $scope.setDistributionCount(response.data)
                        });
                    }
                }

                $scope.checklists = []
                $scope.setChecklistCount = function (data) {
                    $scope.checklists = data
                    var areas = {}
                    for (var k in data) {
                        areas[data[k].area_name] = data[k]
                    }
                    var areaCount = 0
                    for (var k in areas) {
                        areaCount++
                    }
                    for (var k in $scope.items) {
                        if ('Checklist species distributions' == $scope.items[k].name) {
                            $scope.items[k].value = data.length
                        }
                        if ('Checklist areas' == $scope.items[k].name) {
                            $scope.items[k].value = areaCount
                        }
                    }
                }
                $scope.checklistCounts = function () {
                    if ($scope.area.wkt !== undefined && $scope.area.wkt.length > 0) {
                        $http.get(LayersService.url() + "/checklists?wkt=" + $scope.area.wkt).then(function (response) {
                            $scope.setChecklistCount(response.data)
                        });
                    } else {
                        $http.get(LayersService.url() + "/checklists?pid=" + $scope.area.pid).then(function (response) {
                            $scope.setChecklistCount(response.data)
                        });
                    }
                }


                $scope.gazPoints = []
                $scope.setGazCount = function (data) {
                    $scope.gazPoints = data
                    for (var k in $scope.items) {
                        if ('Gazetteer Points' == $scope.items[k].name) {
                            $scope.items[k].value = data.length
                        }
                    }
                }
                $scope.gazPointCounts = function () {
                    if ($scope.area.wkt !== undefined && $scope.area.wkt.length > 0) {
                        $http.get(LayersService.url() + "/objects/inarea/" + ListsService.gazField() + "?wkt=" + $scope.area.wkt + "&limit=9999999").then(function (response) {
                            $scope.setGazCount(response.data)
                        });
                    } else {
                        $http.get(LayersService.url() + "/objects/inarea/" + ListsService.gazField() + "?pid=" + $scope.area.pid + "&limit=9999999").then(function (response) {
                            $scope.setGazCount(response.data)
                        });
                    }
                }

                $scope.poi = []
                $scope.setPoi = function (data) {
                    $scope.poi = data
                    for (var k in $scope.items) {
                        if ('Points of interest' == $scope.items[k].name) {
                            $scope.items[k].value = data.length
                        }
                    }
                }
                $scope.pointOfInterestCounts = function () {
                    if ($scope.area.wkt !== undefined && $scope.area.wkt.length > 0) {
                        $http.get(LayersService.url() + "/intersect/poi/wkt?wkt=" + $scope.area.wkt + "&limit=9999999").then(function (response) {
                            $scope.setPoi(response.data)
                        });
                    } else {
                        $http.get(LayersService.url() + "/intersect/poi/wkt?pid=" + $scope.area.pid + "&limit=9999999").then(function (response) {
                            $scope.setPoi(response.data)
                        });
                    }
                }

                $timeout(function () {
                    $scope.checklistCounts()
                    $scope.distributionCounts()
                    $scope.gazPointCounts()
                    $scope.pointOfInterestCounts()
                }, 0)

                $scope.items = [
                    {
                        name: 'Area (sq km)',
                        link: 'http://www.ala.org.au/spatial-portal-help/note-area-sq-km/',
                        linkName: 'info',
                        value: $scope.area.area_km
                    },
                    {
                        name: 'Number of species',
                        q: $scope.area.q,
                        map: false
                    },
                    {
                        name: 'Number of species - spatially valid only',
                        q: $scope.area.q + '&fq=geospatial_kosher:true',
                        map: false
                    },
                    {
                        name: 'Occurrences',
                        q: $scope.area.q,
                        occurrences: true
                    },
                    {
                        name: 'Occurrences - spatially valid only',
                        q: $scope.area.q + '&fq=geospatial_kosher:true',
                        occurrences: true
                    },
                    {
                        name: 'Expert distributions',
                        list: $scope.openExpertDistribution,
                        value: 'counting...'
                    },
                    {
                        name: 'Checklist areas',
                        value: 'counting...'
                    },
                    {
                        name: 'Checklist species distributions',
                        list: $scope.openChecklists,
                        value: 'counting...'
                    },
                    {
                        name: 'Journalmap documents',
                        list: $scope.openJournalMapDocuments,
                        link: 'https://www.journalmap.org',
                        linkName: 'JournalMap',
                        value: 'counting....'
                    },
                    {
                        name: 'Gazetteer Points',
                        mapGaz: true,
                        value: 'counting...'
                    },
                    {
                        name: 'Points of interest',
                        value: 'counting...'
                    },
                    {
                        name: 'Invasive Species',
                        q: $scope.area.q + "&fq=species_list_uid:dr947 OR species_list_uid:dr707 OR species_list_uid:dr945 OR species_list_uid:dr873 OR species_list_uid:dr872 OR species_list_uid:dr1105 OR species_list_uid:dr1787 OR species_list_uid:dr943 OR species_list_uid:dr877 OR species_list_uid:dr878 OR species_list_uid:dr1013 OR species_list_uid:dr879 OR species_list_uid:dr880 OR species_list_uid:dr881 OR species_list_uid:dr882 OR species_list_uid:dr883 OR species_list_uid:dr927 OR species_list_uid:dr823"
                    },
                    {
                        name: 'Threatened Species',
                        q: $scope.area.q + "&fq=species_list_uid:dr1782 OR species_list_uid:dr967 OR species_list_uid:dr656 OR species_list_uid:dr649 OR species_list_uid:dr650 OR species_list_uid:dr651 OR species_list_uid:dr492 OR species_list_uid:dr1770 OR species_list_uid:dr493 OR species_list_uid:dr653 OR species_list_uid:dr884 OR species_list_uid:dr654 OR species_list_uid:dr655 OR species_list_uid:dr490 OR species_list_uid:dr2201"
                    },
                    {
                        name: 'Migratory species - EPBC',
                        q: $scope.area.q + "&fq=species_list_uid:dr1005",
                        link: ListsService.url() + '/speciesListItem/list/dr1005',
                        linkName: 'Full list'
                    },
                    {
                        name: 'Australian iconic species',
                        q: $scope.area.q + "&fq=species_list_uid:dr781",
                        link: ListsService.url() + '/speciesListItem/list/dr781',
                        linkName: 'Full list'
                    },
                    {
                        name: 'Algae',
                        q: $scope.area.q + "&fq=species_group:Algae"
                    },
                    {
                        name: 'Amphibians',
                        q: $scope.area.q + "&fq=species_group:Amphibians"
                    },
                    {
                        name: 'Angiosperms',
                        q: $scope.area.q + "&fq=species_group:Angiosperms"
                    },
                    {
                        name: 'Animals',
                        q: $scope.area.q + "&fq=species_group:Animals"
                    },
                    {
                        name: 'Arthropods',
                        q: $scope.area.q + "&fq=species_group:Arthropods"
                    },
                    {
                        name: 'Bacteria',
                        q: $scope.area.q + "&fq=species_group:Bacteria"
                    },
                    {
                        name: 'Birds',
                        q: $scope.area.q + "&fq=species_group:Birds"
                    },
                    {
                        name: 'Bryophytes',
                        q: $scope.area.q + "&fq=species_group:Bryophytes"
                    },
                    {
                        name: 'Chromista',
                        q: $scope.area.q + "&fq=species_group:Chromista"
                    },
                    {
                        name: 'Crustaceans',
                        q: $scope.area.q + "&fq=species_group:Crustaceans"
                    },
                    {
                        name: 'Dicots',
                        q: $scope.area.q + "&fq=species_group:Dicots"
                    },
                    {
                        name: 'FernsAndAllies',
                        q: $scope.area.q + "&fq=species_group:FernsAndAllies"
                    },
                    {
                        name: 'Fish',
                        q: $scope.area.q + "&fq=species_group:Fish"
                    },
                    {
                        name: 'Fungi',
                        q: $scope.area.q + "&fq=species_group:Fungi"
                    },
                    {
                        name: 'Fish',
                        q: $scope.area.q + "&fq=species_group:Fish"
                    },
                    {
                        name: 'Gymnosperms',
                        q: $scope.area.q + "&fq=species_group:Gymnosperms"
                    },
                    {
                        name: 'Insects',
                        q: $scope.area.q + "&fq=species_group:Insects"
                    },
                    {
                        name: 'Mammals',
                        q: $scope.area.q + "&fq=species_group:Mammals"
                    },
                    {
                        name: 'Molluscs',
                        q: $scope.area.q + "&fq=species_group:Molluscs"
                    },
                    {
                        name: 'Monocots',
                        q: $scope.area.q + "&fq=species_group:Monocots"
                    },
                    {
                        name: 'Plants',
                        q: $scope.area.q + "&fq=species_group:Plants"
                    },
                    {
                        name: 'Protozoa',
                        q: $scope.area.q + "&fq=species_group:Protozoa"
                    },
                    {
                        name: 'Reptiles',
                        q: $scope.area.q + "&fq=species_group:Reptiles"
                    }
                ]

                $scope.count = function (item, promise) {
                    promise.then(function (data) {
                        item.value = data
                    })
                }

                var items = $scope.items
                var k
                for (k in items) {
                    if (items[k].q !== undefined) {
                        items[k].value = 'counting...'
                        if (items[k].occurrences !== undefined && items[k].occurrences) {
                            $scope.count(items[k], BiocacheService.count(items[k].q))
                        } else {
                            $scope.count(items[k], BiocacheService.speciesCount(items[k].q))
                        }
                    }
                }

                $scope.list = function (item) {

                }

                $scope.map = function (item) {
                    BiocacheService.newLayer(item, item.wkt, item.name).then(function (data) {
                        MapService.add(data)
                    })
                }

                $scope.sample = function (item) {

                }

                $scope.cancel = function (data) {
                    $uibModalInstance.close(data);
                };
            }])
}(angular));
