# Note: this list is duplicated in Scala.
define ->
  colors = [
    "#ff0009",
    "#ff7700",
    "#fff700",
    #"#89ff00", # commented out for a prime number, for good distribution after hashing strings
    "#09ff00",
    "#00ff77",
    "#00fff7",
    "#0089ff",
    "#0009ff",
    "#7700ff",
    "#f700ff",
    "#ff0089",
    "#ff7378",
    "#ffb573",
    "#fffb73",
    "#beff73",
    "#78ff73",
    "#73ffb5",
    "#73fffb",
    "#73beff",
    "#7378ff",
    "#b573ff",
    "#fb73ff",
    "#ff73be",
  ]

  string_to_hash_integer = (s) ->
    # Taken from http://werxltd.com/wp/2010/05/13/javascript-implementation-of-javas-string-hashcode-method/
    hash = 0
    for i in [0..(s.length - 1)]
      c = s.charCodeAt(i)
      hash = ((hash << 5) - hash) + c # hash*31 + c
      hash = hash & hash # convert to integer
    hash

  string_to_colors_index = (s) ->
    h = string_to_hash_integer(s)
    ((h % colors.length) + colors.length) % colors.length

  class ColorTable
    get: (s) ->
      i = string_to_colors_index(s)
      colors[i]
