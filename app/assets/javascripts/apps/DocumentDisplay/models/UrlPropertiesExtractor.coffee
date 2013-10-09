define [], ->
  Extractors = [
    {
      id: 'twitter'
      name: 'Twitter tweet'
      regex: /// ^(?:https?:)?//(?:www\.)?twitter\.com/[\#!/]*([a-zA-Z0-9_]{1,15})/status(?:es)?/(\d+) ///
      capture: [ 'username', 'id' ]
      url: (o) -> "//twitter.com/#{o.username}/status/#{o.id}"
    }
    {
      id: 'facebook'
      name: 'Facebook object'
      # These aren't just posts: they can be anything
      regex: /// ^(?:https?:)?//(?:www\.)?facebook\.com/(.+) ///
      capture: [ 'path' ]
      url: (o) -> "//www.facebook.com/#{o.path}"
    }
    {
      id: 'documentCloud'
      name: 'DocumentCloud document'
      regex: /// ^(?:https?:)?//(?:www\.)?documentcloud\.org/documents/([-a-zA-Z0-9]+)(\#p[0-9]+)? ///
      capture: [ 'id', 'page' ]
      url: (o) -> "https://www.documentcloud.org/documents/#{o.id}.html"
    }
    {
      id: 'localPDF'
      name: 'Local PDF document'
      regex: /// ^(/documents/\d+/pdf-download)$ ///
      capture: [ 'uri' ]
      url: (o) -> o.uri
    }
    {
      id: 'secure'
      name: 'Secure web page'
      regex: /// ^(https://.*) ///
      capture: [ 'rawUrl' ]
      url: (o) -> o.rawUrl
    }
    {
      id: 'insecure'
      name: 'Insecure web page'
      regex: /// ^(http://.*) ///
      capture: [ 'rawUrl' ]
      url: (o) -> o.rawUrl
    }
    {
      id: 'unknown'
      name: 'Unknown'
      regex: /// ^(.+) ///
      capture: [ 'rawUrl' ]
      url: (o) -> o.rawUrl
    }
    {
      id: 'none'
      name: 'None'
      regex: /// ^$ ///
      capture: []
      url: (o) -> ''
    }
  ]

  # Returns some URL-derived properties, given a URL
  #
  # For instance:
  #
  #   UrlPropertiesExtractor.urlToProperties("https://twitter.com/adamhooper/status/1231241324")
  #   ==> {
  #     type: 'twitter',
  #     typeName: 'Twitter tweet',
  #     username: 'adamhooper',
  #     id: '1231241324',
  #     url: <better URL>
  #   }
  #
  # The returned URL is "better": it replaces http:// or https:// with plain //.
  {
    urlToProperties: (url) ->
      ret = undefined

      for extractor in Extractors
        if m = url.match(extractor.regex)
          ret = { type: extractor.id, typeName: extractor.name }
          for name, i in extractor.capture
            ret[name] = m[i + 1]
          ret.url = extractor.url(ret)
          break

      ret
  }
