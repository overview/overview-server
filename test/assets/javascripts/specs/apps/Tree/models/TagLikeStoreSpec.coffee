require [
  'apps/Tree/models/TagLikeStore'
], (TagLikeStore) ->
  describe 'models/TagLikeStore', ->
    describe 'TagLikeStore', ->
      store = undefined

      beforeEach ->
        store = new TagLikeStore('name')
        store.parse = _.identity

      afterEach ->
        store = undefined

      dummy_tag = (id, name) ->
        { id: id, name: name }

      describe 'beginning empty', ->
        it 'should start empty', ->
          expect(store.objects).toEqual([])

        it 'should add a tag', ->
          tag1 = dummy_tag(1, 'Tag')
          tag = store.add(tag1)
          expect(store.objects).toEqual([tag1])

        it 'should add a negative id to a tag', ->
          tag1 = { name: 'name' }
          store.add(tag1)
          expect(tag1.id).toBeLessThan(0)

        it 'should add a position to a tag', ->
          tag1 = dummy_tag(1, 'Tag')
          store.add(tag1)
          tag2 = dummy_tag(2, 'Z')
          store.add(tag2)
          expect(tag2.position).toEqual(1)

        it 'should notify :added when a tag is added', ->
          tag1 = dummy_tag(1, 'Tag')
          val = undefined
          store.observe('added', (v) -> val = v)
          store.add(tag1)
          expect(val).toEqual(tag1)

        it 'should sort tags as it adds them', ->
          tag1 = dummy_tag(1, 'A')
          tag2 = dummy_tag(2, 'B')
          store.add(tag2)
          store.add(tag1)
          expect(store.objects).toEqual([ tag1, tag2 ])

        it 'should reset positions before :added', ->
          tag1 = dummy_tag(1, 'A')
          tag2 = dummy_tag(2, 'B')
          tag3 = dummy_tag(3, 'C')
          store.add(tag1)
          store.add(tag3)

          store.observe 'added', ->
            expect(tag1.position).toEqual(0)
            expect(tag2.position).toEqual(1)
            expect(tag3.position).toEqual(2)

          store.add(tag2)

        it 'should reset positions before :changed', ->
          tag1 = dummy_tag(1, 'A')
          tag2 = dummy_tag(2, 'B')
          store.add(tag1)
          store.add(tag2)

          store.observe 'changed', (tag) ->
            expect(tag.id).toEqual(1)
            expect(tag1.position).toEqual(1)
            expect(tag2.position).toEqual(0)

          store.change(tag1, { name: 'C' })

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

        it 'should remove a tag', ->
          store.remove(tag2)
          expect(store.objects).toEqual([ tag1, tag3 ])

        it 'should notify the position removed in :removed', ->
          val = undefined
          store.observe('removed', (v) -> val = v)
          store.remove(tag2)
          expect(val).toEqual(tag2)

        it 'should notify :id-changed', ->
          v = undefined
          store.observe('id-changed', (old_id, tag) -> v = [ old_id, tag ])
          store.change(tag2, { id: 10 })
          expect(v).toEqual([ 2, tag2 ])

        it 'should reposition tags in :removed', ->
          store.observe 'removed', () ->
            expect(tag1.position).toEqual(0)
            expect(tag2.position).toEqual(1)
            expect(tag3.position).toEqual(1)

          store.remove(tag2)

        it 'should change a tag property', ->
          store.change(tag2, { name: 'Afoo' })
          expect(store.objects[1].name).toEqual('Afoo')

        it 'should set changed values by value', ->
          doclist = { n: 1, docids: [ 1 ] }
          store.change(tag2, { doclist: doclist })
          doclist.n = 2
          expect(tag2.doclist.n).toEqual(1)
          doclist.docids.push(4)
          expect(tag2.doclist.docids).toEqual([1])

        it 'should change a tag property to undefined', ->
          store.change(tag2, { foo: 'bar' })
          store.change(tag2, { foo: undefined })
          expect(store.objects[1].foo).toBeUndefined()

        it 'should notify :changed', ->
          val = undefined
          store.observe('changed', (v) -> val = v)
          store.change(tag2, { doclist: { n: 1, docids: [ 1 ] } })
          expect(val).toBe(tag2)

        it 'should throw an exception when removing if the tag is not present', ->
          expect(-> store.remove(dummy_tag(4, 'other'))).toThrow('tagLikeNotFound')

        it 'should find_by_key() for an existing tag', ->
          tag = store.find_by_key('AA')
          expect(tag).toBe(tag1)

        it 'should find_by_id() for an existing tag', ->
          tag = store.find_by_id(1)
          expect(tag).toBe(tag1)

        it 'should return undefined when find_by_key() does not find a tag', ->
          tag = store.find_by_key('A')
          expect(tag).toBeUndefined()

        it 'should throw an exception when find_by_id() does not find a tag', ->
          expect(-> tag = store.find_by_id(63)).toThrow('tagLikeNotFound')

