# OAuth code exchange

The browser generates a 32-byte random verifier in `sessionStorage` and sends only its SHA-256 Base64URL challenge as `exchange_challenge` on `/api/v1/oauth2/authorize/{provider}`. The server copies that challenge into the OAuth authorization request's server-side attributes. The existing provider `state` and HTTP session check remain in place.

On a successful provider callback, the server creates a random, two-minute exchange code. PostgreSQL stores only its SHA-256 hash, the challenge, the member reference, and expiry. The browser redirect contains only `?code=...`; it has `Cache-Control: no-store` and `Referrer-Policy: no-referrer`. The frontend removes the query from browser history before calling `POST /api/v1/auth/exchange` with `{code, verifier}`.

Exchange first reads the member ID, then locks the Member row, then locks and rechecks the code row. It compares the verifier's challenge, deletes the code, and calls `AuthService.loginSuccess(Member)` in the same transaction. Invalid, expired, or already used codes return 401. A failed token issuance rolls back code consumption. Successful token responses have `Cache-Control: no-store`. A scheduled bulk delete removes expired rows every minute after a one-minute initial delay. Disable it for isolated tests with `app.oauth2.exchange-cleanup-enabled=false`.

Apply `docs/sql/login-exchange.sql` before deploying the new callback and API. It is safe to repeat and does not change existing member or refresh rows. Existing provider configuration and frontend token storage remain in use. The frontend goes to the home page after exchange without requiring additional profile fields.
