define [
  'jquery'
  'underscore'
  'backbone'
  'apps/Show/views/ViewTabs'
  'i18n'
], ($, _, Backbone, ViewTabs, i18n) ->
  class State extends Backbone.Model
    defaults:
      view: null

  class View extends Backbone.Model
    defaults:
      title: 'title'
    idAttribute: 'longId'

  class ViewList extends Backbone.Collection
    model: View

  class Plugin extends Backbone.Model
    defaults:
      name: 'name'
      description: 'description'
      url: 'http://example.org'

  class Plugins extends Backbone.Collection
    model: Plugin

  describe 'apps/Show/views/ViewTabs', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @plugin1 = new Plugin(name: 'tree', description: 'treedesc', url: 'about:tree')
      @plugin2 = new Plugin(name: 'plugin2', description: 'urldesc', url: 'http://example.org')
      @plugins = new Plugins([ @plugin1, @plugin2 ])

      i18n.reset_messages_namespaced 'views.DocumentSet.show.ViewTabs',
        'cancelJob': 'cancelJob'
        'newView': 'newView'
        'newView.custom': 'newView.custom'
        'nDocuments': 'nDocuments,{0}'
        'view.close.top': 'view.close.top'
        'view.close.bottom': 'view.close.bottom'
        'view.delete': 'view.delete'
        'view.delete.confirm': 'view.delete.confirm'
        'view.title.dt': 'view.title.dt'
        'view.title.dd': 'view.title.dd,{0}'
        'view.title.rename': 'view.title.rename'
        'view.title.label': 'view.title.label'
        'view.title.placeholder': 'view.title.placeholder'
        'view.title.save': 'view.title.save'
        'view.title.reset': 'view.title.reset'
        'view.nDocuments.dt': 'view.nDocuments.dt'
        'view.nDocuments.dd': 'view.nDocuments.dd,{0},{1}'
        'view.createdAt.dt': 'view.createdAt.dt'
        'view.createdAt.dd': 'view.createdAt.dd,{0}'
        'view.thing1.dt': 'view.thing1.dt'
        'view.thing1.dd': 'view.thing1.dd,{0}'
        'view.thing2.dt': 'view.thing2.dt'
        'view.thing2.dd': 'view.thing2.dd,{0}'

    afterEach ->
      @sandbox.restore()

    describe 'starting with a View', ->
      beforeEach ->
        @view1 = new View(type: 'view', id: 1, longId: 'view-1', title: 'foo', nDocuments: 10, createdAt: new Date(), creationData: [])
        @viewList = new ViewList([@view1])
        @state = new State(view: @view1)
        @state.nDocuments = 1234
        @view = new ViewTabs(collection: @viewList, plugins: @plugins, state: @state)

      describe 'after opening the popover', ->
        beforeEach ->
          @view.$('span.view-info-icon:eq(0)').click()
          @$popover = @view.$('.popover.in')
          @sandbox.stub(window, 'confirm').returns(true)
          @view.on('delete-view', @deleteSpy = sinon.spy())

        it 'should show a "Delete" button', ->
          expect(@$popover.find('button.delete')).to.contain('view.delete')

        it 'should confirm before deleting', ->
          window.confirm.returns(false)
          @$popover.find('button.delete').click()
          expect(window.confirm).to.have.been.calledWith('view.delete.confirm')
          expect(@deleteSpy).not.to.have.been.called

        it 'should delete on confirm', ->
          window.confirm.returns(true)
          @$popover.find('button.delete').click()
          expect(@deleteSpy).to.have.been.calledWith(@view1)

        it 'should rename', ->
          @view.on('update-view', updateSpy = sinon.spy())
          @$popover.find('a.rename').click()
          @$popover.find('input[name=title]').val('new-title')
          @$popover.find('form.rename').submit()
          expect(updateSpy).to.have.been.calledWith(@view1, title: 'new-title')

        it 'should close the popover from the top button', ->
          @$popover.find('a.close-top').click()
          expect(@$popover).not.to.have.class('in')

        it 'should close the popover from the bottom button', ->
          @$popover.find('a.close-bottom').click()
          expect(@$popover).not.to.have.class('in')

    describe 'starting with two views', ->
      beforeEach ->
        @view1 = new View(type: 'view', id: 1, longId: 'view-1', title: 'foo', nDocuments: 10, createdAt: new Date(), creationData: [[ 'thing1', 'value1' ], [ 'thing2', 'value2' ]])
        @view2 = new View(type: 'view', id: 2, longId: 'view-2', title: 'bar', nDocuments: 10, createdAt: new Date(), creationData: [])
        @viewList = new ViewList([@view1, @view2])
        @state = new State(view: @view1)
        @state.nDocuments = 1234
        @view = new ViewTabs(collection: @viewList, plugins: @plugins, state: @state)
        $('body').append(@view.el)

      afterEach ->
        @view?.remove()
        @view?.off()

      it 'should be a ul', -> expect(@view.$el).to.match('ul')
      it 'should contain an li per view', -> expect(@view.$('li.view').length).to.eq(2)
      it 'should contain the viewualization', -> expect(@view.$('a:eq(0)')).to.contain('foo')
      it 'should have an info bubble per visualization', -> expect(@view.$('li.view span.view-info-icon').length).to.eq(2)

      it 'should update the tab when the View title chanages', ->
        @view2.set(title: 'bar2')
        expect(@view.$('li.view:eq(0) a:eq(0)')).to.contain('foo')
        expect(@view.$('li.view:eq(1) a:eq(0)')).to.contain('bar2')

      it 'should update the popover then the View title changes', ->
        @view2.set(title: 'bar2')
        expect(@view.$('.popover:eq(0) span.title')).to.contain('foo')
        expect(@view.$('.popover:eq(1) span.title')).to.contain('bar2')

      it 'should show nDocuments in tab', ->
        $span = $('li:eq(0) a:eq(0) span.count')
        expect($span).to.contain('nDocuments,10')

      it 'should set "active" on selected view', ->
        expect(@view.$('li:eq(0)')).to.have.class('active')
        expect(@view.$('li:eq(1)')).not.to.have.class('active')

      it 'should switch the "active" view when the state changes', ->
        @state.set(view: @view2)
        expect(@view.$('li:eq(0)')).not.to.have.class('active')
        expect(@view.$('li:eq(1)')).to.have.class('active')

      it 'should emit click', ->
        @view.on('click', spy = sinon.spy())
        @view.$('li:eq(1) a:eq(0)').click()
        expect(spy).to.have.been.calledWith(@view2)

      it 'should not emit click when clicking view-info icon', ->
        spy = sinon.spy()
        @view.on('click', spy)
        @view.$('span.view-info-icon:eq(0)').click()
        expect(spy).not.to.have.been.called

      it 'should show popover when clicking view-info icon', ->
        @view.$('span.view-info-icon:eq(0)').click()
        $popover = $('.popover.in')
        expect($popover).to.contain('foo')
        expect($popover.find('dt.title')).to.contain('view.title.dt')
        expect($popover.find('dd.title')).to.contain('view.title.dd,foo')
        expect($popover.find('dt.n-documents')).to.contain('view.nDocuments.dt')
        expect($popover.find('dd.n-documents')).to.contain('view.nDocuments.dd,10,1234')
        expect($popover.find('dt')).to.contain('view.thing1.dt')
        expect($popover.find('dd')).to.contain('view.thing1.dd,value1')

      it 'should emit click-new-tree', ->
        spy = sinon.spy()
        @view.on('click-new-tree', spy)
        @view.$('a[data-toggle=dropdown]').click()
        @view.$('a[data-plugin-url="about:tree"]').click()
        expect(spy).to.have.been.called

      it 'should emit click-new-view', ->
        spy = sinon.spy()
        @view.on('click-new-view', spy)
        @view.$('a[data-toggle=dropdown]').click()
        @view.$('a[data-plugin-url="http://example.org"]').click()
        expect(spy).to.have.been.calledWith(url: 'http://example.org', title: 'plugin2')

      it 'should emit click-new-view with no args about:custom', ->
        spy = sinon.spy()
        @view.on('click-new-view', spy)
        @view.$('a[data-toggle=dropdown]').click()
        @view.$('a[data-plugin-url="about:custom"]').click()
        expect(spy).to.have.been.calledWith(undefined)

      it 'should destroy a view when it is removed', ->
        @viewList.remove(@view1)
        expect(@view.$('li.view').length).to.eq(1)

      it 'should add a view to the end', ->
        view3 = new View(type: 'view', id: 3, longId: 'view-3', title: 'bar', createdAt: new Date(), creationData: [])
        @viewList.add(view3)
        expect(@view.$('li.view').length).to.eq(3)
        expect(@view.$('li:eq(2)')).to.have.attr('data-id': 'view-3')

      it 'should add a view in the correct position', ->
        view3 = new View(type: 'view', id: 3, longId: 'view-3', title: 'bar', createdAt: new Date(), creationData: [])
        @viewList.add(view3, at: 1)
        expect(@view.$('li.view').length).to.eq(3)
        expect(@view.$('li:eq(1)')).to.have.attr('data-id': 'view-3')

    describe 'starting with a View and a Job', ->
      beforeEach ->
        @job = new View
          type: 'job'
          id: 1
          longId: 'job-1'
          title: 'foo'
          progress: { fraction: 0.32, state: 'IN_PROGRESS', description: 'doing_x' }
          creationData: [[ 'thing1', 'value1' ], [ 'thing2', 'value2' ]]
        @view = new View
          type: 'view'
          id: 1
          longId: 'view-1'
          title: 'foo'
          createdAt: new Date()
          creationData: [[ 'rootNodeId', '123' ], [ 'thing1', 'value2' ], [ 'thing2', 'value3' ]]

        @state = new State(view: @view)
        @state.nDocuments = 1234

        @viewList = new ViewList([@job, @view])
        @view = new ViewTabs(collection: @viewList, plugins: @plugins, state: @state)
        $('body').append(@view.el)

      afterEach ->
        @view?.remove()
        $('.popover').remove()
        @view?.off()

      it 'should give the job class "job"', -> expect(@view.$('li:eq(0)')).to.have.class('job')
      it 'should give the view class "view"', -> expect(@view.$('li:eq(1)')).to.have.class('view')
      it 'should not show nDocuments for the job', -> expect($('li:eq(0) abbr.count')).not.to.exist

      it 'should emit click on a job', ->
        spy = sinon.spy()
        @view.on('click', spy)
        @view.$('a:eq(0)').click()
        expect(spy).to.have.been.calledWith(@job)

      it 'should switch to viewing the job', ->
        @state.set(view: @job)
        expect(@view.$('li:eq(0)')).to.have.class('active')

      it 'should give the job progress', ->
        $progress = @view.$('li:eq(0) progress')
        expect($progress.length).to.eq(1)
        expect($progress).to.have.attr(value: 0.32)

      it 'should show a popover when clicking on the job', ->
        @view.$('li:eq(0) span.view-info-icon').click()
        $popover = @view.$('li:eq(0) .popover.in')
        expect($popover).to.be.visible

      it 'should hide a popover on second click', ->
        @view.$('li:eq(0) span.view-info-icon').click()
        @view.$('li:eq(0) span.view-info-icon').click()
        expect(@view.$('.popover.in')).not.to.be.visible

      it 'should have a cancel button in the job popover', ->
        @view.$('li:eq(0) span.view-info-icon').click()
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
        @view.$('li:eq(0) span.view-info-icon').click()
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
        @view.$('li:eq(0) span.view-info-icon').click()
        expect($popover.position().top).to.eq(21)
        expect($popover.position().left).to.eq(0) # 0-250 -- centering on 89
        expect($popover.find('.arrow').position().left).to.eq(89)

      it 'should update progress', ->
        @job.set(progress: { fraction: 0.4, state: 'IN_PROGRESS', description: 'retrieving_documents:1141:4691' })
        expect(@view.$('li:eq(0) progress')).to.have.attr('value', '0.4')

      it 'should switch from job to view', ->
        @job.set(type: 'view', createdAt: new Date())
        $li = @view.$('li:eq(0)')
        expect($li.find('a')).to.contain('foo')
        expect($li).not.to.have.class('job')
        expect($li).to.have.class('view')

      it 'should keep "active" class when switching from job to view', ->
        @state.set(view: @job)
        @job.set(type: 'view', createdAt: new Date())
        expect(@view.$('li:eq(0)')).to.have.class('active')
