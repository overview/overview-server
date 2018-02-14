require 'base64'
require 'json'
require 'net/http'

class ApiBrowser
  def initialize(base_url: nil, api_token: nil)
    @base_url = base_url
    @api_token = api_token
  end

  def HEAD(path)
    req('HEAD', path)
  end

  def GET(path)
    req('GET', path)
  end

  def POST(path, json, extra_headers: nil)
    req('POST', path, json: json, extra_headers: extra_headers)
  end

  def POST_raw(path, s, extra_headers: nil)
    req('POST', path, data: s, extra_headers: extra_headers)
  end

  def PUT(path, json, extra_headers: nil)
    req('PUT', path, json: json, extra_headers: extra_headers)
  end

  def PATCH(path, json, extra_headers: nil)
    req('PATCH', path, json: json, extra_headers: extra_headers)
  end

  private

  def req(method, path, data: nil, json: nil, extra_headers: nil)
    full_path = "/api/v1#{path}"
    uri = URI("#{@base_url}/#{full_path}")

    headers = {
      'Authorization': "Basic #{Base64.encode64(@api_token + ':x-auth-token').strip}",
      'Accept': 'application/json',
      'X-Requested-With': 'api_browser.rb',
    }.merge(extra_headers || {})

    if data.nil? && json
      data = JSON.dump(json)
      headers['Content-Type'] = 'application/json'
    end

    Net::HTTP.start(uri.host, uri.port) do |http|
      http.send_request(method, full_path, data, headers)
    end
  end
end
