define [ 'jquery', 'underscore', 'backbone', 'i18n' ], ($, _, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.tag_list.#{key}", args...)

  # Represents a list of tags
  #
  # Usage:
  #   tags = new Tags([], { tagStore: tagStore })
  #   view = new TagList({ collection: tags })
  #   $('body').append(view.el)
  #
  # The view will emit the following events:
  #
  # * add: user requested to add a new Tag (plain-old-data object with name/color)
  # * remove: user requested to remove a Tag
  # * update(tag, attrs): user requested to update a Tag (Backbone object) with
  #   new attributes.
  # * export: user requested to export the list of tags to CSV
  Backbone.View.extend
    className: 'vertical-tag-list'

    events:
      'click a.remove': '_onClickRemove'
      'submit li.new form': '_onSubmitNewTag'
      'submit li.existing form': '_onSubmitExistingTag'
      'change li.existing form': '_onChange'

    tagTemplate: _.template("""
      <li class="existing" data-cid="<%= tag.cid %>">
        <form method="post" action="#" class="form-horizontal">
          <input type="hidden" name="id" value="<%- tag.id || '' %>" />
          <input type="color" name="color" value="<%- tag.get('color') %>"
          /><input type="text" name="name" value="<%- tag.get('name') %>" />
          <div class="count"><%- t('n_documents', tagCount(tag)) %></div>
        </form>
        <a class="remove" href="#"><%- t('remove') %></a>
      </li>
    """)

    template: _.template("""
      <p class="preamble">
        <%- t('preamble') %>
        <% if (tags.length && exportUrl) { %>
          <a class="export" href="<%- exportUrl %>"><%- t('export') %></a>
        <% } %>
      </p>
      <ul class="unstyled">
        <%= tags.map(renderTag).join('') %>
        <li class="new">
          <form method="post" action="#" class="form-horizontal">
            <input type="text" name="name" required="required" placeholder="<%- t('tag_name.placeholder') %>" />
            <button type="submit" class="btn"><%- t('submit') %></button>
          </form>
        </li>
      </ul>
    """)

    initialize: ->
      throw 'must set options.collection' if !@options.collection
      throw 'must set options.tagToCount: (tag) -> Number' if !@options.tagToCount

      @listenTo(@collection, 'add', (tag) => @_addTag(tag))
      @listenTo(@collection, 'remove', (tag) => @_removeTag(tag))
      @listenTo(@collection, 'change', (tag, options) => @_changeTag(tag, options))
      @listenTo(@collection, 'reset', => @render())
      @render()

    render: ->
      html = @template({
        t: t
        tags: @collection
        exportUrl: @options.exportUrl
        renderTag: (tag) => @tagTemplate({ tag: tag, tagCount: @options.tagToCount, t: t })
      })
      @$el.html(html)
      this

    _$liForTag: (tag) ->
      @$("[data-cid=#{tag.cid}]")

    _addTag: (tag) ->
      html = @tagTemplate({
        t: t
        tag: tag
        tagCount: @options.tagToCount
      })

      index = @collection.indexOf(tag)
      @$("ul>li:eq(#{index})").before(html)

    _removeTag: (tag) ->
      @_$liForTag(tag).remove()

    _changeTag: (tag, options) ->
      $li = @_$liForTag(tag)
      $li.find('input[name=id]').val(tag.id)

      if !options? || !options.interacting
        $li.find('input[name=name]').val(tag.get('name'))
        $li.find('input[name=color]').val(tag.get('color'))

    _onClickRemove: (e) ->
      e.preventDefault()
      cid = $(e.target).closest('[data-cid]').attr('data-cid')

      tag = @collection.get(cid)
      count = @options.tagToCount(tag)
      message = t('remove.confirm', tag.get('name'), count)

      @trigger('remove', tag) if confirm(message)

    _onSubmitNewTag: (e) ->
      e.preventDefault()
      $form = $(e.currentTarget).closest('form')

      tag = {
        name: $form.find('input[name=name]').val()
        color: $form.find('input[name=color]').val()
      }

      @trigger('add', tag)
      $form[0].reset()

    _onSubmitExistingTag: (e) ->
      e.preventDefault()
      $form = $(e.currentTarget).closest('form')

      $li = $(e.currentTarget).closest('[data-cid]')
      cid = $li.attr('data-cid')
      tag = @collection.get(cid)

      attrs = {
        name: $form.find('input[name=name]').val()
        color: $form.find('input[name=color]').val()
      }

      @trigger('update', tag, attrs)

    _onChange: (e) ->
      $form = $(e.currentTarget).closest('form')
      $form.submit()
