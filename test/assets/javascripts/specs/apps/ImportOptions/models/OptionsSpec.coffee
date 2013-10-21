define [
  'apps/ImportOptions/models/Options'
], (Options) ->
  describe 'apps/ImportOptions/models/Options', ->
    subject = undefined

    describe 'with excludeOptions', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'
        excludeOptions: [ 'split_documents' ]

      beforeEach ->
        subject = new Options({}, ctorOptions)

      it 'should not have a defined attribute for an excluded option', ->
        expect('split_documents' of subject.attributes).toBe(false)

    describe 'with all options', ->
      ctorOptions =
        supportedLanguages: [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        defaultLanguageCode: 'en'

      beforeEach ->
        subject = new Options({}, ctorOptions)

      describe 'split_documents', ->
        it 'should begin false', ->
          expect(subject.get('split_documents')).toBe(false)

      describe 'lang', ->
        it 'should begin as defaultLanguageCode', ->
          expect(subject.get('lang')).toEqual('en')

      describe 'supplied_stop_words', ->
        it 'should begin as empty string', ->
          expect(subject.get('supplied_stop_words')).toEqual('')

      describe 'important_words', ->
        it 'should begin as empty string', ->
          expect(subject.get('important_words')).toEqual('')

      describe 'supportedLanguages', ->
        it 'should be the list of supported languages', ->
          expect(subject.supportedLanguages).toEqual(ctorOptions.supportedLanguages)
