
loading_template = _.template("""<p>Loading sharing settings.""")
sharing_dialog_template =  _.template("""
    <div class="well" id="manage-user-roles">
      <ul class="unstyled collaborators" remove_url_pattern="<%- remove_url_pattern %>">
        <% _.each(viewers, function(viewer) { %> <li class="email">
          <%- viewer.email %>
          <a class="remove-viewer" href="<%- remove_url_pattern.replace('{0}', encodeURIComponent(viewer.email)) %>" email="<%- viewer.email %>">&times;</a>
        </li> <% }); %>
      </ul>
    </div>
    <form method="post" class="form-inline add-viewer" action="<%- create_url %>" >
      <input type="email" class="input-small" placeholder="Email" name="email" />
      <input type="hidden" name="role" value="Viewer" />
      <input type="submit" value="Add Viewer" disabled="true"/>
    </form>
    <% if (admin == 'true') { %>    
    <form method="post" class="update form-inline" action="<%- update_url %>">
      <label>
        <input type="checkbox" name="public" value="true" <% if (is_public == 'true') { %>
          checked="checked"
        <% } %> />
        <input type="hidden" name="public" value="false" />
        Set as Example Document Set
      </label>
    </form>
    <% } %>
        """)

sort_by_email = (viewers) ->
  viewers.sort (a, b) ->
    return if (a.email >= b.email) then 1 else -1
  

show_sharing_settings = (url, create_url, remove_url_pattern, update_url, admin, is_public) ->
  $modal = $('#sharing-options-modal')
  $modal.modal('show')
  $modal.find('.modal-body').html(loading_template)
  $.getJSON(url)
    .success((emails) ->
      $modal.find('.modal-body').html(sharing_dialog_template({ viewers: sort_by_email(emails.viewers), create_url: create_url, remove_url_pattern: remove_url_pattern, update_url: update_url, admin: admin, is_public: is_public })))
    .error((a, b, c) ->
      console.log("error #{b}"))

add_email_to_list = (new_email, emails) ->
  emails.push new_email
  refresh_email_list(emails)
    
remove_email_from_list = (email_to_remove, emails) ->
  remaining_emails = emails.filter (email) -> email isnt email_to_remove
  refresh_email_list(remaining_emails)

refresh_email_list = (emails) ->
  $collaborators = $('ul.collaborators')
  $collaborators.empty()
  remove_url_pattern = $collaborators.attr('remove_url_pattern')
  for email in emails.sort()
    remove_url = remove_url_pattern.replace('{0}', encodeURIComponent(email))
    $email_item = $('<li/>').addClass('email').text(email)
    $remove_button = $('<a/>')
      .addClass('remove-viewer')
      .attr('href', remove_url)
      .attr('email', email)
      .text('\u00d7')
    $email_item.append($remove_button)
    $collaborators.append($email_item)

$ ->
  $('#sharing-options-modal').one 'show', ->
    $('#sharing-options-modal').on 'submit', 'form.add-viewer', (e) ->
      e.preventDefault()
      data = $(e.currentTarget).serialize()
      url = $(e.currentTarget).attr('action')

      emails = $('li.email a').map(-> $(this).attr('email')).get()

      $email =  $('input[name=email]')
      new_email = $email.val()
      $email.val("")

      if (new_email != '' and new_email not in emails)
        add_email_to_list(new_email, emails)
        $.ajax({
          url: url
          type: 'POST'
          data: data
          })

    $('#sharing-options-modal').on 'click', 'div ul li a', (e) ->
      e.preventDefault()
      url = $(e.currentTarget).attr('href')
      email = $(e.currentTarget).attr('email')
      emails = $('li.email a').map(-> $(this).attr('email')).get()
      remove_email_from_list(email, emails)

      $.ajax({
        url: url
        type: 'DELETE'
        })


    $('#sharing-options-modal').keyup (e) ->
      text = $('input[name=email]').val()
      $add_viewer_button = $('input[type=submit]')

      if (text == '')
        $add_viewer_button.attr('disabled', 'true')
      else
        $add_viewer_button.removeAttr('disabled')

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
    admin = $share.attr('admin')
    is_public = $share.attr('is-public')
    update_url = $share.attr('data-update-url')
    show_sharing_settings(url, create_url, remove_url_pattern, update_url, admin, is_public)



