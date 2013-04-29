require [
  'apps/DocumentCloudImportForm/models/Credentials'
], (Credentials) ->
  describe 'apps/DocumentCloudImportForm/models/Credentials', ->
    validCredentials = new Credentials({ email: 'user@example.org', password: 'password' })
    onlyEmailCredentials = new Credentials({ email: 'user@example.org' })
    onlyPasswordCredentials = new Credentials({ password: 'password' })
    emptyCredentials = new Credentials({ email: '', password: '' })

    describe 'toAuthHeaders', ->
      it 'should return auth headers', ->
        expect(validCredentials.toAuthHeaders()).toEqual({
          'Authorization': 'Basic dXNlckBleGFtcGxlLm9yZzpwYXNzd29yZA=='
        })

      it 'should not return auth headers when email or password is not set', ->
        expect(onlyEmailCredentials.toAuthHeaders()).toBeUndefined()
        expect(onlyPasswordCredentials.toAuthHeaders()).toBeUndefined()
        expect(emptyCredentials.toAuthHeaders()).toBeUndefined()

    describe 'toPostData', ->
      it 'should return post data', ->
        expect(validCredentials.toPostData()).toEqual({
          email: 'user@example.org',
          password: 'password'
        })

      it 'should not return data when email or password is not set', ->
        expect(onlyEmailCredentials.toPostData()).toBeUndefined()
        expect(onlyPasswordCredentials.toPostData()).toBeUndefined()
        expect(emptyCredentials.toPostData()).toBeUndefined()
