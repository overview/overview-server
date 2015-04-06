tests = []

for file, __ of window.__karma__.files
  if /spec\.js/i.test(file)
    tests.push(file)

requirejs.config
  baseUrl: '/base/app/assets/javascripts'

  shim:
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
    'bootstrap-toggle':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.bootstrapToggle'
    'bootstrap-tooltip':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.tooltip'
    underscore: { exports: '_' }
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
    'jquery.mousewheel':
      deps: [ 'jquery' ]
      exports: 'jQuery.fn.mousewheel'

  paths:
    'backbone': 'vendor/backbone'
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
    'jquery': 'vendor/jquery-2-1-0'
    'jquery.mousewheel': 'vendor/jquery.mousewheel'
    'jquery.validate': 'vendor/jquery.validate'
    md5: 'vendor/md5'
    select2: 'vendor/select2'
    spectrum: 'vendor/spectrum'
    tinycolor: 'vendor/tinycolor'
    typeahead: 'vendor/typeahead.jquery'
    underscore: 'vendor/underscore'
    rsvp: 'vendor/rsvp'
    MassUpload: 'vendor/mass-upload'
    'chai': '../../../test/assets/javascripts/autotest/node_modules/chai/chai'
    'sinon': '../../../test/assets/javascripts/autotest/node_modules/sinon/pkg/sinon'
    'sinon-chai': '../../../test/assets/javascripts/autotest/node_modules/sinon-chai/lib/sinon-chai'
    'chai-as-promised': '../../../test/assets/javascripts/autotest/node_modules/chai-as-promised/lib/chai-as-promised'
    'chai-jquery': '../../../test/assets/javascripts/autotest/node_modules/chai-jquery/chai-jquery'

  # ask Require.js to load these files (all our tests)
  deps: tests,

  # start test run, once Require.js is done
  callback: ->
    require [
      'chai'
      'sinon'
      'sinon-chai'
      'chai-jquery'
      'chai-as-promised'
    ], (chai, sinon, sinonChai, chaiJquery, chaiAsPromised) ->
      window.expect = chai.expect
      #window.sinon = sinon -- this happens on its own
      chai.use(chaiAsPromised) # first, so other chai plugins will work
      chai.use(sinonChai)
      chai.use(chaiJquery)
      window.__karma__.start()
