define [
  'apps/Show/controllers/node_form_controller'
  'apps/Show/models/observable'
  'backbone'
], (node_form_controller, observable, Backbone) ->
  class MockNodeFormView
    observable(this)

    constructor: (@node) ->
    close: () -> @_notify('closed')
    change: (new_node) -> @_notify('change', new_node)

  # Mostly copy/pasted from tag_form_controller-spec
  describe 'controllers/node_form_controller', ->
    describe 'node_form_controller', ->
      log_values = undefined
      view = undefined

      options = {
        log: (s1, s2) -> log_values.push([s1, s2])
        create_form: (node) -> view = new MockNodeFormView(node)
      }

      beforeEach ->
        log_values = []
        @sandbox = sinon.sandbox.create(useFakeServer: true)
        @node = { id: 1, description: 'node', color: '#abcdef' }
        @onDemandTree =
          saveNode: sinon.spy()
        # XXX hack: 
        @state =
          get: sinon.stub().withArgs('vizApp').returns
            onDemandTree: @onDemandTree
        @controller = node_form_controller(@node, @state, options)

      afterEach ->
        @sandbox.restore()

      it 'should create a view when called', ->
        expect(view).not.to.be.undefined

      it 'should call onDemandTree.saveNode on change', ->
        attrs = { description: 'node2' }
        view.change(attrs)
        expect(@onDemandTree.saveNode).to.have.been.calledWith(@node, attrs)

      it 'should log on start', ->
        expect(log_values[0]).to.deep.eq(['began editing node', '1 (node)'])

      it 'should log on exit', ->
        view.close()
        expect(log_values[1]).to.deep.eq(['stopped editing node', '1 (node)'])

      it 'should log on change', ->
        view.change({ description: 'new-description', color: '#fedcba' })
        expect(log_values[1]).to.deep.eq(['edited node', '1: description: <<node>> to <<new-description>>'])

      it 'should log on no-change', ->
        view.change({ description: 'node', color: '#abcdef' })
        expect(log_values[1]).to.deep.eq(['edited node', '1: (no change)'])
