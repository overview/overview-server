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
      expect(view.$('input[type=email][name=email]').length).to.eq(1)

    it 'should prompt for a password', ->
      expect(view.$('input[type=password][name=password]').length).to.eq(1)

    describe 'on valid submit', ->
      params =
        email: 'user@example.org'
        password: 's0mE-pwrd#&'
      createSpy = undefined

      beforeEach ->
        createSpy = sinon.spy()
        view.on('create', createSpy)
        view.$('input[name=email]').val(params.email)
        view.$('input[name=password]').val(params.password)
        view.$('form').submit()

      it 'should trigger :create with email/password', -> expect(createSpy).to.have.been.calledWith(params)
      it 'should focus the email', ->
        $email = view.$('input[name=email]')
        expect($email[0]).to.eq($email[0].ownerDocument.activeElement)
      it 'should reset the form', ->
        expect(view.$('input[name=email]').val()).to.eq('')
        expect(view.$('input[name=password]').val()).to.eq('')
