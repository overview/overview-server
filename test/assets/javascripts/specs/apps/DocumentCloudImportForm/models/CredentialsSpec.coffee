define [
  'apps/DocumentCloudImportForm/models/Credentials'
], (Credentials) ->
  describe 'apps/DocumentCloudImportForm/models/Credentials', ->
    validCredentials = new Credentials({ email: 'user@example.org', password: 'password' })
    onlyEmailCredentials = new Credentials({ email: 'user@example.org' })
    onlyPasswordCredentials = new Credentials({ password: 'password' })
    emptyCredentials = new Credentials({ email: '', password: '' })

    describe 'toAuthHeaders', ->
      it 'should return auth headers', ->
        expect(validCredentials.toAuthHeaders()).to.deep.eq({
          'Authorization': 'Basic dXNlckBleGFtcGxlLm9yZzpwYXNzd29yZA=='
        })

      it 'should not return auth headers when email or password is not set', ->
        expect(onlyEmailCredentials.toAuthHeaders()).to.be.undefined
        expect(onlyPasswordCredentials.toAuthHeaders()).to.be.undefined
        expect(emptyCredentials.toAuthHeaders()).to.be.undefined

    describe 'toPostData', ->
      it 'should return post data', ->
        expect(validCredentials.toPostData()).to.deep.eq({
          email: 'user@example.org',
          password: 'password'
        })

      it 'should not return data when email or password is not set', ->
        expect(onlyEmailCredentials.toPostData()).to.be.undefined
        expect(onlyPasswordCredentials.toPostData()).to.be.undefined
        expect(emptyCredentials.toPostData()).to.be.undefined

    describe 'isComplete', ->
      it 'should return true when email and password are set', ->
        expect(validCredentials.isComplete()).to.be(true)

      it 'should return false when email or password is not set', ->
        expect(onlyEmailCredentials.isComplete()).to.be(false)
        expect(onlyPasswordCredentials.isComplete()).to.be(false)
        expect(emptyCredentials.isComplete()).to.be(false)
