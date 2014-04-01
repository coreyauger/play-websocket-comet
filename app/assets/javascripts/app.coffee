#
# Author: Corey Auger
# corey@nxtwv.com
#

webApp = window.angular.module('walkaboutApp', ['ngRoute','ngSanitize','ui.bootstrap','walkaboutControllers']);

webApp.config(($locationProvider,$routeProvider) ->
  # $locationProvider.html5Mode(true);
  $routeProvider.when('/home',
    templateUrl: '/assets/partials/home.html',
    controller: 'HomeCtrl'
  ).when('/history',
    templateUrl: '/assets/partials/history.html',
    controller: 'HistoryCtrl'
  ).otherwise(
    redirectTo: '/home'
  )
)


walkaboutApp.run(($rootScope, $location, worker, $modal) ->
  isDesktop = self.window.innerWidth > 700;
  $rootScope.isDesktop = isDesktop;


  # TODO: HACK fix me
  $rootScope.$on('webrtc', (event, data) ->
    console.log('GOT WebRTC ' + data);
    frame = document.getElementById("appframe").contentWindow
    frame.postMessage(JSON.stringify(data.msg),'http://'+self.location.host)
  )

  $rootScope.back = -> window.history.back()
  $rootScope.nav = (path) -> $location.path(path)
  $rootScope.loading = true


  $rootScope.launchCallModal = (friendid) ->
    $rootScope.friendId = friendid;
    modalInstance = $modal.open({
      templateUrl: 'myModalContent.html',
      controller: ModalInstanceCtrl,
      backdrop: true,
      resolve:
        friendId: ->
          #return $sce.trustAsUrl( '/apps/webrtc/531f4bc0042a2c7483af19d8');
          #return $sce.trustAsUrl( '/webrtc/' + $rootScope.friendId );
          return $rootScope.friendId
    })
    modalInstance.result.then((selectedItem) ->
      $rootScope.selected = selectedItem
    , ->
      console.log('Modal dismissed at: ' + new Date());
    )

  # Please note that $modalInstance represents a modal window (instance) dependency.
  # It is not the same as the $modal service used above.
  ModalInstanceCtrl = ($scope, $modalInstance, friendId) ->
    #$scope.friendId =  $sce.getTrustedUrl(friendId);
    $scope.friendId = friendId
    window._webrtc = new WebRTC(worker, friendId)
    $scope.ok = -> $modalInstance.close($scope.friendId)
    $scope.cancel = ->
      window._webrtc.onHangup()
      $modalInstance.dismiss('cancel')
    window._webrtc.webrtcOnUserMediaSuccess = ->
      worker.work('notification','notification',{actors:[friendId],app:{id:'webrtc'}})
      $modalInstance.close($scope.friendId)
    window._webrtc.webrtcOnUserMediaFail = ->
      $modalInstance.dismiss('cancel');
    setTimeout( ->
      window._webrtc.webRtcInitialize()
    ,1000)


)



walkaboutApp.directive('resize', ($window) ->
  (scope, element) ->
    w = angular.element($window)
    scope.getWindowDimensions = ->
      return { 'h': w.height(), 'w': w.width() }
    scope.$watch(scope.getWindowDimensions, (newValue, oldValue) ->
      scope.windowHeight = newValue.h
      scope.windowWidth = newValue.w
      if( typeof Android == 'undefined' )
        scope.chatHeight = newValue.h - 170
      else
        scope.chatHeight = newValue.h
      scope.style = ->
        return {
        'height': (newValue.h - 100) + 'px',
        'width': (newValue.w - 100) + 'px'}
    , true);
    w.bind('resize', -> scope.$apply())
)



