requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    'bootstrap-alert': { deps: [ 'jquery' ] }
    underscore: { exports: '_' }

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
  $('iframe.intro-video').attr('src', 'https://player.vimeo.com/video/71483614?byline=0&portrait=0&color=ffffff')
