'use strict'

// These methods document a shared plain-old-data-Object standard for querying
// Overview through JavaScript methods.
//
// DocumentListParams are passed as plain old data Objects: we send them using
// `window.postMessage()`, which doesn't pass Object prototypes. That's why
// this file does not define a class.
//
// A valid Object has only the following properties, never null or undefined:
//
// * **Filters set by plugins**:
//   * `title`: a String containing "%s": for instance, '%s found by plugin'.
//              Overview will replace "%s" with e.g. "3 documents" when
//              displaying search results. You _must_ set `title` if `objects`,
//              `nodes` or `documentIdsBitSetBase64` are set.
//   * `objects`: Array of Object IDs.
//   * `nodes`: [deprecated] Array of Node IDs.
//   * `documentIdsBitSetBase64`: Truncate all document IDs to 32 bits. Now add
//                                to a bitset all the IDs you want to filter
//                                for. (A Bitset is a contiguous sequence of
//                                bits in memory. Think of it as an enormous
//                                integer.) For instance: set the most-
//                                significant bit to 1 to include document 0;
//                                set the second-most significant bit to 1 to
//                                include document 1; and so on. Finally,
//                                Base64-encode the bitset. Beware: real-world
//                                bitset implementations usually arrange bits in
//                                "words" of 32 or 64 bits, and they often store
//                                each word's bits _backwards_, with the
//                                _least_-significant bit representing document
//                                0. Be sure to give Overview a "true" bitset:
//                                the most-significant bit must refer to
//                                document 0.
//   * `filters`: Object keyed by view ID, with values that look like
//                `{ ids: (Array of Strings), operation: 'any|all|none' }`
// * **Filters related to tags**:
//   * tags: Array of Tag IDs
//   * tagOperation: 'all' or 'none' (if unset, 'any'). If 'all', accept
//                    documents that have _all_ the tags in the `tags` Array; if
//                    'none', only accept documents that have _none_ of them.
//                    The default is to accept documents that have _at least one_
//                    of the tags.
//   * tagged: Boolean: if true, accept documents that have any tag at all; if
//             false, reject documents that have any tag at all.
// * **Filters related to text**:
//   * q: String search query.
// * **Parameters related to sorting**:
//   * sortByMetadataField: String. Empty for default sort, which is by title.
//
// (Notably absent is `reverse`. [adam, 2017-11-08] I coded it this way, and
// I don't know why. `sortByMetadataField` and `reverse` should clearly be
// passed together, in retrospect -- probably in a `sort` field.)
//
// Example usage:
//
//     params = DocumentListParams.normalize({
//       objects: [ 234 ], title: '%s in folder "foo"', q: 'bar'
//     }) // => already normalized: will return a copy
//
//     DocumentListParams.extend(params, {
//       tagged: false
//     }) // => { objects: [ 234 ], title: '%s in folder "foo"', q: 'bar', tagged: false }
//
//     DocumentListParams.buildQueryJson(params)   // => { objects: [ 234 ], q: 'bar' }
//     DocumentListParams.buildQueryString(params) // => objects=234&q=bar

const _ = require('underscore')

// Builds a valid Object from untamed user input.
//
// This method is designed to be backwards-compatible: if a plugin is using
// a parameter, we should make an effort to keep it operational.
function normalize(options) {
  return Object.assign(
    parseObjects(options) || {},
    parseFilters(options) || {},
    parseTags(options) || {},
    parseQ(options) || {},
    parseSortByMetadataField(options) || {}
  )
}

// Replaces elements of the first Object with elements from the second.
//
// The elements are replaced in groups. For instance:
//
//     // You can overwrite empty parts of the Object
//     extend({ tagged: true }, { q: 'foo' }) // => { tagged: true, q: 'foo' }
//
//     // Any new info in the "tags", "plugins", "text" or "sort" part
//     // overwrites all old info in that part. Here, "tags" and "tagged"
//     // are both in the same part, so the new info overwrites the old.
//     extend({ tagged: true }, { tags: [ 1 ] }) // => { tags: [ 1 ] }
//
// Each filter is its own group. To change or remove a filter, set a new value
// for that filter or set it to `null`.
function extend(lhs, rhs) {
  const ret = Object.assign(
    parseObjects(rhs) || parseObjects(lhs) || {},
    parseTags(rhs) || parseTags(lhs) || {},
    parseQ(rhs) || parseQ(lhs) || {},
    extendFilters(parseFilters(lhs), parseFiltersWithNull(rhs)),
    parseSortByMetadataField(rhs) || parseSortByMetadataField(lhs) || {}
  )
  return ret
}

function extendFilters(lhs, rhs) {
  const filters = Object.assign({}, lhs.filters || {})

  const rhsFilters = rhs.filters || {}

  for (const viewId of Object.keys(rhsFilters)) {
    const filter = rhsFilters[viewId]

    if (!filter || filter.ids.length === 0) {
      delete filters[viewId]
    } else {
      filters[viewId] = filter
    }
  }

  return Object.keys(filters).length === 0 ? {} : { filters: filters }
}

// Returns { q: 'foo' }
//
// Returns a partial selection Object if any data was given, or `null` if none
// was given.
function parseQ(options) {
  if (options.q !== undefined) {
    const trimmed = (options.q || '').trim()
    if (options.q === null || trimmed === '') {
      return {}
    } else {
      return { q: trimmed }
    }
  } else {
    return null
  }
}

