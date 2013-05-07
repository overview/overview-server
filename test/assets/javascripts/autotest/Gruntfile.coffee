module.exports = (grunt) ->
  spawn = require('child_process').spawn

  childProcesses = []
  otherChildProcess = undefined
  needAnotherJsTestDriverRun = false

  process.on 'exit', ->
    for childProcess in childProcesses
      childProcess.kill()
    otherChildProcess?.kill()
    undefined

  process.on('SIGTERM', process.exit)

  grunt.initConfig
    pkg: grunt.file.readJSON('package.json')

    clean: [ 'framework-js', 'src-js', 'test-js' ]

    copy:
      src:
        expand: true
        cwd: '../../../../app/assets/javascripts'
        src: [ '**/*.js' ]
        dest: 'src-js/'
      framework:
        expand: true
        cwd: '../framework'
        src: [ '**/*.js' ]
        dest: 'framework-js/'

    regarde:
      js:
        files: '../../../../app/assets/javascripts/**/*.js'
        tasks: [ 'copy' ]

      tests:
        files: [ 'src-js/**/*.js', 'test-js/**/*.js' ]
        tasks: [ 'runJsTests' ]

  grunt.registerTask 'startCoffee', 'Starts monitoring CoffeeScript files for changes', ->
    coffeePath = 'node_modules/grunt-contrib-coffee/node_modules/coffee-script/bin/coffee'

    opts = { stdio: [ 'ignore', process.stdout, process.stderr ] }
    childProcesses.push(spawn(coffeePath, [ '-c', '-o', 'src-js', '-w', '../../../../app/assets/javascripts' ], opts))
    childProcesses.push(spawn(coffeePath, [ '-c', '-o', 'framework-js', '-w', '../framework' ], opts))
    childProcesses.push(spawn(coffeePath, [ '-c', '-o', 'test-js/specs', '-w', '../specs' ], opts))

  grunt.registerTask 'startJstdServer', 'Starts a JSTD server on port 9876', ->
    opts = { stdio: [ 'ignore', process.stdout, process.stderr ] }
    childProcesses.push(spawn('java', [ '-jar', '../framework/JsTestDriver.jar', '--port', '9876' ], opts))

  grunt.registerTask 'prompt', 'Waits until the user hits Enter', ->
    done = @async()

    # no need for async callback here
    setTimeout(->
      process.stdout.write("Browse to http://localhost:9876/capture?strict in several browsers, then press Enter:")
    , 5000)

    process.stdin.resume()
    process.stdin.once 'readable', ->
      process.stdin.pause()
      done()

  grunt.registerTask 'runJsTests', 'Run JS tests through JSTD', ->
    run = (callback) ->
      opts = { stdio: [ 'ignore', process.stdout, process.stderr ] }
      child = spawn('java', [ '-jar', '../framework/JsTestDriver.jar', '--config', '../jsTestDriver.conf', '--basePath', '.', '--captureConsole', '--reset', '--tests', 'all' ], opts)
      child.once 'exit', ->
        otherChildProcess = undefined
        callback()
      otherChildProcess = child

    runUntilDone = (callback) ->
      run ->
        if needAnotherJsTestDriverRun
          needAnotherJsTestDriverRun = false
          runUntilDone(callback)

    if otherChildProcess
      needAnotherJsTestDriverRun = true
    else
      runUntilDone ->

  grunt.loadNpmTasks('grunt-regarde')
  grunt.loadNpmTasks('grunt-contrib-clean')
  grunt.loadNpmTasks('grunt-contrib-copy')

  grunt.registerTask('default', [
    'clean'
    'startCoffee'
    'copy'
    'startJstdServer'
    'prompt'
    'runJsTests'
    'regarde'
  ])
