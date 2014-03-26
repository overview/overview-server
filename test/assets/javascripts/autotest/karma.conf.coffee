module.exports = (config) ->
  base = "../../../.."
  src = "app/assets/javascripts"
  spec = "test/assets/javascripts/specs"
  framework = "test/assets/javascripts/framework"

  config.set
    autoWatch: true
    basePath: base
    browsers: [ 'PhantomJS' ]
    frameworks: [ 'jasmine', 'requirejs' ]
    preprocessors: { '**/*.coffee': ['coffee'] }
    reporters: [ 'dots' ]
    reportSlowerThan: 50

    files: [
      "#{src}/vendor/jquery-2-1-0.js"
      { pattern: "#{src}/**/*.js", included: false }
      { pattern: "#{src}/**/*.coffee", included: false }
      { pattern: "#{spec}/**/*.coffee", included: false }
      "#{framework}/timecop-0.1.1.js"
      "#{framework}/mock-ajax.js"
      "#{framework}/jasmine-jquery.js"
      "#{framework}/bind.js"
      "#{framework}/init.js"
      "#{framework}/requirejs_config.coffee" # this one last
    ]
