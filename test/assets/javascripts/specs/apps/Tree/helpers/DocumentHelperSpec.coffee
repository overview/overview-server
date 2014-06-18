define [
  'apps/Tree/helpers/DocumentHelper'
  'i18n'
], (subject, i18n) ->
  describe 'apps/Tree/helpers/DocumentHelper', ->
    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.helpers.DocumentHelper.title': 'title,{0}'
        'views.Tree.show.helpers.DocumentHelper.title.empty': 'title.empty'
        'views.Tree.show.helpers.DocumentHelper.title.page': 'title.page,{0},{1}'

    it 'should give a title for a document', ->
      expect(subject.title(title: 'foo')).to.eq('title,foo')

    it 'should give a placeholder title for a no-title document', ->
      expect(subject.title(title: '')).to.eq('title.empty')

    it 'should give a page title for a page document', ->
      expect(subject.title(title: 'foo', pageNumber: 51)).to.eq('title.page,title,foo,51')

    it 'should give a page title for a no-title page document', ->
      expect(subject.title(title: '', pageNumber: 51)).to.eq('title.page,title.empty,51')

    it 'should give a placeholder title for a nonexistent document', ->
      expect(subject.title(null)).to.eq('title.empty')
