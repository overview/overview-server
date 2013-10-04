tests = [];
for file, __ of window.__karma__.files
  if /spec\.js$/i.test(file)
    tests.push(file)

requirejs.config({
  baseUrl: '/base/src-js'

  shim: {
    jquery: { exports: '$' }
    'backbone':
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    'base64':
      exports: 'Base64'
    'bootstrap-modal':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    underscore: { exports: '_' }
    md5: { exports: 'CryptoJS.MD5' }
    spectrum:
      deps: [ 'jquery', 'tinycolor' ]
      exports: 'jQuery.fn.spectrum'
    tinycolor: { exports: 'tinycolor' }
    'jquery.mousewheel':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.mousewheel'
    'bootstrap-dialog':
      deps: [ 'bootstrap-modal' ]
      exports: 'BootstrapDialog'
  }

  paths: {
    'backbone': 'vendor/backbone'
    'base64': 'vendor/base64'
    'bootstrap-alert': 'vendor/bootstrap-alert'
    'bootstrap-collapse': 'vendor/bootstrap-collapse'
    'bootstrap-dropdown': 'vendor/bootstrap-dropdown'
    'bootstrap-modal': 'vendor/bootstrap-modal'
    'bootstrap-tab': 'vendor/bootstrap-tab'
    'bootstrap-transition': 'vendor/bootstrap-transition'
    'bootstrap-dialog': 'vendor/bootstrap-dialog'
    jquery: 'vendor/jquery-1-8-1'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    md5: 'vendor/md5'
    silverlight: 'vendor/Silverlight.debug'
    spectrum: 'vendor/spectrum'
    tinycolor: 'vendor/tinycolor'
    underscore: 'vendor/underscore'
    MassUpload: 'vendor/mass-upload'
  }

  # ask Require.js to load these files (all our tests)
  deps: tests,

  # start test run, once Require.js is done
  callback: window.__karma__.start
})
