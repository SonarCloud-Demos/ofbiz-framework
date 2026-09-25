terraform {
  required_version = "= 1.13.3"
  required_providers {
    azurerm = { source = "hashicorp/azurerm", version = "~> 4.0" }
  }
}
provider "azurerm" {
  subscription_id = var.subscription_id
  tenant_id       = var.tenant_id
  use_oidc        = true
  features {}
}
