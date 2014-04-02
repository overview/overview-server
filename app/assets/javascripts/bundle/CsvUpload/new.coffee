requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    'base64': { exports: 'Base64' }
    'backbone':
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    'bootstrap-alert':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.alert'
    'bootstrap-collapse':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.collapse'
    'bootstrap-dropdown':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.dropdown'
    'bootstrap-modal':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    'bootstrap-transition':
      deps: [ 'jquery' ]
    'jquery.validate':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.validate'
    underscore: { exports: '_' }
    md5: { exports: 'CryptoJS.MD5' }

  paths:
    'backbone': 'vendor/backbone'
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-2-1-0'
    'jquery.validate': 'vendor/jquery.validate'
    md5: 'vendor/md5'
    underscore: 'vendor/underscore'

require [
  'for-view/CsvUpload/new'
  'elements/twitter-bootstrap'
], ->
