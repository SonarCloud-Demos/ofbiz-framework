variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "publisher_name" { type = string }
variable "publisher_email" { type = string }
variable "origin_host_name" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}
