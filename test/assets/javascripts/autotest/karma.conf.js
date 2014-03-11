module.exports = function(config) {
  config.set({
    autoWatch: false,
    basePath: '.',
    browsers: [ 'PhantomJS' ],
    frameworks: [ 'jasmine', 'requirejs' ],
    reporters: [ 'dots' ],

    files: [
      'src-js/vendor/jquery-2-1-0.js',
      {pattern: 'src-js/**/*.js', included: false},
      {pattern: 'test-js/**/*.js', included: false},
      'framework-js/timecop-0.1.1.js',
      'framework-js/mock-ajax.js',
      'framework-js/jasmine-jquery.js',
      'framework-js/bind.js',
      'framework-js/init.js',
      'framework-js/requirejs_config.js' // this one last
    ]
  });
};
