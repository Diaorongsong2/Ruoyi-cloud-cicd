# 若依（RuoYi-Cloud）全流程部署操作日志

**文档版本**：V1.0

**记录人**：刁荣松

**核心项目**：ruoyi-cloud（若依微服务版本，3.8.5）

**部署目标**：本地验证→GitLab托管→Jenkins自动化构建→Harbor镜像存储→K8s容器化上线，全流程标准化落地，确保服务稳定可用

**部署环境**：所有节点均为 CentOS 7.9 x86_64



 

[TOC]



# **1. 部署架构与环境规划**

## **1.1 节点架构表**

说明：所有节点需提前完成系统初始化（关闭防火墙、SELinux、时间同步），确保网络互通。

| 节点IP        | 节点角色      | 部署组件                                  | 核心作用                                           | 硬件配置（建议） |
| ------------- | ------------- | ----------------------------------------- | -------------------------------------------------- | ---------------- |
| 192.168.44.20 | K8s Master    | kube-apiserver、etcd、Docker、MySQL       | K8s集群控制节点，部署业务数据库                    | 4核8G，100G硬盘  |
| 192.168.44.21 | K8s Node1     | kubelet、kube-proxy、Docker、Redis、Nacos | K8s工作节点，部署中间件（缓存、配置中心）          | 4核8G，100G硬盘  |
| 192.168.44.22 | K8s Node2     | kubelet、kube-proxy、Docker、Nginx        | K8s工作节点，负载分担、Nginx备用（高可用）         | 4核8G，100G硬盘  |
| 192.168.44.10 | Docker Agent  | Docker、Maven、Node.js、Git               | 配合Jenkins执行自动化构建、前端/后端镜像打包       | 2核4G，40G硬盘   |
| 192.168.44.23 | Jenkins服务器 | Jenkins 2.426.3、Docker                   | CI/CD自动化流水线、镜像打包推送、任务调度          | 2核4G，40G硬盘   |
| 192.168.44.24 | GitLab服务器  | GitLab 16.0.0、Docker                     | 代码托管、版本控制、分支管理、代码权限控制         | 2核4G，40G硬盘   |
| 192.168.44.30 | Harbor服务器  | Harbor 2.8.3、Docker、Docker-Compose      | 私有镜像仓库、镜像管理、镜像安全校验、镜像推送拉取 | 2核4G，40G硬盘   |

## **1.2 技术栈清单**

| 技术/组件      | 版本要求 | 核心用途                               |
| -------------- | -------- | -------------------------------------- |
| JDK            | 1.8      | 后端服务运行环境，ruoyi-cloud核心依赖  |
| Maven          | 3.8.9    | 后端项目依赖管理、打包构建             |
| Node.js        | 14.0.0   | 前端项目依赖管理、打包构建（ruoyi-ui） |
| MySQL          | 5.7      | 业务数据存储、ruoyi-cloud数据库支撑    |
| Redis          | 6.2.14   | 缓存服务、会话存储、减轻数据库压力     |
| Nacos          | 2.2.3    | 微服务配置中心、服务注册与发现         |
| Docker         | 20.10.24 | 容器化构建、运行，统一部署环境         |
| Docker-Compose | 2.20.2   | Harbor服务快速部署、多容器管理         |
| Jenkins        | 2.426.3  | 自动化构建、镜像推送、流水线编排       |
| GitLab         | 16.0.0   | 代码托管、版本控制、分支管理           |
| Harbor         | 2.8.3    | 私有镜像仓库，存储前端、后端镜像       |
| Kubernetes     | 1.26.0   | 容器编排、集群部署、服务负载均衡       |
| RuoYi-Cloud    | 3.8.5    | 核心业务系统源码，微服务架构           |
| minio          |          |                                        |

 

# **2. 前置环境初始化（所有节点必做）**

## **2.1安装基础依赖包 **

```shell
yum install -y wget vim net-tools gcc gcc-c++ make
```

![1773825787871](C:\Users\刁荣松\AppData\Local\Temp\1773825787871.png)

## **2.2 Docker安装（所有节点必装）**

### **2.2.1 卸载旧版本并配置阿里云源**

