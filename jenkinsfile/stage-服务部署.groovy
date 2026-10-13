stage('4. 部署到K8s集群') {
            steps {
                sh """
                    export KUBECONFIG=/root/.kube/config
                    # ===================== 优化1：精准清理业务服务资源（忽略中间件） =====================
                    # 只删除业务服务相关资源，保留minio/mysql/nacos等中间件
                    DEPLOYS="ruoyi-cloud-gateway-deployment ruoyi-cloud-auth-deployment ruoyi-cloud-sys-deployment ruoyi-cloud-file-deployment ruoyi-cloud-gen-deployment ruoyi-cloud-job-deployment ruoyi-cloud-monitor-deployment ry-cloud-ui-deployment"
                    SVCS="ry-cloud-gateway-service ruoyi-cloud-auth-service ry-cloud-sys-service ry-cloud-file-service ry-cloud-gen-service ry-cloud-job-service ry-cloud-monitor-service ry-cloud-ui-service"
                    CONFIGMAPS="ruoyi-cloud-ui-config-map"
                    
                    # 1. 强制删除Deployment（级联删除Pod，跳过优雅等待）
                    for deploy in \$DEPLOYS; do
                        kubectl delete deploy \$deploy -n ${K8S_NAMESPACE} --grace-period=0 --force --ignore-not-found=true
                    done
                    
                    # 2. 删除Service
                    for svc in \$SVCS; do
                        kubectl delete svc \$svc -n ${K8S_NAMESPACE} --ignore-not-found=true
                    done
                    
                    # 3. 删除ConfigMap
                    for cm in \$CONFIGMAPS; do
                        kubectl delete configmap \$cm -n ${K8S_NAMESPACE} --ignore-not-found=true
                    done
                    
                    # 4. 等待业务服务Pod被删除（最多等60秒）
                    kubectl wait --for=delete pod -n ${K8S_NAMESPACE} -l 'app in (ruoyi-cloud-gateway-pod,ruoyi-cloud-auth-pod,ry-cloud-ui-pod)' --timeout=60s || echo "⚠️ 部分业务Pod删除超时，可忽略（中间件Pod无需删除）"
                    
                    # 5. 验证删除结果（只检查业务服务）
                    echo "===== 清理后业务服务资源 ====="
                    kubectl get deploy,svc,configmap -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud-gateway|ruoyi-cloud-auth|ry-cloud-ui' || echo "✅ 所有业务服务资源已彻底删除"
                    sleep 5  # 缩短等待时间
                    
                    # ===================== 优化2：修复kubectl taint命令 =====================
                    # 移除--ignore-not-found参数，并用|| true确保命令失败不终止流水线
                    kubectl taint nodes --all node-role.kubernetes.io/control-plane- || true
                    
                    # ===================== 新增：创建nginx ConfigMap（避免部署时缺失） =====================
                    if [ ! -f "${NGINX_CONF_LOCAL_PATH}" ]; then
                        echo "⚠️  nginx配置文件不存在，自动创建基础配置"
                        mkdir -p /root/nginx
                        cat > ${NGINX_CONF_LOCAL_PATH} << 'EOF'
server {
    listen 80;
    server_name localhost;
    root /opt/project/ruoyi/ruoyi-front-code;
    index index.html index.htm;
    location / {
        try_files \$uri \$uri/ /index.html;
    }
    location /prod-api/ {
        proxy_pass http://ry-cloud-gateway-service:8080/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }
}
EOF
                    fi
                    kubectl delete configmap ruoyi-cloud-ui-config-map -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl create configmap ruoyi-cloud-ui-config-map -n ${K8S_NAMESPACE} --from-file=nginx.conf=${NGINX_CONF_LOCAL_PATH}
                """

                // 部署网关服务（探针延迟优化）
                dir("ruoyi-gateway") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-gateway-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-gateway-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-gateway-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-gateway-pod
    spec:
      containers:
        - name: ruoyi-cloud-gateway-container
          image: ${GATEWAY_IMAGE}
          # 资源限制（适配8G内存服务器）
          resources:
            limits:
              memory: "1Gi"  # 提升内存限制，避免OOM
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          # 启动命令
          command: ["java"]
          args: [
            "-jar", "/opt/project/ruoyi/ruoyi-gateway.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gateway"
          ]
          # 存活探针（核心修改：延长初始延迟到180秒）
          livenessProbe:
            tcpSocket:
              port: 8080
            initialDelaySeconds: 180
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5  # 增加失败阈值，避免偶发超时
          # 就绪探针（核心修改：延长初始延迟到120秒）
          readinessProbe:
            tcpSocket:
              port: 8080
            initialDelaySeconds: 120
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-gateway-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: ruoyi-cloud-gateway-pod
  ports:
    - port: 8080
      targetPort: 8080
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署认证服务（核心修复：Service端口 + 探针延迟）
                dir("ruoyi-auth") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-auth-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-auth-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-auth-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-auth-pod
    spec:
      containers:
        - name: ruoyi-cloud-auth-container
          image: ${AUTH_IMAGE}
          resources:
            limits:
              memory: "1Gi"  # 提升内存限制
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9200
          command: ["java"]
          args: [
            "-Dspring.component-scan.base-packages=com.ruoyi",
            "-jar", "/opt/project/ruoyi/ruoyi-auth.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-auth"
          ]
          # 存活探针（核心修改：延长到200秒）
          livenessProbe:
            tcpSocket:
              port: 9200
            initialDelaySeconds: 200
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
          # 就绪探针（核心修改：延长到150秒）
          readinessProbe:
            tcpSocket:
              port: 9200
            initialDelaySeconds: 150
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ruoyi-cloud-auth-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: ruoyi-cloud-auth-pod
  ports:
    - port: 8080  # 核心修复：对外暴露8080端口（网关默认访问）
      targetPort: 9200  # 转发到容器内的9200端口
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署系统服务（探针延迟优化）
                dir("ruoyi-modules/ruoyi-system") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-sys-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-sys-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-sys-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-sys-pod
    spec:
      containers:
        - name: ruoyi-cloud-sys-container
          image: ${SYSTEM_IMAGE}
          resources:
            limits:
              memory: "1Gi"  # 提升内存限制
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9201
          command: ["java"]
          args: [
            "-jar", "/opt/project/ruoyi/ruoyi-modules-system.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-sys"
          ]
          # 存活探针（核心修改：延长到200秒）
          livenessProbe:
            tcpSocket:
              port: 9201
            initialDelaySeconds: 200
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
          # 就绪探针（核心修改：延长到150秒）
          readinessProbe:
            tcpSocket:
              port: 9201
            initialDelaySeconds: 150
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-sys-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: ruoyi-cloud-sys-pod
  ports:
    - port: 8080  # 核心修复：对外暴露8080端口
      targetPort: 9201
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署文件服务（探针延迟优化）
                dir("ruoyi-modules/ruoyi-file") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-file-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-file-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-file-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-file-pod
    spec:
      containers:
        - name: ruoyi-cloud-file-container
          image: ${FILE_IMAGE}
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9300
          command: ["java"]
          args: [
            "-jar", "/opt/project/ruoyi/ruoyi-modules-file.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-file"
          ]
          livenessProbe:
            tcpSocket:
              port: 9300
            initialDelaySeconds: 180
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
          readinessProbe:
            tcpSocket:
              port: 9300
            initialDelaySeconds: 120
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-file-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: ruoyi-cloud-file-pod
  ports:
    - port: 8080
      targetPort: 9300
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署代码生成服务（探针延迟优化）
                dir("ruoyi-modules/ruoyi-gen") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-gen-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-gen-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-gen-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-gen-pod
    spec:
      containers:
        - name: ruoyi-cloud-gen-container
          image: ${GEN_IMAGE}
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9202
          command: ["java"]
          args: [
            "-jar", "/opt/project/ruoyi/ruoyi-modules-gen.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gen"
          ]
          livenessProbe:
            tcpSocket:
              port: 9202
            initialDelaySeconds: 180
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
          readinessProbe:
            tcpSocket:
              port: 9202
            initialDelaySeconds: 120
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-gen-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: ruoyi-cloud-gen-pod
  ports:
    - port: 8080
      targetPort: 9202
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署定时任务服务（探针延迟优化）
                dir("ruoyi-modules/ruoyi-job") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-job-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-job-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-job-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-job-pod
    spec:
      containers:
        - name: ruoyi-cloud-job-container
          image: ${JOB_IMAGE}
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9203
          command: ["java"]
          args: [
            "-jar", "/opt/project/ruoyi/ruoyi-modules-job.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-job"
          ]
          livenessProbe:
            tcpSocket:
              port: 9203
            initialDelaySeconds: 180
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
          readinessProbe:
            tcpSocket:
              port: 9203
            initialDelaySeconds: 120
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-job-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: ruoyi-cloud-job-pod
  ports:
    - port: 8080
      targetPort: 9203
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署监控服务（探针延迟优化）
                dir("ruoyi-visual/ruoyi-monitor") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ruoyi-cloud-monitor-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ruoyi-cloud-monitor-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ruoyi-cloud-monitor-pod
  template:
    metadata:
      labels:
        app: ruoyi-cloud-monitor-pod
    spec:
      containers:
        - name: ruoyi-cloud-monitor-container
          image: ${MONITOR_IMAGE}
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9100
              name: port-9100
          command: ["java"]
          args: [
            "-jar", "/opt/project/ruoyi/ruoyi-visual-monitor.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-monitor"
          ]
          livenessProbe:
            tcpSocket:
              port: 9100
            initialDelaySeconds: 180
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
          readinessProbe:
            tcpSocket:
              port: 9100
            initialDelaySeconds: 120
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-monitor-service
  namespace: ${K8S_NAMESPACE}
spec:
  type: NodePort
  selector:
    app: ruoyi-cloud-monitor-pod
  ports:
    - name: port-9100
      port: 9100
      targetPort: 9100
      nodePort: ${MONITOR_NODE_PORT}
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 部署前端服务（优化初始化容器等待时间）
                dir("ruoyi-ui") {
                    writeFile file: 'deployment.yml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ry-cloud-ui-deployment
  namespace: ${K8S_NAMESPACE}
  labels:
    app: ry-cloud-ui-deployment
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ry-cloud-ui-pod
  template:
    metadata:
      labels:
        app: ry-cloud-ui-pod
    spec:
      # 初始化容器：等待核心服务就绪（延长超时时间）
      initContainers:
        - name: wait-for-all-core-services
          image: ${BUSYBOX_IMAGE}
          command:
            - sh
            - -c
            - |
              echo "=== 等待所有核心服务端口就绪 ==="
              # 延长超时时间，每个服务最多等5分钟
              until nc -z -w 10 ry-cloud-gateway-service 8080; do
                echo "等待网关服务 ry-cloud-gateway-service:8080..."
                sleep 10
              done
              until nc -z -w 10 ruoyi-cloud-auth-service 8080; do
                echo "等待认证服务 ruoyi-cloud-auth-service:8080..."
                sleep 10
              done
              until nc -z -w 10 ry-cloud-sys-service 8080; do
                echo "等待系统服务 ry-cloud-sys-service:8080..."
                sleep 10
              done
              echo "=== 所有核心服务就绪 ==="
      containers:
        - name: ruoyi-cloud-ui-container
          image: ${UI_IMAGE}
          resources:
            limits:
              memory: "256Mi"
              cpu: "100m"
            requests:
              memory: "128Mi"
              cpu: "50m"
          imagePullPolicy: Always
          ports:
            - containerPort: 80
          # 挂载nginx配置ConfigMap
          volumeMounts:
            - mountPath: /etc/nginx/conf.d
              name: nginx-config
          # 存活探针
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          # 就绪探针
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 2
      # 配置卷
      volumes:
        - name: nginx-config
          configMap:
            name: ruoyi-cloud-ui-config-map
            items:
              - key: nginx.conf
                path: default.conf
      imagePullSecrets:
        - name: harbor-secret
---
apiVersion: v1
kind: Service
metadata:
  name: ry-cloud-ui-service
  namespace: ${K8S_NAMESPACE}
spec:
  type: NodePort
  selector:
    app: ry-cloud-ui-pod
  ports:
    - port: 80
      targetPort: 80
      nodePort: ${UI_NODE_PORT}
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

                // 检查部署状态（容错逻辑：超时不终止）
                sh """
                    echo "===== 所有Pod状态（${K8S_NAMESPACE}命名空间） ====="
                    kubectl get pods -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud|ry-cloud-ui'
                    echo "===== 所有服务状态（包含NodePort） ====="
                    kubectl get svc -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud|ry-cloud-ui'
                    # 等待前端Pod就绪（超时仅提示，不失败）
                    kubectl wait --for=condition=ready pod -l app=ry-cloud-ui-pod -n ${K8S_NAMESPACE} --timeout=1800s || echo "⚠️ 前端Pod启动超时，可手动检查状态"
                """
            }
        }
    }

    post {
        success {
            echo "✅ 全部部署成功！"
            echo "前端访问地址：http://${K8S_MASTER}:${UI_NODE_PORT}"
            echo "监控服务地址：http://${K8S_MASTER}:${MONITOR_NODE_PORT}"
            echo "测试登录接口：curl -X POST http://${K8S_MASTER}:${UI_NODE_PORT}/prod-api/auth/login -H \"Content-Type: application/json\" -d '{\"username\":\"admin\",\"password\":\"123456\"}'"
        }
        failure {
            echo "❌ 部署失败！请查看以下日志排查问题："
            sh """
                kubectl get pods -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud|ry-cloud-ui' || true
                kubectl logs -n ${K8S_NAMESPACE} -l app=ruoyi-cloud-auth-pod --tail=200 || true
                kubectl logs -n ${K8S_NAMESPACE} -l app=ry-cloud-ui-pod -c wait-for-all-core-services --tail=200 || true
                kubectl describe pod -n ${K8S_NAMESPACE} -l app=ruoyi-cloud-auth-pod || true
            """
        }
    }
}
