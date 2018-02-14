#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users analyze documents
# We need to help them upload documents
# Sometimes overriding Overview's user interface by using scripts
describe 'File-upload API' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)

    # Create "@global_api" API browser for the user with no document set
    page.visit('/api-tokens')
    page.fill_in('App name', with: 'FileUploadApiSpec', wait: WAIT_LOAD) # wait for form to load
    page.click_button('Generate token')
    global_token = page.find('td.token', wait: WAIT_LOAD).text # wait for AJAX request to complete
    @global_api = ApiBrowser.new(base_url: OVERVIEW_URL, api_token: global_token)

    # Create @document_set_id and @api API browser for a new document set
    global_res = @global_api.POST('/document-sets', {
      title: 'FileUploadApiSpec',
      metadataSchema: { version: 1, fields: [
        { name: 'foo', type: 'String' },
        { name: 'bar', type: 'String' }
      ]}
    })
    json = JSON.load(global_res.body)
    @document_set_id = json['documentSet']['id']
    api_token = json['apiToken']['token']
    @api = ApiBrowser.new(base_url: OVERVIEW_URL, api_token: api_token)
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  def wait_and_get_documents
    t1 = Time.now

    loop do
      if Time.now - t1 > WAIT_SLOW
        throw Exception.new("Waited #{WAIT_SLOW}s and API never returned documents")
      end

      res = @api.GET("/document-sets/#{@document_set_id}/documents?fields=title,metadata&refresh=true")
      documents = JSON.load(res.body)

      return documents if documents['items'].length > 0
    end
  end

  it 'should add metadata in POST /files/finish' do
    res = @api.POST_raw('/files/11111111-1111-1111-1111-111111111111', 'Hello, world!', extra_headers: {
      'Content-Disposition' => 'attachment; filename=file1.txt',
      'Content-Type' => 'text/plain',
      'Content-Length' => '13',
    })
    assert_instance_of(Net::HTTPCreated, res)

    res = @api.POST('/files/finish', {
      lang: 'en',
      metadata_json: '{"foo":"bar"}',
    })
    assert_instance_of(Net::HTTPCreated, res)

    documents = wait_and_get_documents

    assert_equal('file1.txt', documents['items'][0]['title'])
    assert_equal(
      { 'foo' => 'bar', 'bar' => '' },
      documents['items'][0]['metadata']
    )
  end

  it 'should add metadata in POST /files/:uuid, overriding POST /files/finish' do
    res = @api.POST_raw('/files/11111111-1111-1111-1111-111111111111', 'Hello, world!', extra_headers: {
      'Content-Disposition' => 'attachment; filename=file1.txt',
      'Content-Type' => 'text/plain',
      'Content-Length' => '13',
      'Overview-Document-Metadata-JSON': '{"foo":"baz"}',
    })
    assert_instance_of(Net::HTTPCreated, res)

    res = @api.POST('/files/finish', {
      lang: 'en',
      metadata_json: '{"foo":"bar","bar":"baz"}',
    })
    assert_instance_of(Net::HTTPCreated, res)

    documents = wait_and_get_documents
    assert_equal(
      { 'foo' => 'baz', 'bar' => '' },
      documents['items'][0]['metadata']
    )
  end
end
