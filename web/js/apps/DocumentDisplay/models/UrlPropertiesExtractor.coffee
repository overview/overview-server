buildExtractors = (documentCloudUrl) =>
  documentCloudRegexStart = documentCloudUrl
    .replace(/^https?:/, '^(?:https?:)?')
    .replace('//www.', '//(?:www.)?')
    .replace('.', '\\.')

  [
    {
      displayType: 'twitter'
      regex: /// ^(?:https?:)?//(?:www\.)?twitter\.com/[\#!/]*([a-zA-Z0-9_]{1,15})/status(?:es)?/(\d+) ///
      capture: [ 'username', 'id' ]
      url: (o) -> "//twitter.com/#{o.username}/status/#{o.id}"
    }
    {
      displayType: 'facebook'
      # These aren't just posts: they can be anything
      regex: /// ^(?:https?:)?//(?:www\.)?facebook\.com/(.+) ///
      capture: [ 'path' ]
      url: (o) -> "//www.facebook.com/#{o.path}"
    }
    {
      displayType: 'documentCloud'
      regex: /// #{documentCloudRegexStart}/documents/([-a-zA-Z0-9]+)(?:\.html)?(\#p[0-9]+)? ///
      capture: [ 'id', 'page' ]
      url: (o) -> "#{documentCloudUrl}/documents/#{o.id}.html#{o.page || ''}"
    }
    {
      displayType: 'pdf'
      regex: /// ^/documents/(\d+).pdf$ ///
    },
    {
      displayType: 'pdf'
      regex: /// ^local://(.+) ///
      capture: [ 'fileName' ]
      url: (o) -> "/localfiles/#{o.fileName}"
    }
    {
      displayType: 'https'
      regex: /// ^https:// ///
    }
    {
      # http != https because we can't securely embed http:// iframes in https:// Overview
      displayType: 'http'
      regex: /// ^http:// ///
    }
    {
      displayType: 'unknown'
      regex: /// ^.+ ///
    }
  ]

export default class UrlPropertiesExtractor
  constructor: (@options) ->
    throw "must specify options.documentCloudUrl, an HTTPS prefix like 'https://www.documentcloud.org'" if !@options.documentCloudUrl
    @extractors = buildExtractors(@options.documentCloudUrl)

  # Returns `documentType` and `displayUrl` for a document.
  #
  # For instance:
  #
  #   extractor = new UrlPropertiesExtractor(documentCloudUrl: "https://www.documentcloud.org")
  #   extractor.urlToProperties("https://twitter.com/adamhooper/status/1231241324")
  #   ==> {
  #     displayType: 'twitter',
  #     displayUrl: <better URL>
  #   }
  #
  # The returned URL is "better": it replaces http:// or https:// with plain //.
  #
  # Return values will be all-null if there is no URL.
  urlToProperties: (url) ->
    for extractor in @extractors
      if m = extractor.regex.exec(url || '')
        ret = { displayType: extractor.displayType }

        ret.displayUrl = if extractor.url
          captures = {}
          for name, i in (extractor.capture || [])
            captures[name] = m[i + 1]
          extractor.url(captures)
        else
          url

        return ret

    # No match? That's because there's no URL
    return {
      displayType: null,
      displayUrl: null,
    }
