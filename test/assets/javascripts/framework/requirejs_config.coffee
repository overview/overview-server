tests = []

for file, __ of window.__karma__.files
  if /spec\.js/i.test(file)
    tests.push(file)

requirejs.config
  baseUrl: '/base/app/assets/javascripts'

  shim: {
    'backbone':
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    'base64':
      exports: 'Base64'
    'bootstrap-modal':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    'bootstrap-popover':
      deps: [ 'jquery', 'bootstrap-tooltip' ]
      exports: 'jQuery.fn.popover'
    'bootstrap-tooltip':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tooltip'
    underscore: { exports: '_' }
    md5: { exports: 'CryptoJS.MD5' }
    spectrum:
      deps: [ 'jquery', 'tinycolor' ]
      exports: 'jQuery.fn.spectrum'
    tinycolor: { exports: 'tinycolor' }
    'jquery.mousewheel':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.mousewheel'
  }

  paths: {
    'backbone': 'vendor/backbone'
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-popover': 'vendor/bootstrap-popover'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-tooltip': 'vendor/bootstrap-tooltip'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    jquery: '../../../test/assets/javascripts/framework/jquery-hack'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    'jquery.validate': 'vendor/jquery.validate'
    md5: 'vendor/md5'
    spectrum: 'vendor/spectrum'
    tinycolor: 'vendor/tinycolor'
    underscore: 'vendor/underscore'
    rsvp: 'vendor/rsvp'
    MassUpload: 'vendor/mass-upload'
  }

  # ask Require.js to load these files (all our tests)
  deps: tests,

  # start test run, once Require.js is done
  callback: window.__karma__.start
