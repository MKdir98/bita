# Kubernetes Deployment Guide

## Document Information

**Project**: BITA - Next-Generation Enterprise Service Gateway  
**Version**: 2.0  
**Date**: January 28, 2026  
**Purpose**: Kubernetes Deployment and Operations Guide

---

## 1. Prerequisites

### 1.1 Required Software

| Component | Version | Purpose |
|-----------|---------|---------|
| Kubernetes | 1.26+ | Container orchestration |
| kubectl | 1.26+ | Kubernetes CLI |
| Helm | 3.12+ | Package manager |
| Docker | 24+ | Container runtime |

### 1.2 Required Access

- Kubernetes cluster admin access
- Container registry credentials
- Domain name with DNS access
- SSL certificates (or cert-manager)

### 1.3 Cluster Requirements

**Minimum for Development:**
- 3 worker nodes
- 4 CPU cores per node
- 8 GB RAM per node
- 100 GB storage

**Recommended for Production:**
- 5+ worker nodes
- 8 CPU cores per node
- 32 GB RAM per node
- 500 GB SSD storage
- Multiple availability zones

---

## 2. Architecture Overview

### 2.1 Namespace Structure

```
kubernetes-cluster/
├── ingress-nginx/          # Nginx Ingress Controller
├── cert-manager/           # SSL certificate management
├── bita-esm/               # ESM services
│   ├── esm-backend
│   ├── esm-frontend
│   ├── postgresql
│   └── redis
├── bita-esb/               # ESB services
│   ├── esb-core
│   ├── camel-k-operator
│   └── integrations/       # Dynamic service pods
├── bita-messaging/         # Messaging infrastructure
│   ├── kafka
│   ├── zookeeper
│   └── activemq
└── bita-monitoring/        # Observability
    ├── elasticsearch
    ├── kibana
    ├── prometheus
    └── grafana
```

### 2.2 Network Topology

```
                                Internet
                                   │
                           ┌───────▼───────┐
                           │  Load Balancer │
                           │  (Cloud/Metal) │
                           └───────┬───────┘
                                   │
┌──────────────────────────────────┼──────────────────────────────────────┐
│  Kubernetes Cluster              │                                       │
│                           ┌──────▼──────┐                               │
│                           │   Nginx     │                               │
│                           │   Ingress   │                               │
│                           └──────┬──────┘                               │
│                    ┌─────────────┼─────────────┐                        │
│                    │             │             │                        │
│            ┌───────▼───────┐ ┌───▼─────┐ ┌────▼────┐                   │
│            │  ESM Frontend │ │   ESM   │ │   ESB   │                   │
│            │   (React)     │ │ Backend │ │  Core   │                   │
│            └───────────────┘ └────┬────┘ └────┬────┘                   │
│                                   │           │                         │
│                    ┌──────────────┼───────────┘                         │
│                    │              │                                     │
│            ┌───────▼───────┐ ┌────▼────┐                               │
│            │  PostgreSQL   │ │  Kafka  │                               │
│            └───────────────┘ └─────────┘                               │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Deployment

### 3.1 Create Namespaces

```bash
# Create all namespaces
kubectl create namespace ingress-nginx
kubectl create namespace cert-manager
kubectl create namespace bita-esm
kubectl create namespace bita-esb
kubectl create namespace bita-messaging
kubectl create namespace bita-monitoring

# Label namespaces for network policies
kubectl label namespace bita-esm app.kubernetes.io/part-of=bita
kubectl label namespace bita-esb app.kubernetes.io/part-of=bita
kubectl label namespace bita-messaging app.kubernetes.io/part-of=bita
```

### 3.2 Install Nginx Ingress Controller

```bash
# Add Helm repo
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

# Install
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --set controller.replicaCount=2 \
  --set controller.nodeSelector."kubernetes\.io/os"=linux \
  --set controller.service.type=LoadBalancer \
  --set controller.config.proxy-body-size="50m" \
  --set controller.config.proxy-read-timeout="300" \
  --set controller.config.proxy-send-timeout="300"

# Verify
kubectl get svc -n ingress-nginx
```

### 3.3 Install Cert-Manager (Optional but recommended)

```bash
# Add Helm repo
helm repo add jetstack https://charts.jetstack.io
helm repo update

