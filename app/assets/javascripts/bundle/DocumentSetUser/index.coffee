requirejs.config
  baseUrl: '/assets/javascripts'

  #enforceDefine: true
  
  shim:
    backbone:
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    underscore: { exports: '_' }

  paths:
    backbone: 'vendor/backbone'
    jquery: 'vendor/jquery-2-1-0'
    underscore: 'vendor/underscore'

require [
  'for-view/DocumentSetUser/index'
], ->
