define [ 'jquery', 'dcimport/import_project_with_login' ], ($, import_project_with_login) ->
  $ ->
    $dcimportDiv = $('#import-from-documentcloud-account .import-pane-contents')

    $dcquery_submit = $('#import-from-documentcloud-query button')
    refresh_dcquery_submit = () ->
      $form = $dcquery_submit.closest('form')
      title = $form.find('input[name=title]').val() || ''
      query = $form.find('input[name=query]').val() || ''
      $button_span = $dcquery_submit.find('span.query')
      $button_span.text(query)
      if title && query
        $dcquery_submit.removeClass('disabled')
      else
        $dcquery_submit.addClass('disabled')

    $dcquery_submit.closest('form').on('change keyup cut paste blur', 'input', refresh_dcquery_submit)
    refresh_dcquery_submit()

    show = ->
      import_project_with_login($dcimportDiv[0])

    $('#import-from-documentcloud-account').one('activate', show)

    $dcimportDiv.on 'change click', 'form.update', (e) ->
      splitDocuments = $(e.currentTarget).find('[name=split_documents]').is(':checked')
      $('input[type=hidden][name=split_documents]').val(splitDocuments && 'true' || 'false')

      lang = $(e.currentTarget).find('[name=lang]').val()
      $('input[type=hidden][name=lang]').val(lang)
