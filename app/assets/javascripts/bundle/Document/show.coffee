requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    backbone:
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    underscore: { exports: '_' }

  paths:
    backbone: 'vendor/backbone'
    jquery: 'vendor/jquery-1-8-1'
    underscore: 'vendor/underscore'

define [
  'for-view/Document/show'
], (controller) ->
  controller
