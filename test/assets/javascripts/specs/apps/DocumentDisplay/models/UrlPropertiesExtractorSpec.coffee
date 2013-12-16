define [
  'apps/DocumentDisplay/models/UrlPropertiesExtractor'
], (UrlPropertiesExtractor) ->
  describe 'apps/DocumentDisplay/models/UrlPropertiesExtractor', ->
    extractor = undefined
    urlToProperties = undefined

    beforeEach ->
      extractor = new UrlPropertiesExtractor(documentCloudUrl: 'https://www.documentcloud.org')
      urlToProperties = (url) -> extractor.urlToProperties(url)

    itShouldRecognize = (name, inUrl, outProperties) ->
      it "should recognize #{name}", ->
        ret = urlToProperties(inUrl)
        for property, value of outProperties
          expect(ret[property]).toEqual(value)

    describe 'urlToProperties', ->
      itShouldRecognize(
        'an https Twitter url',
        'https://twitter.com/adamhooper/status/317041719847813120',
          type: 'twitter'
          url: '//twitter.com/adamhooper/status/317041719847813120'
      )

      itShouldRecognize(
        'username and id in a Twitter url',
        'https://twitter.com/adamhooper/status/317041719847813120',
          username: 'adamhooper'
          id: '317041719847813120'
      )

      itShouldRecognize(
        'an http Twitter url',
        'http://twitter.com/adamhooper/status/317041719847813120',
          type: 'twitter'
      )

      itShouldRecognize(
        'a Twitter url with "/statuses/" as opposed to "/status/"',
        'https://twitter.com/adamhooper/statuses/317041719847813120',
          type: 'twitter'
          url: '//twitter.com/adamhooper/status/317041719847813120'
      )

      itShouldRecognize(
        'a // Twitter url',
        '//twitter.com/adamhooper/status/317041719847813120',
          type: 'twitter'
      )

      itShouldRecognize(
        'a www Twitter url',
        'https://www.twitter.com/adamhooper/status/317041719847813120',
          type: 'twitter'
      )

      itShouldRecognize(
        'an https Facebook url',
        'https://facebook.com/adam.hooper/posts/10101122388042297',
          type: 'facebook'
          url: '//www.facebook.com/adam.hooper/posts/10101122388042297'
      )

      itShouldRecognize(
        'an http Facebook url',
        'http://facebook.com/adam.hooper/posts/10101122388042297',
          type: 'facebook'
      )

      itShouldRecognize(
        'a // Facebook url',
        '//facebook.com/adam.hooper/posts/10101122388042297',
          type: 'facebook'
      )

      itShouldRecognize(
        'a www Facebook url',
        'https://www.facebook.com/adam.hooper/posts/10101122388042297',
          type: 'facebook'
      )

      itShouldRecognize(
        'an https DocumentCloud URL',
        'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html',
          type: 'documentCloud'
          url: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html'
      )

      itShouldRecognize(
        'an http DocumentCloud URL (which DocumentCloud redirects to https)',
        'http://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html',
          type: 'documentCloud'
          url: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html'
      )

      itShouldRecognize(
        'a // DocumentCloud URL (which DocumentCloud redirects to https)',
        '//www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html',
          type: 'documentCloud'
          url: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html'
      )

      itShouldRecognize(
        'a local PDF',
        '/documents/1234/contents/4567',
          type: 'localObject'
          url: '/documents/1234/contents/4567'
      )

      itShouldRecognize(
        'a secure web page',
        'https://example.org',
          type: 'secure'
          url: 'https://example.org'
      )

      itShouldRecognize(
        'an insecure web page',
        'http://example.org',
          type: 'insecure'
          url: 'http://example.org'
      )

      itShouldRecognize(
        'anything else',
        'abc123',
          type: 'unknown'
          url: 'abc123'
      )

      itShouldRecognize(
        'the empty string',
        '',
          type: 'none'
          url: ''
      )

      itShouldRecognize(
        'no url',
        undefined,
          type: 'none'
          url: ''
      )


      describe 'with a custom DocumentCloud URL', ->
        beforeEach ->
          extractor = new UrlPropertiesExtractor(documentCloudUrl: 'https://foo.bar')

        itShouldRecognize(
          'a custom DocumentCloud URL',
          'https://foo.bar/documents/123-foo.html',
            type: 'documentCloud'
            url: 'https://foo.bar/documents/123-foo.html'
        )