// Finds the tags from the constructor option `tags`.
//
// If the argument is an Array of IDs, we use those. Otherwise, we parse an
// Object that contains any of `ids`, `tagged` and/or `operation`.
//
// Returns a partial selection Object if any data was given, or `null` if none
// was given.
const ValidTagsOperations = { all: null, any: null, none: null }
function parseTags(options) {
  if (options.tagged === undefined && options.tags === undefined && options.tagOperation === undefined) {
    return null
  } else {
    if (options.tagged === null || options.tags === null || options.tagOperation === null) {
      return {}
    } else {
      const ret = {}

      if (options.tagged !== undefined) {
        if (!_.isBoolean(options.tagged)) {
          throw new Error('Invalid option options.tagged=' + JSON.stringify(options.tagged))
        }
        ret.tagged = options.tagged
      }

      if (options.tags !== undefined) {
        if (!_.isArray(options.tags)) {
          throw new Error('options.tags must be an Array of Numbers; got ' + JSON.stringify(options.tags))
        }
        if (!_.isEmpty(options.tags)) {
          ret.tags = options.tags

          if (options.tagOperation !== undefined) {
            if (!ValidTagsOperations.hasOwnProperty(options.tagOperation)) {
              throw new Error('Invalid option tagOperation=' + JSON.stringify(options.tagOperation))
            }

            if (options.tagOperation !== 'any') {
              ret.tagOperation = options.tagOperation
            }
          }
        }
      }

      return ret
    }
  }
}

// Finds the objects from the constructor options `objects`, `title`,
// `nodes` and `documentIdsBitSetBase64
//
// Returns a partial selection Object if any data was given, or `null` if none
// was given.
function parseObjects(options) {
  if (options.objects === null || options.nodes === null || options.documentIdsBitSet64 === null || options.title === null) {
    return {}
  } else {
    const hasTitle = (options.title !== undefined)
    const hasObjects = options.objects !== undefined || options.nodes !== undefined || options.documentIdsBitSetBase64 !== undefined

    if (!hasTitle && hasObjects) {
      throw new Error('Missing options.title')
    } else if (hasTitle && !hasObjects) {
      throw new Error('Missing options.ids or options.nodes or options.documentIdsBitSetBase64')
    }

    if (!hasTitle && !hasObjects) {
      return null
    } else {
      const ret = { title: options.title }
      if (!_.isEmpty(options.objects)) ret.objects = options.objects
      if (!_.isEmpty(options.nodes)) ret.nodes = options.nodes
      if (options.documentIdsBitSetBase64 !== undefined) ret.documentIdsBitSetBase64 = options.documentIdsBitSetBase64

      return ret
    }
  }
}

function parseFiltersWithNull(options) {
  const input = options.filters || {}

  const filters = {}

  for (const key of Object.keys(input)) {
    const spec = options.filters[key]
    const ids = (spec || {}).ids || []
    if (!_.isArray(ids)) throw new Error("Each Filter must have a 'ids' property")
    if (ids.length > 0) {
      const operation = spec.operation || 'any'
      if (operation !== 'any' && operation !== 'all' && operation !== 'none') {
        throw new Error("Each Filter operation must be 'any', 'all' or 'none'")
      }
      filters[key] = { ids: ids.map(String), operation: operation }
    } else {
      filters[key] = null
    }
  }

  return { filters: filters }
}

function parseFilters(options) {
  const ret = parseFiltersWithNull(options)

  if (ret.filters) {
    for (const key of Object.keys(ret.filters)) {
      if (!ret.filters[key]) delete ret.filters[key]
    }

    if (Object.keys(ret.filters).length === 0) delete ret.filters
  }

  return ret
}

// Finds the sortByMetadataField from the constructor option `sortByMetadataField`.
//
// Returns a partial selection Object if any data was given, or `null` if none
// was given.
function parseSortByMetadataField(options) {
  if (options.sortByMetadataField !== undefined) {
    if (options.sortByMetadataField === null) {
      return {}
    } else {
      if (!_.isString(options.sortByMetadataField) || options.sortByMetadataField == '') {
        throw new Error('sortByMetadataField must be a non-empty String')
      }
      return { sortByMetadataField: options.sortByMetadataField }
    }
  } else {
    return null
  }
}

// Returns a flattened representation, for querying the server via JSON POST.
function buildQueryJson(params) {
  const ret = Object.assign({}, params)
  if (ret.title) delete ret.title
  return ret
}

function flattenObjectIntoArray(array, root, o) {
  Object.keys(o).sort().forEach(function(key) {
    const value = o[key]
    if (_.isObject(value) && !_.isArray(value)) {
      flattenObjectIntoArray(array, root + key + '.', value)
    } else {
      // String(value) does what we want on Array, Number and String
      // encodeURIComponent() encodes commas, which is correct; but we _like_
      // commas, because they're easy to read, and we don't expect any
      // servers to barf from them.
      const encodedValue = encodeURIComponent(String(value))
        .replace(/%2C/g, ',')
      array.push(root + key + '=' + encodedValue)
    }
  })
}

function buildQueryString(params) {
  const parts = []

  const json = buildQueryJson(params)

  flattenObjectIntoArray(parts, '', json)

  return parts.join('&')
}

module.exports = {
  normalize: normalize,
  extend: extend,
  buildQueryJson: buildQueryJson,
  buildQueryString: buildQueryString,
}
