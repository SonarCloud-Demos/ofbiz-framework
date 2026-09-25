module "resource_group" {
  source   = "../modules/resource-group"
  name     = "ofbiz-${var.environment}-rg"
  location = var.location
  tags     = local.tags
}
module "network" {
  source              = "../modules/network"
  name                = "ofbiz-${var.environment}-vnet"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  address_space       = [var.vnet_cidr]
  tags                = local.tags
}
module "observability" {
  source              = "../modules/observability"
  name                = "ofbiz-${var.environment}"
  resource_group_name = module.resource_group.name
  location            = module.resource_group.location
  alert_email         = var.alert_email
  tags                = local.tags
}
module "data_platform" {
  source                     = "../modules/data-platform"
  name                       = "ofbiz-${var.environment}"
  resource_group_name        = module.resource_group.name
  location                   = module.resource_group.location
  private_endpoint_subnet_id = module.network.private_endpoint_subnet_id
  private_dns_zone_ids       = module.network.private_dns_zone_ids
  postgres_admin_login       = var.postgres_admin_login
  postgres_admin_password    = var.postgres_admin_password
  tags                       = local.tags
}
module "container_platform" {
  source                     = "../modules/container-platform"
  name                       = "ofbiz-${var.environment}"
  resource_group_name        = module.resource_group.name
  location                   = module.resource_group.location
  apps_subnet_id             = module.network.apps_subnet_id
  private_endpoint_subnet_id = module.network.private_endpoint_subnet_id
  acr_private_dns_zone_id    = module.network.private_dns_zone_ids["privatelink.azurecr.io"]
  workspace_id               = module.observability.workspace_id
  sample_image               = var.sample_image
  shell_image                = var.shell_image
  tags                       = local.tags
}
module "edge" {
  source                   = "../modules/edge"
  name                     = "ofbiz-${var.environment}"
  resource_group_name      = module.resource_group.name
  location                 = module.resource_group.location
  monthly_budget           = var.monthly_budget
  alert_email              = var.alert_email
  apim_subnet_id           = module.network.apim_subnet_id
  container_environment_id = module.container_platform.environment_id
  shell_origin_host        = module.container_platform.shell_fqdn
  legacy_origin_host       = var.legacy_origin_host
  tags                     = local.tags
}
module "platform_alerts" {
  source                  = "../modules/platform-alerts"
  name                    = "ofbiz-${var.environment}"
  resource_group_name     = module.resource_group.name
  sample_app_id           = module.container_platform.sample_app_id
  shell_app_id            = module.container_platform.shell_app_id
  postgres_server_id      = module.data_platform.postgres_server_id
  servicebus_namespace_id = module.data_platform.servicebus_namespace_id
  action_group_id         = module.observability.action_group_id
  tags                    = local.tags
}
