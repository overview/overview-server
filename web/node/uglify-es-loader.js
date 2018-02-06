const UglifyES = require("uglify-es")
const loaderUtils = require('loader-utils')
const sourceMap = require('source-map')

function mergeSourceMaps(map, inputMap) {
  return sourceMap.SourceMapConsumer.with(inputMap, null, inputMapConsumer => {
    // SourceMapConsumer keeps a singleton "currentCallback" alive behind the
    // scenes. We can't keep two loaded at once. Extract the data, then load the
    // next.
    const mappings = []
    inputMapConsumer.eachMapping(mapping => mappings.push(Object.assign({}, mapping)))
    return mappings
  }).then(inputMappings => sourceMap.SourceMapConsumer.with(map, null, outputMapConsumer => {
    const mergedGenerator = new sourceMap.SourceMapGenerator({
      file: inputMap.file,
      sourceRoot: inputMap.sourceRoot,
    })

    const source = outputMapConsumer.sources[0]

    inputMappings.forEach(mapping => {
      const generatedPosition = outputMapConsumer.generatedPositionFor({
        line: mapping.generatedLine,
        column: mapping.generatedColumn,
        source: source
      })
      if (generatedPosition.column != null) {
        mergedGenerator.addMapping({
          source: mapping.source,

          original: mapping.source == null ? null : {
            line: mapping.originalLine,
            column: mapping.originalColumn
          },

          generated: generatedPosition
        })
      }
    })

    const mergedMap = mergedGenerator.toJSON()
    const ret = Object.assign({}, inputMap, { mappings: mergedMap.mappings })
    return ret
  }))
}

module.exports = function(source, inputSourceMap) {
    const callback = this.async()

    if (this.cacheable) {
      this.cacheable()
    }

    const opts = loaderUtils.getOptions(this) || {}

    const result = UglifyES.minify(source, opts)
    if (result.error) return callback(new Error("Error in JS file: " + JSON.stringify(result.error)))
    const sourceMap = JSON.parse(result.map)

    if (inputSourceMap) {
      mergeSourceMaps(sourceMap, inputSourceMap)
        .then(mergedSourceMap => callback(null, result.code, mergedSourceMap), err => callback(err))
    } else {
      const sourceFilename = loaderUtils.getRemainingRequest(this)
      const current = loaderUtils.getCurrentRequest(this)
      sourceMap.sources = [sourceFilename]
      sourceMap.file = current
      sourceMap.sourcesContent = [source]

      callback(null, result.code, sourceMap)
    }
}