```shell
# 安装Docker依赖包
[root@docker ~]# yum install -y yum-utils device-mapper-persistent-data lvm2

# 设置阿里云Docker镜像源（加速镜像拉取）
[root@docker ~]# yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
```

**2.2.2 安装指定版本Docker并验证**

```shell
# 安装Docker CE 20.10.24（稳定版本，适配后续组件）
[root@docker ~]# yum install -y docker-ce-20.10.16 docker-ce-cli-20.10.16 containerd.io

# 启动Docker服务并设置开机自启
[root@docker ~]# systemctl start docker
[root@docker ~]# systemctl enable docker

# 验证Docker安装成功（输出版本信息即正常）
[root@docker ~]# docker --version
Docker version 20.10.16, build aa7e414
```

![1773825904260](C:\Users\刁荣松\AppData\Local\Temp\1773825904260.png)



# **3. 核心组件部署（按节点执行）**

## **3.1 Docker Agent节点（192.168.44.10）部署**

核心作用：配合Jenkins执行前端、后端自动化构建，需安装构建依赖组件。

### **3.1.1 安装Maven（3.8.9，后端构建依赖）**

```shell
# 下载Maven安装包
[root@docker ~]# wget https://mirrors.aliyun.com/apache/maven/maven-3/3.8.9/binaries/apache-maven-3.8.9-bin.tar.gz

# 解压到指定目录
[root@docker ~]# tar -zxvf apache-maven-3.8.9-bin.tar.gz -C /usr/local/

# 配置环境变量（永久生效）
[root@docker ~]# vi /etc/profile
[root@docker ~]# source /etc/profile
[root@docker ~]# mvn -v
```

![1773826236591](C:\Users\刁荣松\AppData\Local\Temp\1773826236591.png)

### **3.1.2 安装Node.js（14.4.0，前端构建依赖）**

```shell
# 下载Node.js安装包
[root@docker ~]# wget https://nodejs.org/dist/v4.4.0/node-v14.4.0-linux-x64.tar.xz

# 解压到指定目录（/usr/local/）
[root@docker ~]# tar -xvf node-v14.4.0-linux-x64.tar.xz -C /usr/local/

# 配置环境变量（永久生效）
[root@docker ~]# echo "export NODE_HOME=/usr/local/node-v14.4.0-linux-x64" >> /etc/profile
[root@docker ~]# echo "export PATH=\$NODE_HOME/bin:\$PATH" >> /etc/profile
[root@docker ~]# source /etc/profile

# 验证安装成功（输出版本信息即正常）
[root@docker ~]# node -v
v14.4.0
[root@docker ~]# npm -v
6.14.5
```

![1773826316629](C:\Users\刁荣松\AppData\Local\Temp\1773826316629.png)

### **3.1.3 安装Git（代码拉取依赖）**

```shell
# 安装Git
[root@docker ~]# yum install -y git
# 验证安装成功
[root@docker ~]# git --version
```

![1773826439739](C:\Users\刁荣松\AppData\Local\Temp\1773826439739.png)

### **3.1.4 组件整体验证**

```shell
# 依次执行以下命令，均无报错、输出版本信息即正常
[root@docker ~]# docker --version
Docker version 20.10.16, build aa7e414
[root@docker ~]# mvn -v
Apache Maven 3.8.9 (e26b057cc3a17459358ef53e4d0e2e381bf08a1c)
Maven home: /usr/local/maven/apache-maven-3.8.9
Java version: 1.8.0_421, vendor: Oracle Corporation, runtime: /usr/local/java/jdk1.8.0_421/jre
Default locale: zh_CN, platform encoding: UTF-8
OS name: "linux", version: "3.10.0-1160.71.1.el7.x86_64", arch: "amd64", family: "unix"
[root@docker ~]# node -v
v14.4.0
[root@docker ~]# git --version
git version 1.8.3.1

```

![1773826610234](C:\Users\刁荣松\AppData\Local\Temp\1773826610234.png)

## **3.2 GitLab服务器（192.168.44.24）部署**

核心作用：代码托管、版本控制，需确保容器稳定运行，网页端可正常访问。

### **3.2.1 拉取GitLab镜像并启动容器**

