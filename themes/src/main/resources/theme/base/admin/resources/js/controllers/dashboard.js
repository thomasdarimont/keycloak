module.controller('DashboardCtrl', function($scope, $route, realm, Dashboard, dashboard) {
    $scope.realm = realm;

    // var dashboardHolder = Dashboard.get(realm);
    //
    // dashboardHolder.$promise.then(function(response){
    //
    $scope.userSummary = dashboard.userSummary;
    $scope.latestUserLogins = dashboard.latestUserLogins;
    $scope.latestUserRegistrations = dashboard.latestUserRegistrations;
    //$scope.dailyAggregatedUserLogins = dashboard.dailyAggregatedUserLogins;

    var dailyAggregatedUserLogins = dashboard.dailyAggregatedUserLogins.dataPoints;
    var result = {};
    for (var i = 0; i < dailyAggregatedUserLogins.length; i++){
        result[dailyAggregatedUserLogins[i].eventDate/1000] = dailyAggregatedUserLogins[i].eventCount;
    }

    var now = new Date();
    var previousMonth = new Date();
    previousMonth.setDate(1);
    previousMonth.setMonth(now.getMonth()-1);

    var cal = new CalHeatMap();
    cal.init({
          "domain": "month"
        , "subDoman": "day"
        , "cellSize": "12"
        , "legend": [10, 20, 30, 40]
        , "data": result
        , "empty": "No logins {connector} {date}"
        , "filled": "{count} Logins {connector} {date}"
        , "start": previousMonth
    });
    //
    //
    // });
});
