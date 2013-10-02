define [ 'jquery', 'underscore', 'i18n' ], ($, _, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet._share.#{key}", args...)

  EMAIL_REGEX = /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/ # http://www.w3.org/TR/html-markup/datatypes.html#form.data.emailaddress

  loading_html = _.template('<p><%- message %></p>', { message: t('loading') })
  error_html = _.template('<p class="errpr"><%- message %></p>', { message: t('error') })
  viewer_li_template = _.template("""
    <li data-email="<%- viewer.email %>">
      <%- viewer.email %><a class="remove" href="<%- remove_url_pattern.replace('{0}', encodeURIComponent(viewer.email)) %>"><%- t('remove') %></a>
    </li>
  """)
  sharing_dialog_template =  _.template("""
    <div id="manage-user-roles">
      <p class="list-header"><%- t('list_header', viewers.length) %></p>
        
      <ul class="unstyled collaborators" remove_url_pattern="<%- remove_url_pattern %>">
        <% _.each(viewers, function(viewer) { %>
          <%= viewer_li_template({ t: t, viewer: viewer, remove_url_pattern: remove_url_pattern }) %>
        <% }) %>
      </ul>
    </div>
    <p><%- t('explanation') %></p>
    <form method="post" class="input-append add-viewer" action="<%- create_url %>">
      <%= window.csrfTokenHtml %>
      <input type="hidden" name="role" value="Viewer" />
      <input type="email" class="span2" placeholder="<%- t('email_placeholder') %>" name="email" />
      <button type="submit" class="btn add-viewer-button" disabled="true"><i class="overview-icon-plus"/> <%- t('add') %></button>
    </form>
    <% if (is_admin) { %>    
      <form method="post" class="update form-inline" action="<%- update_url %>">
        <%= window.csrfTokenHtml %>
        <input id="set-as-example-document-set" type="checkbox" name="public" value="true"<%= is_public && ' checked="checked"' || '' %> />
        <input type="hidden" name="public" value="false" />
        <label for="set-as-example-document-set">
          <%- t('example_document_set.checkbox_label') %>
        </label>
      </form>
    <% } %>
  """)

  # Sets the modal dialog's HTML to reflect the given viewers.
  #
  # Each viewer looks like: { email: 'user@example.org' }
  set_viewers = (viewers) ->

  show_sharing_settings = (url, create_url, remove_url_pattern, update_url, is_admin, is_public) ->
    $modal = $('#sharing-options-modal')
    $modal.modal('show')

    set_viewers = (viewers) ->
      html = sharing_dialog_template({
        viewers: _.sortBy(viewers, 'email')
        viewer_li_template: viewer_li_template
        create_url: create_url
        remove_url_pattern: remove_url_pattern
        update_url: update_url
        is_admin: is_admin
        is_public: is_public
        t: t
      })
      $modal.find('.modal-body').html(html)
      $modal.find('input[name=email]').focus()
      undefined

    $.getJSON(url)
      .success((data) -> set_viewers(data.viewers))
      .error(-> $modal.find('.modal-body').html(error_html))

  get_email_list_from_dom = () ->
    $('#sharing-options-modal li').map(-> this.getAttribute('data-email')).get()

  add_email_to_list = (new_email, emails) ->
    emails.push new_email
    refresh_email_list(emails)

  remove_email_from_list = (email_to_remove, emails) ->
    emails = emails.filter((email) -> email != email_to_remove)
    refresh_email_list(emails)

  refresh_email_list = (emails) ->
    viewers = _.map(emails, (email) -> { email: email })
    set_viewers(viewers)

  $ ->
    $('#sharing-options-modal').on 'shown', ->
      $('input[name=email]', this).focus()

    $('#sharing-options-modal').one 'show', ->
      $('#sharing-options-modal').on 'submit', 'form.add-viewer', (e) ->
        e.preventDefault()
        data = $(e.currentTarget).serialize()
        url = $(e.currentTarget).attr('action')

        emails = get_email_list_from_dom()

        $email =  $('#sharing-options-modal input[name=email]')
        new_email = $email.val()
        $email.val("")

        if (new_email && new_email not in emails)
          add_email_to_list(new_email, emails)
          $.ajax({
            url: url
            type: 'POST'
            data: data
          })

      $('#sharing-options-modal').on 'click', 'a.remove', (e) ->
        e.preventDefault()
        url = $(e.currentTarget).attr('href')
        email = $(e.currentTarget).closest('li').attr('data-email')
        emails = get_email_list_from_dom()
        remove_email_from_list(email, emails)

        $.ajax({
          url: url
          type: 'DELETE'
          data: window.csrfTokenData || {}
        })

      $('#sharing-options-modal').on 'input', (e) ->
        text = $('input[name=email]').val()
        $add_viewer_button = $('.add-viewer-button')
  
        if EMAIL_REGEX.test(text)
          $add_viewer_button.removeAttr('disabled')
        else
          $add_viewer_button.attr('disabled', 'true')

      $('#sharing-options-modal').on 'change click', 'form.update input[type=checkbox]', (e) ->
        $checkbox = $(e.currentTarget)
        $checkbox.closest('form').submit()

      $('#sharing-options-modal').on 'submit', 'form.update', (e) ->
        $form = $(e.currentTarget)
        data = $form.serialize()
        old_data = $form.data('last-data')
        if data != old_data
          $form.data('last-data', data)
          request = $.ajax({
            type: 'PUT'
            url: $form.attr('action')
            data: data
          })
          request.done ->
            $share_button = $('div.document-sets a.show-sharing-settings')
            is_public = data != 'public=false'
            $share_button.attr('is-public', is_public)
  
        e.preventDefault()

    $('div.document-sets').on 'click', 'a.show-sharing-settings', (e) ->
      e.preventDefault()
      $share = $(e.currentTarget)
      url = $share.attr('href')
      create_url = $share.attr('data-create-url')
      remove_url_pattern = $share.attr('data-delete-url-pattern')
      admin = $share.attr('data-is-admin')
      is_public = $share.attr('data-is-public')
      update_url = $share.attr('data-update-url')
      show_sharing_settings(url, create_url, remove_url_pattern, update_url, admin == 'true', is_public == 'true')
