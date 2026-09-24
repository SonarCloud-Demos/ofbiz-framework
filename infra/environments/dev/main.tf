terraform {
  required_version = ">= 1.9.0, < 2.0.0"
  required_providers { azurerm = { source = "hashicorp/azurerm", version = "~> 4.0" } }
  backend "azurerm" {}
}
provider "azurerm" {
  features {}
  storage_use_azuread = true
}
module "platform" {
  source                        = "../../modules/platform"
  name                          = "ofbiz-modern"
  environment                   = "dev"
  location                      = var.location
  address_space                 = ["10.40.0.0/16"]
  container_subnet_cidr         = "10.40.0.0/23"
  private_endpoint_subnet_cidr  = "10.40.2.0/24"
  postgres_admin_password       = var.postgres_admin_password
  template_service_image        = var.template_service_image
  product_catalog_service_image = var.product_catalog_service_image
  catalog_database_password     = var.catalog_database_password
  catalog_ingestion_key         = var.catalog_ingestion_key
  catalog_legacy_export_key     = var.catalog_legacy_export_key
  legacy_ofbiz_internal_url     = var.legacy_ofbiz_internal_url
  alert_email                   = var.alert_email
  monthly_budget                = 500
}
variable "location" {
  type    = string
  default = "switzerlandnorth"
}
variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
variable "template_service_image" { type = string }
variable "product_catalog_service_image" { type = string }
variable "catalog_database_password" {
  type      = string
  sensitive = true
}
variable "catalog_ingestion_key" {
  type      = string
  sensitive = true
}
variable "catalog_legacy_export_key" {
  type      = string
  sensitive = true
}
variable "legacy_ofbiz_internal_url" { type = string }
variable "alert_email" { type = string }
