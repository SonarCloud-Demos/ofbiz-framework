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
  source                       = "../../modules/platform"
  name                         = "ofbiz-modern"
  environment                  = "staging"
  location                     = var.location
  address_space                = ["10.50.0.0/16"]
  container_subnet_cidr        = "10.50.0.0/23"
  private_endpoint_subnet_cidr = "10.50.2.0/24"
  postgres_admin_password      = var.postgres_admin_password
  template_service_image       = var.template_service_image
  alert_email                  = var.alert_email
  monthly_budget               = 1500
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
variable "alert_email" { type = string }
