#!/bin/bash
# Ruoyi-cloud-cicd — GitLab服务器(192.168.44.24)部署
# 由 SOP 中对应章节的命令整理而成（含执行输出示例）

# --------------------------------------------------------------------
# 拉取GitLab镜像并启动容器
# --------------------------------------------------------------------
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
