#!/bin/bash
# Ruoyi-cloud-cicd — Harbor服务器(192.168.44.30)部署
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 安装Docker-Compose(Harbor依赖)
# --------------------------------------------------------------------
# 下载Docker-Compose 1.25.0版本
wget https://github.com/docker/compose/releases/download/v1.25.0/docker-compose-linux-x86_64

# 重命名并赋予执行权限（便于直接使用docker-compose命令）
mv docker-compose-linux-x86_64 /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# 验证安装成功（输出版本信息即正常）
docker-compose --version

# --------------------------------------------------------------------
# 部署并配置Harbor
# --------------------------------------------------------------------
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
