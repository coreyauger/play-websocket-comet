playWSCometControllers = angular.module('playWSCometControllers', [])
.config(($provide, $compileProvider, $filterProvider) ->
  console.log('configure')
)


playWSCometControllers.controller('HomeCtrl',($scope, $routeParams, $location, worker) ->
  $scope.isDesktop = false
  $scope.messages = []
  $scope.username = ''

  $scope.getStarted = ->
    if( $scope.username != '' )
      clean = $scope.username.replace(/[^a-z0-9]/gi,'')
      $location.path('/slides/'+clean)



).controller('SlideCtrl', ($scope, $routeParams, worker) ->
  $scope.username = $routeParams.username
  alert($scope.username)

  worker.connect($scope.username)
)




playWSCometControllers.factory("worker",['$rootScope','$q', ($rootScope,$q) ->
  ws = {}
  ws.onopen = (evt) ->
    console.log('worker websocket CONNECT.')

  ws.onclose = (evt) ->
    console.log('ws: worker socket CLOSED.  trying to reconnect')

  ws.onmessage = (evt) ->
    console.log('ws: onmessage');

  ws.onerror = (evt) ->
    console.log('ws: Worker socket ERROR: '+ evt.data)


  return {
    connect: (username) ->
      console.log('call connect for ' + username);
      if( window.WebSocket? )
        alert('ws://'+self.location.hostname+':'+self.location.port+'/ws/'+username);
        ws = new WebSocket('ws://'+self.location.hostname+':'+self.location.port+'/ws/'+username);
      else
        #ws = new CometSocket(username);
        setTimeout(->
          ws.onopen()  # fire the open event..
        ,2500);


  }
])