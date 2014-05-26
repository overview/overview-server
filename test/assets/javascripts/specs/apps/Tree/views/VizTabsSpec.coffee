define [
  'jquery'
  'underscore'
  'backbone'
  'apps/Tree/views/VizTabs'
  'i18n'
], ($, _, Backbone, VizTabs, i18n) ->
  class Viz extends Backbone.Model
    defaults:
      name: 'name'

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
        @viz1 = new Viz(id: 1, name: 'foo')
        @viz2 = new Viz(id: 2, name: 'bar')
        @vizList = new VizList([@viz1, @viz2])
        @view = new VizTabs(collection: @vizList)
        $('body').append(@view.el)

      it 'should be a ul', -> expect(@view.el).toBeMatchedBy('ul')
      it 'should contain an li per viz', -> expect(@view.$('a.viz').length).toEqual(2)
      it 'should contain the vizualization', -> expect(@view.$('a:eq(0)')).toContainText('foo')
      it 'should emit click', ->
        spy = jasmine.createSpy()
        @view.on('click', spy)
        @view.$('a:eq(1)').click()
        expect(spy).toHaveBeenCalledWith(@viz2)
