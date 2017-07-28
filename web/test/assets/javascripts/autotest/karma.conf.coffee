module.exports = (config) ->
  base = "../../../.."
  src = "js"
  spec = "test/assets/javascripts/specs"
  framework = "test/assets/javascripts/framework"
  modules = "test/assets/javascripts/autotest/node_modules"

  config.set
    autoWatch: true
    basePath: base
    browsers: [ 'Electron' ]
    frameworks: [ 'mocha', 'requirejs', 'chai', 'chai-as-promised', 'chai-jquery', 'sinon-chai' ]
    preprocessors: { '**/*.coffee': ['coffee'] }
    reporters: [ 'dots', 'junit' ]
    reportSlowerThan: 40 # TODO upgrade to PhantomJS 2 when it comes out, and change this to 10
    verbose: true

    junitReporter:
      outputFile: 'test-results.xml'
      outputDir: 'test/assets/javascripts/autotest/results'

    client:
      captureConsole: true # https://github.com/karma-runner/karma/issues/961
      mocha:
        globals: 'sinon,expect'
        timeout: 5000 # some unit tests stub the clock and advance 2000ms+.

    files: [
      { pattern: "#{src}/**/*.js", included: false }
      { pattern: "#{src}/**/*.coffee", included: false }
      { pattern: "#{spec}/**/*.coffee", included: false }
      "#{framework}/requirejs_config.coffee"
    ]
