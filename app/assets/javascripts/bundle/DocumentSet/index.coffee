requirejs.config({
  baseUrl: '/assets/javascripts'

  #enforceDefine: true

  shim: {
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
    'bootstrap-tab': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    }
    'bootstrap-transition': {
      deps: [ 'jquery' ]
      exports: -> 'jQuery.fn' # doesn't export anything
    }
    underscore: { exports: '_' }
    md5: { exports: 'CryptoJS.MD5' }
  }

  paths: {
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-1-8-1'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    md5: 'vendor/md5'
    spectrum: 'vendor/spectrum'
    underscore: 'vendor/underscore'
  }
})

require [
  'for-view/DocumentSet/_index/import-dc-query',
  'for-view/DocumentSet/_index/import-public-document-sets',
  'for-view/DocumentSet/_index/import-upload',
  'for-view/DocumentSet/_index/watch-jobs',
  'for-view/DocumentSet/index',
  'elements/form-submit-with-feedback',
  'elements/form-with-confirm',
  'elements/twitter-bootstrap'
], ->
