package ofbiz.terraform.phase2

# CI evaluates Terraform and scanner output against this deny contract. Keep
# production disabled until the reliability/security review is recorded.
deny contains message if {
  input.environment == "prod"
  not input.production_gate_approved
  message := "production apply is gated"
}

deny contains message if {
  input.long_lived_client_secret
  message := "long-lived CI client secrets are forbidden"
}
