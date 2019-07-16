'use strict'

const path = require('path')
const webpack = require('webpack')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const WebpackCleanupPlugin = require('webpack-cleanup-plugin')

const jsEntryPoints = [
  'admin/Job/index',
  'admin/Plugin/index',
  'admin/User/index',
  'ApiToken/index',
  'CsvUpload/new',
  'DocumentCloudImportJob/new',
  'DocumentCloudProject/index',
  'DocumentSet/index',
  'DocumentSet/show',
  'DocumentSet/show-progress',
  'DocumentSetUser/index',
  'FileImport/new',
  'PdfViewer/show',
  'PublicDocumentSet/index',
  'SharedDocumentSet/index',
  'Welcome/show',
  // "tracking" is a special case
]

const cssEntryPoints= [
  'main',
]

// We compile web/js/ => web/public/javascript-bundles/, staying as far away
// from sbt-web as we can.
//
// But sbt-web still generates the asset bundle, so we need it for now. That's
// why we write to web/public/* (which sbt-web builds into a jarfile) instead
// of web/target/*.
//
// TODO:
// * Digests: add `.md5` files and hashes to `/public/**/*.(css|js|png|jpg)`
//   in Webpack or sbt -- and nix sbt-digest
// * Change `web/public/` to `web/target/public/` and add that to
//   `unmanagedResourceDirectories` so the assets all get bundled into the same
//   jarfile as our Play code -- and nix sbt-web

/*
 * Play's AssetsFinder searches for a "versioned" .js file like this:
 * 1. Reads for [dirname]/[basename].js.md5
 * 2. Serves [dirname]/[md5]-[basename].js
 *
 * We require hashed filenames and hashed source-map filenames, so they can be
 * served with never-expire headers and browsers won't use cached versions when
 * the <script> src points to a new version.
 *
 * One solution: do everything in Webpack. Play's default
 * play.assets.digest.algorithm is "md5", and Play never actually hashes file
 * contents. That means we can pretend [chunkhash] is an md5 and write it to
 * [basename].js.md5 so Play will find the chunkhash-versioned file.
 *
 * We don't want/need this hack with chunks. Webpack is the one linking to the
 * chunks, so we want the original Webpack-generated URLs.
 *
 * Hack: Webpack's [name] seems to be atomic: we can't insert a hash between
 * dirname and filename. So we nix all folders and output a flat directory.
 */
function RedirectPlayFrameworkUsingDummyMd5Files() {}
RedirectPlayFrameworkUsingDummyMd5Files.prototype.apply = function(compiler) {
  const emitted = {} // cache of filename => contents

  compiler.plugin('emit', (compilation, callback) => {
    Object.keys(compilation.assets).forEach(filename => {
      const pathParts = filename.split('/')
      const basename = pathParts[pathParts.length - 1]
      const m = /^([a-f0-9]+)-(.*\.(js|css))$/.exec(basename)
      if (m) {
        const hashThatIsntReallyMd5 = m[1]
        const basenameWithoutHash = m[2]
        const newFilename = `${basenameWithoutHash}.md5`
        const newContents = hashThatIsntReallyMd5
        if (emitted[newFilename] !== newContents) {
          compilation.assets[newFilename] = {
            source: () => newContents,
            size: () => newContents.length,
          }
          emitted[newFilename] = newContents
        }
      }
    })

    callback()
  })
}

