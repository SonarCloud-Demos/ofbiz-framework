variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "alert_email" { type = string }
variable "tags" { type = map(string) }
resource "azurerm_log_analytics_workspace" "this" {
  name                = "${var.name}-logs"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = var.tags
}
resource "azurerm_application_insights" "this" {
  name                = "${var.name}-insights"
  resource_group_name = var.resource_group_name
  location            = var.location
  workspace_id        = azurerm_log_analytics_workspace.this.id
  application_type    = "web"
  tags                = var.tags
}
resource "azurerm_monitor_action_group" "platform" {
  name                = "${var.name}-alerts"
  resource_group_name = var.resource_group_name
  short_name          = substr(replace(var.name, "-", ""), 0, 12)
  tags                = var.tags
  email_receiver {
    name          = "platform-oncall"
    email_address = var.alert_email
  }
}
output "workspace_id" { value = azurerm_log_analytics_workspace.this.id }
output "connection_string" {
  value     = azurerm_application_insights.this.connection_string
  sensitive = true
}
output "action_group_id" { value = azurerm_monitor_action_group.platform.id }
