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
        'views.DocumentSet.show.VizTabs.viz.title.dt': 'viz.title.dt'
        'views.DocumentSet.show.VizTabs.viz.title.dd': 'viz.title.dd,{0}'
        'views.DocumentSet.show.VizTabs.viz.createdAt.dt': 'viz.createdAt.dt'
        'views.DocumentSet.show.VizTabs.viz.createdAt.dd': 'viz.createdAt.dd,{0}'
        'views.DocumentSet.show.VizTabs.viz.thing1.dt': 'viz.thing1.dt'
        'views.DocumentSet.show.VizTabs.viz.thing1.dd': 'viz.thing1.dd,{0}'
        'views.DocumentSet.show.VizTabs.viz.thing2.dt': 'viz.thing2.dt'
        'views.DocumentSet.show.VizTabs.viz.thing2.dd': 'viz.thing2.dd,{0}'

    describe 'starting with two vizs', ->
      beforeEach ->
        @viz1 = new Viz(id: 1, title: 'foo', createdAt: new Date(), creationData: [[ 'thing1', 'value1' ], [ 'thing2', 'value2' ]])
        @viz2 = new Viz(id: 2, title: 'bar', createdAt: new Date(), creationData: [])
        @vizList = new VizList([@viz1, @viz2])
        @view = new VizTabs(collection: @vizList, selected: @viz1)
        $('body').append(@view.el)

      afterEach ->
        @view?.remove()
        $('.popover').remove()
        @view?.off()

      it 'should be a ul', -> expect(@view.$el).to.match('ul')
      it 'should contain an li per viz', -> expect(@view.$('li.viz').length).to.eq(2)
      it 'should contain the vizualization', -> expect(@view.$('a:eq(0)')).to.contain('foo')
      it 'should have an info bubble per visualization', -> expect(@view.$('li.viz span.viz-info-icon').length).to.eq(2)

      it 'should set "active" on selected vis', ->
        expect(@view.$('li:eq(0)')).to.have.class('active')
        expect(@view.$('li:eq(1)')).not.to.have.class('active')

      it 'should emit click', ->
        spy = sinon.spy()
        @view.on('click', spy)
        @view.$('a:eq(1)').click()
        expect(spy).to.have.been.calledWith(@viz2)

      it 'should not emit click when clicking viz-info icon', ->
        spy = sinon.spy()
        @view.on('click', spy)
        @view.$('span.viz-info-icon:eq(0)').click()
        expect(spy).not.to.have.been.called

      it 'should show popover when clicking viz-info icon', ->
        @view.$('span.viz-info-icon:eq(0)').click()
        $popover = $('.popover')
        expect($popover).to.contain('foo')
        expect($popover.find('dt')).to.contain('viz.thing1.dt')
        expect($popover.find('dd')).to.contain('viz.thing1.dd,value1')
