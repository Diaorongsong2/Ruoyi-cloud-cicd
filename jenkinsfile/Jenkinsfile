pipeline {
    agent {
        node {
            label 'ruoyi-cloud'
        }
    }

    environment {
        K8S_MASTER = "192.168.44.20"
        K8S_NAMESPACE = "default"

        HARBOR_ADDR = "harbor:443"
        HARBOR_AUTH_ADDR = "https://harbor:443"
        HARBOR_PROJECT = "ruoyi-cloud"
        HARBOR_CRED_ID = "harbor-ruoyi"
        DOCKER_REGISTRY = "${HARBOR_ADDR}"

        BUILD_TAG = "${BUILD_NUMBER}-${currentBuild.startTimeInMillis}"

        JAVA_HOME = "/usr/local/java/jdk1.8.0_421"
        MAVEN_HOME = "/usr/local/maven/apache-maven-3.8.9"
        MAVEN_CMD = "${MAVEN_HOME}/bin/mvn"
        NODE_HOME = "/usr/local/node/14.4.0/bin"
        PATH = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${NODE_HOME}:/usr/bin:/bin:/usr/local/bin"
        LOCAL_REPO_PATH = "/home/jenkins/.m2/repository"

        NACOS_INNER_ADDR = "ry-cloud-nacos-service:8848"
        MYSQL_ADDR = "ry-cloud-mysql-service:3306"
        MYSQL_USER = "root"
        MYSQL_PWD = "123456"
        REDIS_ADDR = "ry-cloud-redis-service:6379"
        REDIS_PWD = "123456"

        JENKINS_WORKSPACE = "${WORKSPACE}"
        NODE_OPTIONS = "--max_old_space_size=1024"
        GATEWAY_SERVICE_NAME = "ruoyi-gateway.${K8S_NAMESPACE}.svc.cluster.local"

        GATEWAY_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-gateway:${BUILD_TAG}"
        AUTH_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-auth:${BUILD_TAG}"
        SYSTEM_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-system:${BUILD_TAG}"
        FILE_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-file:${BUILD_TAG}"
        GEN_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-gen:${BUILD_TAG}"
        JOB_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-job:${BUILD_TAG}"
        MONITOR_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-monitor:${BUILD_TAG}"
        UI_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-ui:${BUILD_TAG}"

        OPENJDK_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/openjdk:8-jre-slim"
        BUSYBOX_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/busybox:1.35"
        NGINX_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/nginx:alpine"

        UI_NODE_PORT = "30080"       
        MONITOR_NODE_PORT = "9100"

        TZ_PARAM = "-Duser.timezone=Asia/Shanghai"
    }

    stages {
        stage('1. 后端构建') {
            steps {
                sh '''
                    cd ${JENKINS_WORKSPACE}
                    ${MAVEN_CMD} clean install -DskipTests -U
                    echo "===== 生成的 jar 包列表 ====="
                    find . -name "*.jar" -path "*/target/*" | grep -E "(file|gen|job|monitor|system|auth|gateway)"
                '''
            }
        }

        stage('2. 前端打包') {
            steps {
                sh '''
                    cd ${JENKINS_WORKSPACE}/ruoyi-ui
                    npm config set registry https://registry.npmmirror.com/
                    npm cache clean --force
                    npm install --no-audit --no-fund --legacy-peer-deps
                    NODE_OPTIONS="--max_old_space_size=2048" npm run build:prod
                '''
                script {
                    if (!fileExists("${JENKINS_WORKSPACE}/ruoyi-ui/dist/index.html")) {
                        error "前端打包失败！未生成dist/index.html文件"
                    }
                    echo "✅ 前端打包成功，dist目录文件列表："
                    sh "ls -lh ${JENKINS_WORKSPACE}/ruoyi-ui/dist/"
                }
            }
        }

        stage('3. 构建并推送镜像') {
            steps {
                withCredentials([usernamePassword(credentialsId: "${HARBOR_CRED_ID}", passwordVariable: 'HARBOR_PWD', usernameVariable: 'HARBOR_USER')]) {
                    sh "echo \"\${HARBOR_PWD}\" | docker login ${HARBOR_AUTH_ADDR} -u \${HARBOR_USER} --password-stdin"

                    dir("ruoyi-gateway") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-gateway
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-gateway.jar ruoyi-gateway.jar
EXPOSE 8080
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-gateway.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gateway"]
                        """.stripIndent()
                        sh "docker build -t ${GATEWAY_IMAGE} . && docker push ${GATEWAY_IMAGE}"
                    }

                    dir("ruoyi-auth") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-auth
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-auth.jar ruoyi-auth.jar
EXPOSE 9200
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-auth.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-auth"]
                        """.stripIndent()
                        sh "docker build -t ${AUTH_IMAGE} . && docker push ${AUTH_IMAGE}"
                    }

                    dir("ruoyi-modules/ruoyi-system") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-sys
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-system.jar ruoyi-modules-system.jar
EXPOSE 9201
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-modules-system.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-sys"]
                        """.stripIndent()
                        sh "docker build -t ${SYSTEM_IMAGE} . && docker push ${SYSTEM_IMAGE}"
                    }

                    dir("ruoyi-modules/ruoyi-file") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-file
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-file.jar ruoyi-modules-file.jar
EXPOSE 9300
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-modules-file.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-file"]
                        """.stripIndent()
                        sh "docker build -t ${FILE_IMAGE} . && docker push ${FILE_IMAGE}"
                    }

                    dir("ruoyi-modules/ruoyi-gen") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-gen
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-gen.jar ruoyi-modules-gen.jar
EXPOSE 9202
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-modules-gen.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gen"]
                        """.stripIndent()
                        sh "docker build -t ${GEN_IMAGE} . && docker push ${GEN_IMAGE}"
                    }

                    dir("ruoyi-modules/ruoyi-job") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-job
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-job.jar ruoyi-modules-job.jar
EXPOSE 9203
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-modules-job.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-job"]
                        """.stripIndent()
                        sh "docker build -t ${JOB_IMAGE} . && docker push ${JOB_IMAGE}"
                    }

                    dir("ruoyi-visual/ruoyi-monitor") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-monitor
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-visual-monitor.jar ruoyi-visual-monitor.jar
EXPOSE 9100
CMD ["java", "${TZ_PARAM}", "-jar", "/opt/project/ruoyi/ruoyi-visual-monitor.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-monitor"]
                        """.stripIndent()
                        sh "docker build -t ${MONITOR_IMAGE} . && docker push ${MONITOR_IMAGE}"
                    }

                    dir("ruoyi-ui") {
                        writeFile file: 'Dockerfile', text: """
FROM ${NGINX_IMAGE}
RUN ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo Asia/Shanghai > /etc/timezone
RUN mkdir -p /opt/project/ruoyi/ruoyi-front-code
COPY dist /opt/project/ruoyi/ruoyi-front-code
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
                        """.stripIndent()
                        sh """
                            docker build -t ${UI_IMAGE} . 
                            if [ \$? -eq 0 ]; then
                                echo "✅ 前端镜像构建成功"
                                docker push ${UI_IMAGE}
                            else
                                echo "❌ 前端镜像构建失败"
                                exit 1
                            fi
                        """
                    }

                    sh "docker logout ${HARBOR_AUTH_ADDR}"
                }
            }
        }

        stage('4. 部署到K8s集群') {
            steps {
                sh """
                    export KUBECONFIG=/root/.kube/config
                    DEPLOYS="ruoyi-cloud-gateway-deployment ruoyi-cloud-auth-deployment ruoyi-cloud-sys-deployment ruoyi-cloud-file-deployment ruoyi-cloud-gen-deployment ruoyi-cloud-job-deployment ruoyi-cloud-monitor-deployment ry-cloud-ui-deployment"
                    SVCS="ry-cloud-gateway-service ruoyi-cloud-auth-service ry-cloud-sys-service ry-cloud-file-service ry-cloud-gen-service ry-cloud-job-service ry-cloud-monitor-service ry-cloud-ui-service"
                    
                    for deploy in \$DEPLOYS; do
                        kubectl delete deploy \$deploy -n ${K8S_NAMESPACE} --grace-period=0 --force --ignore-not-found=true
                    done
                    
                    for svc in \$SVCS; do
                        kubectl delete svc \$svc -n ${K8S_NAMESPACE} --ignore-not-found=true
                    done
                    
                    kubectl wait --for=delete pod -n ${K8S_NAMESPACE} -l 'app in (ruoyi-cloud-gateway-pod,ruoyi-cloud-auth-pod,ry-cloud-ui-pod)' --timeout=60s || true
                    sleep 5
                    
                    kubectl taint nodes --all node-role.kubernetes.io/control-plane- || true
                """

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
          env:
            - name: TZ
              value: "Asia/Shanghai"
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          command: ["java"]
          args: [
            "${TZ_PARAM}",
            "-jar", "/opt/project/ruoyi/ruoyi-gateway.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gateway"
          ]
          livenessProbe:
            tcpSocket:
              port: 8080
            initialDelaySeconds: 180
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
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
          env:
            - name: TZ
              value: "Asia/Shanghai"
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9200
          command: ["java"]
          args: [
            "${TZ_PARAM}",
            "-Dspring.component-scan.base-packages=com.ruoyi",
            "-jar", "/opt/project/ruoyi/ruoyi-auth.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-auth"
          ]
          livenessProbe:
            tcpSocket:
              port: 9200
            initialDelaySeconds: 200
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
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
    - port: 8080
      targetPort: 9200
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

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
          env:
            - name: TZ
              value: "Asia/Shanghai"
          resources:
            limits:
              memory: "1Gi"
              cpu: "500m"
            requests:
              memory: "512Mi"
              cpu: "200m"
          imagePullPolicy: Always
          ports:
            - containerPort: 9201
          command: ["java"]
          args: [
            "${TZ_PARAM}",
            "-jar", "/opt/project/ruoyi/ruoyi-modules-system.jar",
            "--spring.profiles.active=k8s",
            "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-sys"
          ]
          livenessProbe:
            tcpSocket:
              port: 9201
            initialDelaySeconds: 200
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 5
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
    - port: 8080
      targetPort: 9201
                    """.stripIndent()
                    sh "kubectl apply -f deployment.yml"
                }

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
          env:
            - name: TZ
              value: "Asia/Shanghai"
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
            "${TZ_PARAM}",
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
          env:
            - name: TZ
              value: "Asia/Shanghai"
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
            "${TZ_PARAM}",
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
          env:
            - name: TZ
              value: "Asia/Shanghai"
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
            "${TZ_PARAM}",
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
          env:
            - name: TZ
              value: "Asia/Shanghai"
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
            "${TZ_PARAM}",
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
      initContainers:
        - name: wait-for-all-core-services
          image: ${BUSYBOX_IMAGE}
          command:
            - sh
            - -c
            - |
              echo "=== 等待所有核心服务端口就绪 ==="
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
          env:
            - name: TZ
              value: "Asia/Shanghai"
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
          volumeMounts:
            - mountPath: /etc/nginx/conf.d
              name: nginx-config
          livenessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 2
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

                sh """
                    echo "===== 所有Pod状态 ====="
                    kubectl get pods -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud|ry-cloud-ui'
                    echo "===== 所有服务状态 ====="
                    kubectl get svc -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud|ry-cloud-ui'
                    kubectl wait --for=condition=ready pod -l app=ry-cloud-ui-pod -n ${K8S_NAMESPACE} --timeout=1800s || true
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
            echo "❌ 部署失败！"
            sh """
                kubectl get pods -n ${K8S_NAMESPACE} | grep -E 'ruoyi-cloud|ry-cloud-ui' || true
                kubectl logs -n ${K8S_NAMESPACE} -l app=ruoyi-cloud-auth-pod --tail=200 || true
                kubectl logs -n ${K8S_NAMESPACE} -l app=ry-cloud-ui-pod -c wait-for-all-core-services --tail=200 || true
            """
        }
    }
}