```shell
[root@GitLab ~]# yum -y install policycoreutils-python-utils.noarch     #安装依赖包
#安装GitLab软件包
[root@GitLab ~]# ls
gitlab-ce-12.4.6-ce.0.el7.x86_64.rpm
[root@GitLab ~]# rpm -ivh --nodeps \
> --force gitlab-ce-12.4.6-ce.0.el7.x86_64.rpm                  #强制忽略依赖安装

#重载GitLab配置
[root@GitLab ~]# gitlab-ctl reconfigure
...
Running handlers:
Running handlers complete
Chef Client finished, 527/1423 resources updated in 02 minutes 05 seconds
gitlab Reconfigured!
```

![1773826915064](C:\Users\刁荣松\AppData\Local\Temp\1773826915064.png)

**补充说明**：GitLab初始化需要5-10分钟，启动后请等待片刻再访问网页端。

### **3.2.2 网页端初始化与验证**

1. 浏览器访问：http://192.168.44.24（无需端口，默认80端口）

2. 首次访问需设置root账号密码

3. 设置完成后，使用root账号密码登录GitLab

![1773827431875](C:\Users\刁荣松\AppData\Local\Temp\1773827431875.png)

![1773827500226](C:\Users\刁荣松\AppData\Local\Temp\1773827500226.png)

## **3.3 Jenkins服务器（192.168.44.23）部署**

核心作用：自动化构建、镜像推送，需挂载Docker命令，关联Docker Agent构建机。

### **3.3.1 拉取Jenkins镜像并启动容器**

```shell
[root@Jenkins ~]# vim /etc/hosts
192.168.88.30 Jenkins
[root@Jenkins ~]# yum -y install java-11-openjdk-devel.x86_64   #安装OpenJDK11
[root@Jenkins ~]# ln -s /usr/lib/jvm/java-11-openjdk-11.0.15.0.9-2.el8_5.x86_64/ /usr/lib/jvm/jdk                                                #创建JDK环境软链接
[root@Jenkins ~]# vim /etc/bashrc 
[root@Jenkins ~]# tail -2 /etc/bashrc                           #声明JAVA_HOME环境变量
export JAVA_HOME="/usr/lib/jvm/jdk/"
export PATH=${JAVA_HOME}/bin/:$PATH
[root@Jenkins ~]# source /etc/bashrc                            #刷新当前bash环境
[root@Jenkins ~]# echo ${JAVA_HOME}                             #查看JAVA_HOME变量
/usr/lib/jvm/jdk/
[root@Jenkins ~]# which java
/usr/lib/jvm/jdk/bin/java
[root@Jenkins ~]# java -version
openjdk version "11.0.15" 2022-04-19 LTS
OpenJDK Runtime Environment 18.9 (build 11.0.15+9-LTS)
OpenJDK 64-Bit Server VM 18.9 (build 11.0.15+9-LTS, mixed mode, sharing)
[root@Jenkins ~]# 

#安装工具相关软件
[root@Jenkins ~]# yum -y install git postfix    #Git用于拉取代码、postfix用于发邮件

#安装Jenkins
[root@Jenkins ~]# ls jenkins-2.361.4-1.1.noarch.rpm 
jenkins-2.361.4-1.1.noarch.rpm
[root@Jenkins ~]# yum -y localinstall ./jenkins-2.361.4-1.1.noarch.rpm  #安装Jenkins

#启动Jenkins服务
[root@Jenkins ~]# systemctl enable jenkins.service              #设置Jenkins开机自启动
[root@Jenkins ~]# systemctl start jenkins.service               #启动Jenkins服务器
[root@Jenkins ~]# ss -antpul | grep java                        #确认8080端口被监听
tcp   LISTEN 0      50     *:8080     *:*    users:(("java",pid=13602,fd=8))
```

![1773827823795](C:\Users\刁荣松\AppData\Local\Temp\1773827823795.png)

**3.3.2 Jenkins初始化配置**

1. 浏览器访问：http://192.168.44.23:8080（Jenkins默认端口8080）

2. 输入查询到的初始密码，点击“继续”

3. 选择“安装推荐的插件”，等待插件安装完成

