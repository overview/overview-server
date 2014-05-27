module.exports = (config) ->
  base = "../../../.."
  src = "app/assets/javascripts"
  spec = "test/assets/javascripts/specs"
  framework = "test/assets/javascripts/framework"

  config.set
    autoWatch: true
    basePath: base
    browsers: [ 'PhantomJS' ]
    frameworks: [ 'mocha', 'requirejs', 'chai-jquery', 'sinon-chai', 'jquery-2.1.0' ]
    preprocessors: { '**/*.coffee': ['coffee'] }
    reporters: [ 'dots' ]
    reportSlowerThan: 15
    verbose: true

    files: [
      { pattern: "#{src}/**/*.js", included: false }
      { pattern: "#{src}/**/*.coffee", included: false }
      { pattern: "#{spec}/**/*.coffee", included: false }
      { pattern: "#{framework}/jquery-hack.js", included: false }
      "#{framework}/bind.js"
      "#{framework}/init.coffee"
      "#{framework}/requirejs_config.coffee"
    ]
