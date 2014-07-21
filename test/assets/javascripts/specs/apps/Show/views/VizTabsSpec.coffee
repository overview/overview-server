define [
  'jquery'
  'underscore'
  'backbone'
  'apps/Show/views/VizTabs'
  'i18n'
], ($, _, Backbone, VizTabs, i18n) ->
  class State extends Backbone.Model
    defaults:
      viz: null

  class Viz extends Backbone.Model
    defaults:
      title: 'title'
    idAttribute: 'longId'

  class VizList extends Backbone.Collection
    model: Viz

  describe 'apps/Show/views/VizTabs', ->
    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.show.VizTabs.cancelJob': 'cancelJob'
        'views.DocumentSet.show.VizTabs.new_viz': 'new_viz'
        'views.DocumentSet.show.VizTabs.nDocuments': 'nDocuments,{0}'
        'views.DocumentSet.show.VizTabs.viz.title.dt': 'viz.title.dt'
        'views.DocumentSet.show.VizTabs.viz.title.dd': 'viz.title.dd,{0}'
        'views.DocumentSet.show.VizTabs.viz.nDocuments.dt': 'viz.nDocuments.dt'
        'views.DocumentSet.show.VizTabs.viz.nDocuments.dd': 'viz.nDocuments.dd,{0},{1}'
        'views.DocumentSet.show.VizTabs.viz.createdAt.dt': 'viz.createdAt.dt'
        'views.DocumentSet.show.VizTabs.viz.createdAt.dd': 'viz.createdAt.dd,{0}'
        'views.DocumentSet.show.VizTabs.viz.thing1.dt': 'viz.thing1.dt'
        'views.DocumentSet.show.VizTabs.viz.thing1.dd': 'viz.thing1.dd,{0}'
        'views.DocumentSet.show.VizTabs.viz.thing2.dt': 'viz.thing2.dt'
        'views.DocumentSet.show.VizTabs.viz.thing2.dd': 'viz.thing2.dd,{0}'

    describe 'starting with two vizs', ->
      beforeEach ->
        @documentSet = { nDocuments: 1234 }
        @viz1 = new Viz(type: 'viz', id: 1, longId: 'viz-1', title: 'foo', nDocuments: 10, createdAt: new Date(), creationData: [[ 'thing1', 'value1' ], [ 'thing2', 'value2' ]])
        @viz2 = new Viz(type: 'viz', id: 2, longId: 'viz-2', title: 'bar', nDocuments: 10, createdAt: new Date(), creationData: [])
        @vizList = new VizList([@viz1, @viz2])
        @state = new State(viz: @viz1)
        @view = new VizTabs(collection: @vizList, state: @state, documentSet: @documentSet)
        $('body').append(@view.el)

      afterEach ->
        @view?.remove()
        @view?.off()

      it 'should be a ul', -> expect(@view.$el).to.match('ul')
      it 'should contain an li per viz', -> expect(@view.$('li.viz').length).to.eq(2)
      it 'should contain the vizualization', -> expect(@view.$('a:eq(0)')).to.contain('foo')
      it 'should have an info bubble per visualization', -> expect(@view.$('li.viz span.viz-info-icon').length).to.eq(2)

      it 'should show nDocuments in tab', ->
        $span = $('a:eq(0) span.count')
        expect($span).to.contain('nDocuments,10')

      it 'should set "active" on selected viz', ->
        expect(@view.$('li:eq(0)')).to.have.class('active')
        expect(@view.$('li:eq(1)')).not.to.have.class('active')

      it 'should switch the "active" viz when the state changes', ->
        @state.set(viz: @viz2)
        expect(@view.$('li:eq(0)')).not.to.have.class('active')
        expect(@view.$('li:eq(1)')).to.have.class('active')

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
        $popover = $('.popover.in')
        expect($popover).to.contain('foo')
        expect($popover.find('dt.title')).to.contain('viz.title.dt')
        expect($popover.find('dd.title')).to.contain('viz.title.dd,foo')
        expect($popover.find('dt.n-documents')).to.contain('viz.nDocuments.dt')
        expect($popover.find('dd.n-documents')).to.contain('viz.nDocuments.dd,10,1234')
        expect($popover.find('dt')).to.contain('viz.thing1.dt')
        expect($popover.find('dd')).to.contain('viz.thing1.dd,value1')

      it 'should emit click-new', ->
        spy = sinon.spy()
        @view.on('click-new', spy)
        @view.$('a.new-viz').click()
        expect(spy).to.have.been.called

      it 'should destroy a viz when it is removed', ->
        @vizList.remove(@viz1)
        expect(@view.$('li.viz').length).to.eq(1)

      it 'should add a viz to the end', ->
        viz3 = new Viz(type: 'viz', id: 3, longId: 'viz-3', title: 'bar', createdAt: new Date(), creationData: [])
        @vizList.add(viz3)
        expect(@view.$('li.viz').length).to.eq(3)
        expect(@view.$('li:eq(2)')).to.have.attr('data-id': 'viz-3')

      it 'should add a viz in the correct position', ->
        viz3 = new Viz(type: 'viz', id: 3, longId: 'viz-3', title: 'bar', createdAt: new Date(), creationData: [])
        @vizList.add(viz3, at: 1)
        expect(@view.$('li.viz').length).to.eq(3)
        expect(@view.$('li:eq(1)')).to.have.attr('data-id': 'viz-3')

    describe 'starting with a Viz and a Job', ->
      beforeEach ->
        @documentSet = { nDocuments: 1234 }
        @job = new Viz
          type: 'job'
          id: 1
          longId: 'job-1'
          title: 'foo'
          progress: { fraction: 0.32, state: 'IN_PROGRESS', description: 'doing_x' }
          creationData: [[ 'thing1', 'value1' ], [ 'thing2', 'value2' ]]
        @viz = new Viz
          type: 'viz'
          id: 1
          longId: 'viz-1'
          title: 'foo'
          createdAt: new Date()
          creationData: [[ 'rootNodeId', '123' ], [ 'thing1', 'value2' ], [ 'thing2', 'value3' ]]

        @state = new State(viz: @viz)

        @vizList = new VizList([@job, @viz])
        @view = new VizTabs(collection: @vizList, state: @state, documentSet: @documentSet)
        $('body').append(@view.el)

      afterEach ->
        @view?.remove()
        $('.popover').remove()
        @view?.off()

      it 'should give the job class "job"', -> expect(@view.$('li:eq(0)')).to.have.class('job')
      it 'should give the viz class "viz"', -> expect(@view.$('li:eq(1)')).to.have.class('viz')
      it 'should not show nDocuments for the job', -> expect($('li:eq(0) abbr.count')).not.to.exist

      it 'should emit click on a job', ->
        spy = sinon.spy()
        @view.on('click', spy)
        @view.$('a:eq(0)').click()
        expect(spy).to.have.been.calledWith(@job)

      it 'should switch to viewing the job', ->
        @state.set(viz: @job)
        expect(@view.$('li:eq(0)')).to.have.class('active')

      it 'should give the job progress', ->
        $progress = @view.$('li:eq(0) progress')
        expect($progress.length).to.eq(1)
        expect($progress).to.have.attr(value: 0.32)

      it 'should show a popover when clicking on the job', ->
        @view.$('li:eq(0) span.viz-info-icon').click()
        $popover = @view.$('li:eq(0) .popover.in')
        expect($popover).to.be.visible

      it 'should hide a popover on second click', ->
        @view.$('li:eq(0) span.viz-info-icon').click()
        @view.$('li:eq(0) span.viz-info-icon').click()
        expect(@view.$('.popover.in')).not.to.be.visible

      it 'should have a cancel button in the job popover', ->
        @view.$('li:eq(0) span.viz-info-icon').click()
        $button = @view.$('li:eq(0) .popover.in button.cancel')
        expect($button.length).to.eq(1)
        spy = sinon.spy()
        @view.on('cancel', spy)
        $button.click()
        expect(spy).to.have.been.calledWith(@job)

      it 'should position the popover centered under the element', ->
        $li = @view.$('li:eq(0)')
        $li.find('a').css(display: 'inline-block', position: 'relative', width: '200px')
        $li.find('span').css(display: 'inline-block', position: 'absolute', left: '180px', top: '0px', height: '20px', width: '20px')
        $li.find('abbr').css(display: 'none')
        $popover = $li.find('.popover')
        $popover.css(display: 'inline-block', position: 'absolute', height: '100px', width: '50px')
        $popover.find('.arrow').css(position: 'absolute')
        @view.$('li:eq(0) span.viz-info-icon').click()
        expect($popover.position().top).to.eq(21)
        expect($popover.position().left).to.eq(164) # 164-214 -- centers on 189
        expect($popover.find('.arrow').position().left).to.eq(189-164)

      it 'should not position to the left of 0', ->
        $li = @view.$('li:eq(0)')
        $li.css(display: 'block', position: 'absolute', top: 0, left: 0)
        $li.offset(left: 0, top: 0)
        $li.find('a').css(display: 'inline-block', position: 'relative', width: '100px')
        $li.find('span').css(display: 'inline-block', position: 'absolute', left: '80px', top: '0px', height: '20px', width: '20px')
        $li.find('abbr').css(display: 'none')
        $popover = $li.find('.popover')
        $popover.css(display: 'inline-block', position: 'absolute', height: '100px', width: '250px')
        $popover.find('.arrow').css(position: 'absolute')
        @view.$('li:eq(0) span.viz-info-icon').click()
        expect($popover.position().top).to.eq(21)
        expect($popover.position().left).to.eq(0) # 0-250 -- centering on 89
        expect($popover.find('.arrow').position().left).to.eq(89)

      it 'should update progress', ->
        @job.set(progress: { fraction: 0.4, state: 'IN_PROGRESS', description: 'retrieving_documents:1141:4691' })
        expect(@view.$('li:eq(0) progress')).to.have.attr('value', '0.4')

      it 'should switch from job to viz', ->
        @job.set(type: 'viz', createdAt: new Date())
        $li = @view.$('li:eq(0)')
        expect($li.find('a')).to.contain('foo')
        expect($li).not.to.have.class('job')
        expect($li).to.have.class('viz')

      it 'should keep "active" class when switching from job to viz', ->
        @state.set(viz: @job)
        @job.set(type: 'viz', createdAt: new Date())
        expect(@view.$('li:eq(0)')).to.have.class('active')
