requirejs.config
  baseUrl: '/assets/javascripts'

  #enforceDefine: true

  shim: {
    'backbone': {
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    }
    'base64': { exports: 'Base64' }
    'bootstrap-alert': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.alert'
    }
    'bootstrap-collapse': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.collapse'
    }
    'bootstrap-dropdown': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.dropdown'
    }
    'bootstrap-modal': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    }
    'bootstrap-popover':
      deps: [ 'jquery', 'bootstrap-tooltip' ]
      exports: 'jQuery.fn.popover'
    'bootstrap-tab': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    }
    'bootstrap-toggle':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.bootstrapToggle'
    'bootstrap-tooltip':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tooltip'
    'bootstrap-transition': {
      deps: [ 'jquery' ]
    }
    md5: { exports: 'CryptoJS.MD5' }
    spectrum: {
      deps: [ 'jquery', 'tinycolor' ]
      exports: 'jQuery.fn.spectrum'
    }
    tinycolor: { exports: 'tinycolor' }
    typeahead:
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.typeahead'
    underscore: { exports: '_' }
  }

  paths:
    'backbone': 'vendor/backbone'
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-popover': 'vendor/bootstrap-popover'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-toggle': 'vendor/bootstrap-toggle'
    'bootstrap-tooltip': 'vendor/bootstrap-tooltip'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-2-1-0'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    'jquery.validate': 'vendor/jquery.validate'
    md5: 'vendor/md5'
    rsvp: 'vendor/rsvp'
    spectrum: 'vendor/spectrum'
    tinycolor: 'vendor/tinycolor'
    typeahead: 'vendor/typeahead.jquery'
    underscore: 'vendor/underscore'

require [
  'for-view/DocumentSet/delete-document-set',
  'for-view/DocumentSet/export-document-set',
  'for-view/DocumentSet/share-document-set'
  'for-view/DocumentSet/show'
], ->
