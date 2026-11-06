# Deploying cicd-demo to AWS EKS

Pipeline: GitHub Actions → build/test → Docker image → Amazon ECR → `kubectl apply` on EKS.
AWS authentication uses GitHub OIDC, so there are no AWS access keys stored in GitHub.

## One-time AWS setup

Replace the placeholders (`<ACCOUNT_ID>`, `<REGION>`, etc.) with your values.

### 1. ECR repository
```bash
aws ecr create-repository --repository-name cicd-demo --region <REGION>
```

### 2. EKS cluster (skip if you already have one)
```bash
eksctl create cluster --name cicd-demo-cluster --region <REGION> \
  --nodes 2 --node-type t3.medium
```
Nodes pull from ECR using their node IAM role (AmazonEC2ContainerRegistryReadOnly is included by default).
The workflow builds `linux/amd64` images; if you use Graviton (arm64) nodes, change `--platform` in the workflow.

### 3. GitHub OIDC provider in IAM (once per AWS account)
```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com
```

### 4. IAM role for GitHub Actions
Trust policy (`trust.json`) — restricts the role to your repo's `main` branch:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike":   { "token.actions.githubusercontent.com:sub": "repo:<GITHUB_USER>/<REPO>:ref:refs/heads/main" }
    }
  }]
}
```
Permissions policy (`permissions.json`):
```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    { "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "arn:aws:ecr:<REGION>:<ACCOUNT_ID>:repository/cicd-demo" },
    { "Effect": "Allow", "Action": "eks:DescribeCluster",
      "Resource": "arn:aws:eks:<REGION>:<ACCOUNT_ID>:cluster/cicd-demo-cluster" }
  ]
}
```
```bash
aws iam create-role --role-name github-actions-eks-deploy --assume-role-policy-document file://trust.json
aws iam put-role-policy --role-name github-actions-eks-deploy --policy-name deploy --policy-document file://permissions.json
```

### 5. Give the role access inside the cluster (EKS access entries)
```bash
aws eks create-access-entry --cluster-name cicd-demo-cluster --region <REGION> \
  --principal-arn arn:aws:iam::<ACCOUNT_ID>:role/github-actions-eks-deploy

aws eks associate-access-policy --cluster-name cicd-demo-cluster --region <REGION> \
  --principal-arn arn:aws:iam::<ACCOUNT_ID>:role/github-actions-eks-deploy \
  --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
  --access-scope type=cluster
```
The pipeline creates a namespace, so it needs cluster scope. After the first deploy you can create the
namespace yourself and tighten this to `AmazonEKSEditPolicy` scoped to the `cicd-demo` namespace.
(Clusters still on the `aws-auth` ConfigMap need an `aws-auth` mapping instead.)

## GitHub repository configuration

Settings → Secrets and variables → Actions

| Type     | Name               | Example                                              |
|----------|--------------------|------------------------------------------------------|
| Secret   | `AWS_ROLE_ARN`     | `arn:aws:iam::<ACCOUNT_ID>:role/github-actions-eks-deploy` |
| Variable | `AWS_REGION`       | `ap-south-1`                                         |
| Variable | `ECR_REPOSITORY`   | `cicd-demo`                                          |
| Variable | `EKS_CLUSTER_NAME` | `cicd-demo-cluster`                                  |

## Deploy and verify

Push to `main`. The `deploy` job runs after `build` succeeds. Then:
```bash
aws eks update-kubeconfig --name cicd-demo-cluster --region <REGION>
kubectl get svc cicd-demo -n cicd-demo      # EXTERNAL-IP is the NLB DNS name (can take a few minutes)
curl http://<EXTERNAL-IP>/health            # -> Application is running!
```

## Local image test
```bash
docker build -t cicd-demo .
docker run --rm -p 8080:8080 cicd-demo
```
