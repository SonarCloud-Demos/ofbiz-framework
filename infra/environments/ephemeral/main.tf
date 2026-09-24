terraform {
  required_version = "= 1.13.3"
  backend "azurerm" { use_azuread_auth = true }
}
module "platform" {
  source                  = "./.."
  environment             = "pr-${var.pull_request_number}"
  subscription_id         = var.subscription_id
  tenant_id               = var.tenant_id
  vnet_cidr               = var.vnet_cidr
  alert_email             = var.alert_email
  monthly_budget          = 150
  sample_image            = var.sample_image
  shell_image             = var.shell_image
  legacy_origin_host      = var.legacy_origin_host
  postgres_admin_login    = var.postgres_admin_login
  postgres_admin_password = var.postgres_admin_password
  extra_tags              = { ephemeral = "true", expires-at = var.expires_at, pull-request = tostring(var.pull_request_number) }
}
variable "pull_request_number" { type = number }
variable "expires_at" { type = string }
variable "subscription_id" { type = string }
variable "tenant_id" { type = string }
variable "vnet_cidr" { type = string }
variable "alert_email" { type = string }
variable "sample_image" { type = string }
variable "shell_image" { type = string }
variable "legacy_origin_host" { type = string }
variable "postgres_admin_login" { type = string }
variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
