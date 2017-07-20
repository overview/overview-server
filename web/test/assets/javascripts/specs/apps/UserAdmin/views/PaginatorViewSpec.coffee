define [ 'backbone', 'apps/UserAdmin/views/PaginatorView' ], (Backbone, PaginatorView) ->
  describe 'apps/UserAdmin/views/PaginatorView', ->
    paginator = undefined
    view = undefined

    beforeEach ->
      paginator = new Backbone.Model
        page: 1
        pageSize: 50
      view = new PaginatorView(model: paginator)
      view.render()

    afterEach -> view.remove()

    it 'should be a div.paginator', ->
      expect(view.$el).to.match('div.paginator')

    describe 'before knowing how many pages there are', ->
      it 'should have class=loading', -> expect(view.$el).to.have.class('loading')

    describe 'on a single-page list', ->
      beforeEach -> paginator.set(total: 50)
      it 'should have class=single', -> expect(view.$el).to.have.class('single')

    describe 'on a multi-page list', ->
      beforeEach -> paginator.set(total: 51)
      it 'should have class=multiple', -> expect(view.$el).to.have.class('multiple')
      it 'should show the page number', -> expect(view.$('.active').text()).to.eq('1')
      it 'should show other pages', -> expect(view.$('li:eq(2)').text()).to.eq('2')
      it 'should not show too many pages', -> expect(view.$('li:eq(3)').text()).not.to.eq('3')

      it 'should modify the paginator on click', ->
        view.$('li:eq(2) a').click()
        expect(paginator.get('page')).to.eq(2)

      it 'should go to the next page', ->
        view.$('li:eq(3) a').click()
        expect(paginator.get('page')).to.eq(2)

      it 'should go to the previous page', ->
        paginator.set(page: 2)
        view.$('li:eq(0) a').click()
        expect(paginator.get('page')).to.eq(1)

      it 'should not go below page 1', ->
        view.$('li:eq(0) a').click()
        expect(paginator.get('page')).to.eq(1)

      it 'should not go above the final page', ->
        paginator.set(page: 2)
        view.$('li:eq(3) a').click()
        expect(paginator.get('page')).to.eq(2)
