#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users find patterns in documents
# They need to upload their documents
# And one-row-per-document CSV is one way to do that
describe 'CSV Upload' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.click_link('Import from a CSV file')
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  def choose_file(basename)
    page.attach_file('Select a CSV file from your computer:', "/app/files/csv-upload-spec/#{basename}")
  end

  def choose_encoding(shortname)
    name = case shortname
    when 'windows-1252' then 'Western (Windows-1252, ISO-8859-1)'
    when 'utf8' then 'Unicode (UTF-8)'
    else throw Exception.new("Invalid encoding key #{shortname}")
    end
    page.select(name, from: 'Specify an encoding:')
  end

  it 'should load UTF-8' do
    choose_file('basic-utf8.csv')
    page.assert_selector('.preview table', text: 'achète avec des €')
  end

  it 'should load Windows-1252' do
    choose_file('basic-windows-1252.csv')
    page.assert_selector('.requirements li.text.bad', text: 'UTF-8')
    page.assert_selector('.preview table', text: 'ach�te avec des �')

    choose_encoding('windows-1252')
    page.assert_no_selector('.requirements li.text.bad')
    page.assert_selector('.preview table', text: 'achète avec des €')
  end

  it 'should reset the form, including encoding' do
    choose_file('basic-windows-1252.csv')
    choose_encoding('windows-1252')
    page.click_button('Reset')

    page.assert_no_selector('.requirements li.bad, .requirements li.good')
    choose_file('basic-utf8.csv')
    page.assert_selector('.preview table', text: 'achète avec des €')
  end

  it 'should show an error when there is no text column' do
    choose_file('basic-no-text.csv')
    page.assert_selector('li.header.bad', text: 'One column must be named “text”')
  end

  describe 'after uploading a document set' do
    before do
      choose_file('basic.csv')
      page.click_button('Upload', match: :first)
      # wait for:
      # 1. Worker to process document
      # 2. Browser to redirect to Show page
      # 3. Document list to load
      page.assert_selector('#document-list-title h3', text: 'Found 7 documents', wait: WAIT_SLOW)
    end

    it 'should show the document set' do
      page.assert_selector('h2', text: 'basic.csv')
      page.assert_selector('#document-list li.document h3', text: 'Second')
    end

    it 'should import text' do
      page.find('#document-list h3', text: 'Second').click
      page.assert_selector('#document-current pre', text: 'This is the second document', wait: WAIT_LOAD) # wait for text to load
    end
  end
end
