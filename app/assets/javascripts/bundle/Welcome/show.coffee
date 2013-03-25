requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    underscore: { exports: '_' }

  paths:
    jquery: 'vendor/jquery-1-8-1'
    underscore: 'vendor/underscore'

require [
  'elements/confirm-password'
  'elements/form-submit-with-feedback'
], () ->