# Install CRDs
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.crds.yaml

# Install cert-manager
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --set installCRDs=false \
  --set replicaCount=2

# Create ClusterIssuer for Let's Encrypt
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

### 3.4 Deploy Kafka

```bash
# Add Strimzi operator
kubectl create -f 'https://strimzi.io/install/latest?namespace=bita-messaging' -n bita-messaging

# Wait for operator
kubectl wait deployment/strimzi-cluster-operator \
  --for=condition=available \
  --timeout=300s \
  -n bita-messaging

# Create Kafka cluster
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: bita-kafka
  namespace: bita-messaging
spec:
  kafka:
    version: 3.6.0
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
      default.replication.factor: 3
      min.insync.replicas: 2
    storage:
      type: persistent-claim
      size: 100Gi
      class: standard  # Adjust for your storage class
    resources:
      requests:
        memory: 2Gi
        cpu: "500m"
      limits:
        memory: 4Gi
        cpu: "2"
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 20Gi
      class: standard
    resources:
      requests:
        memory: 1Gi
        cpu: "250m"
  entityOperator:
    topicOperator: {}
    userOperator: {}
EOF

# Create topics
cat <<EOF | kubectl apply -f -
apiVersion: kafka.strimzi.io/v1beta2
kind: KafkaTopic
metadata:
  name: bita.config.changes
  namespace: bita-messaging
  labels:
    strimzi.io/cluster: bita-kafka
spec:
  partitions: 6
  replicas: 3
  config:
    retention.ms: 604800000  # 7 days
EOF
```

### 3.5 Deploy PostgreSQL

```bash
# Create secrets
kubectl create secret generic postgres-credentials \
  --namespace bita-esm \
  --from-literal=POSTGRES_USER=bita \
  --from-literal=POSTGRES_PASSWORD=$(openssl rand -base64 32) \
  --from-literal=POSTGRES_DB=bita_esm

# Deploy PostgreSQL
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgresql
  namespace: bita-esm
spec:
  serviceName: postgresql
  replicas: 1
  selector:
    matchLabels:
      app: postgresql
  template:
    metadata:
      labels:
        app: postgresql
    spec:
      containers:
      - name: postgresql
        image: postgres:15
        ports:
        - containerPort: 5432
        envFrom:
        - secretRef:
            name: postgres-credentials
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "4Gi"
            cpu: "2"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: standard
      resources:
        requests:
          storage: 50Gi
---
apiVersion: v1
kind: Service
metadata:
  name: postgresql
  namespace: bita-esm
spec:
  ports:
  - port: 5432
  selector:
    app: postgresql
  clusterIP: None
EOF
```

### 3.6 Deploy Redis

```bash
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: bita-esm
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: bita-esm
spec:
  ports:
  - port: 6379
  selector:
    app: redis
EOF
```

### 3.7 Deploy ESM Backend

```bash
# Create ConfigMap
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: esm-backend-config
  namespace: bita-esm
data:
  application.yml: |
    spring:
      datasource:
        url: jdbc:postgresql://postgresql:5432/bita_esm
      kafka:
        bootstrap-servers: bita-kafka-kafka-bootstrap.bita-messaging:9092
      redis:
        host: redis
        port: 6379
    
    llm:
      provider: openai
      model: gpt-4
    
    server:
      port: 8080
EOF

# Create secrets for LLM API key
kubectl create secret generic llm-api-key \
  --namespace bita-esm \
  --from-literal=OPENAI_API_KEY=sk-xxx

# Deploy ESM Backend
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: esm-backend
  namespace: bita-esm
spec:
  replicas: 2
  selector:
    matchLabels:
      app: esm-backend
  template:
    metadata:
      labels:
        app: esm-backend
    spec:
      containers:
      - name: esm-backend
        image: registry.example.com/bita/esm-backend:latest
        ports:
        - containerPort: 8080
        envFrom:
        - secretRef:
            name: postgres-credentials
        - secretRef:
            name: llm-api-key
        volumeMounts:
        - name: config
          mountPath: /app/config
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "4Gi"
            cpu: "2"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: config
        configMap:
          name: esm-backend-config
---
apiVersion: v1
kind: Service
metadata:
  name: esm-backend
  namespace: bita-esm
spec:
  ports:
  - port: 8080
  selector:
    app: esm-backend
EOF
```

