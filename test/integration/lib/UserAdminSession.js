const faker = require('faker')
const request = require('request-promise-native')

// Creates and deletes fake users, so tests can log in as them.
module.exports = class UserAdminSession {
  constructor(options) {
    this.options = options
    this.request = request.defaults({
      baseUrl: options.baseUrl,
      timeout: options.timeout,
      simple: false,
      resolveWithFullResponse: true,
      headers: { 'X-Requested-With': 'integration tests' },
      jar: request.jar(),
    })

    this.loginPromise = this._login()
  }

  async r(options) {
    const response = await this.request(options)
    if (/!^[23]\d\d/.test(response.statusCode)) {
      throw new Error(`Bad response from server: ${response.statusCode}`)
    } else {
      return response.body
    }
  }

  GET(url) { return this.r({ method: 'GET', url: url }) }
  POST(url, json) { return this.r({ method: 'POST', url: url, json: json }) }
  DELETE(url) { return this.r({ method: 'DELETE', url: url }) }

  _login() {
    return this.POST('/login', this.options.login)
  }

  // Returns a Promise of { id, email, is_admin, confirmation_token, ... }
  createTemporaryUser() {
    const email = faker.internet.email()
    return this.createUser({ email: email, password: email })
  }

  async createUser(user) {
    await this.loginPromise
    return this.POST('/admin/users', user)
  }

  async deleteUser(user) {
    await this.loginPromise
    await this.DELETE(`/admin/users/${encodeURIComponent(user.email)}`)
  }

  // Returns a Promise of { id, email, is_admin, confirmation_token, ... }
  async showUser(user) {
    await this.loginPromise
    return await this.GET(`/admin/users/${encodeURIComponent(user.email)}`)
  }
}
