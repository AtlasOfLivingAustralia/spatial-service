(function (angular) {
    'use strict';
    angular.module('layers-auto-complete-service', [])
        .factory("LayersAutoCompleteService", ["$http", function ($http) {
            return {
                search: function (term) {
                    return $http.get(LayersService.url() + "/fields/search?q=" + term).then(function (response) {
                        return response.data;
                    });
                }
            };
        }])
}(angular));