4. 插件安装完成后，创建管理员账号

5. 完成初始化，进入Jenkins首页

![1773827932933](C:\Users\刁荣松\AppData\Local\Temp\1773827932933.png)

## **3.4 Harbor服务器（192.168.44.30）部署**

核心作用：私有镜像仓库，存储前端、后端镜像，需先安装Docker-Compose依赖。

### **3.4.1 安装Docker-Compose（Harbor依赖）**

```shell
# 下载Docker-Compose 1.25.0版本
wget https://github.com/docker/compose/releases/download/v1.25.0/docker-compose-linux-x86_64

# 重命名并赋予执行权限（便于直接使用docker-compose命令）
mv docker-compose-linux-x86_64 /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# 验证安装成功（输出版本信息即正常）
docker-compose --version
```

![1773828364726](C:\Users\刁荣松\AppData\Local\Temp\1773828364726.png)

### **3.4.2 部署并配置Harbor**

```shell
# 下载Harbor 2.5.3离线安装包（避免在线安装卡顿）
wget https://github.com/goharbor/harbor/releases/download/v2.5.3/harbor-offline-installer-v2.5.3.tgz

# 解压安装包到指定目录（/usr/local/）
tar -zxvf harbor-offline-installer-v2.5.3.tgz -C /usr/local/
cd /usr/local/harbor

# 复制配置文件模板（修改模板配置，避免直接修改原文件）
cp harbor.yml.tmpl harbor.yml

# 编辑配置文件（核心修改3点，使用vim编辑）
vim harbor.yml
# 1. 修改hostname为Harbor服务器IP：harbor: 192.168.44.30

# 安装并启动Harbor（执行安装脚本）
./install.sh

# 查看Harbor所有容器运行状态（所有容器状态为Up即正常）
docker-compose ps
```

![1773828326261](C:\Users\刁荣松\AppData\Local\Temp\1773828326261.png)

### **3.4.3 网页端验证Harbor**

1. 浏览器访问：http://192.168.44.30（默认80端口）

2. 使用账号：admin，密码：123456登录
3. 登录后，创建项目（命名为ruoyi-cloud，用于存储若依项目镜像

![1773828495045](C:\Users\刁荣松\AppData\Local\Temp\1773828495045.png)

## **3.5 K8s集群验证（复用已有环境，无需重新搭建）**

本次部署复用已搭建完成的K8s v1.26.3集群（Master：192.168.44.20；Node：192.168.44.21、22），仅需验证集群状态，确保后续项目可正常部署。

### **3.5.1 节点状态验证（Master节点192.168.44.20执行）**

```shell
[root@master ~]# kubectl get nodes
```



![1773828561886](C:\Users\刁荣松\AppData\Local\Temp\1773828561886.png)

### **3.5.2 核心组件状态验证（Master节点192.168.44.20执行）**

```shell
[root@master ~]# kubectl get pods -n kube-system
```

![1773828624535](C:\Users\刁荣松\AppData\Local\Temp\1773828624535.png)

# **4. 本地ruoyi-cloud代码验证**

核心目标：在本地（Windows/macOS）搭建开发环境，验证ruoyi-cloud源码可正常启动，排除代码本身及环境依赖问题，为后续上传GitLab、自动化构建铺垫。

## **4.1 本地环境准备（Windows为例，macOS操作类似）**

### **4.1.1 安装JDK 1.8**

1. 下载JDK 1.8：https://www.oracle.com/java/technologies/downloads/#java8-windows

2. 安装JDK，记住安装路径（如C:\Program Files\Java\jdk1.8.0_381）

3. 配置环境变量：

￮ 新建系统变量JAVA_HOME，值为JDK安装路径

￮ 在Path变量中添加%JAVA_HOME%\bin、%JAVA_HOME%\jre\bin

4. 验证安装：打开CMD，输入java -version，输出版本信息即正常

![1773829303990](C:\Users\刁荣松\AppData\Local\Temp\1773829303990.png)

### **4.1.2 安装Maven 3.8.6**

1. 下载Maven 3.8.6：https://maven.apache.org/download.cgi（选择bin.tar.gz格式）

