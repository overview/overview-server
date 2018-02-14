#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users find patterns in documents
# We provide a clustering feature called "Tree"
describe 'Search' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  describe 'with document1.csv' do
    before do
      page.create_document_set_from_csv('files/search-spec/documents1.csv')
    end

    it 'should filter documents by search term' do
      page.search_for_q('word')
      page.assert_selector('#document-list-title h3', text: '2 documents')
      page.assert_selector('#document-list h3', text: 'First')
      page.assert_selector('#document-list h3', text: 'Second')
      page.assert_no_selector('#document-list h3', text: 'Third')
    end

    it 'should highlight snippets' do
      page.search_for_q('word')
      page.assert_selector('#document-list p.snippets', match: :first)
      html = page.find('#document-list p.snippets', match: :first)[:innerHTML]
      assert_equal('This is <em>word</em> one and then <em>word</em> two and then <em>word</em> three', html)
    end

    it 'should highlight in text mode' do
      page.search_for_q('word')
      page.open_document_in_list_with_name('First')
      page.find('.document-display-preferences a', text: 'Text').click

      html = page.find('#document-current pre', text: 'This is word one', wait: WAIT_LOAD)[:innerHTML] # wait for text to load
      assert_match(
        /\AThis is <em[^>]*>word<\/em> one and then <em[^>]*>word<\/em> two and then <em[^>]*>word<\/em> three\Z/,
        html.gsub(/<!--[^-]*-->/, '')
      )
    end

    it 'should scroll through highlights in text mode' do
      page.search_for_q('word')
      page.open_document_in_list_with_name('First')

      # <pre> with text results and <div> with find HTML
      pre = page.find('#document-current pre', text: 'This is word one', wait: WAIT_LOAD) # wait for text+highlights to load
      find_bar = page.find('#document-current .find')

      # No "wait" clauses in the rest of this test: implementation is in Svelte,
      # which redraws synchronously.

      # starts on "1 of 3"
      find_bar.assert_selector('.label', text: 'Highlighting match 1 of 3 for “word”')
      assert_equal('current', pre.all('em').at(0)[:className])
      assert_equal('', pre.all('em').at(1)[:className])

      # moves to "2 of 3"
      find_bar.click_button('Highlight next match')
      find_bar.assert_selector('.label', text: '2 of 3')
      assert_equal('current', pre.all('em').at(1)[:className])
      assert_equal('', pre.all('em').at(0)[:className])

      # moves to "3 of 3"
      find_bar.click_button('Highlight next match')
      find_bar.assert_selector('.label', text: '3 of 3')
      assert_equal('current', pre.all('em').at(2)[:className])

      # wraps forward: 3 -> 1
      find_bar.click_button('Highlight next match')
      find_bar.assert_selector('.label', text: '1 of 3')

      # wraps backwards: 1 -> 3
      find_bar.click_button('Highlight previous match')
      find_bar.assert_selector('.label', text: '3 of 3')

      # moves backwards, to "2 of 3"
      find_bar.click_button('Highlight previous match')
      find_bar.assert_selector('.label', text: '2 of 3')
    end

    it 'should regex search' do
      page.search_for_q('text:/ .çi)/ AND title:Third')
      page.assert_selector('#document-list-title h3', text: 'one document')
      page.assert_selector('#document-list h3', text: 'Third')
    end

    it 'should warn on invalid regex' do
      page.search_for_q('text:/(foo/')
      page.assert_selector('#document-list .warnings', text: /Overview ignored your regular expression, “\(foo”, because of a syntax error: missing closing \)\./)
    end

    it 'should warn when nesting a regex' do
      page.search_for_q('"and" OR /\\bth.*/')
      page.assert_selector('#document-list .warnings', text: /Overview assumed all documents match your regular expression, “\\bth\.\*”, because the surrounding search is too complex\. Rewrite your search so the regular expression is outside any OR or NOT\(AND\(\.\.\.\)\) clauses\./)
    end
  end

  it 'should search within a PDF' do
    page.create_document_set_from_pdfs_in_folder('files/search-spec/pdfs')
    page.search_for_q('tail behind the couch')
    page.open_document_in_list_with_name('Cat0.pdf')

    page.assert_selector('#document-contents[src="/pdf-viewer"]', wait: WAIT_FAST) # wait for PDF to start loading
    page.within_frame('document-contents') do
      # Wait for page to load and search to execute
      page.assert_selector('.textLayer .highlight', text: 'tail', wait: WAIT_LOAD)

      # It would be nice if PDFJS would highlight the entire string,
      # "tail behind the couch". But our search is far from perfect: AND and OR
      # clauses don't actually analyze the text, for instance. So we check
      # "Highlight all", and each word gets highlighted individually.
      #
      # This behavior finds non-matches. In a sense that's _more_ broken than
      # trying to highlight an exact match. But in reality, this is closer to
      # what users want.
      #
      # Strategy to consider in the future: request highlights from Lucene
      # (which we already do in text mode), and then somehow OR-search for
      # the exact matches Lucene returns. This would require hacking PDFJS,
      # and it wouldn't work when spans cross page boundaries. But it would
      # be an improvement.
      #
      # For now, just test that all the words are selected.
      page.assert_selector('.textLayer .highlight', text: 'behind')
      page.assert_selector('.textLayer .highlight', text: 'the')
      page.assert_selector('.textLayer .highlight', text: 'couch')

      # Assert the find bar is open
      assert_equal('tail behind the couch', page.find('#findInput')[:value])
    end
  end

  it 'should warn when a prefix search has a truncated term list' do
    page.create_document_set_from_csv('files/search-spec/manyTermsWithSamePrefix.csv')
    page.search_for_q('phrase OR (phrase foo*)')
    page.assert_selector('#document-list .warnings', text: /This list may be incomplete. “text:foo\*” matched too many words from your document set; we limited our search to 1,000 words\./)
  end
end
