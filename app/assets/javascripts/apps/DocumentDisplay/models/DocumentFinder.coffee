define [
  'jquery'
  'backbone'
  './Document'
  './UrlPropertiesExtractor'
], ($, Backbone, Document, UrlPropertiesExtractor) ->
  URL_BASE = "/documents"

  class DocumentFinder
    constructor: (@options) ->
      throw "Must set options.documentCloudUrl, a URL prefix like 'https://www.documentcloud.org'" if !@options.documentCloudUrl
      @urlPropertiesExtractor = new UrlPropertiesExtractor(@options)

    documentUrl: (id) -> "#{@options.documentCloudUrl}/documents/#{id}"

    findDocumentFromJson: (json) ->
      resolvedJson = if json.heading?
        $.Deferred().resolve(json)
      else if json.documentcloud_id
        $.Deferred().resolve
          heading: json.title || json.description
          url: @documentUrl(json.documentcloud_id)
      else
        $.getJSON("#{URL_BASE}/#{json.id}.json")

      resolvedJson.pipe (data) =>
        new Document($.extend(
          { urlProperties: @urlPropertiesExtractor.urlToProperties(data.url) },
          data
        ))
