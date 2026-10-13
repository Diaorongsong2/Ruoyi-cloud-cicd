#!/bin/bash
# Ruoyi-cloud-cicd — Docker Agent节点(192.168.44.10)部署
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 安装Maven(3.8.9，后端构建依赖)
# --------------------------------------------------------------------
# 下载Maven安装包
[root@docker ~]# wget https://mirrors.aliyun.com/apache/maven/maven-3/3.8.9/binaries/apache-maven-3.8.9-bin.tar.gz

# 解压到指定目录
[root@docker ~]# tar -zxvf apache-maven-3.8.9-bin.tar.gz -C /usr/local/

# 配置环境变量（永久生效）
[root@docker ~]# vi /etc/profile
[root@docker ~]# source /etc/profile
[root@docker ~]# mvn -v

# --------------------------------------------------------------------
# 安装Node.js(14.4.0，前端构建依赖)
# --------------------------------------------------------------------
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
v16.20.2
[root@docker ~]# npm -v
8.19.4

# --------------------------------------------------------------------
# 安装Git(代码拉取依赖)
# --------------------------------------------------------------------
# 安装Git
[root@docker ~]# yum install -y git
# 验证安装成功
[root@docker ~]# git --version

# --------------------------------------------------------------------
# 组件整体验证
# --------------------------------------------------------------------
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
