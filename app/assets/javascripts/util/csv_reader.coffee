csv_parser = window.csv_parser

DEFAULT_OPTIONS = {
  file_reader_factory: () -> new FileReader()
}

# Reads CSV data from a Blob, asynchronously.
#
# This class behaves similarly to FileReader, as in this example code:
#
#     reader = new CsvReader()
#     reader.onloadend = () ->
#       if reader.error
#         console.log("Error!", error) # object with 'name',
#                                      # and 'line'/'column' if 'name' is
#                                      # "SyntaxError"
#         console.log("Entire text was: ", reader.text)
#       else
#         console.log("Headers: ", reader.result.headers) # Array of Strings
#         for record in reader.result.records
#           console.log("Record: ", record)
#     reader.read(blob, encoding) # e.g., 'utf-8'
#
# We only provide an "onloadend" callback. (The others would be nice, but we
# have been too lazy to implement them, so far.)
#
# See FileReader doc: https://developer.mozilla.org/en-US/docs/DOM/FileReader
class CsvReader
  constructor: (options = {}) ->
    @error = null
    @text = null
    @result = null
    @readyState = 0
    @options = _.extend({}, DEFAULT_OPTIONS, options)
    @onloadend = () ->

  read: (blob, encoding) ->
    file_reader = @options.file_reader_factory()
    file_reader.onloadend = () =>
      this._handle_loadend(
        file_reader.readyState, file_reader.error, file_reader.result)
    file_reader.readAsText(blob, encoding)

  _handle_loadend: (readyState, error, text) ->
    @readyState = readyState
    @error = error
    @text = text

    try
      @result = csv_parser.parse(@text)
    catch error
      @error = error

    @onloadend.apply(this)

exports = require.make_export_object('util/csv_reader')
exports.CsvReader = CsvReader
