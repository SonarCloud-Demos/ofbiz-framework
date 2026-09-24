variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "monthly_budget" { type = number }
variable "alert_email" { type = string }
variable "apim_subnet_id" { type = string }
variable "container_environment_id" { type = string }
variable "shell_origin_host" { type = string }
variable "tags" { type = map(string) }
resource "azurerm_api_management" "this" {
  name                          = "${var.name}-apim"
  resource_group_name           = var.resource_group_name
  location                      = var.location
  publisher_name                = "OFBiz Platform"
  publisher_email               = var.alert_email
  sku_name                      = "Developer_1"
  public_network_access_enabled = false
  virtual_network_type          = "Internal"
  tags                          = var.tags
  virtual_network_configuration {
    subnet_id = var.apim_subnet_id
  }
}
resource "azurerm_cdn_frontdoor_profile" "this" {
  name                = "${var.name}-frontdoor"
  resource_group_name = var.resource_group_name
  sku_name            = "Premium_AzureFrontDoor"
  tags                = var.tags
}
resource "azurerm_cdn_frontdoor_firewall_policy" "this" {
  name                = substr(replace("${var.name}waf", "-", ""), 0, 128)
  resource_group_name = var.resource_group_name
  sku_name            = azurerm_cdn_frontdoor_profile.this.sku_name
  enabled             = true
  mode                = "Prevention"
  tags                = var.tags
  managed_rule {
    type    = "DefaultRuleSet"
    version = "2.1"
    action  = "Block"
  }
}
resource "azurerm_cdn_frontdoor_endpoint" "this" {
  name                     = "${var.name}-endpoint"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.this.id
  enabled                  = true
  tags                     = var.tags
}
resource "azurerm_cdn_frontdoor_origin_group" "shell" {
  name                     = "shell"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.this.id
  session_affinity_enabled = false
  health_probe {
    interval_in_seconds = 30
    path                = "/"
    protocol            = "Https"
    request_type        = "HEAD"
  }
  load_balancing {
    additional_latency_in_milliseconds = 50
    sample_size                        = 4
    successful_samples_required        = 3
  }
}
resource "azurerm_cdn_frontdoor_origin" "shell" {
  name                           = "shell"
  cdn_frontdoor_origin_group_id  = azurerm_cdn_frontdoor_origin_group.shell.id
  enabled                        = true
  host_name                      = var.shell_origin_host
  origin_host_header             = var.shell_origin_host
  certificate_name_check_enabled = true
  http_port                      = 80
  https_port                     = 443
  priority                       = 1
  weight                         = 1000
  private_link {
    request_message        = "Front Door access to the OFBiz web shell"
    target_type            = "managedEnvironments"
    location               = var.location
    private_link_target_id = var.container_environment_id
  }
}
resource "azurerm_cdn_frontdoor_route" "shell" {
  name                          = "shell"
  cdn_frontdoor_endpoint_id     = azurerm_cdn_frontdoor_endpoint.this.id
  cdn_frontdoor_origin_group_id = azurerm_cdn_frontdoor_origin_group.shell.id
  cdn_frontdoor_origin_ids      = [azurerm_cdn_frontdoor_origin.shell.id]
  enabled                       = true
  forwarding_protocol           = "HttpsOnly"
  https_redirect_enabled        = true
  patterns_to_match             = ["/*"]
  supported_protocols           = ["Http", "Https"]
  link_to_default_domain        = true
}
resource "azurerm_cdn_frontdoor_security_policy" "this" {
  name                     = "waf"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.this.id
  security_policies {
    firewall {
      cdn_frontdoor_firewall_policy_id = azurerm_cdn_frontdoor_firewall_policy.this.id
      association {
        patterns_to_match = ["/*"]
        domain {
          cdn_frontdoor_domain_id = azurerm_cdn_frontdoor_endpoint.this.id
        }
      }
    }
  }
}
data "azurerm_resource_group" "this" { name = var.resource_group_name }
resource "azurerm_consumption_budget_resource_group" "this" {
  name              = "${var.name}-monthly"
  resource_group_id = data.azurerm_resource_group.this.id
  amount            = var.monthly_budget
  time_grain        = "Monthly"
  time_period { start_date = formatdate("YYYY-MM-01'T'00:00:00'Z'", timestamp()) }
  notification {
    enabled        = true
    threshold      = 80
    operator       = "GreaterThan"
    threshold_type = "Forecasted"
    contact_emails = [var.alert_email]
  }
  lifecycle { ignore_changes = [time_period] }
}
output "frontdoor_profile_id" { value = azurerm_cdn_frontdoor_profile.this.id }
output "waf_policy_id" { value = azurerm_cdn_frontdoor_firewall_policy.this.id }
output "apim_id" { value = azurerm_api_management.this.id }
output "frontdoor_endpoint_host_name" { value = azurerm_cdn_frontdoor_endpoint.this.host_name }
output "security_controls" {
  value = {
    waf_mode           = azurerm_cdn_frontdoor_firewall_policy.this.mode
    apim_public_access = azurerm_api_management.this.public_network_access_enabled
  }
}
