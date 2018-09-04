'use strict';

// Creating angular Application with module name "GoogleOAuth2"
var app = angular.module('ViewerDemo',[]);

app.config(['$httpProvider', function ($httpProvider) {
    $httpProvider.defaults.headers.common['X-Requested-With'] = 'XMLHttpRequest';
}]);