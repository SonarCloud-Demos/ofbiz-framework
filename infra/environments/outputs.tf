output "deployment" {
  value = {
    resource_group     = module.resource_group.name
    registry           = module.container_platform.registry_login_server
    sample_app_id      = module.container_platform.sample_app_id
    shell_app_id       = module.container_platform.shell_app_id
    frontdoor_hostname = module.edge.frontdoor_endpoint_host_name
  }
}
