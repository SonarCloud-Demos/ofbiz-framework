variable "name" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "address_space" { type = list(string) }
variable "tags" { type = map(string) }
resource "azurerm_virtual_network" "this" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  address_space       = var.address_space
  tags                = var.tags
}
resource "azurerm_subnet" "apps" {
  name                 = "container-apps"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = [cidrsubnet(var.address_space[0], 3, 0)]
  delegation {
    name = "container-apps"
    service_delegation { name = "Microsoft.App/environments" }
  }
}
resource "azurerm_subnet" "private_endpoints" {
  name                              = "private-endpoints"
  resource_group_name               = var.resource_group_name
  virtual_network_name              = azurerm_virtual_network.this.name
  address_prefixes                  = [cidrsubnet(var.address_space[0], 3, 1)]
  private_endpoint_network_policies = "Disabled"
}
resource "azurerm_network_security_group" "apim" {
  name                = "${var.name}-apim-nsg"
  resource_group_name = var.resource_group_name
  location            = var.location
  tags                = var.tags
  security_rule {
    name                       = "ApiManagementControlPlane"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "3443"
    source_address_prefix      = "ApiManagement"
    destination_address_prefix = "VirtualNetwork"
  }
}
resource "azurerm_subnet" "apim" {
  name                 = "api-management"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = [cidrsubnet(var.address_space[0], 3, 2)]
  service_endpoints    = ["Microsoft.KeyVault", "Microsoft.Sql", "Microsoft.Storage"]
}
resource "azurerm_subnet_network_security_group_association" "apim" {
  subnet_id                 = azurerm_subnet.apim.id
  network_security_group_id = azurerm_network_security_group.apim.id
}
locals { zones = toset(["privatelink.vaultcore.azure.net", "privatelink.blob.core.windows.net", "privatelink.postgres.database.azure.com", "privatelink.servicebus.windows.net", "privatelink.azurecr.io"]) }
resource "azurerm_private_dns_zone" "this" {
  for_each            = local.zones
  name                = each.value
  resource_group_name = var.resource_group_name
  tags                = var.tags
}
resource "azurerm_private_dns_zone_virtual_network_link" "this" {
  for_each              = local.zones
  name                  = "${var.name}-${replace(each.value, ".", "-")}"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.this[each.key].name
  virtual_network_id    = azurerm_virtual_network.this.id
}
output "vnet_id" { value = azurerm_virtual_network.this.id }
output "apps_subnet_id" { value = azurerm_subnet.apps.id }
output "private_endpoint_subnet_id" { value = azurerm_subnet.private_endpoints.id }
output "apim_subnet_id" { value = azurerm_subnet.apim.id }
output "private_dns_zone_ids" { value = { for k, v in azurerm_private_dns_zone.this : k => v.id } }
