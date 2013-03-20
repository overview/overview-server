requirejs.config({
  baseUrl: '/test/src-js'

  shim: {
    'backbone': {
      deps: [ 'jquery', 'underscore' ]
      exports: 'Backbone'
    }
    'bootstrap-modal': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    }
    underscore: { exports: '_' }
    md5: { exports: 'CryptoJS.MD5' }
    spectrum: {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.spectrum'
    }
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
    jquery: 'vendor/jquery-1-8-1'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    md5: 'vendor/md5'
    silverlight: 'vendor/Silverlight.debug'
    spectrum: 'vendor/spectrum'
    underscore: 'vendor/underscore'
  }
})
