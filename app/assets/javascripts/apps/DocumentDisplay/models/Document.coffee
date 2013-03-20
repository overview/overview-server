define [ 'backbone' ], (Backbone) ->
  Backbone.Model.extend {
    defaults: {
      heading: '' # Text, chosen by our server
      documentCloudUrl: undefined # URL on a DocumentCloud server
      suppliedUrl: undefined # Source URL
      secureSuppliedUrl: undefined # Source URL that we can display securely (https)
      twitterTweet: undefined # Data from Twitter: { text, username, url }
      text: '' # Text, if it's not a DocumentCloud document
    }

    initialize: ->
      type = if @get('documentCloudUrl')
        'DocumentCloudDocument'
      else if @get('twitterTweet')
        'TwitterTweet'
      else if @get('secureSuppliedUrl')
        'SecureCsvImportDocument'
      else
        'CsvImportDocument'

      @set('type', type)
  }
