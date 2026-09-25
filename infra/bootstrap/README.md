# Azure Terraform state bootstrap

This root is applied once by a subscription administrator before any environment root. It creates the resource group, hardened storage account, versioned/soft-deleted state container, and RBAC assignment used by CI. State locking is provided by Azure Blob leases.

```sh
az login
terraform init
terraform apply -var='subscription_id=<id>' -var='ci_principal_object_id=<oid>'
```

After the first apply, move this root's local state into the created backend and retain the recovery output in the approved operations vault. Environment/component keys are deliberately distinct (for example `dev/platform.tfstate` and `staging/platform.tfstate`). Never commit backend access keys: CI authenticates with federated OIDC and Azure RBAC.
