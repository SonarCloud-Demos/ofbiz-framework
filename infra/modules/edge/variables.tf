variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "publisher_name" { type = string }
variable "publisher_email" { type = string }
variable "origin_host_name" { type = string }
variable "waf_mode" {
  type    = string
  default = "Prevention"
  validation {
    condition     = contains(["Detection", "Prevention"], var.waf_mode)
    error_message = "waf_mode must be Detection or Prevention."
  }
}
variable "tags" {
  type    = map(string)
  default = {}
}
