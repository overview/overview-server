define [
  'apps/ImportOptions/models/Options'
], (Options) ->
  describe 'apps/ImportOptions/models/Options', ->
    subject = undefined

    it 'should not allow both excludeOptions and onlyOptions', ->
      expect(->
        new Options {},
          supportedLanguages: [{code:'en',name:'English'}]
          defaultLanguageCode: 'en'
          excludeOptions: [ 'split_documents' ]
          onlyOptions: [ 'lang' ]
      ).to.throw()

    it 'should throw when trying to exclude a non-option', ->
      expect(->
        new Options {},
          supportedLanguages: [{code:'en',name:'English'}]
          defaultLanguageCode: 'en'
          excludeOptions: [ 'splitdocuments' ]
      ).to.throw()

    it 'should throw when trying to include a non-option', ->
      expect(->
        new Options {},
          supportedLanguages: [{code:'en',name:'English'}]
          defaultLanguageCode: 'en'
          onlyOptions: [ 'splitdocuments' ]
      ).to.throw()

    describe 'with excludeOptions', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        excludeOptions: [ 'split_documents' ]

      beforeEach ->
        subject = new Options({}, ctorOptions)

      it 'should define an included option', -> expect(subject.has('lang')).to.be(true)
      it 'should not define an excluded option', -> expect(subject.has('split_documents')).to.be(false)

    describe 'with onlyOptions', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        onlyOptions: [ 'split_documents' ]

      beforeEach -> subject = new Options({}, ctorOptions)
      it 'should define an included option', -> expect(subject.has('split_documents')).to.be(true)
      it 'should not define an excluded option', -> expect(subject.has('lang')).to.be(false)

    describe 'with all options', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'

      beforeEach ->
        subject = new Options({}, ctorOptions)

      describe 'supportedLanguages', ->
        it 'should be the list of supported languages', -> expect(subject.supportedLanguages).to.eq(ctorOptions.supportedLanguages)

      describe 'name', ->
        it 'should begin empty', -> expect(subject.get('name')).to.be('')

      describe 'tree_title', ->
        it 'should begin empty', -> expect(subject.get('tree_title')).to.be('')

      describe 'tag_id', ->
        it 'should begin as the empty string', -> expect(subject.get('tag_id')).to.be('')

      describe 'split_documents', ->
        it 'should begin false', -> expect(subject.get('split_documents')).to.be(false)

      describe 'lang', ->
        it 'should begin as defaultLanguageCode', -> expect(subject.get('lang')).to.eq('en')

      describe 'supplied_stop_words', ->
        it 'should begin as empty string', -> expect(subject.get('supplied_stop_words')).to.eq('')

      describe 'important_words', ->
        it 'should begin as empty string', -> expect(subject.get('important_words')).to.eq('')