const entryPointsMap = {}
for (const entryPoint of jsEntryPoints) {
  entryPointsMap[entryPoint.replace(/\//g, '-')] = `bundle/${entryPoint}`
}
for (const entryPoint of cssEntryPoints) {
  entryPointsMap[entryPoint] = `./css/${entryPoint}.less`
}
entryPointsMap['tracking'] = 'tracking'

const extractLess = new MiniCssExtractPlugin({
  filename: '[contenthash]-[name].css',
})

const uglifyLoader = {
  loader: './node/uglify-es-loader',
  options: {
    sourceMap: true, // slow, but necessary
    compress: false, // save 5s: compression is slow and barely affects file size
    mangle: true,    // costs 1.5s, but saves 25% or so in file sizes
  },
}

const babelLoader = {
  loader: 'babel-loader',
  options: {
    presets: [
      [ '@babel/preset-env', {
        targets: {
          browsers: [ 'last 2 Chrome versions', 'last 2 Firefox versions', 'last 2 Edge versions', ],
        },
      } ],
    ],
    plugins: ['@babel/transform-runtime'],
  },
}

const cacheLoader = {
  loader: 'cache-loader',
  options: {
    cacheDirectory: path.resolve('node_modules', '.cache', 'cache-loader'),
  },
}

module.exports = {
  context: `${__dirname}`,
  entry: entryPointsMap,
  devtool: 'source-map', // slow, important
  output: {
    path: `${__dirname}/public/javascript-bundles`,
    publicPath: '/assets/javascript-bundles/',
    filename: '[chunkhash]-[name].js',
    sourceMapFilename: '[file].map',
    chunkFilename: '[chunkhash].chunk.js',

    // expose a "jquery" global variable. Integration tests will use this to
    // test that loading is done.
    library: 'jQuery',
    libraryTarget: 'window',
  },
  module: {
    rules: [
      {
        test: /\.coffee$/,
        use: [
          cacheLoader,
          uglifyLoader,
          {
            loader: 'coffee-loader',
            options: { sourceMap: true },
          },
        ],
      },
      {
        test: /\.svelte$/,
        use: [
          cacheLoader,
          uglifyLoader,
          babelLoader,
          {
            loader: 'svelte-loader',
          },
        ],
      },
      {
        test: /node_modules\/.*\.js$/,
        use: [
          cacheLoader,
          uglifyLoader,
        ],
      },
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: [
          cacheLoader,
          uglifyLoader,
          babelLoader,
        ],
      },
      {
        test: /\.less$/,
        use: [
          MiniCssExtractPlugin.loader,
          'css-loader',
        ],
      },
      {
        test: /\.(woff|svg|png|jpg)$/,
        use: [
          {
            loader: 'file-loader',
          }
        ],
      },
    ],
  },
  plugins: [
    extractLess,
    new WebpackCleanupPlugin({
      // buggy plugin -- deletes Woff files on recompile
      exclude: [ "**/*.{woff,svg,png,jpg}", "*.md5" ],
    }),
    new webpack.ProvidePlugin({
        $: "jquery",
        jQuery: "jquery",
        "window.jQuery": "jquery"
    }),
    new webpack.HashedModuleIdsPlugin(),
    new RedirectPlayFrameworkUsingDummyMd5Files(),
  ],
  resolve: {
    extensions: [ '.js', '.coffee', '.svelte' ],
    modules: [ '.', 'js', 'node_modules' ],
    alias: {
      // for our code:
      backbone: 'vendor/backbone',
      backform: 'vendor/backform',
      backgrid: 'vendor/backgrid',
      base64: 'vendor/base64',
      'bootstrap-alert': 'vendor/bootstrap-alert',
      'bootstrap-collapse': 'vendor/bootstrap-collapse',
      'bootstrap-dropdown': 'vendor/bootstrap-dropdown',
      'bootstrap-modal': 'vendor/bootstrap-modal',
      'bootstrap-popover': 'vendor/bootstrap-popover',
      'bootstrap-tab': 'vendor/bootstrap-tab',
      'bootstrap-toggle': 'vendor/bootstrap-toggle',
      'bootstrap-tooltip': 'vendor/bootstrap-tooltip',
      'bootstrap-transition': 'vendor/bootstrap-transition',
      html5sortable: 'vendor/html5sortable-0-6-1',
      jquery: 'vendor/jquery-2-1-0',
      'jquery.mousewheel': 'vendor/jquery.mousewheel',
      'jquery.validate': 'vendor/jquery.validate',
      'mass-upload': 'vendor/mass-upload',
      md5: 'vendor/md5',
      select2: 'vendor/select2',
      sha1: 'vendor/git-sha1',
      spectrum: 'vendor/spectrum',
      tinycolor: 'vendor/tinycolor',
      typeahead: 'vendor/typeahead.jquery',
      underscore: 'vendor/underscore',
      oboe: 'vendor/oboe-browser-2-1-3',
      MassUpload: 'vendor/mass-upload',
    }
  },
}
