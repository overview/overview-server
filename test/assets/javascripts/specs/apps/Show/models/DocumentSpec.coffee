define [
  'backbone'
  'apps/Show/models/Document'
], (Backbone, Document) ->
  describe 'apps/Show/models/Document', ->
    class Tag extends Backbone.Model

    class Documents extends Backbone.Collection
      model: Document
      hasTag: -> undefined

    beforeEach ->
      @collection = new Documents
      @document = new Document(title: 'title', description: 'a description')
      @collection.add(@document)

    it 'should have a type', ->
      # We use the type in DocumentListView, to differentiate a Document from
      # a placeholder
      expect(@document.get('type')).to.eq('document')

    it 'should not have a tag by default', ->
      tag = new Tag()
      expect(@document.hasTag(tag)).to.be.false

    it 'should have a tag if it is in tagIds', ->
      tag = new Tag(id: 3)
      @document.set(tagIds: { 3: null })
      expect(@document.hasTag(tag)).to.be.true

    it 'should have a tag if it is in tagCids', ->
      tag = new Tag()
      @document.attributes.tagCids = cids = {}
      cids[tag.cid] = true
      expect(@document.hasTag(tag)).to.be.true

    it 'should not have a tag if it in tagCids as false', ->
      tag = new Tag(id: 3)
      @document.set(tagIds: { 3: null })
      @document.attributes.tagCids = cids = {}
      cids[tag.cid] = false
      expect(@document.hasTag(tag)).to.be.false

    it 'should have a tag if it is in collection', ->
      tag = new Tag()
      @collection.hasTag = (aTag) -> aTag == tag
      expect(@document.hasTag(tag)).to.be.true

    it 'should not have a tag if it is out of collection', ->
      tag = new Tag(id: 3)
      @document.set(tagIds: { 3: null })
      @collection.hasTag = (aTag) -> aTag != tag
      expect(@document.hasTag(tag)).to.be.false

    it 'should not have a tag if it is true in collection.tagCids and false in document.tagCids', ->
      tag = new Tag(id: 3)
      @document.set(tagIds: { 3: null })
      @collection.tagCids = cids = {}
      cids[tag.cid] = true
      cids = {}
      cids[tag.cid] = false
      @document.set(tagCids: cids)
      expect(@document.hasTag(tag)).to.be.false

    it 'should have tag() and untag()', ->
      tag = new Tag()
      @document.tag(tag)
      expect(@document.hasTag(tag)).to.be.true
      @document.untag(tag)
      expect(@document.hasTag(tag)).to.be.false

    it 'should have unsetTag() to undo the tag overriding', ->
      tag = new Tag(id: 3)
      @document.set(tagIds: { 3: null })
      @document.untag(tag)
      @document.unsetTag(tag)
      expect(@document.hasTag(tag)).to.be.true