### 3.8 Deploy ESM Frontend

```bash
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: esm-frontend
  namespace: bita-esm
spec:
  replicas: 2
  selector:
    matchLabels:
      app: esm-frontend
  template:
    metadata:
      labels:
        app: esm-frontend
    spec:
      containers:
      - name: esm-frontend
        image: registry.example.com/bita/esm-frontend:latest
        ports:
        - containerPort: 80
        env:
        - name: API_URL
          value: "http://esm-backend:8080"
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: esm-frontend
  namespace: bita-esm
spec:
  ports:
  - port: 80
  selector:
    app: esm-frontend
EOF
```

### 3.9 Install Camel K Operator

```bash
# Install Camel K operator
kubectl apply -f https://github.com/apache/camel-k/releases/download/v2.1.0/camel-k-crds.yaml
kubectl apply -n bita-esb -f https://github.com/apache/camel-k/releases/download/v2.1.0/camel-k-operator.yaml

# Wait for operator
kubectl wait deployment/camel-k-operator \
  --for=condition=available \
  --timeout=300s \
  -n bita-esb

# Create IntegrationPlatform
cat <<EOF | kubectl apply -f -
apiVersion: camel.apache.org/v1
kind: IntegrationPlatform
metadata:
  name: camel-k
  namespace: bita-esb
spec:
  build:
    registry:
      address: registry.example.com
      secret: registry-credentials
    maven:
      settings:
        configMapKeyRef:
          key: settings.xml
          name: maven-settings
  traits:
    container:
      configuration:
        requestCPU: "500m"
        requestMemory: "512Mi"
    health:
      configuration:
        enabled: true
EOF
```

### 3.10 Deploy ESB Core

```bash
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: esb-core
  namespace: bita-esb
spec:
  replicas: 2
  selector:
    matchLabels:
      app: esb-core
  template:
    metadata:
      labels:
        app: esb-core
    spec:
      serviceAccountName: esb-deployer
      containers:
      - name: esb-core
        image: registry.example.com/bita/esb-core:latest
        ports:
        - containerPort: 8080
        env:
        - name: ESM_URL
          value: "http://esm-backend.bita-esm:8080"
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "bita-kafka-kafka-bootstrap.bita-messaging:9092"
        - name: ESB_DOMAIN
          value: "Tehran"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "4Gi"
            cpu: "2"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 30
---
apiVersion: v1
kind: Service
metadata:
  name: esb-core
  namespace: bita-esb
spec:
  ports:
  - port: 8080
  selector:
    app: esb-core
---
# RBAC for deploying Camel K integrations
apiVersion: v1
kind: ServiceAccount
metadata:
  name: esb-deployer
  namespace: bita-esb
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: integration-deployer
  namespace: bita-esb
rules:
- apiGroups: ["camel.apache.org"]
  resources: ["integrations"]
  verbs: ["get", "list", "create", "update", "delete", "patch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: esb-deployer-binding
  namespace: bita-esb
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: integration-deployer
subjects:
- kind: ServiceAccount
  name: esb-deployer
  namespace: bita-esb
EOF
```

### 3.11 Configure Ingress

```bash
cat <<EOF | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: bita-ingress
  namespace: bita-esm
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - esm.example.com
    - esb.example.com
    secretName: bita-tls
  rules:
  - host: esm.example.com
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: esm-backend
            port:
              number: 8080
      - path: /
        pathType: Prefix
        backend:
          service:
            name: esm-frontend
            port:
              number: 80
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: esb-ingress
  namespace: bita-esb
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - esb.example.com
    secretName: bita-esb-tls
  rules:
  - host: esb.example.com
    http:
      paths:
      - path: /services
        pathType: Prefix
        backend:
          service:
            name: esb-core
            port:
              number: 8080
EOF
```

---

## 4. Camel K Integration Deployment Flow

### 4.1 How ESB Deploys Routes

When a new route is created in ESM:

```
1. ESM creates route configuration
   ↓
2. ESM publishes ROUTE_CREATED event to Kafka
   ↓
3. ESB Core receives event
   ↓
4. ESB fetches route details from ESM API
   ↓
5. ESB generates Kamel YAML
   ↓
6. ESB applies Integration CRD to Kubernetes
   ↓
7. Camel K Operator:
   a. Detects new Integration
   b. Builds container image
   c. Creates Deployment
   d. Service is live!
```

