locals {
  tags = merge(var.tags, {
    environment = var.environment
    managed-by  = "terraform"
    system      = "ofbiz-modern"
  })
}

data "azurerm_client_config" "current" {}

resource "azurerm_resource_group" "this" {
  name     = "rg-${var.name}-${var.environment}"
  location = var.location
  tags     = local.tags
}

resource "azurerm_virtual_network" "this" {
  name                = "vnet-${var.name}-${var.environment}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  address_space       = var.address_space
  tags                = local.tags
}

resource "azurerm_subnet" "containers" {
  name                 = "snet-container-apps"
  resource_group_name  = azurerm_resource_group.this.name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = [var.container_subnet_cidr]
  delegation {
    name = "container-apps"
    service_delegation {
      name = "Microsoft.App/environments"
    }
  }
}

resource "azurerm_subnet" "private_endpoints" {
  name                              = "snet-private-endpoints"
  resource_group_name               = azurerm_resource_group.this.name
  virtual_network_name              = azurerm_virtual_network.this.name
  address_prefixes                  = [var.private_endpoint_subnet_cidr]
  private_endpoint_network_policies = "Disabled"
}

resource "azurerm_log_analytics_workspace" "this" {
  name                = "log-${var.name}-${var.environment}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  sku                 = "PerGB2018"
  retention_in_days   = var.environment == "staging" ? 90 : 30
  tags                = local.tags
}

resource "azurerm_application_insights" "this" {
  name                = "appi-${var.name}-${var.environment}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  workspace_id        = azurerm_log_analytics_workspace.this.id
  application_type    = "web"
  tags                = local.tags
}

resource "azurerm_container_registry" "this" {
  name                          = replace("acr${var.name}${var.environment}", "-", "")
  resource_group_name           = azurerm_resource_group.this.name
  location                      = azurerm_resource_group.this.location
  sku                           = "Premium"
  admin_enabled                 = false
  public_network_access_enabled = false
  zone_redundancy_enabled       = var.environment == "staging"
  tags                          = local.tags
}

resource "azurerm_user_assigned_identity" "workload" {
  name                = "id-${var.name}-${var.environment}-workload"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  tags                = local.tags
}

resource "azurerm_key_vault" "this" {
  name                          = substr(replace("kv-${var.name}-${var.environment}", "_", "-"), 0, 24)
  location                      = azurerm_resource_group.this.location
  resource_group_name           = azurerm_resource_group.this.name
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "standard"
  rbac_authorization_enabled    = true
  purge_protection_enabled      = true
  soft_delete_retention_days    = 90
  public_network_access_enabled = false
  tags                          = local.tags
}

resource "azurerm_app_configuration" "this" {
  name                       = "appcs-${var.name}-${var.environment}"
  resource_group_name        = azurerm_resource_group.this.name
  location                   = azurerm_resource_group.this.location
  sku                        = "standard"
  public_network_access      = "Disabled"
  purge_protection_enabled   = true
  soft_delete_retention_days = 7
  local_auth_enabled         = false
  tags                       = local.tags
}

resource "azurerm_servicebus_namespace" "this" {
  name                          = "sb-${var.name}-${var.environment}"
  location                      = azurerm_resource_group.this.location
  resource_group_name           = azurerm_resource_group.this.name
  sku                           = "Premium"
  capacity                      = 1
  premium_messaging_partitions  = 1
  local_auth_enabled            = false
  public_network_access_enabled = false
  minimum_tls_version           = "1.2"
  tags                          = local.tags
}

resource "azurerm_servicebus_topic" "events" {
  name                  = "domain-events-v1"
  namespace_id          = azurerm_servicebus_namespace.this.id
  partitioning_enabled  = false
  max_size_in_megabytes = 1024
}

resource "azurerm_postgresql_flexible_server" "this" {
  name                          = "psql-${var.name}-${var.environment}"
  resource_group_name           = azurerm_resource_group.this.name
  location                      = azurerm_resource_group.this.location
  version                       = "16"
  administrator_login           = "platformadmin"
  administrator_password        = var.postgres_admin_password
  sku_name                      = var.environment == "staging" ? "GP_Standard_D2s_v3" : "B_Standard_B1ms"
  storage_mb                    = 32768
  backup_retention_days         = var.environment == "staging" ? 14 : 7
  geo_redundant_backup_enabled  = false
  public_network_access_enabled = false
  zone                          = "1"
  tags                          = local.tags
  lifecycle { prevent_destroy = true }
}

