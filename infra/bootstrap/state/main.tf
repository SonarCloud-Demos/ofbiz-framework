terraform {
  required_version = ">= 1.9.0, < 2.0.0"
  required_providers { azurerm = { source = "hashicorp/azurerm", version = "~> 4.0" } }
}
provider "azurerm" {
  features {}
  storage_use_azuread = true
}
resource "azurerm_resource_group" "state" {
  name     = var.resource_group_name
  location = var.location
}
resource "azurerm_storage_account" "state" {
  name                          = var.storage_account_name
  resource_group_name           = azurerm_resource_group.state.name
  location                      = azurerm_resource_group.state.location
  account_tier                  = "Standard"
  account_replication_type      = "ZRS"
  min_tls_version               = "TLS1_2"
  shared_access_key_enabled     = false
  public_network_access_enabled = false
  blob_properties {
    versioning_enabled  = true
    change_feed_enabled = true
    delete_retention_policy { days = 30 }
  }
  lifecycle { prevent_destroy = true }
}
resource "azurerm_storage_container" "state" {
  name               = "tfstate"
  storage_account_id = azurerm_storage_account.state.id
}
variable "resource_group_name" { type = string }
variable "storage_account_name" { type = string }
variable "location" {
  type    = string
  default = "switzerlandnorth"
}
