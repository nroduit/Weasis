'use strict';

// Creating the Angular Controller
app.controller('AppCtrl', function ($http, $scope) {

    // method for getting user details
    var getUser = function () {
        $http.get('/user').success(function (user) {
            $scope.user = user;
            console.log('Logged User : ', user);
            getProjects();
        }).error(function (error) {
            $scope.resource = error;
        });
    };
    
    var getProjects = function () {
        $http.get('/project').success(function (data) {
            $scope.projects = data;
            console.log('Projects: ', data);
        }).error(function (error) {
            $scope.resource = error;
        });
    };

    
    getUser();

    // method for logout
    $scope.logout = function () {
        $http.post('/logout').success(function (res) {
            $scope.user = null;
        }).error(function (error) {
            console.log("Logout error : ", error);
        });
    };
    
    $scope.projectChanged = function () {
    	console.log("Current project:" + $scope.project);
    }
});
