// Karma configuration used only for headless test runs inside the CI/Docker
// environment (no Chrome installed on the host — see CLAUDE.md /
// task-12-18-report.md for context). Adds a ChromeHeadlessNoSandbox launcher
// because Chromium refuses to start as root without --no-sandbox inside a
// container. Not part of the original task briefs; added because `ng test`
// would otherwise be unable to launch a browser at all in this environment.
process.env.CHROME_BIN = process.env.CHROME_BIN || '/usr/bin/chromium-browser';

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    port: 9876,
    colors: true,
    logLevel: config.LOG_INFO,
    autoWatch: false,
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    singleRun: true,
    restartOnFileChange: false,
  });
};
