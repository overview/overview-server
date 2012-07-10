# A least-recently-used paging strategy.
#
# This class helps a page-based cache determine which pages to reuse.
#
# It operates on an "id" principle: it figures out which page to give the
# caller based on (integer) IDs passed by the caller.
#
# To use:
#
#     strategy = new LruPagingStrategy(2) # a 2-page cache
#     strategy.add(id1) # returns 0, an array index
#     strategy.is_full() # returns false
#     strategy.add(id2) # returns 1, another array index
#     strategy.is_full() # returns true
#     strategy.add(id3) # throws 'AllPagesFull'
#     strategy.free(id2)
#     strategy.free(id3) # throws 'IdNotCached'
#     strategy.add(id3) # returns 1, because it's free
#     strategy.find_id_to_free() # returns id1, the least-recently used
#     strategy.free(id1)
#     strategy.add(id4) # returns 0, because it's free again
#     strategy.touch(id3) # notifies the strategy that we're using id3
#     strategy.find_id_to_free() # returns id4, the least-recently used
#     strategy.free(id4)
#     strategy.find_id_to_free_if_full() # returns undefined
#     strategy.find_id_to_free() # returns id3
#
# There are also `freeze` and `thaw` methods. These will prevent the pages
# that contain an ID from losing it. `find_id_to_free()` will throw
# 'AllPagesFrozen' if it's impossible to free anything.
class LruPagingStrategy
  constructor: (@n_pages) ->
    @_pages = []
    @_ids = []
    @_n = 0
    @_counter = 0

  add: (id) ->
    page = undefined
    for i in [ 0 ... @n_pages ]
      if !@_pages[i]?
        page = i
        break

    throw 'AllPagesFull' if !page?
    @_pages[page] = id
    @_ids[id] = { page: page, counter: @_counter++, frozen: false }
    @_n++
    page

  free: (id) ->
    page = @_ids[id]?.page
    throw 'IdNotCached' if !page?
    @_pages[page] = undefined
    delete @_ids[id]
    @_n--
    page

  is_full: () ->
    @_n >= @n_pages

  is_frozen: (id) ->
    @_ids[id].frozen

  freeze: (id) ->
    @_ids[id].frozen = true

  thaw: (id) ->
    @_ids[id].frozen = false

  find_id_to_free: () ->
    best_id = undefined
    best_counter = @_counter + 1

    for id, info of @_ids
      if info.counter < best_counter && !info.frozen
        best_counter = info.counter
        best_id = +id

    throw 'AllPagesFrozen' if @_n > 0 && !best_id?

    best_id

  touch: (id) ->
    @_ids[id].counter = @_counter++

  find_id_to_free_if_full: () ->
    this.is_full() && this.find_id_to_free() || undefined

exports = require.make_export_object('models/lru_paging_strategy')
exports.LruPagingStrategy = LruPagingStrategy
