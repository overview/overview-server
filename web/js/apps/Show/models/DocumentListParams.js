'use strict'

// These methods document a shared plain-old-data-Object standard for querying
// Overview through JavaScript methods.
//
// DocumentListParams are passed as plain old data Objects: we send them using
// `window.postMessage()`, which doesn't pass Object prototypes. That's why
// this file does not define a class.
//
// A valid Object is the intersection of the lists of documents found from each
// of the following searches:
//
//     objects: {
//       title: <String>,
//       ids: <Array of Object IDs>,
//       nodeIds: <Array of Node IDs>,
//       documentIds: <Array of Document IDs>,
//       documentIdsBitSetBase64: <String -- empty means no documents>,
//     },
//     tags: {
//       ids: <Array of Tag IDs>,
//       tagged: <Boolean>,
//       operation: '<all|any|none, default any>'
//     }, // (alternatively: `tags`: <Array of tag IDs>)
//     q: <String -- empty string for no search>,
//     sortByMetadataField: <String -- empty for default sort, which is by title>
//
// (Notably absent is `reverse`. [adam, 2017-11-08] I coded it this way, and
// I don't know why. `sortByMetadataField` and `reverse` should clearly be
// passed together, in retrospect -- probably in a `sort` field.)
//
// Example usage:
//
//     params = DocumentListParams.normalize({
//       objects: { title: '%s in folder "foo"', ids: [ 234 ] }
//       q: 'bar'
//     }) // => already normalized: will return a copy
//
//     DocumentListParams.buildQueryJson(params)   // => { objects: [ 234 ], q: 'bar' }
//     DocumentListParams.buildQueryString(params) // => objects=234&q=bar

const _ = require('underscore')

// Builds a valid Object from untamed user input.
//
// This method is designed to be backwards-compatible: if a plugin is using
// a parameter, we should make an effort to keep it operational.
function normalize(options) {
  const objects = parseObjects(options.objects)
  const tags = parseTags(options.tags)
  const q = parseQ(options.q)
  const sortByMetadataField = parseSortByMetadataField(options.sortByMetadataField)

  const ret = {}
  if (objects) ret.objects = objects
  if (tags) ret.tags = tags
  if (q) ret.q = q
  if (sortByMetadataField) ret.sortByMetadataField = sortByMetadataField

  return ret
}

// Finds the query string from the constructor option `q`.
//
// The argument is trimmed; if it is empty, we use `null` instead.
function parseQ(q) {
  return (q || '').trim() || null
}

// Finds the tags from the constructor option `tags`.
//
// If the argument is an Array of IDs, we use those. Otherwise, we parse an
// Object that contains any of `ids`, `tagged` and/or `operation`.
const ValidTagsKeys = { ids: null, tagged: null, operation: null }
const ValidTagsOperations = { all: null, any: null, none: null }
function parseTags(tags) {
  if (!tags) return null
  if (_.isArray(tags)) return { ids: tags }

  for (const key in tags) {
    if (!ValidTagsKeys.hasOwnProperty(key)) {
      throw new Error('Invalid option tags.' + key)
    }
  }

  if (tags.hasOwnProperty('tagged') && !_.isBoolean(tags.tagged)) {
    throw new Error('Invalid option tags.tagged=' + JSON.stringify(tags.tagged))
  }

  if (tags.hasOwnProperty('operation') && !ValidTagsOperations.hasOwnProperty(tags.operation)) {
    throw new Error('Invalid option tags.operation=' + JSON.stringify(tags.operation))
  }

  if (!tags.ids && !tags.hasOwnProperty('tagged')) return null

  const ret = {}
  if (tags.ids) ret.ids = tags.ids
  if (tags.hasOwnProperty('tagged')) ret.tagged = !!tags.tagged
  if (tags.operation && tags.operation !== 'any') ret.operation = tags.operation
  return ret
}

// Finds the objects from the constructor option `objects`.
//
// We parse an Object that contains `title` and one of `ids` or `nodeIds`.
function parseObjects(objects) {
  if (!objects) return null

  if (_.isEmpty(objects.ids) && _.isEmpty(objects.nodeIds) && _.isEmpty(objects.documentIdsBitSetBase64)) {
    throw new Error('Missing option objects.ids or objects.nodeIds or objects.documentIdsBitSetBase64')
  }
  if (!objects.title) {
    throw new Error('Missing option objects.title')
  }

  const ret = { title: objects.title }
  if (!_.isEmpty(objects.ids)) ret.ids = objects.ids
  if (!_.isEmpty(objects.nodeIds)) ret.nodeIds = objects.nodeIds
  if (objects.hasOwnProperty('documentIdsBitSetBase64')) ret.documentIdsBitSetBase64 = objects.documentIdsBitSetBase64
  return ret
}

// Finds the sortByMetadataField from the constructor option `sortByMetadataField`.
function parseSortByMetadataField(sortByMetadataField) {
  if (!sortByMetadataField) return null

  if (!_.isString(sortByMetadataField)) {
    throw new Error('sortByMetadataField must be a String')
  }

  return sortByMetadataField
}

// Returns a flattened representation, for querying the server.
//
// The query strings (or JSON) may include some (or none) of these parameters:
//
// * documentIdsBitSetBase64: a String describing any number of document IDs
// * objects: an Array of StoreObject IDs
// * nodes: Tree node IDs (deprecated)
// * q: a full-text search query string (toQueryString() url-encodes it)
// * tags: an Array of Tag IDs
// * tagged: a boolean
// * tagOperation: 'all', 'none' or undefined (default, 'any')
// * sortByMetadataField: a String or null (default null)
function buildQueryJson(params) {
  const ret = {}

  if (params.objects) {
    if (params.objects.ids) ret.objects = params.objects.ids
    if (params.objects.nodeIds) ret.nodes = params.objects.nodeIds // deprecated
    if (params.objects.documentIdsBitSetBase64) ret.documentIdsBitSetBase64 = params.objects.documentIdsBitSetBase64
  }

  if (params.tags) {
    if (params.tags.ids) ret.tags = params.tags.ids
    if (params.tags.tagged !== undefined) ret.tagged = params.tags.tagged
    if (params.tags.operation) ret.tagOperation = params.tags.operation
  }

  if (params.q) ret.q = params.q

  if (params.sortByMetadataField) ret.sortByMetadataField = params.sortByMetadataField

  return ret
}

function buildQueryString(params) {
  const parts = []

  const json = buildQueryJson(params)

  // sort so unit tests are easier elsewhere
  Object.keys(json).sort().forEach(function(key) {
    const value = json[key]
    if (_.isArray(value)) {
      parts.push(key + '=' + String(value))
    } else {
      parts.push(key + '=' + encodeURIComponent(value))
    }
  })

  return parts.join('&')
}

module.exports = {
  normalize: normalize,
  buildQueryJson: buildQueryJson,
  buildQueryString: buildQueryString,
}
