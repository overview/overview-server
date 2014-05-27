define [
  'jquery'
  'underscore'
  'backbone'
  'apps/Tree/views/VizTabs'
  'i18n'
], ($, _, Backbone, VizTabs, i18n) ->
  class Viz extends Backbone.Model
    defaults:
      title: 'title'

  class VizList extends Backbone.Collection
    model: Viz

  describe 'apps/Tree/views/VizTabs', ->
    beforeEach ->
      i18n.reset_messages
        x: 'y'

    afterEach ->
      @view?.remove()
      @view?.off()

    describe 'starting with two vizs', ->
      beforeEach ->
        @viz1 = new Viz(id: 1, title: 'foo')
        @viz2 = new Viz(id: 2, title: 'bar')
        @vizList = new VizList([@viz1, @viz2])
        @view = new VizTabs(collection: @vizList, selected: @viz1)
        $('body').append(@view.el)

      it 'should be a ul', -> expect(@view.$el).to.match('ul')
      it 'should contain an li per viz', -> expect(@view.$('li.viz').length).to.eq(2)
      it 'should contain the vizualization', -> expect(@view.$('a:eq(0)')).to.contain('foo')
      it 'should set "active" on selected vis', ->
        expect(@view.$('li:eq(0)')).to.have.class('active')
        expect(@view.$('li:eq(1)')).not.to.have.class('active')
      it 'should emit click', ->
        spy = sinon.spy()
        @view.on('click', spy)
        @view.$('a:eq(1)').click()
        expect(spy).to.have.been.calledWith(@viz2)
