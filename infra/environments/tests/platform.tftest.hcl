mock_provider "azurerm" {
  mock_data "azurerm_client_config" {
    defaults = { tenant_id = "00000000-0000-0000-0000-000000000001" }
  }
  mock_data "azurerm_resource_group" {
    defaults = { id = "/subscriptions/test/resourceGroups/ofbiz-test-rg" }
  }
}

variables {
  subscription_id         = "00000000-0000-0000-0000-000000000001"
  tenant_id               = "00000000-0000-0000-0000-000000000001"
  environment             = "dev"
  vnet_cidr               = "10.20.0.0/20"
  alert_email             = "platform@example.invalid"
  monthly_budget          = 500
  sample_image            = "example.invalid/platform-sample@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  shell_image             = "example.invalid/web-shell@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  postgres_admin_login    = "bootstrapadmin"
  postgres_admin_password = "Not-A-Real-Password-For-Mocked-Tests!"
}

run "secure_platform_plan" {
  command = plan

  assert {
    condition     = module.data_platform.security_controls.key_vault_public_access == false
    error_message = "Key Vault must not expose its data plane publicly."
  }
  assert {
    condition     = module.data_platform.security_controls.servicebus_local_auth == false
    error_message = "Service Bus local authentication must remain disabled."
  }
  assert {
    condition     = module.container_platform.security_controls.registry_admin_enabled == false
    error_message = "ACR administrator credentials must remain disabled."
  }
  assert {
    condition     = module.edge.security_controls.waf_mode == "Prevention"
    error_message = "The edge WAF must operate in prevention mode."
  }
}
