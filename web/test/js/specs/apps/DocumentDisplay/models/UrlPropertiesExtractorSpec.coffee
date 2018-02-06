import UrlPropertiesExtractor from 'apps/DocumentDisplay/models/UrlPropertiesExtractor'

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
        expect(ret[property]).to.eq(value)

  describe 'urlToProperties', ->
    itShouldRecognize(
      'an https Twitter url',
      'https://twitter.com/adamhooper/status/317041719847813120',
        displayType: 'twitter'
        displayUrl: '//twitter.com/adamhooper/status/317041719847813120'
    )

    itShouldRecognize(
      'an http Twitter url',
      'http://twitter.com/adamhooper/status/317041719847813120',
        displayType: 'twitter'
    )

    itShouldRecognize(
      'a Twitter url with "/statuses/" as opposed to "/status/"',
      'https://twitter.com/adamhooper/statuses/317041719847813120',
        displayType: 'twitter'
        displayUrl: '//twitter.com/adamhooper/status/317041719847813120'
    )

    itShouldRecognize(
      'a // Twitter url',
      '//twitter.com/adamhooper/status/317041719847813120',
        displayType: 'twitter'
    )

    itShouldRecognize(
      'a www Twitter url',
      'https://www.twitter.com/adamhooper/status/317041719847813120',
        displayType: 'twitter'
    )

    itShouldRecognize(
      'an https Facebook url',
      'https://facebook.com/adam.hooper/posts/10101122388042297',
        displayType: 'facebook'
        displayUrl: '//www.facebook.com/adam.hooper/posts/10101122388042297'
    )

    itShouldRecognize(
      'an http Facebook url',
      'http://facebook.com/adam.hooper/posts/10101122388042297',
        displayType: 'facebook'
    )

    itShouldRecognize(
      'a // Facebook url',
      '//facebook.com/adam.hooper/posts/10101122388042297',
        displayType: 'facebook'
    )

    itShouldRecognize(
      'a www Facebook url',
      'https://www.facebook.com/adam.hooper/posts/10101122388042297',
        displayType: 'facebook'
    )

    itShouldRecognize(
      'an https DocumentCloud URL',
      'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html',
        displayType: 'documentCloud'
        displayUrl: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html'
    )

    itShouldRecognize(
      'an https DocumentCloud URL with a #p23 page',
      'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html#p23',
        displayType: 'documentCloud'
        displayUrl: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html#p23'
    )

    itShouldRecognize(
      'an http DocumentCloud URL (which DocumentCloud redirects to https)',
      'http://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html',
        displayType: 'documentCloud'
        displayUrl: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html'
    )

    itShouldRecognize(
      'a // DocumentCloud URL (which DocumentCloud redirects to https)',
      '//www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html',
        displayType: 'documentCloud'
        displayUrl: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principal.html'
    )

    itShouldRecognize(
      'a local PDF',
      '/documents/1234.pdf',
        displayType: 'pdf'
        displayUrl: '/documents/1234.pdf'
    )

    itShouldRecognize(
      'a local:// PDF',
      'local://foo/bar.pdf',
        displayType: 'pdf',
        displayUrl: '/localfiles/foo/bar.pdf',
    )

    itShouldRecognize(
      'a secure web page',
      'https://example.org',
        displayType: 'https'
        displayUrl: 'https://example.org'
    )

    itShouldRecognize(
      'an insecure web page',
      'http://example.org',
        displayType: 'http'
        displayUrl: 'http://example.org'
    )

    itShouldRecognize(
      'anything else',
      'abc123',
        displayType: 'unknown'
        displayUrl: 'abc123'
    )

    itShouldRecognize(
      'the empty string',
      '',
        displayType: null,
        displayUrl: null,
    )

    itShouldRecognize(
      'no url',
      undefined,
        displayType: null,
        displayUrl: null,
    )

    describe 'with a custom DocumentCloud URL', ->
      beforeEach ->
        extractor = new UrlPropertiesExtractor(documentCloudUrl: 'https://foo.bar')

      itShouldRecognize(
        'a custom DocumentCloud URL',
        'https://foo.bar/documents/123-foo.html',
          displayType: 'documentCloud'
          displayUrl: 'https://foo.bar/documents/123-foo.html'
      )
