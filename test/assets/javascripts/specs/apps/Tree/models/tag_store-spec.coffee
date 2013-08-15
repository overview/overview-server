define [
  'apps/Tree/models/tag_store'
], (TagStore) ->
  describe 'models/tag_store', ->
    describe 'TagStore', ->
      store = undefined

      beforeEach ->
        store = new TagStore()

      afterEach ->
        store = undefined

      dummy_tag = (id, name) ->
        { id: id, name: name }

      describe 'beginning empty', ->
        it 'should add a color to a tag', ->
          tag1 = dummy_tag(1, 'Tag')
          store.add(tag1)
          expect(tag1.color).toBeDefined()

      describe 'beginning full', ->
        tag1 = undefined
        tag2 = undefined
        tag3 = undefined

        beforeEach ->
          tag1 = dummy_tag(1, 'AA')
          tag2 = dummy_tag(2, 'BB')
          tag3 = dummy_tag(3, 'CC')
          store.add(tag1)
          store.add(tag2)
          store.add(tag3)

        afterEach ->
          tag1 = undefined
          tag2 = undefined
          tag3 = undefined

        it 'should have .tags as an alias for .objects', ->
          expect(store.tags).toBe(store.objects)

        it 'should find_by_name() for an existing tag', ->
          tag = store.find_by_name('AA')
          expect(tag).toBe(tag1)

        it 'should return undefined when find_by_name() does not find a tag', ->
          tag = store.find_by_name('A')
          expect(tag).toBeUndefined()
