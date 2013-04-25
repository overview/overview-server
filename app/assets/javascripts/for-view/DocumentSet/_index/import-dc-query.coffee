define [ 'jquery', 'dcimport/import_project_with_login' ], ($, import_project_with_login) ->
  $ ->
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
      dcimport_div = document.getElementById('import-from-documentcloud-account')
      import_project_with_login(dcimport_div)

    if $('#import-from-documentcloud-account').is('.active')
      $('button.toggle-import').one('click', show)
    else
      $('a[data-toggle=tab][href="#import-from-documentcloud-account"]').one('show', show)

    $('div #import-from-documentcloud-account').on 'change click', 'form.update input[type=checkbox]', (e) ->
      $checkbox = $(e.currentTarget)
      $splitDocumentInputs = $('input[type=hidden][name=split_documents]')
      $splitDocumentInputs.each (i, input) ->
        $input = $(input)
        if ($checkbox.is(":checked"))
          $input.val('true')
        else
          $input.val('false')