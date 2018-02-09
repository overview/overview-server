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

  def POST(path, json)
    req('POST', path, json: json)
  end

  def PUT(path, json)
    req('PUT', path, json: json)
  end

  def PATCH(path, json)
    req('PATCH', path, json: json)
  end

  private

  def req(method, path, json: nil)
    full_path = "/api/v1#{path}"
    uri = URI("#{@base_url}/#{full_path}")

    data = nil
    headers = {
      'Authorization': "Basic #{Base64.encode64(@api_token + ':x-auth-token')}",
      'Accept': 'application/json',
    }

    if json
      data = JSON.dump(json)
      headers['Content-Type'] = 'application/json'
    end

    Net::HTTP.start(uri.host, uri.port) do |http|
      http.send_request(method, full_path, data, headers)
    end
  end
end
