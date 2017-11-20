const UglifyES = require("uglify-es");
const loaderUtils = require('loader-utils');
const sourceMap = require('source-map');

function mergeSourceMap(map, inputMap) {
  const inputMapConsumer = new sourceMap.SourceMapConsumer(inputMap);
  const outputMapConsumer = new sourceMap.SourceMapConsumer(map);

  const mergedGenerator = new sourceMap.SourceMapGenerator({
    file: inputMapConsumer.file,
    sourceRoot: inputMapConsumer.sourceRoot
  });

  const source = outputMapConsumer.sources[0];

  inputMapConsumer.eachMapping(function (mapping) {
    const generatedPosition = outputMapConsumer.generatedPositionFor({
      line: mapping.generatedLine,
      column: mapping.generatedColumn,
      source: source
    });
    if (generatedPosition.column != null) {
      mergedGenerator.addMapping({
        source: mapping.source,

        original: mapping.source == null ? null : {
          line: mapping.originalLine,
          column: mapping.originalColumn
        },

        generated: generatedPosition
      });
    }
  });

  const mergedMap = mergedGenerator.toJSON();
  inputMap.mappings = mergedMap.mappings;
  return inputMap
};

module.exports = function(source, inputSourceMap) {
    const callback = this.async();

    if (this.cacheable) {
      this.cacheable();
    }

    const opts = loaderUtils.getOptions(this) || {};
    // just an indicator to generate source maps, the output result.map will be modified anyway
    // tell UglifyES not to emit a name by just setting outSourceMap to true
    opts.sourceMap = true;

    const result = UglifyES.minify(source, opts);
    if (result.error) throw new Error("Error in JS file: " + JSON.stringify(result.error))
    const sourceMap = JSON.parse(result.map);

    if (inputSourceMap) {
      callback(null, result.code, mergeSourceMap(sourceMap, inputSourceMap));
    } else {
      const sourceFilename = loaderUtils.getRemainingRequest(this);
      const current = loaderUtils.getCurrentRequest(this);
      sourceMap.sources = [sourceFilename];
      sourceMap.file = current;
      sourceMap.sourcesContent = [source];

      callback(null, result.code, sourceMap);
    }
};
