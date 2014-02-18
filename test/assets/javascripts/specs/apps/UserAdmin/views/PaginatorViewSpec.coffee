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

    it 'should be a div.pagination', ->
      expect(view.$el).toBeMatchedBy('div.pagination')

    describe 'before knowing how many pages there are', ->
      it 'should have class=loading', -> expect(view.$el).toHaveClass('loading')

    describe 'on a single-page list', ->
      beforeEach -> paginator.set(total: 50)
      it 'should have class=single', -> expect(view.$el).toHaveClass('single')

    describe 'on a multi-page list', ->
      beforeEach -> paginator.set(total: 51)
      it 'should have class=multiple', -> expect(view.$el).toHaveClass('multiple')
      it 'should show the page number', -> expect(view.$('.active').text()).toEqual('1')
      it 'should show other pages', -> expect(view.$('li:eq(2)').text()).toEqual('2')
      it 'should not show too many pages', -> expect(view.$('li:eq(3)').text()).not.toEqual('3')

      it 'should modify the paginator on click', ->
        view.$('li:eq(2) a').click()
        expect(paginator.get('page')).toEqual(2)

      it 'should go to the next page', ->
        view.$('li:eq(3) a').click()
        expect(paginator.get('page')).toEqual(2)

      it 'should go to the previous page', ->
        paginator.set(page: 2)
        view.$('li:eq(0) a').click()
        expect(paginator.get('page')).toEqual(1)

      it 'should not go below page 1', ->
        view.$('li:eq(0) a').click()
        expect(paginator.get('page')).toEqual(1)

      it 'should not go above the final page', ->
        paginator.set(page: 2)
        view.$('li:eq(3) a').click()
        expect(paginator.get('page')).toEqual(2)
