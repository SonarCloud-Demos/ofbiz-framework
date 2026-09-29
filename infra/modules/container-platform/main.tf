variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "apps_subnet_id" { type = string }
variable "private_endpoint_subnet_id" { type = string }
variable "acr_private_dns_zone_id" { type = string }
variable "workspace_id" { type = string }
variable "sample_image" { type = string }
variable "shell_image" { type = string }
variable "tags" { type = map(string) }
resource "azurerm_container_registry" "this" {
  name                          = substr(replace("${var.name}acr", "-", ""), 0, 50)
  resource_group_name           = var.resource_group_name
  location                      = var.location
  sku                           = "Premium"
  admin_enabled                 = false
  public_network_access_enabled = false
  zone_redundancy_enabled       = true
  tags                          = var.tags
}
resource "azurerm_private_endpoint" "acr" {
  name                = "${var.name}-acr-pe"
  resource_group_name = var.resource_group_name
  location            = var.location
  subnet_id           = var.private_endpoint_subnet_id
  tags                = var.tags
  private_service_connection {
    name                           = "acr"
    private_connection_resource_id = azurerm_container_registry.this.id
    subresource_names              = ["registry"]
    is_manual_connection           = false
  }
  private_dns_zone_group {
    name                 = "default"
    private_dns_zone_ids = [var.acr_private_dns_zone_id]
  }
}
resource "azurerm_container_app_environment" "this" {
  name                           = "${var.name}-apps"
  resource_group_name            = var.resource_group_name
  location                       = var.location
  infrastructure_subnet_id       = var.apps_subnet_id
  internal_load_balancer_enabled = true
  log_analytics_workspace_id     = var.workspace_id
  zone_redundancy_enabled        = true
  tags                           = var.tags
}
resource "azurerm_user_assigned_identity" "sample" {
  name                = "${var.name}-sample-id"
  resource_group_name = var.resource_group_name
  location            = var.location
  tags                = var.tags
}
resource "azurerm_user_assigned_identity" "shell" {
  name                = "${var.name}-shell-id"
  resource_group_name = var.resource_group_name
  location            = var.location
  tags                = var.tags
}
resource "azurerm_role_assignment" "pull" {
  scope                = azurerm_container_registry.this.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.sample.principal_id
}
resource "azurerm_role_assignment" "shell_pull" {
  scope                = azurerm_container_registry.this.id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.shell.principal_id
}
resource "azurerm_container_app" "sample" {
  name                         = "platform-sample"
  resource_group_name          = var.resource_group_name
  container_app_environment_id = azurerm_container_app_environment.this.id
  revision_mode                = "Multiple"
  tags                         = var.tags
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.sample.id]
  }
  registry {
    server   = azurerm_container_registry.this.login_server
    identity = azurerm_user_assigned_identity.sample.id
  }
  template {
    min_replicas = 1
    max_replicas = 3
    container {
      name   = "platform-sample"
      image  = var.sample_image
      cpu    = 0.5
      memory = "1Gi"
      liveness_probe {
        transport = "HTTP"
        port      = 8081
        path      = "/health/live"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8081
        path      = "/health/ready"
      }
    }
  }
  ingress {
    external_enabled = false
    target_port      = 8081
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}
resource "azurerm_container_app" "shell" {
  name                         = "web-shell"
  resource_group_name          = var.resource_group_name
  container_app_environment_id = azurerm_container_app_environment.this.id
  revision_mode                = "Multiple"
  tags                         = var.tags
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.shell.id]
  }
  registry {
    server   = azurerm_container_registry.this.login_server
    identity = azurerm_user_assigned_identity.shell.id
  }
  template {
    min_replicas = 1
    max_replicas = 3
    container {
      name   = "web-shell"
      image  = var.shell_image
      cpu    = 0.5
      memory = "1Gi"
      liveness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/"
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8080
        path      = "/"
      }
    }
  }
  ingress {
    external_enabled = true
    target_port      = 8080
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}
output "registry_id" { value = azurerm_container_registry.this.id }
output "registry_login_server" { value = azurerm_container_registry.this.login_server }
output "environment_id" { value = azurerm_container_app_environment.this.id }
output "sample_app_id" { value = azurerm_container_app.sample.id }
output "shell_app_id" { value = azurerm_container_app.shell.id }
output "shell_fqdn" { value = azurerm_container_app.shell.ingress[0].fqdn }
output "environment_default_domain" { value = azurerm_container_app_environment.this.default_domain }
output "security_controls" {
  value = {
    registry_admin_enabled = azurerm_container_registry.this.admin_enabled
    registry_public_access = azurerm_container_registry.this.public_network_access_enabled
  }
}
