define [
  'apps/Show/controllers/tag_form_controller'
  'apps/Show/models/observable'
  'backbone'
], (tag_form_controller, observable, Backbone) ->
  class MockTagFormView
    observable(this)

    constructor: (@tag) ->

    close: () -> @_notify('closed')
    change: (new_tag) -> @_notify('change', new_tag)
    delete: () -> @_notify('delete')

  class Tag extends Backbone.Model

  describe 'controllers/tag_form_controller', ->
    describe 'tag_form_controller', ->
      view = null

      options = {
        create_form: (tag) -> view = new MockTagFormView(tag)
      }

      beforeEach ->
        @tag = new Tag(id: 1, name: 'tag', color: '#abcdef')
        @tag.save = sinon.spy()
        @tag.destroy = sinon.spy()
        @tag.collection = { sort: sinon.spy() }
        @state = new Backbone.Model
        @resetSpy = sinon.spy()
        @state.resetDocumentListParams = => { all: @resetSpy }
        @controller = tag_form_controller(@tag, @state, options)

      it 'should create a view when called', ->
        expect(view).to.exist

      it 'should call tag.save on change', ->
        view.change(name: 'tag2', color: '#fedcba')
        expect(@tag.save).to.have.been.calledWith(name: 'tag2', color: '#fedcba')

      it 'should sort tag.collection on change', ->
        view.change(name: 'tag2')
        expect(@tag.collection.sort).to.have.been.called

      it 'should call tag.destroy on delete', ->
        view.delete()
        expect(@tag.destroy).to.have.been.called

      it 'should deselect state.taglikeCid if necessary on delete', ->
        @state.set('taglikeCid', @tag.cid)
        view.delete()
        expect(@state.get('taglikeCid')).to.be.null

      it 'should not deselect state.taglikeCid if unnecessary on delete', ->
        @state.set('taglikeCid', 'untagged')
        view.delete()
        expect(@state.get('taglikeCid')).to.eq('untagged')

      it 'should change documentListParams if necessary on delete', ->
        @state.set(documentListParams: { type: 'tag', tag: @tag })
        view.delete()
        expect(@resetSpy).to.have.been.called

      it 'should not change documentListParams if unnecessary on delete', ->
        @state.set(documentListParams: { type: 'foo' })
        view.delete()
        expect(@resetSpy).not.to.have.been.called
