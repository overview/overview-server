asUser = require('../support/asUser-new')
shouldBehaveLikeATree = require('../support/behave/likeATree-new')

describe 'FileUpload', ->
  asUser.usingTemporaryUser ->
    before ->
      @browser
        .loadShortcuts('documentSets')
        .loadShortcuts('importFiles')

    describe 'after uploading files', ->
      before ->
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles([
            'FileUpload/Cat1.docx'
            'FileUpload/Cat2.txt'
            'FileUpload/Cat3.rtf'
            'FileUpload/Cat4.html'
            'FileUpload/Jules1.doc'
            #'FileUpload/Jules2.pptx' # XXX this triggers a Selenium bug: the upload fails
            'FileUpload/Jules3.xlsx'
          ])
          .shortcuts.importFiles.finish(name: 'FileUpload')

      after ->
        @browser
          .shortcuts.documentSets.destroy('FileUpload')

      shouldBehaveLikeATree
        documents: [
          { type: 'pdf', title: 'Cat1.docx' }
          { type: 'pdf', title: 'Cat2.txt' }
          { type: 'pdf', title: 'Cat3.rtf' }
          { type: 'pdf', title: 'Cat4.html' }
          { type: 'pdf', title: 'Jules1.doc' }
          #{ type: 'pdf', title: 'Jules2.pptx' }
          { type: 'pdf', title: 'Jules3.xlsx' }
        ]
        searches: [
          { query: 'chase', nResults: 4 }
        ]

    describe 'after splitting a file into pages', ->
      before ->
        @browser
          .shortcuts.importFiles.open()
          .shortcuts.importFiles.addFiles([ 'FileUpload/Cat1.docx' ])
          .shortcuts.importFiles.finish(name: 'Split FileUpload', splitByPage: true)

      after ->
        @browser
          .shortcuts.documentSets.destroy('Split FileUpload')

      shouldBehaveLikeATree
        documents: [
            { type: 'pdf', title: 'Cat1.docx – page 1' }
            { type: 'pdf', title: 'Cat1.docx – page 2' }
            { type: 'pdf', title: 'Cat1.docx – page 3' }
        ]
        searches: [
          { query: 'face', nResults: 3 }
        ]

    it 'should allow splitting >50 files', ->
      # https://www.pivotaltracker.com/story/show/81624750
      @browser
        .shortcuts.importFiles.open()
        .shortcuts.importFiles.addFiles("ManyFiles/file-#{n}.pdf" for n in [1..60])
        .shortcuts.importFiles.finish(name: 'Many FileUpload', splitByPage: true)
        .assertExists(tag: 'h3', contains: '120 documents')
        .shortcuts.documentSets.destroy('Many FileUpload')
