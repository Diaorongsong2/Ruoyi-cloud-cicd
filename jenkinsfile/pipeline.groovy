pipeline {
    agent {
        node {
            label 'ruoyi-cloud'
        }
    }

    environment {
        // K8S基础配置
        K8S_MASTER = "192.168.44.20"
        K8S_NAMESPACE = "default"
        // 镜像仓库配置
        HARBOR_ADDR = "harbor:443"
        HARBOR_AUTH_ADDR = "https://harbor:443"
        HARBOR_PROJECT = "ruoyi-cloud"
        HARBOR_CRED_ID = "harbor-ruoyi"
        DOCKER_REGISTRY = "${HARBOR_ADDR}"
        // 关键修改：用时间戳+构建号确保镜像标签唯一
        BUILD_TAG = "${BUILD_NUMBER}-${currentBuild.startTimeInMillis}"
        // 构建工具路径
        JAVA_HOME = "/usr/local/java/jdk1.8.0_421"
        MAVEN_HOME = "/usr/local/maven/apache-maven-3.8.9"
        MAVEN_CMD = "${MAVEN_HOME}/bin/mvn"
        NODE_HOME = "/usr/local/node/14.4.0/bin"
        PATH = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:${NODE_HOME}:/usr/bin:/bin:/usr/local/bin"
        LOCAL_REPO_PATH = "/home/jenkins/.m2/repository"
        // 中间件地址（已修正Redis拼写）
        NACOS_INNER_ADDR = "ry-cloud-nacos-service:8848"
        MYSQL_ADDR = "ry-cloud-mysql-service:3306"
        MYSQL_USER = "root"
        MYSQL_PWD = credentials('ruoyi-mysql-pwd')      // Jenkins 凭据绑定，不写明文
        REDIS_ADDR = "ry-cloud-redis-service:6379"
        REDIS_PWD = credentials('ruoyi-redis-pwd')      // Jenkins 凭据绑定，不写明文
        // 构建路径配置
        JENKINS_WORKSPACE = "${WORKSPACE}"
        NODE_OPTIONS = "--max_old_space_size=1024"
        GATEWAY_SERVICE_NAME = "ruoyi-gateway.${K8S_NAMESPACE}.svc.cluster.local"
        // 镜像名称
        GATEWAY_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-gateway:${BUILD_TAG}"
        AUTH_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-auth:${BUILD_TAG}"
        SYSTEM_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-system:${BUILD_TAG}"
        FILE_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-file:${BUILD_TAG}"
        GEN_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-gen:${BUILD_TAG}"
        JOB_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-job:${BUILD_TAG}"
        MONITOR_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-monitor:${BUILD_TAG}"
        UI_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/ruoyi-ui:${BUILD_TAG}"
        // 基础镜像
        OPENJDK_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/openjdk:8-jre-slim"
        BUSYBOX_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/busybox:1.35"
        NGINX_IMAGE = "${DOCKER_REGISTRY}/${HARBOR_PROJECT}/nginx:alpine"
        // 端口配置
        UI_NODE_PORT = "30080"       
        MONITOR_NODE_PORT = "9100"

    }

    stages {
        stage('1. 后端构建') {
            steps {
                sh '''
                    cd ${JENKINS_WORKSPACE}
                    # 清理并构建后端项目（跳过测试加速构建）
                    ${MAVEN_CMD} clean install -DskipTests -U
                    # 验证jar包生成
                    echo "===== 生成的 jar 包列表 ====="
                    find . -name "*.jar" -path "*/target/*" | grep -E "(file|gen|job|monitor|system|auth|gateway)"
                '''
            }
        }

        stage('2. 前端打包') {
            steps {
                sh '''
                    cd ${JENKINS_WORKSPACE}/ruoyi-ui
                    # 配置npm镜像加速
                    npm config set registry https://registry.npmmirror.com/
                    npm cache clean --force
                    # 安装依赖并打包
                    npm install --no-audit --no-fund --legacy-peer-deps
                    NODE_OPTIONS="--max_old_space_size=2048" npm run build:prod
                '''
                script {
                    // 验证前端打包结果
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
                    // 登录Harbor
                    sh "echo \"\${HARBOR_PWD}\" | docker login ${HARBOR_AUTH_ADDR} -u \${HARBOR_USER} --password-stdin"

                    // 构建网关镜像
                    dir("ruoyi-gateway") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-gateway
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-gateway.jar ruoyi-gateway.jar
EXPOSE 8080
# 前台运行，避免容器退出
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-gateway.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gateway"]
                        """.stripIndent()
                        sh "docker build -t ${GATEWAY_IMAGE} . && docker push ${GATEWAY_IMAGE}"
                    }

                    // 构建认证服务镜像
                    dir("ruoyi-auth") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-auth
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-auth.jar ruoyi-auth.jar
EXPOSE 9200
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-auth.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-auth"]
                        """.stripIndent()
                        sh "docker build -t ${AUTH_IMAGE} . && docker push ${AUTH_IMAGE}"
                    }

                    // 构建系统服务镜像
                    dir("ruoyi-modules/ruoyi-system") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-sys
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-system.jar ruoyi-modules-system.jar
EXPOSE 9201
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-modules-system.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-sys"]
                        """.stripIndent()
                        sh "docker build -t ${SYSTEM_IMAGE} . && docker push ${SYSTEM_IMAGE}"
                    }

                    // 构建文件服务镜像
                    dir("ruoyi-modules/ruoyi-file") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-file
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-file.jar ruoyi-modules-file.jar
EXPOSE 9300
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-modules-file.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-file"]
                        """.stripIndent()
                        sh "docker build -t ${FILE_IMAGE} . && docker push ${FILE_IMAGE}"
                    }

                    // 构建代码生成服务镜像
                    dir("ruoyi-modules/ruoyi-gen") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-gen
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-gen.jar ruoyi-modules-gen.jar
EXPOSE 9202
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-modules-gen.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-gen"]
                        """.stripIndent()
                        sh "docker build -t ${GEN_IMAGE} . && docker push ${GEN_IMAGE}"
                    }

                    // 构建定时任务服务镜像
                    dir("ruoyi-modules/ruoyi-job") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-job
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-modules-job.jar ruoyi-modules-job.jar
EXPOSE 9203
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-modules-job.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-job"]
                        """.stripIndent()
                        sh "docker build -t ${JOB_IMAGE} . && docker push ${JOB_IMAGE}"
                    }

                    // 构建监控服务镜像
                    dir("ruoyi-visual/ruoyi-monitor") {
                        writeFile file: 'Dockerfile', text: """
FROM  ${OPENJDK_IMAGE}
RUN mkdir -p /opt/project/ruoyi/logs/ruoyi-monitor
WORKDIR /opt/project/ruoyi
COPY ./target/ruoyi-visual-monitor.jar ruoyi-visual-monitor.jar
EXPOSE 9100
CMD ["java", "-jar", "/opt/project/ruoyi/ruoyi-visual-monitor.jar", "--spring.profiles.active=k8s", "-Dlogging.path=/opt/project/ruoyi/logs/ruoyi-monitor"]
                        """.stripIndent()
                        sh "docker build -t ${MONITOR_IMAGE} . && docker push ${MONITOR_IMAGE}"
                    }

                    // 构建前端镜像
                    dir("ruoyi-ui") {
                        writeFile file: 'Dockerfile', text: """
FROM ${NGINX_IMAGE}
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

                    // 退出Harbor登录
                    sh "docker logout ${HARBOR_AUTH_ADDR}"
                }
            }
        }
