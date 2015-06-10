requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    'bootstrap-alert': { deps: [ 'jquery' ] }

  paths:
    'bootstrap-alert': 'vendor/bootstrap-alert'
    jquery: 'vendor/jquery-2-1-0'
    underscore: 'vendor/underscore'

require [
  'elements/confirm-password'
  'elements/minlength-password'
  'elements/form-submit-with-feedback'
  'bootstrap-alert'
], () ->
