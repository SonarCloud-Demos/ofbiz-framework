# Reviewed Azure modules

Modules are deliberately capability-sized: resource group, network/private DNS, observability, data platform, container platform, and edge. Environment roots compose them explicitly; Terraform workspaces are not used for isolation. Modules default to private data-plane access and expose only the identifiers required by downstream modules.
