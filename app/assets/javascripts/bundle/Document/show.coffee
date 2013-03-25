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

require [
  'for-view/Document/show'
], (controller) ->
  if !window.documentViewerStartDocument?
    throw 'Please set window.documentViewerStartDocument before including this file'

  controller.el.done (el) -> document.body.appendChild(el)
  controller.setDocument(window.documentViewerStartDocument)
  window.setDocument = controller.setDocument
  window.scrollByPages = controller.scrollByPages
