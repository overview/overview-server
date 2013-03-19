# Just do RequireJS configuration.
#
# This is used by Play's asset compiler.
#
# The rest of RequireJS requires a separate config per bundle (I think?). Make
# each file start with a copy of this requirejs.config() call.
requirejs.config({
  baseUrl: '/assets/javascripts'

  #enforceDefine: true

  shim: {
    'base64': { exports: 'Base64' }
    'bootstrap-alert': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.alert'
    }
    'bootstrap-collapse': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.collapse'
    }
    'bootstrap-dropdown': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.dropdown'
    }
    'bootstrap-modal': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.modal'
    }
    'bootstrap-tab': {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tab'
    }
    'bootstrap-transition': {
      deps: [ 'jquery' ]
      exports: -> 'jQuery.fn' # doesn't export anything
    }
    underscore: { exports: '_' }
    spectrum: {
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.spectrum'
    }
    md5: { exports: 'CryptoJS.MD5' }
  }

  paths: {
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
    spectrum: 'vendor/spectrum'
    underscore: 'vendor/underscore'
  }
})