resource "azurerm_postgresql_flexible_server_database" "template" {
  name      = "template_service"
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

locals {
  private_services = {
    registry = {
      resource_id = azurerm_container_registry.this.id
      subresource = "registry"
      zone        = "privatelink.azurecr.io"
    }
    vault = {
      resource_id = azurerm_key_vault.this.id
      subresource = "vault"
      zone        = "privatelink.vaultcore.azure.net"
    }
    servicebus = {
      resource_id = azurerm_servicebus_namespace.this.id
      subresource = "namespace"
      zone        = "privatelink.servicebus.windows.net"
    }
    postgres = {
      resource_id = azurerm_postgresql_flexible_server.this.id
      subresource = "postgresqlServer"
      zone        = "privatelink.postgres.database.azure.com"
    }
  }
}

resource "azurerm_private_dns_zone" "services" {
  for_each            = local.private_services
  name                = each.value.zone
  resource_group_name = azurerm_resource_group.this.name
  tags                = local.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "services" {
  for_each              = local.private_services
  name                  = "link-${each.key}"
  resource_group_name   = azurerm_resource_group.this.name
  private_dns_zone_name = azurerm_private_dns_zone.services[each.key].name
  virtual_network_id    = azurerm_virtual_network.this.id
  registration_enabled  = false
  tags                  = local.tags
}

resource "azurerm_private_endpoint" "services" {
  for_each            = local.private_services
  name                = "pe-${each.key}-${var.environment}"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  subnet_id           = azurerm_subnet.private_endpoints.id
  private_service_connection {
    name                           = "psc-${each.key}"
    private_connection_resource_id = each.value.resource_id
    subresource_names              = [each.value.subresource]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "default"
    private_dns_zone_ids = [azurerm_private_dns_zone.services[each.key].id]
  }
  tags = local.tags
}

resource "azurerm_container_app_environment" "this" {
  name                           = "cae-${var.name}-${var.environment}"
  location                       = azurerm_resource_group.this.location
  resource_group_name            = azurerm_resource_group.this.name
  log_analytics_workspace_id     = azurerm_log_analytics_workspace.this.id
  infrastructure_subnet_id       = azurerm_subnet.containers.id
  internal_load_balancer_enabled = true
  zone_redundancy_enabled        = var.environment == "staging"
  tags                           = local.tags
}

resource "azurerm_container_app" "template" {
  name                         = "ca-template-service"
  container_app_environment_id = azurerm_container_app_environment.this.id
  resource_group_name          = azurerm_resource_group.this.name
  revision_mode                = "Multiple"
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.workload.id]
  }
  registry {
    server   = azurerm_container_registry.this.login_server
    identity = azurerm_user_assigned_identity.workload.id
  }
  ingress {
    external_enabled = false
    target_port      = 8080
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
  template {
    min_replicas = 1
    max_replicas = 3
    container {
      name   = "template-service"
      image  = var.template_service_image
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = azurerm_application_insights.this.connection_string
      }
      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/liveness"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/actuator/health/readiness"
      }
    }
  }
  tags = local.tags
}

resource "azurerm_role_assignment" "acr_pull" {
  scope                = azurerm_container_registry.this.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.workload.principal_id
}

resource "azurerm_role_assignment" "vault_secrets" {
  scope                = azurerm_key_vault.this.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.workload.principal_id
}

resource "azurerm_role_assignment" "servicebus_sender" {
  scope                = azurerm_servicebus_namespace.this.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = azurerm_user_assigned_identity.workload.principal_id
}

resource "azurerm_monitor_action_group" "platform" {
  name                = "ag-${var.name}-${var.environment}"
  resource_group_name = azurerm_resource_group.this.name
  short_name          = "platform"
  email_receiver {
    name          = "platform"
    email_address = var.alert_email
  }
  tags = local.tags
}

resource "azurerm_monitor_metric_alert" "service_errors" {
  name                = "template-service-restarts"
  resource_group_name = azurerm_resource_group.this.name
  scopes              = [azurerm_container_app.template.id]
  description         = "Template service restarted unexpectedly"
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT15M"
  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "RestartCount"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 0
  }
  action { action_group_id = azurerm_monitor_action_group.platform.id }
  tags = local.tags
}

resource "azurerm_consumption_budget_resource_group" "this" {
  name              = "budget-${var.name}-${var.environment}"
  resource_group_id = azurerm_resource_group.this.id
  amount            = var.monthly_budget
  time_grain        = "Monthly"
  time_period { start_date = formatdate("YYYY-MM-01'T'00:00:00'Z'", timestamp()) }
  notification {
    enabled        = true
    threshold      = 80
    operator       = "GreaterThan"
    threshold_type = "Actual"
    contact_emails = [var.alert_email]
  }
  lifecycle { ignore_changes = [time_period] }
}
