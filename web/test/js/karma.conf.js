const webpackConfig = require('../../webpack.config.js')
webpackConfig.plugins = webpackConfig.plugins.filter(plugin => plugin.constructor.name === 'ProvidePlugin')
webpackConfig.module.loaders = webpackConfig.module.loaders
  .map(loader => {
    return Object.assign({}, loader, { use: (
      Array.isArray(loader.use) ? loader.use.filter(u => !/uglify-es-loader/.test(u.loader)) : loader.use
    )})
  })
webpackConfig.devtool = 'inline-source-map'
delete webpackConfig.entry

module.exports = function(config) {
  config.set({
    files: [
      'test_index.js',
      { pattern: './mock-plugin/**/*', included: false },
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
      outputFile: 'test-results.xml',
      outputDir: 'test/js',
    },

    client: {
      mocha: {
        globals: 'sinon,expect',
        timeout: 5000, // some unit tests stub the clock and advance 2000ms+
      },
    },
  })
}