2. 解压到指定目录（如D:\apache-maven-3.8.6）

3. 配置环境变量：

￮ 新建系统变量MAVEN_HOME，值为Maven解压路径

￮ 在Path变量中添加%MAVEN_HOME%\bin

4. 配置阿里云私服（加速依赖下载）：编辑Maven安装目录下的conf/settings.xml，在mirrors标签中添加：
        <mirror>
    <id>aliyunmaven</id>
    <mirrorOf>central</mirrorOf>
    <name>阿里云公共仓库</name>
    <url>https://maven.aliyun.com/repository/public</url>
</mirror>

5. 验证安装：CMD输入mvn -v，输出版本信息即正常

![1773829329274](C:\Users\刁荣松\AppData\Local\Temp\1773829329274.png)

### **4.1.3 安装MySQL 8.0.36 & Redis 6.2.14**

#### **MySQL安装与配置**

1. 下载MySQL 5.7.36：https://dev.mysql.com/downloads/installer/
2. 安装过程中，设置root账号密码，端口保持默认3306

#### **Redis安装与配置**

1. 下载Redis 6.2.14：https://github.com/tporadowski/redis/releases
2. 解压到指定目录（如D:\Redis），双击redis-server.exe启动Redis服务（默认端口6379）
3. 验证安装：双击redis-cli.exe，输入ping，返回PONG即正常

### **4.1.4 安装Nacos 2.2.3（本地测试用）**

1. 下载Nacos 2.2.3：https://nacos.io/zh-cn/docs/quick-start.html（选择zip格式）

2. 解压到指定目录（如D:\nacos），进入bin目录，双击startup.cmd启动Nacos（默认端口8848）

3. 验证安装：浏览器访问http://localhost:8848/nacos，使用默认账号nacos/nacos登录，即正常

## **4.2 拉取并配置ruoyi-cloud源码**

### **4.2.1 克隆ruoyi-cloud源码**

```shell
git clone https://gitee.com/y_project/RuoYi-Cloud.git ruoyi-cloud
```

![1773829582657](C:\Users\刁荣松\AppData\Local\Temp\1773829582657.png)

### **4.2.2 后端配置（IDEA打开项目）**

1. 打开IDEA，导入（选择maven项目，自动加载依赖）
2. 创建MySQL数据库：登录本地MySQL，执行项目中sql目录下的脚本（ry_20210908.sql、ry_config_20210908.sql），创建ry-cloud、ry-config两个数据库
3. 修改后端配置文件（核心修改3处）：

￮ 修改ruoyi-admin模块（ruoyi-cloud\ruoyi-admin\src\main\resources）下的application-druid.yml：配置本地MySQL地址、账号、密码（url: jdbc:mysql://localhost:3306/ry-cloud?xxx，username: root，password: Root@123）

￮ 修改ruoyi-admin模块下的bootstrap.yml：配置本地Redis（host: localhost，port: 6379）、本地Nacos（server-addr: localhost:8848）

￮ 修改所有微服务模块（ruoyi-gateway、ruoyi-auth等）下的bootstrap.yml，统一配置本地Nacos地址

### **4.2.3 前端配置（VS Code打开项目）**

1. 打开VS Code，导入ruoyi-ui模块（ruoyi-cloud\ruoyi-ui）

2. 打开终端，进入ruoyi-ui目录，安装前端依赖：
        npm install --legacy-peer-deps  # 避免依赖版本冲突

3. 修改前端配置文件：编辑.ruoyi-ui\.env.development，设置VUE_APP_BASE_API = http://localhost:8080（对应后端ruoyi-admin端口）

## **4.3 前后端启动与功能验证**

### **4.3.1 启动后端服务（IDEA中）**

1. 启动顺序：先启动Nacos（已启动）→ 启动ruoyi-gateway（网关）→ 启动ruoyi-auth（认证服务）→ 启动ruoyi-admin（核心服务）

2. 观察控制台日志，无报错、显示“Started XxxApplication in xxx seconds”即启动成功

**截图时机**：ruoyi-admin启动成功日志截图，命名「图4-14：本地-后端服务启动成功」

### **4.3.2 启动前端服务（VS Code中）**

