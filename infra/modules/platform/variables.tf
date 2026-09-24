variable "name" { type = string }
variable "location" { type = string }
variable "environment" { type = string }
variable "address_space" { type = list(string) }
variable "container_subnet_cidr" { type = string }
variable "private_endpoint_subnet_cidr" { type = string }
variable "postgres_admin_password" {
  type      = string
  sensitive = true
}
variable "template_service_image" { type = string }
variable "product_catalog_service_image" { type = string }
variable "catalog_database_user" {
  type    = string
  default = "product_catalog"
}
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
variable "tags" {
  type    = map(string)
  default = {}
}
variable "monthly_budget" {
  type    = number
  default = 500
}
variable "alert_email" { type = string }
