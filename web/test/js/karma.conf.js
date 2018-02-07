const webpack = require('webpack')
const webpackConfig = require('../../webpack.config.js')
webpackConfig.plugins = webpackConfig.plugins.filter(plugin => plugin.constructor.name === 'ProvidePlugin')
webpackConfig.module.rules = webpackConfig.module.rules.map(rule => {
  const ret = Object.assign({}, rule)
  if (Array.isArray(ret.use)) {
    ret.use = ret.use.filter(u => !/uglify-es-loader|cache-loader/.test(u.loader))
  }
  return ret
})
delete webpackConfig.entry

module.exports = function(config) {
  config.set({
    files: [
      'test_index.js',
      { pattern: './mock-js/**/*', included: false },
      { pattern: './mock-plugin/**/*', included: false },
      { pattern: './mock-pdf-viewer/**/*', included: false },
    ],
    preprocessors: {
      'test_index.js': [ 'webpack', 'sourcemap' ],
    },
    frameworks: [ 'mocha' ],
    reporters: [ 'dots', 'junit' ],
    reportSlowerThan: 20,
    verbose: true,
    browsers: [ 'Electron' ],
    webpack: webpackConfig,

    junitReporter: {
      outputFile: 'js-test-results.xml',
      outputDir: '/app/unit-test-results',
    },

    client: {
      mocha: {
        globals: 'sinon,expect',
        timeout: 3000, // some unit tests stub the clock and advance 2000ms+
      },
    },
  })
}