启动成功后，浏览器自动打开http://localhost:80（前端默认端口）

**截图时机**：前端启动成功终端截图+浏览器访问页面截图，命名「图4-15：本地-前端服务启动成功」

### **4.3.3 核心功能验证**

1. 登录：使用默认账号admin，密码admin123登录系统

2. 验证功能：访问“系统管理→用户管理”，查看用户列表；点击“新增用户”，测试新增功能；访问“代码生成”，测试代码生成功能

**截图时机**：1. 系统登录成功首页截图，命名「图4-16：本地-系统登录成功」；2. 用户管理页面截图，命名「图4-17：本地-核心功能验证」

**补充说明**：若启动失败，优先排查配置文件（数据库、Redis、Nacos地址），确保依赖服务正常运行。

 

## **5. GitLab代码托管与分支管理**

核心目标：将本地验证通过的ruoyi-cloud源码上传到GitLab，进行版本控制，为Jenkins自动化构建提供代码来源。

**5.1 GitLab创建项目仓库**

1. 登录GitLab（http://192.168.44.24），使用root账号登录

2. 点击右上角“New project”，创建项目：

￮ Project name：ruoyi-cloud（与项目名称一致）

￮ Project description：若依微服务项目，全流程部署测试

￮ Visibility Level：Public（测试环境，便于访问；生产环境可设为Private）

￮ 点击“Create project”，完成项目创建

![1773829654655](C:\Users\刁荣松\AppData\Local\Temp\1773829654655.png)

## **5.2 本地代码上传到GitLab**

```shell
# 打开CMD，进入本地ruoyi-cloud项目目录（D:\project\ruoyi-cloud）
cd D:\project\ruoyi-cloud

# 初始化git仓库（若未初始化）
git init

# 添加所有文件到暂存区
git add .

# 提交到本地仓库
git commit -m "初始化ruoyi-cloud源码，本地验证通过"

# 关联GitLab远程仓库（替换下面的地址为自己GitLab项目的地址）
git remote add origin http://192.168.44.24/root/ruoyi-cloud.git

# 拉取远程仓库（首次上传，避免冲突）
git pull origin main --allow-unrelated-histories

# 推送本地代码到GitLab远程仓库（main分支）
git push -u origin main
```

![1773829876590](C:\Users\刁荣松\AppData\Local\Temp\1773829876590.png)

### **5.3 GitLab分支管理（规范版本控制）**

1. 在GitLab项目页面，点击“Branches”，创建分支：

￮ Branch name：dev（开发分支，用于日常开发）

￮ Create from：main（从主分支创建）

2. 本地切换到dev分支，后续开发、修改均在dev分支进行，测试通过后再合并到main分支：

​        # 本地切换到dev分支
git checkout -b dev
\# 推送dev分支到远程仓库
git push -u origin dev

![1773829998032](C:\Users\刁荣松\AppData\Local\Temp\1773829998032.png)

**补充说明**：分支管理规范：main分支为生产分支，仅用于发布稳定版本；dev分支为开发分支，日常开发、代码修改均在dev分支进行，测试通过后通过GitLab合并请求（Merge Request）合并到main分支，避免直接修改main分支代码。

 

# **6. Jenkins CI/CD流水线配置**

核心目标：配置Jenkins流水线，实现从GitLab拉取代码→自动化构建（前端/后端）→镜像打包→推送至Harbor，全程自动化，减少手动操作，确保构建一致性。

## **6.1 Jenkins前置配置（关联GitLab、Docker Agent）**

### **6.1.1 安装必备插件**

1. 登录Jenkins（http://192.168.44.23:8080），使用admin账号登录

2. 点击左侧「系统管理」→「插件管理」→「可选插件」，搜索并安装以下插件（勾选后点击「直接安装」，安装完成后重启Jenkins）：
    Git Plugin（关联GitLab，拉取代码）

3. Maven Integration Plugin（后端Maven构建）

4. NodeJS Plugin（前端Node构建）

5. Docker Plugin（Docker镜像打包、推送）

6. Pipeline Plugin（流水线配置）

![1773830271386](C:\Users\刁荣松\AppData\Local\Temp\1773830271386.png)

