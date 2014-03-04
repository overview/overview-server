module.exports = (grunt) ->
  jsSrc = '../../../../app/assets/javascripts'
  jsTest = '../specs'
  jsFramework = '../framework'

  grunt.initConfig
    clean: [ 'framework-js', 'src-js', 'test-js' ]

    copy:
      src:
        expand: true
        cwd: jsSrc
        src: [ '**/*.js' ]
        dest: 'src-js'

      framework:
        expand: true
        cwd: jsFramework
        src: [ '**/*.js' ]
        dest: 'framework-js'

    coffee:
      options:
        flatten: false

      src:
        expand: true
        cwd: jsSrc
        src: [ '**/*.coffee' ]
        dest: 'src-js'
        ext: '.js'

      test:
        expand: true
        cwd: jsTest
        src: [ '**/*.coffee' ]
        dest: 'test-js'
        ext: '.js'

      framework:
        expand: true
        cwd: jsFramework
        src: [ '**/*.coffee' ]
        dest: 'framework-js'
        ext: '.js'

    karma:
      options:
        configFile: 'karma.conf.js'
      unit:
        background: true
      continuous:
        singleRun: true

    watch:
      options:
        spawn: false
      'coffee-src':
        files: [ "#{jsSrc}/**/*.coffee" ]
        tasks: [ 'coffee:src', 'karma:unit:run' ]
      'coffee-test':
        files: [ "#{jsTest}/**/*.coffee" ]
        tasks: [ 'coffee:test', 'karma:unit:run' ]
      'js-src':
        files: [ "#{jsSrc}/**/*.js" ]
        tasks: [ 'copy:src', 'karma:unit:run' ]

  grunt.loadNpmTasks('grunt-contrib-clean')
  grunt.loadNpmTasks('grunt-contrib-copy')
  grunt.loadNpmTasks('grunt-contrib-coffee')
  grunt.loadNpmTasks('grunt-contrib-watch')
  grunt.loadNpmTasks('grunt-karma')

  # Only rewrite changed files on watch
  grunt.event.on 'watch', (action, filepath) ->
    if filepath.indexOf(jsSrc) == 0
      grunt.config('coffee.src.src', filepath.replace(jsSrc, '.'))
      grunt.config('copy.src.src', filepath.replace(jsSrc, '.'))
    else if filepath.indexOf(jsTest) == 0
      grunt.config('coffee.test.src', filepath.replace(jsTest, '.'))

  grunt.registerTask('test', [
    'clean'
    'copy'
    'coffee'
    'karma:continuous'
  ])

  grunt.registerTask('default', [
    'karma:unit' # spin up PhantomJS early, so it'll be ready later
    'clean'
    'copy'
    'coffee'
    'karma:unit:run'
    'watch'
  ])
