Open Policy Agent Environment
---

# Run
```
docker compose up
```

```bash
--spi-access-policy-opa-url=http://localhost:8181/v1/data
--spi-access-policy-opa-policy-path=/keycloak/realms/{realm}/{action}/allow
--spi-access-policy-opa-context-attributes=remoteAddress
--spi-access-policy-opa-user-attributes=email,emailVerified
--log-level=info,org.keycloak.accesscontrol:debug
```


--spi-required-action-opa-check-access-url=http://localhost:8181/v1/data
--spi-required-action-opa-check-access-policy-path=/keycloak/realms/{realm}/{action}/allow
--spi-required-action-opa-check-access-context-attributes=remoteAddress
--log-level=info,org.keycloak.accesscontrol:debug

```bash
curl -v \
-X POST \
-H "content-type: application/json" \
-d @./inputs/input.json \
http://localhost:8181/v1/data/keycloak/realms/opademo/access/allow
```

brew install opa

```
$ opa test ./policies -v
policies/keycloak/realms/opademo/access/policy_test.rego:
data.keycloak.realms.opademo.access.test_access_account_console: PASS (282.084µs)
--------------------------------------------------------------------------------
PASS: 1/1
```