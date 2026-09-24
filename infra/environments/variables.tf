variable "subscription_id" { type = string }
variable "tenant_id" { type = string }
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment) || can(regex("^pr-[0-9]+$", var.environment))
    error_message = "environment must be dev, staging, prod, or pr-<number>"
  }
}
variable "location" {
  type    = string
  default = "switzerlandnorth"
}
variable "vnet_cidr" { type = string }
variable "alert_email" { type = string }
variable "monthly_budget" { type = number }
variable "sample_image" { type = string }
variable "shell_image" { type = string }
variable "extra_tags" {
  type    = map(string)
  default = {}
}
variable "postgres_admin_login" {
  type      = string
  sensitive = true
}
variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
locals { tags = merge({ environment = var.environment, managed-by = "terraform", system = "ofbiz-modern" }, var.extra_tags) }
