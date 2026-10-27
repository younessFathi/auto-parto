path "kv/data/production/audit/*" {
  capabilities = ["read"]
}

path "database/creds/production-audit" {
  capabilities = ["read"]
}
