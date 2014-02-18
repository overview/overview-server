define [ 'apps/UserAdmin/views/NewUserView', 'i18n' ], (NewUserView, i18n) ->
  describe 'apps/UserAdmin/views/NewUserView', ->
    view = undefined

    beforeEach ->
      i18n.reset_messages
        'views.admin.User.index.new.title': 'new.title'
        'views.admin.User.index.new.email': 'new.email'
        'views.admin.User.index.new.password': 'new.password'
        'views.admin.User.index.new.submit': 'new.submit'


      view = new NewUserView()
      view.render()
      $('body').append(view.el) # make form focus work

    afterEach ->
      view.remove()

    it 'should prompt for an email address', ->
      expect(view.$('input[type=email][name=email]').length).toEqual(1)

    it 'should prompt for a password', ->
      expect(view.$('input[type=password][name=password]').length).toEqual(1)

    describe 'on valid submit', ->
      params =
        email: 'user@example.org'
        password: 's0mE-pwrd#&'
      createSpy = undefined

      beforeEach ->
        createSpy = jasmine.createSpy('create')
        view.on('create', createSpy)
        view.$('input[name=email]').val(params.email)
        view.$('input[name=password]').val(params.password)
        view.$('form').submit()

      it 'should trigger :create with email/password', -> expect(createSpy).toHaveBeenCalledWith(params)
      it 'should focus the email', -> expect(view.$('input[name=email]')).toBeFocused()
      it 'should reset the form', ->
        expect(view.$('input[name=email]').val()).toEqual('')
        expect(view.$('input[name=password]').val()).toEqual('')
