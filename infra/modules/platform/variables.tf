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
variable "tags" {
  type    = map(string)
  default = {}
}
variable "monthly_budget" {
  type    = number
  default = 500
}
variable "alert_email" { type = string }
