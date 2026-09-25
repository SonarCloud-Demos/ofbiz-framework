variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "private_endpoint_subnet_id" { type = string }
variable "private_dns_zone_ids" { type = map(string) }
variable "postgres_admin_login" {
  type      = string
  sensitive = true
}
variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
variable "tags" { type = map(string) }
data "azurerm_client_config" "current" {}
resource "azurerm_key_vault" "this" {
  name                          = substr("${var.name}-kv", 0, 24)
  resource_group_name           = var.resource_group_name
  location                      = var.location
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "standard"
  rbac_authorization_enabled    = true
  purge_protection_enabled      = true
  public_network_access_enabled = false
  tags                          = var.tags

  network_acls {
    bypass         = "None"
    default_action = "Deny"
  }
}
resource "azurerm_storage_account" "app" {
  name                          = substr(replace("${var.name}data", "-", ""), 0, 24)
  resource_group_name           = var.resource_group_name
  location                      = var.location
  account_tier                  = "Standard"
  account_replication_type      = "LRS"
  min_tls_version               = "TLS1_2"
  shared_access_key_enabled     = false
  public_network_access_enabled = false
  tags                          = var.tags
}
resource "azurerm_storage_container" "documents" {
  name                  = "documents"
  storage_account_id    = azurerm_storage_account.app.id
  container_access_type = "private"
}
resource "azurerm_servicebus_namespace" "this" {
  name                          = "${var.name}-bus"
  resource_group_name           = var.resource_group_name
  location                      = var.location
  sku                           = "Premium"
  capacity                      = 1
  public_network_access_enabled = false
  local_auth_enabled            = false
  tags                          = var.tags
}
resource "azurerm_servicebus_topic" "events" {
  name                                    = "domain-events"
  namespace_id                            = azurerm_servicebus_namespace.this.id
  duplicate_detection_history_time_window = "PT10M"
  requires_duplicate_detection            = true
  support_ordering                        = true
}
resource "azurerm_servicebus_queue" "commands" {
  name                                    = "platform-commands"
  namespace_id                            = azurerm_servicebus_namespace.this.id
  dead_lettering_on_message_expiration    = true
  duplicate_detection_history_time_window = "PT10M"
  requires_duplicate_detection            = true
}
resource "azurerm_postgresql_flexible_server" "this" {
  name                          = "${var.name}-pg"
  resource_group_name           = var.resource_group_name
  location                      = var.location
  version                       = "16"
  administrator_login           = var.postgres_admin_login
  administrator_password        = var.postgres_admin_password
  sku_name                      = "B_Standard_B1ms"
  storage_mb                    = 32768
  backup_retention_days         = 14
  geo_redundant_backup_enabled  = false
  public_network_access_enabled = false
  tags                          = var.tags
}
resource "azurerm_postgresql_flexible_server_database" "platform" {
  name      = "platform_sample"
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}
locals {
  endpoints = {
    vault    = [azurerm_key_vault.this.id, "vault", "privatelink.vaultcore.azure.net"]
    blob     = [azurerm_storage_account.app.id, "blob", "privatelink.blob.core.windows.net"]
    bus      = [azurerm_servicebus_namespace.this.id, "namespace", "privatelink.servicebus.windows.net"]
    postgres = [azurerm_postgresql_flexible_server.this.id, "postgresqlServer", "privatelink.postgres.database.azure.com"]
  }
}
resource "azurerm_private_endpoint" "this" {
  for_each            = local.endpoints
  name                = "${var.name}-${each.key}-pe"
  resource_group_name = var.resource_group_name
  location            = var.location
  subnet_id           = var.private_endpoint_subnet_id
  tags                = var.tags
  private_service_connection {
    name                           = "${var.name}-${each.key}"
    private_connection_resource_id = each.value[0]
    subresource_names              = [each.value[1]]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "default"
    private_dns_zone_ids = [var.private_dns_zone_ids[each.value[2]]]
  }
}
output "key_vault_id" { value = azurerm_key_vault.this.id }
output "storage_account_id" { value = azurerm_storage_account.app.id }
output "servicebus_namespace_id" { value = azurerm_servicebus_namespace.this.id }
output "postgres_server_id" { value = azurerm_postgresql_flexible_server.this.id }
output "security_controls" {
  value = {
    key_vault_public_access = azurerm_key_vault.this.public_network_access_enabled
    servicebus_local_auth   = azurerm_servicebus_namespace.this.local_auth_enabled
    storage_public_access   = azurerm_storage_account.app.public_network_access_enabled
  }
}