### **6.1.2 配置Maven、NodeJS环境**

1. 配置Maven环境：点击「系统管理」→「全局工具配置」→「Maven」→「新增Maven」，配置如下：
    Name：Maven-3.8.6（自定义，便于识别）

2. MAVEN_HOME：/usr/local/apache-maven-3.8.6（Docker Agent节点的Maven安装路径）

3. 配置NodeJS环境：点击「全局工具配置」→「NodeJS」→「新增NodeJS」，配置如下：
    Name：NodeJS-16.18.0（自定义）

4. 勾选「安装自动安装」，选择版本16.18.0（与Docker Agent节点Node版本一致）

5. 点击页面底部「保存」，完成环境配置

![1773830335630](C:\Users\刁荣松\AppData\Local\Temp\1773830335630.png)

### **6.1.3 关联GitLab（授权拉取代码）**

1. 获取GitLab私人令牌：登录GitLab（http://192.168.44.24），点击右上角头像→「Preferences」→「Access Tokens」，

2. Scopes：勾选「read_repository」「write_repository」

3. 点击「Create personal access token」

4. Jenkins配置GitLab令牌：登录Jenkins，点击「系统管理」→「系统」→「GitLab」，配置如下：

​    GitLab server URL：http://192.168.44.24（GitLab服务器IP）

5. Credentials：点击「添加」→「Jenkins」，选择「Username with password」，Username填root，Password填GitLab私人令牌，ID自定义（如gitlab-root-token），点击「添加」

6. 点击「Test Connection」，提示「Success」即关联成功

7. 点击「保存」，完成GitLab关联

   ![1773830429208](C:\Users\刁荣松\AppData\Local\Temp\1773830429208.png)

### **6.1.4 配置Docker Agent构建机（关联Jenkins）**

1. 获取Docker Agent节点密钥（用于Jenkins远程连接）：登录Docker Agent节点（192.168.44.10），执行以下命令：
    # 生成密钥（无需输入密码，直接回车即可）
ssh-keygen -t rsa
\# 查看公钥内容，复制全部内容
cat ~/.ssh/id_rsa.pub
\# 将公钥添加到authorized_keys，授权Jenkins访问
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys

2. Jenkins配置Docker Agent：点击「系统管理」→「节点管理」→「新建节点」，配置如下：
Node name：Docker-Agent-192.168.44.10（自定义，便于识别）

3. 勾选「固定节点」，点击「确定」

4. 远程工作目录：/root/jenkins/workspace（自定义，用于存储构建文件）

5. 标签：docker-agent（后续流水线指定该标签，关联此节点）

6. 启动方式：选择「通过SSH启动代理」

7. 主机：192.168.44.10（Docker Agent）

8. Credentials：点击「添加」→「Jenkins」，选择「SSH Username with private key」，Username填root，Private key选择「Enter directly」，粘贴Docker Agent节点的私钥（cat ~/.ssh/id_rsa获取），ID自定义，点击「添加」

9. 点击「保存」，然后点击节点名称，点击「启动代理」，显示「Agent is connected」即关联成功

![1773830548038](C:\Users\刁荣松\AppData\Local\Temp\1773830548038.png)

## 6.2 编写统一Jenkinsfile及相关配置文件（提交到GitLab）

核心：编写单份unified-Jenkinsfile，包含前端、后端构建的所有阶段，同步执行前后端构建、镜像打包和推送；同时完善后端ruoyi-admin、前端ruoyi-ui的Dockerfile及前端配置。

```shell
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
        MYSQL_PWD = "123456"
        REDIS_ADDR = "ry-cloud-redis-service:6379"
        REDIS_PWD = "123456"
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
```

### 6.2.1 运行统一流水线并验证

1. 登录Jenkins，点击「ruoyi-cloud」→「立即构建」，启动前后端统一流水线

2. 点击构建记录（如#1），点击「Console Output」，查看构建日志，确保每个阶段（拉取代码、并行构建前后端、并行构建镜像、并行推送镜像、清理镜像）无报错，最终显示「前后端统一流水线构建成功！」