### 4.2 Example Integration CRD

```yaml
apiVersion: camel.apache.org/v1
kind: Integration
metadata:
  name: payment-service-v1
  namespace: bita-esb
  labels:
    bita.route.id: "123"
    bita.service.id: "50"
spec:
  replicas: 2
  
  dependencies:
    - "mvn:org.apache.camel:camel-cxf:4.0.0"
    - "mvn:org.apache.cxf:cxf-rt-ws-security:4.0.0"
    - "mvn:ir.iais.bita:bita-common:1.0.0"
  
  traits:
    container:
      configuration:
        requestCPU: "500m"
        requestMemory: "512Mi"
        limitCPU: "2"
        limitMemory: "2Gi"
    health:
      configuration:
        enabled: true
        readinessProbeEnabled: true
        livenessProbeEnabled: true
    service:
      configuration:
        enabled: true
        type: ClusterIP
    prometheus:
      configuration:
        enabled: true
  
  configuration:
    - type: property
      value: esb.domain=Tehran
    - type: secret
      value: keystore-secret
  
  sources:
    - name: PaymentServiceRoute.java
      content: |
        import org.apache.camel.builder.RouteBuilder;
        import ir.iais.bita.processor.*;
        
        public class PaymentServiceRoute extends RouteBuilder {
            @Override
            public void configure() throws Exception {
                from("platform-http:/services/PaymentService_v1")
                    .routeId("payment-service-v1-route")
                    .bean(CheckAccessProcessor.class)
                    .bean(PrepareHeadersProcessor.class)
                    .choice()
                        .when(header("targetDomain").isEqualTo("Tehran"))
                            .bean(CallingServiceProcessor.class)
                        .otherwise()
                            .to("activemq:queue:x509-push-${header.targetDomain}")
                    .end()
                    .bean(PrepareResultProcessor.class)
                    .wireTap("seda:logChannel");
            }
        }
```

### 4.3 ESB Core Code for Deployment

```java
@Service
@RequiredArgsConstructor
public class RouteDeployer {
    
    private final KubernetesClient kubernetesClient;
    private final KamelYamlGenerator generator;
    private final EsmApiClient esmApiClient;
    
    public void deployRoute(Long routeId) {
        // Fetch route config from ESM
        RouteDto route = esmApiClient.getRoute(routeId);
        
        // Generate Kamel YAML
        String kamelYaml = generator.generate(route);
        
        // Parse and apply
        Integration integration = Serialization.unmarshal(kamelYaml, Integration.class);
        
        kubernetesClient.resource(integration)
            .inNamespace("bita-esb")
            .createOrReplace();
        
        log.info("Deployed route {} as integration {}", routeId, integration.getMetadata().getName());
    }
    
    public void undeployRoute(Long routeId) {
        String integrationName = "route-" + routeId;
        
        kubernetesClient.resources(Integration.class)
            .inNamespace("bita-esb")
            .withName(integrationName)
            .delete();
        
        log.info("Undeployed route {}", routeId);
    }
}
```

---

## 5. Zero Downtime Updates

### 5.1 Rolling Update Strategy

Camel K uses Kubernetes rolling updates by default:

```yaml
spec:
  replicas: 2
  traits:
    deployment:
      configuration:
        strategy: RollingUpdate
        rollingUpdateMaxSurge: 1
        rollingUpdateMaxUnavailable: 0
```

### 5.2 Graceful Shutdown

```java
// In Camel route configuration
from("platform-http:/services/MyService")
    .shutdownRoute(ShutdownRoute.Defer)  // Wait for in-flight
    .shutdownRunningTask(ShutdownRunningTask.CompleteCurrentTaskOnly);
```

### 5.3 Health Checks

```yaml
traits:
  health:
    configuration:
      enabled: true
      readinessProbeEnabled: true
      livenessProbeEnabled: true
      readinessSuccessThreshold: 1
      readinessFailureThreshold: 3
      readinessInitialDelay: 10
      readinessPeriod: 10
```

---

## 6. Monitoring Setup

