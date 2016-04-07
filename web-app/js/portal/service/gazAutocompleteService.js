(function (angular) {
    'use strict';
    angular.module('gaz-auto-complete-service', [])
        .factory("GazAutoCompleteService", ["$http", function ($http) {
            return {
                search: function (term) {
                    return $http.get(LayersService.url() + "/search?q=" + term).then(function (response) {
                        return response.data;
                    });
                }
            };
        }])
}(angular));