3. 验证Harbor镜像：登录Harbor（http://192.168.44.30），进入ruoyi-cloud项目，查看是否同时存在ruoyi-cloud-backend:v1.0.0和ruoyi-cloud-frontend:v1.0.0两个镜像

   ![1773831214727](C:\Users\刁荣松\AppData\Local\Temp\1773831214727.png)

   ![1773831231961](C:\Users\刁荣松\AppData\Local\Temp\1773831231961.png)



# **7. K8s集群部署ruoyi-cloud项目**

核心目标：将Harbor中的前端、后端镜像部署到K8s集群，通过ConfigMap配置环境变量，通过Deployment管理容器，通过Service暴露服务，实现项目容器化上线。

说明：所有K8s部署命令均在Master节点（192.168.44.20）执行，所有配置文件统一放在/root/ruoyi-cloud-k8s目录下，便于管理。

## **7.1 前置准备（Master节点执行）**

1. 创建部署目录，用于存放K8s配置文件：mkdir -p /root/ruoyi-cloud-k8s
cd /root/ruoyi-cloud-k8s

2. 配置K8s拉取Harbor私有镜像的密钥（避免拉取镜像时权限不足）：
    # 创建密钥（name自定义，如harbor-secret；server为Harbor地址；username和password为Harbor账号密码）
kubectl create secret docker-registry harbor-secret --docker-server=192.168.44.30 --docker-username=admin --docker-password=123456
\# 查看密钥是否创建成功
kubectl get secrets

![1773831982149](C:\Users\刁荣松\AppData\Local\Temp\1773831982149.png)

## **7.2 中间件服务部署（ruoyi-cloud-backend）**

### **7.2.1 创建ConfigMap（配置环境变量，统一管理配置）**

1. 创建nginx.conf文件：

   ```shell
   server {
       listen       80;
       server_name  localhost;
   
       location / {
           # 镜像中存放前端静态文件的位置
           root   /opt/project/ruoyi/ruoyi-front-code;
           try_files $uri $uri/ /index.html;
           index  index.html index.htm;
       }
   
       location /prod-api/ {
           proxy_set_header Host $http_host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header REMOTE-HOST $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           # 转发到后端网关服务
           proxy_pass http://ry-cloud-gateway-service:8080;
           # 新增：适配K8s的超时配置
           proxy_connect_timeout 10s;
           proxy_read_timeout 30s;
       }
   
       # 避免actuator暴露
       location ~ /actuator {
           return 403;
       }
   
       error_page   500 502 503 504  /50x.html;
       location = /50x.html {
           root   /usr/share/nginx/html;  # 修正为Nginx内置错误页路径
       }
   }
   
   ```

   ![1773832443754](C:\Users\刁荣松\AppData\Local\Temp\1773832443754.png)

### **7.2.2 创建Deployment（部署后端容器）**



![1773834344194](C:\Users\刁荣松\AppData\Local\Temp\1773834344194.png)



### **7.2.3 创建Service（暴露后端服务，供前端访问）**

![1773834477103](C:\Users\刁荣松\AppData\Local\Temp\1773834477103.png)


## 7.3 服务部署

```shell
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
```

## **7.4 部署验证（检查所有资源状态）**

2. 确保所有资源状态正常：ConfigMap存在、Deployment READY 1/1、Pod Running、Service正常暴露端口，后端Pod日志无报错。

![1773831397699](C:\Users\刁荣松\AppData\Local\Temp\1773831397699.png)

**补充说明**：若Pod状态为Pending，可能是镜像拉取失败（检查密钥、Harbor地址）；若为CrashLoopBackOff，查看Pod日志排查配置问题（如数据库、Nacos连接失败）。

# 8.0效果展示

 前端登录：![1773831459615](C:\Users\刁荣松\AppData\Local\Temp\1773831459615.png)

页面展示：

![1773831494210](C:\Users\刁荣松\AppData\Local\Temp\1773831494210.png)

![1773831508696](C:\Users\刁荣松\AppData\Local\Temp\1773831508696.png)

minio展示：

![1773831653272](C:\Users\刁荣松\AppData\Local\Temp\1773831653272.png)

监控：

![1773831689698](C:\Users\刁荣松\AppData\Local\Temp\1773831689698.png)
