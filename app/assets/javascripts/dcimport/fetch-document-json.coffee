$ = jQuery
Deferred = $.Deferred

url_beginning_regex = /^https?:\/\/s3.documentcloud.org(.*)$/

replace_url_beginning = (url, url_beginning) ->
  url.replace(url_beginning_regex, "#{url_beginning}$1")

replace_url_beginnings = (obj, url_beginning) ->
  for k, v of obj
    if _.isString(v)
      obj[k] = replace_url_beginning(v, url_beginning)
    else if _.isObject(v)
      replace_url_beginnings(v, url_beginning)

dcimport.fetch_document_json = (documentcloud_id, prompt_div) ->
  ret = new Deferred()

  url = "https://www.documentcloud.org/api/documents/#{documentcloud_id}.json"
  deferred = dcimport.request_json_with_login(url, prompt_div)
  deferred.done (json) ->
    credentials = dcimport.get_credentials()
    url_beginning = "https://#{encodeURIComponent(credentials.email)}:#{encodeURIComponent(credentials.password)}@s3.documentcloud.org"

    replace_url_beginnings(json.resources, url_beginning)
    ret.resolve(json)

  ret
