terraform {
  required_version = "= 1.13.3"
  backend "azurerm" {
    key              = "staging/platform.tfstate"
    use_azuread_auth = true
  }
}
module "platform" {
  source                  = "./.."
  environment             = "staging"
  subscription_id         = var.subscription_id
  tenant_id               = var.tenant_id
  vnet_cidr               = "10.30.0.0/20"
  alert_email             = var.alert_email
  monthly_budget          = 1000
  sample_image            = var.sample_image
  shell_image             = var.shell_image
  postgres_admin_login    = var.postgres_admin_login
  postgres_admin_password = var.postgres_admin_password
}
variable "subscription_id" { type = string }
variable "tenant_id" { type = string }
variable "alert_email" { type = string }
variable "sample_image" { type = string }
variable "shell_image" { type = string }
variable "postgres_admin_login" { type = string }
variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
