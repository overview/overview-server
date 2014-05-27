define [ 'jquery', 'i18n', 'elements/jquery-time_display' ], ($, i18n) ->
  describe 'elements/jquery-time_display', ->
    $el = undefined
    date = new Date('2014-02-22T23:45:37Z')

    applyWithAttrs = (attrs) ->
      $el.attr(attrs)
      $el.time_display()

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub(i18n, 'translate', -> 'translated time')
      $el = $('<time datetime="2014-02-22T23:45:37Z">foo</time>')

    afterEach ->
      @sandbox.restore()

    describe 'with no other options', ->
      beforeEach -> applyWithAttrs({})
      it 'should display the datetime', -> expect($el.text()).to.eq(date.toString())
      it 'should set the title', -> expect($el.attr('title')).to.eq(date.toString())

    describe 'with format', ->
      beforeEach -> applyWithAttrs('data-format': 'datetime.medium')
      it 'should call i18n()', -> expect(i18n.translate).to.have.been.calledWith('time_display.datetime.medium', date)
      it 'should display the result', -> expect($el.text()).to.eq('translated time')
      it 'should set the title', -> expect($el.attr('title')).to.eq(date.toString())

    describe 'with a title already set', ->
      beforeEach -> applyWithAttrs(title: 'title')
      it 'should not change the title', -> expect($el.attr('title')).to.eq('title')
