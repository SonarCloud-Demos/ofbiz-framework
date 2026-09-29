variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "sample_app_id" { type = string }
variable "shell_app_id" { type = string }
variable "postgres_server_id" { type = string }
variable "servicebus_namespace_id" { type = string }
variable "action_group_id" { type = string }
variable "tags" { type = map(string) }

locals {
  availability_targets = {
    sample = var.sample_app_id
    shell  = var.shell_app_id
  }
}
resource "azurerm_monitor_metric_alert" "restart" {
  for_each            = local.availability_targets
  name                = "${var.name}-${each.key}-restarts"
  resource_group_name = var.resource_group_name
  scopes              = [each.value]
  description         = "Container restart activity requires investigation"
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT15M"
  tags                = var.tags
  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "RestartCount"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 2
  }
  action { action_group_id = var.action_group_id }
}
resource "azurerm_monitor_metric_alert" "postgres_storage" {
  name                = "${var.name}-postgres-storage"
  resource_group_name = var.resource_group_name
  scopes              = [var.postgres_server_id]
  description         = "PostgreSQL storage exceeds 80 percent"
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT15M"
  tags                = var.tags
  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = "storage_percent"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }
  action { action_group_id = var.action_group_id }
}
resource "azurerm_monitor_metric_alert" "servicebus_dead_letters" {
  name                = "${var.name}-servicebus-deadletters"
  resource_group_name = var.resource_group_name
  scopes              = [var.servicebus_namespace_id]
  description         = "Dead-lettered messages require replay or remediation"
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT15M"
  tags                = var.tags
  criteria {
    metric_namespace = "Microsoft.ServiceBus/namespaces"
    metric_name      = "DeadletteredMessages"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 0
  }
  action { action_group_id = var.action_group_id }
}
