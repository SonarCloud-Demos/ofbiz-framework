variable "subscription_id" { type = string }
variable "ci_principal_object_id" { type = string }
variable "location" {
  type    = string
  default = "switzerlandnorth"
}
variable "name_prefix" {
  type    = string
  default = "ofbiz-tfstate"
}
variable "tags" {
  type    = map(string)
  default = {}
}
