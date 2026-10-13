#!/bin/bash
# Ruoyi-cloud-cicd — Docker安装(所有节点必装)
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 卸载旧版本并配置阿里云源
# --------------------------------------------------------------------
# 安装Docker依赖包
[root@docker ~]# yum install -y yum-utils device-mapper-persistent-data lvm2

# 设置阿里云Docker镜像源（加速镜像拉取）
[root@docker ~]# yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# --------------------------------------------------------------------
# 卸载旧版本并配置阿里云源
# --------------------------------------------------------------------
# 安装Docker CE 20.10.24（稳定版本，适配后续组件）
[root@docker ~]# yum install -y docker-ce-20.10.16 docker-ce-cli-20.10.16 containerd.io

# 启动Docker服务并设置开机自启
[root@docker ~]# systemctl start docker
[root@docker ~]# systemctl enable docker

# 验证Docker安装成功（输出版本信息即正常）
[root@docker ~]# docker --version
Docker version 20.10.16, build aa7e414
