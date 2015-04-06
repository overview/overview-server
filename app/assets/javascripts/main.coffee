# Just do RequireJS configuration.
#
# This is used by Play's asset compiler.
#
# The rest of RequireJS requires a separate config per bundle (I think?). Make
# each file start with a copy of this requirejs.config() call.
requirejs.config
  baseUrl: '/assets/javascripts'

  #enforceDefine: true

  shim:
    'backbone':
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    'backform':
      deps: [ 'backbone' ]
      exports: 'Backform'
    'backgrid':
      deps: [ 'backbone' ]
      exports: 'Backgrid'
    'base64': { exports: 'Base64' }
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
    'bootstrap-popover':
      deps: [ 'jquery', 'bootstrap-tooltip' ]
      exports: 'jQuery.fn.popover'
    'bootstrap-tab':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    'bootstrap-toggle':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.bootstrapToggle'
    'bootstrap-tooltip':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tooltip'
    'bootstrap-transition':
      deps: [ 'jquery' ]
    'jquery.validate':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.validate'
    md5: { exports: 'CryptoJS.MD5' }
    select2:
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.select2'
    spectrum:
      deps: [ 'jquery', 'tinycolor' ]
      exports: 'jQuery.fn.spectrum'
    tinycolor: { exports: 'tinycolor' }
    typeahead:
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.typeahead'
    underscore: { exports: '_' }

  paths:
    'backbone': 'vendor/backbone'
    'backform': 'vendor/backform'
    'backgrid': 'vendor/backgrid'
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
    'mass-upload': 'vendor/mass-upload'
    md5: 'vendor/md5'
    rsvp: 'vendor/rsvp'
    select2: 'vendor/select2'
    spectrum: 'vendor/spectrum'
    tinycolor: 'vendor/tinycolor'
    typeahead: 'vendor/typeahead.jquery'
    underscore: 'vendor/underscore'
