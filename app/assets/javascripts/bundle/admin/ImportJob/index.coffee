requirejs.config({
  baseUrl: '/assets/javascripts'

  shim:
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
    'bootstrap-tab':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    'bootstrap-transition':
      deps: [ 'jquery' ]
    jquery: { exports: '$' }
    underscore: { exports: '_' }

  paths:
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: 'vendor/jquery-2-1-0'
    underscore: 'vendor/underscore'
})

require [
  'elements/form-with-confirm'
  'elements/twitter-bootstrap'
], ->
