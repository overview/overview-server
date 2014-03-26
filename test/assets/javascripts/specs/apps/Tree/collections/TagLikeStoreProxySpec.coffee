define [
  'apps/Tree/models/observable'
  'apps/Tree/collections/TagLikeStoreProxy'
], (observable, TagLikeStoreProxy) ->
  class TagLikeStore
    observable(this)

    constructor: (@objects) ->

  describe 'apps/Tree/collections/TagLikeStoreProxy', ->
    store = undefined
    proxy = undefined
    collection = undefined

    afterEach ->
      proxy?.destroy()

    describe 'with an empty TagLikeStore', ->
      beforeEach ->
        store = new TagLikeStore([])
        proxy = new TagLikeStoreProxy(store)
        collection = proxy.collection

      it 'should be empty', ->
        expect(collection.length).toEqual(0)

      it 'should not observe anything after destroy', ->
        proxy.destroy()
        store._notify('added', { id: 3, name: 'tag15', position: 1, color: '#151515' })
        expect(collection.length).toEqual(0)

    describe 'with a TagLikeStore with two objects', ->
      beforeEach ->
        store = new TagLikeStore([
          { id: 10, name: 'tag10', color: '#101010', position: 0 }
          { id: 20, name: 'tag20', color: '#202020', position: 1 }
        ])
        proxy = new TagLikeStoreProxy(store)
        collection = proxy.collection

      it 'should find a tag', ->
        tag = proxy.map({ id: 20, name: 'tag20', color: '#202020', position: 0 })
        expect(tag).toBe(collection.last())

      it 'should find a tag by ID', ->
        tag = proxy.map(20)
        expect(tag).toBe(collection.last())

      it 'should say when it can map an ID', ->
        expect(proxy.canMap(20)).toBe(true)

      it 'should say when it cannot map an ID', ->
        expect(proxy.canMap(21)).toBe(false)

      it 'should throw exception on not-found tag', ->
        expect(-> tag = proxy.map(19)).toThrow()

      it 'should find a plain-old-data object', ->
        obj = proxy.unmap(collection.last())
        expect(obj).toBe(store.objects[1])

      it 'should include the existing objects', ->
        expect(collection.length).toEqual(2)
        expect(collection.first().get('name')).toEqual('tag10')
        expect(collection.last().id).toEqual(20)

      it 'should add a new tag to the middle', ->
        store._notify('added', { id: 3, name: 'tag15', position: 1, color: '#151515' })
        expect(collection.length).toEqual(3)
        tag = collection.at(1)
        expect(tag.id).toEqual(3)
        expect(tag.get('name')).toEqual('tag15')
        expect(tag.get('color')).toEqual('#151515')

      it 'should not set the ID on an ID-less tag', ->
        store._notify('added', { id: -3, name: 'tag30', position: 2, color: '#303030' })
        expect(collection.last().id).toBeUndefined()

      it 'should remove a tag', ->
        store._notify('removed', store.objects[0])
        expect(collection.length).toEqual(1)
        expect(collection.first().id).toEqual(20)

      it 'should change tag attributes', ->
        store._notify('changed', { id: 20, name: 'tag21', color: '#212121' })
        tag = collection.last()
        expect(tag.get('name')).toEqual('tag21')
        expect(tag.get('color')).toEqual('#212121')

      it 'should set options when changing', ->
        tag = collection.last()
        proxy.setChangeOptions({ interacting: true })
        spyOn(tag, 'set')
        store._notify('changed', { id: 20, name: 'tag21', color: '#212121' })
        expect(tag.set.calls.mostRecent().args[1]).toEqual({ interacting: true })

    describe 'with a TagLikeStore with an un-inserted tag', ->
      beforeEach ->
        store = new TagLikeStore([
          { id: -1, name: 'tag01', color: '#010101' }
        ])
        proxy = new TagLikeStoreProxy(store)
        collection = proxy.collection

      it 'should not put a tag ID', ->
        expect(collection.first().id).toBeUndefined()

      it 'should set a tag ID', ->
        tag = store.objects[0]
        tag.id = 1
        store._notify('id-changed', -1, tag)
        expect(collection.first().id).toEqual(1)

      it 'should set options when changing tag ID', ->
        tag = collection.first()
        spyOn(tag, 'set')
        proxy.setChangeOptions({ interacting: true })
        store._notify('id-changed', -1, tag)
        expect(tag.set.calls.mostRecent().args[1]).toEqual({ interacting: true })

      it 'should find the tagLike in a callback by either ID', ->
        found = {
          byOldId: undefined
          byNewId: undefined
        }
        collection.on 'change:id', (model) ->
          found.byOldId = proxy.map(-1)
          found.byNewId = proxy.map(99)
        store.objects[0].id = 99
        store._notify('id-changed', -1, store.objects[0])
        expect(found.byOldId).not.toBeUndefined()
        expect(found.byOldId).toBe(found.byNewId)

      it 'should not find the tagLike by the old ID after callbacks have fired', ->
        store.objects[0].id = 99
        store._notify('id-changed', -1, store.objects[0])
        expect(-> proxy.map(-1)).toThrow()
