DocumentList = require('models/document_list').DocumentList

Deferred = jQuery.Deferred

class MockSelection
  constructor: (properties={}) ->
    @nodes = (properties.nodes || {})
    @tags = (properties.tags || {})
    @documents = (properties.documents || {})

  documents_from_cache: (cache) -> []

class MockResolver
  constructor: () ->
    @deferreds = []

  get_deferred: (key, obj) ->
    @deferreds.push(ret = new Deferred())
    ret

describe 'models/document_list', ->
  describe 'DocumentList', ->
    resolver = undefined

    beforeEach ->
      resolver = new MockResolver()

    it 'should pass get_placeholder_documents() to selection.documents_from_cache()', ->
      selection = new MockSelection()
      dl = new DocumentList(selection, resolver)
      spyOn(selection, 'documents_from_cache')
      dl.get_placeholder_documents({})
      expect(selection.documents_from_cache).toHaveBeenCalledWith({})

    describe 'slice', ->
      it 'should return documents it already has, without a server call', ->
        dl = new DocumentList(new MockSelection(), resolver)
        dl.documents = [ '1', '2', '3', '4', '5' ]
        dl.n = 5
        deferred = dl.slice(0, 3)
        expect(deferred.state()).toEqual('resolved')
        arr = undefined
        deferred.done((x) -> arr = x)
        expect(arr).toEqual(['1', '2', '3'])

      it 'should call Resolver.get_deferred(selection_documents_slice)', ->
        selection = new MockSelection({ documents: [ 1, 2, 3 ] })
        dl = new DocumentList(selection, resolver)
        spyOn(resolver, 'get_deferred').andCallThrough()
        deferred = dl.slice(0, 2)
        expect(resolver.get_deferred).toHaveBeenCalledWith('selection_documents_slice', { selection: selection, start: 0, end: 2 })

      it 'should give the return value of get_selection_documents_slice', ->
        dl = new DocumentList(new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        expect(deferred.state()).toEqual('pending')
        arr = undefined
        deferred.done((x) -> arr = x)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        expect(arr).toEqual(['1', '2'])

      it 'should cache unresolved values', ->
        dl = new DocumentList(new MockSelection(), resolver)
        d1 = dl.slice(0, 2)
        d2 = dl.slice(0, 2)
        expect(d2).toBe(d1)

      it 'should cache resolved values', ->
        dl = new DocumentList(new MockSelection(), resolver)
        d1 = dl.slice(0, 2)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        d2 = dl.slice(0, 2)
        expect(d2).toBe(d1)

      it 'should populate @documents', ->
        dl = new DocumentList(new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        expect(dl.documents).toEqual(['1', '2'])

      it 'should populate @documents with undefined if a later request comes in before an earlier one', ->
        dl = new DocumentList(new MockSelection(), resolver)
        d1 = dl.slice(0, 2)
        d2 = dl.slice(2, 4)
        resolver.deferreds[1].resolve({ documents: [ '3', '4' ] })
        expect(dl.documents).toEqual([undefined, undefined, '3', '4'])
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ] })
        expect(dl.documents).toEqual([ '1', '2', '3', '4' ])

      it 'should populate @n', ->
        dl = new DocumentList(new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ], total_items: 2 })
        expect(dl.n).toEqual(2)

      it 'should notify observers', ->
        dl = new DocumentList(new MockSelection(), resolver)
        deferred = dl.slice(0, 2)
        called = false
        dl.observe(-> called = true)
        resolver.deferreds[0].resolve({ documents: [ '1', '2' ], total_items: 2 })
        expect(called).toBeTruthy()

    describe 'n', ->
      it 'should begin undefined', ->
        dl = new DocumentList(new MockSelection(), resolver)
        expect(dl.n).toBeUndefined()
