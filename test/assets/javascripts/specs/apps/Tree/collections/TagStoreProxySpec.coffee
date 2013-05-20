require [
  'apps/Tree/models/observable'
  'apps/Tree/collections/TagStoreProxy'
], (observable, TagStoreProxy) ->
  class TagStore
    observable(this)

    constructor: (@tags) ->

  describe 'apps/Tree/collections/TagStoreProxy', ->
    tagStore = undefined
    proxy = undefined
    collection = undefined

    describe 'with an empty TagStore', ->
      beforeEach ->
        tagStore = new TagStore([])
        proxy = new TagStoreProxy(tagStore)
        collection = proxy.collection

      it 'should be empty', ->
        expect(collection.length).toEqual(0)

      it 'should not observe anything after destroy', ->
        proxy.destroy()
        tagStore._notify('tag-added', { id: 3, name: 'tag15', position: 1, color: '#151515' })
        expect(collection.length).toEqual(0)

    describe 'with a TagStore with two tags', ->
      beforeEach ->
        tagStore = new TagStore([
          { id: 10, name: 'tag10', color: '#101010', position: 0 }
          { id: 20, name: 'tag20', color: '#202020', position: 1 }
        ])
        proxy = new TagStoreProxy(tagStore)
        collection = proxy.collection

      it 'should find a tag', ->
        tag = proxy.map({ id: 20, name: 'tag20', color: '#202020', position: 0 })
        expect(tag).toBe(collection.last())

      it 'should find a tag by ID', ->
        tag = proxy.map(20)
        expect(tag).toBe(collection.last())

      it 'should throw exception on not-found tag', ->
        expect(-> tag = proxy.map(19)).toThrow()

      it 'should find a plain-old-data object', ->
        obj = proxy.unmap(collection.last())
        expect(obj).toBe(tagStore.tags[1])

      it 'should include the existing tags', ->
        expect(collection.length).toEqual(2)
        expect(collection.first().get('name')).toEqual('tag10')
        expect(collection.last().id).toEqual(20)

      it 'should add a new tag to the middle', ->
        tagStore._notify('tag-added', { id: 3, name: 'tag15', position: 1, color: '#151515' })
        expect(collection.length).toEqual(3)
        tag = collection.at(1)
        expect(tag.id).toEqual(3)
        expect(tag.get('name')).toEqual('tag15')
        expect(tag.get('color')).toEqual('#151515')

      it 'should not set the ID on an ID-less tag', ->
        tagStore._notify('tag-added', { id: -3, name: 'tag30', position: 2, color: '#303030' })
        expect(collection.last().id).toBeUndefined()

      it 'should remove a tag', ->
        tagStore._notify('tag-removed', tagStore.tags[0])
        expect(collection.length).toEqual(1)
        expect(collection.first().id).toEqual(20)

      it 'should change tag attributes', ->
        tagStore._notify('tag-changed', { id: 20, name: 'tag21', color: '#212121' })
        tag = collection.last()
        expect(tag.get('name')).toEqual('tag21')
        expect(tag.get('color')).toEqual('#212121')

      it 'should set options when changing', ->
        tag = collection.last()
        proxy.setChangeOptions({ interacting: true })
        spyOn(tag, 'set')
        tagStore._notify('tag-changed', { id: 20, name: 'tag21', color: '#212121' })
        expect(tag.set.mostRecentCall.args[1]).toEqual({ interacting: true })

    describe 'with a TagStore with an un-inserted tag', ->
      beforeEach ->
        tagStore = new TagStore([
          { id: -1, name: 'tag01', color: '#010101' }
        ])
        proxy = new TagStoreProxy(tagStore)
        collection = proxy.collection

      it 'should not put a tag ID', ->
        expect(collection.first().id).toBeUndefined()

      it 'should set a tag ID', ->
        tag = tagStore.tags[0]
        tag.id = 1
        tagStore._notify('tag-id-changed', -1, tag)
        expect(collection.first().id).toEqual(1)

      it 'should set options when changing tag ID', ->
        tag = collection.first()
        spyOn(tag, 'set')
        proxy.setChangeOptions({ interacting: true })
        tagStore._notify('tag-id-changed', -1, 1)
        expect(tag.set.mostRecentCall.args[1]).toEqual({ interacting: true })