### 6.1 Deploy Prometheus Stack

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace bita-monitoring \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword=admin123
```

### 6.2 ServiceMonitor for ESM

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: esm-backend-monitor
  namespace: bita-monitoring
spec:
  selector:
    matchLabels:
      app: esm-backend
  namespaceSelector:
    matchNames:
    - bita-esm
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 15s
```

### 6.3 Deploy Elasticsearch + Kibana

```bash
# Add Elastic Helm repo
helm repo add elastic https://helm.elastic.co
helm repo update

# Install Elasticsearch
helm install elasticsearch elastic/elasticsearch \
  --namespace bita-monitoring \
  --set replicas=3 \
  --set minimumMasterNodes=2 \
  --set resources.requests.memory=2Gi \
  --set volumeClaimTemplate.resources.requests.storage=100Gi

# Install Kibana
helm install kibana elastic/kibana \
  --namespace bita-monitoring \
  --set elasticsearchHosts="http://elasticsearch-master:9200"
```

---

## 7. Backup and Recovery

### 7.1 PostgreSQL Backup

```bash
# Create backup CronJob
cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: bita-esm
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/sh
            - -c
            - |
              pg_dump -h postgresql -U \$POSTGRES_USER \$POSTGRES_DB | \
              gzip > /backup/bita_esm_\$(date +%Y%m%d).sql.gz
            envFrom:
            - secretRef:
                name: postgres-credentials
            volumeMounts:
            - name: backup
              mountPath: /backup
          restartPolicy: OnFailure
          volumes:
          - name: backup
            persistentVolumeClaim:
              claimName: backup-pvc
EOF
```

### 7.2 Disaster Recovery

```bash
# Restore from backup
kubectl exec -it postgresql-0 -n bita-esm -- \
  psql -U bita -d bita_esm < /backup/bita_esm_20260128.sql
```

---

## 8. Scaling

### 8.1 Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: esm-backend-hpa
  namespace: bita-esm
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: esm-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### 8.2 Camel K Integration Scaling

```yaml
# Scale specific integration
apiVersion: camel.apache.org/v1
kind: Integration
metadata:
  name: payment-service-v1
spec:
  replicas: 5  # Increase replicas
```

---

## 9. Troubleshooting

### 9.1 Common Commands

```bash
# Check pod status
kubectl get pods -n bita-esm
kubectl get pods -n bita-esb

# Check logs
kubectl logs -f deployment/esm-backend -n bita-esm
kubectl logs -f deployment/esb-core -n bita-esb

# Check Camel K integrations
kubectl get integrations -n bita-esb
kubectl describe integration payment-service-v1 -n bita-esb

# Check events
kubectl get events -n bita-esb --sort-by='.lastTimestamp'

# Debug pod
kubectl exec -it deployment/esm-backend -n bita-esm -- /bin/sh
```

### 9.2 Common Issues

**Issue: Integration stuck in Building phase**
```bash
# Check build logs
kubectl logs -l camel.apache.org/integration=payment-service-v1 -c kit-builder -n bita-esb
```

**Issue: Kafka consumer lag**
```bash
# Check consumer lag
kubectl exec -it bita-kafka-kafka-0 -n bita-messaging -- \
  bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group esb-Tehran --describe
```

**Issue: Database connection issues**
```bash
# Test database connectivity
kubectl run psql-test --rm -it --image=postgres:15 -n bita-esm -- \
  psql -h postgresql -U bita -d bita_esm -c "SELECT 1"
```

---

## 10. Production Checklist

### 10.1 Pre-Deployment

- [ ] SSL certificates configured
- [ ] Secrets created (database, API keys, registry)
- [ ] Storage classes available
- [ ] Network policies defined
- [ ] Resource quotas set
- [ ] RBAC configured

### 10.2 Post-Deployment

- [ ] All pods running
- [ ] Health checks passing
- [ ] Ingress accessible
- [ ] Monitoring working
- [ ] Backups scheduled
- [ ] Alerts configured

### 10.3 Security

- [ ] Network policies enforced
- [ ] Pod security policies applied
- [ ] Secrets encrypted at rest
- [ ] RBAC least privilege
- [ ] Image scanning enabled

---

**Document Version**: 1.0  
**Last Updated**: January 28, 2026
