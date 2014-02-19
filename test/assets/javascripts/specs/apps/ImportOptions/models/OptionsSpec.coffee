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
      ).toThrow()

    it 'should throw when trying to exclude a non-option', ->
      expect(->
        new Options {},
          supportedLanguages: [{code:'en',name:'English'}]
          defaultLanguageCode: 'en'
          excludeOptions: [ 'splitdocuments' ]
      ).toThrow()

    it 'should throw when trying to include a non-option', ->
      expect(->
        new Options {},
          supportedLanguages: [{code:'en',name:'English'}]
          defaultLanguageCode: 'en'
          onlyOptions: [ 'splitdocuments' ]
      ).toThrow()

    describe 'with excludeOptions', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        excludeOptions: [ 'split_documents' ]

      beforeEach ->
        subject = new Options({}, ctorOptions)

      it 'should define an included option', -> expect(subject.has('lang')).toBe(true)
      it 'should not define an excluded option', -> expect(subject.has('split_documents')).toBe(false)

    describe 'with onlyOptions', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        onlyOptions: [ 'split_documents' ]

      beforeEach -> subject = new Options({}, ctorOptions)
      it 'should define an included option', -> expect(subject.has('split_documents')).toBe(true)
      it 'should not define an excluded option', -> expect(subject.has('lang')).toBe(false)

    describe 'with all options', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'

      beforeEach ->
        subject = new Options({}, ctorOptions)

      describe 'supportedLanguages', ->
        it 'should be the list of supported languages', -> expect(subject.supportedLanguages).toEqual(ctorOptions.supportedLanguages)

      describe 'name', ->
        it 'should begin empty', -> expect(subject.get('name')).toBe('')

      describe 'tree_title', ->
        it 'should begin empty', -> expect(subject.get('tree_title')).toBe('')

      describe 'tag_id', ->
        it 'should begin as the empty string', -> expect(subject.get('tag_id')).toBe('')

      describe 'split_documents', ->
        it 'should begin false', -> expect(subject.get('split_documents')).toBe(false)

      describe 'lang', ->
        it 'should begin as defaultLanguageCode', -> expect(subject.get('lang')).toEqual('en')

      describe 'supplied_stop_words', ->
        it 'should begin as empty string', -> expect(subject.get('supplied_stop_words')).toEqual('')

      describe 'important_words', ->
        it 'should begin as empty string', -> expect(subject.get('important_words')).toEqual('')
