requirejs.config
  baseUrl: '/assets/javascripts'

  shim:
    underscore: { exports: '_' }

  paths:
    jquery: 'vendor/jquery-1-8-1'
    underscore: 'vendor/underscore'

require [
  'elements/confirm-password'
  'elements/minlength-password'
  'elements/form-submit-with-feedback'
], () ->
  $('iframe.intro-video').attr('src', 'https://player.vimeo.com/video/71483614?byline=0&portrait=0&color=ffffff')
