TagStore = require('models/tag_store').TagStore

describe 'models/tag_store', ->
  describe 'TagStore', ->
    tag_store = undefined

    beforeEach ->
      tag_store = new TagStore()

    afterEach ->
      tag_store = undefined

    dummy_tag = (id, name) ->
      { id: id, name: name, count: 1 }

    describe 'beginning at empty', ->
      it 'should start empty', ->
        expect(tag_store.tags).toEqual([])

      it 'should add a tag', ->
        tag1 = dummy_tag(1, 'Tag')
        tag_store.add(tag1)
        expect(tag_store.tags).toEqual([tag1])

      it 'should add a position to a tag', ->
        tag1 = dummy_tag(1, 'Tag')
        tag_store.add(tag1)
        tag2 = dummy_tag(2, 'Z')
        tag_store.add(tag2)
        expect(tag2.position).toEqual(1)

      it 'should notify :tag-added when a tag is added', ->
        tag1 = dummy_tag(1, 'Tag')
        val = undefined
        tag_store.observe('tag-added', (v) -> val = v)
        tag_store.add(tag1)
        expect(val).toEqual(tag1)

      it 'should sort tags as it adds them', ->
        tag1 = dummy_tag(1, 'A')
        tag2 = dummy_tag(2, 'B')
        tag_store.add(tag2)
        tag_store.add(tag1)
        expect(tag_store.tags).toEqual([ tag1, tag2 ])

      it 'should reset positions before :tag-added', ->
        tag1 = dummy_tag(1, 'A')
        tag2 = dummy_tag(2, 'B')
        tag3 = dummy_tag(3, 'C')
        tag_store.add(tag1)
        tag_store.add(tag3)

        tag_store.observe 'tag-added', ->
          expect(tag1.position).toEqual(0)
          expect(tag2.position).toEqual(1)
          expect(tag3.position).toEqual(2)

        tag_store.add(tag2)

    describe 'beginning full', ->
      tag1 = undefined
      tag2 = undefined
      tag3 = undefined

      beforeEach ->
        tag1 = dummy_tag(1, 'AA')
        tag2 = dummy_tag(2, 'BB')
        tag3 = dummy_tag(3, 'CC')
        tag_store.add(tag1)
        tag_store.add(tag2)
        tag_store.add(tag3)

      afterEach ->
        tag1 = undefined
        tag2 = undefined
        tag3 = undefined

      it 'should remove a tag', ->
        tag_store.remove(tag2)
        expect(tag_store.tags).toEqual([ tag1, tag3 ])

      it 'should notify the position removed in :tag-removed', ->
        val = undefined
        tag_store.observe('tag-removed', (v) -> val = v)
        tag_store.remove(tag2)
        expect(val).toEqual(tag2)

      it 'should reposition tags in :tag-removed', ->
        tag_store.observe 'tag-removed', () ->
          expect(tag1.position).toEqual(0)
          expect(tag2.position).toEqual(1)
          expect(tag3.position).toEqual(1)

        tag_store.remove(tag2)

      it 'should change a tag property', ->
        tag_store.change(tag2, { name: 'foo' })
        expect(tag_store.tags[1].name).toEqual('foo')

      it 'should set changed values by value, not by reference', ->
        doclist = { n: 1, docids: [ 1 ] }
        tag_store.change(tag2, { doclist: doclist })
        doclist.n = 2
        expect(tag2.doclist.n).toEqual(1)
        doclist.docids.push(4)
        expect(tag2.doclist.docids).toEqual([1])

      it 'should change a tag property to undefined', ->
        tag_store.change(tag2, { name: undefined })
        expect(tag_store.tags[1].name).toBeUndefined()

      it 'should notify :tag-changed', ->
        val = undefined
        tag_store.observe('tag-changed', (v) -> val = v)
        tag_store.change(tag2, { doclist: { n: 1, docids: [ 1 ] } })
        expect(val).toBe(tag2)

      it 'should throw an exception when removing if the tag is not present', ->
        expect(-> tag_store.remove(dummy_tag(4, 'other'))).toThrow('tagNotFound')

      it 'should throw an exception when adding if another tag with the same name exists', ->
        expect(-> tag_store.add(dummy_tag(4, 'AA'))).toThrow('tagAlreadyExists')

      it 'should find_tag_by_name() for an existing tag', ->
        tag = tag_store.find_tag_by_name('AA')
        expect(tag).toBe(tag1)

      it 'should find_tag_by_id(0 for an existing tag', ->
        tag = tag_store.find_tag_by_id(1)
        expect(tag).toBe(tag1)

      it 'should return undefined when find_tag_by_name() does not find a tag', ->
        tag = tag_store.find_tag_by_name('A')
        expect(tag).toBeUndefined()

      it 'should throw an exception when find_tag_by_id() does not find a tag', ->
        expect(-> tag = tag_store.find_tag_by_id(63)).toThrow('tagNotFound')

